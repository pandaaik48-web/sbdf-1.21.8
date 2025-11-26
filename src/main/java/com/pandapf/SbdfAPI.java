package com.pandapf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pandapf.data.DungeonStats;
import com.pandapf.data.DungeonStats.PBData;
import com.pandapf.util.TimeUtil;
import com.pandapf.util.DungeonLevelUtil;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.lang.Math;

public class SbdfAPI {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final String USER_AGENT = "sbd)";

    /**
     * Data structure to return augmented player information to the Mixin.
     */
    public static class AugmentedPartyMemberData {
        // The fully formatted lore string to be appended to the player skull.
        public final String augmentedLore;

        public AugmentedPartyMemberData(String augmentedLore) {
            this.augmentedLore = augmentedLore;
        }
    }

    // --- Core Asynchronous API Methods ---

    /**
     * Core function to fetch UUID from Mojang API.
     */
    private static CompletableFuture<String> getUuidAsync(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
                HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("id").getAsString();
                } else if (response.statusCode() == 204) {
                    Sbdf.print("UUID lookup returned 204 (No Content). Player name " + playerName + " not found.");
                    return null;
                } else {
                    Sbdf.print("UUID API failed with status " + response.statusCode() + " for player " + playerName + ". Response: " + response.body());
                    return null;
                }
            } catch (Exception e) {
                Sbdf.print("Error fetching UUID for player " + playerName + ": " + e.getMessage());
            }
            return null;
        }, EXECUTOR);
    }

    /**
     * Fetches SkyBlock Dungeon data using the sbd.evankhell.workers.dev API endpoint.
     */
    private static CompletableFuture<DungeonStats> getDungeonsDataAsync(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = String.format("https://sbd.evankhell.workers.dev/player/%s", uuid);
                HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        return transformFunc(json);
                    } else {
                        Sbdf.print("Data API success=false for UUID " + uuid + ".");
                    }
                } else {
                    Sbdf.print("Data API failed with status " + response.statusCode() + " for UUID " + uuid + ". Response: " + response.body());
                }
            } catch (Exception e) {
                Sbdf.print("Error fetching Dungeons data for UUID " + uuid + ": " + e.getMessage());
            }
            return null;
        }, EXECUTOR);
    }

    // --- JSON Transformation ---

    /**
     * Logic to transform the raw JSON object from the simpler API.
     */
    private static DungeonStats transformFunc(JsonObject data) {
        try {
            JsonObject dungeonsData = data.getAsJsonObject("dungeons");
            if (dungeonsData == null) return null;

            double cataXp = dungeonsData.get("cataxp").getAsDouble();
            int secrets = dungeonsData.get("secrets").getAsInt();

            double cataLevel = DungeonLevelUtil.calculateLevel(cataXp);

            Map<String, Integer> runsMap = new HashMap<>();
            if (dungeonsData.has("runs") && dungeonsData.get("runs").isJsonPrimitive()) {
                runsMap.put("total", dungeonsData.get("runs").getAsInt());
            } else if (dungeonsData.has("runs") && dungeonsData.get("runs").isJsonObject()) {
                dungeonsData.getAsJsonObject("runs").asMap().forEach((key, value) -> runsMap.put(key, value.getAsInt()));
            }

            Map<String, Map<Integer, PBData>> pb = new HashMap<>();
            JsonObject pbData = dungeonsData.getAsJsonObject("pb");

            JsonObject catacombsPb = pbData.getAsJsonObject("catacombs");
            JsonObject masterCatacombsPb = pbData.getAsJsonObject("master_catacombs");

            pb.put("catacombs", extractPBs(catacombsPb));
            pb.put("master_catacombs", extractPBs(masterCatacombsPb));

            return new DungeonStats(pb, cataXp, secrets, runsMap, cataLevel);

        } catch (Exception e) {
            Sbdf.print("Error transforming Dungeons JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Helper to process catacombs or master_catacombs PB object.
     */
    private static Map<Integer, PBData> extractPBs(JsonObject floorPbObject) {
        Map<Integer, PBData> map = new HashMap<>();
        if (floorPbObject == null) return map;

        for (int i = 1; i <= 7; i++) {
            String floorKey = String.valueOf(i);
            if (floorPbObject.has(floorKey)) {
                JsonObject floorData = floorPbObject.getAsJsonObject(floorKey);
                Long rawSPlusTime = null;

                // rawS+ must be a number (Long) to be extracted, excluding strings like "No S+"
                if (floorData.has("rawS+") && floorData.get("rawS+").isJsonPrimitive() && floorData.get("rawS+").getAsJsonPrimitive().isNumber()) {
                    rawSPlusTime = floorData.get("rawS+").getAsLong();
                }

                String sPlusTime = TimeUtil.timeToString(rawSPlusTime);

                // We only care about S+ for the PF display, so other fields are minimal
                map.put(i, new PBData("N/A", sPlusTime, null, rawSPlusTime));
            }
        }
        return map;
    }

    // --- Party Finder API Lookup ---

    /**
     * Fetches comprehensive dungeon stats for a player and formats an augmented lore string
     * for use in Party Finder member skulls.
     *
     * @param playerName The name of the player to look up.
     * @param dungeonMode The mode: "Master Mode" or "The Catacombs" (used to determine PB lookup key).
     * @param floorNum The floor number (1-7).
     * @param callback The consumer to handle the resulting AugmentedPartyMemberData or null on failure.
     */
    public static void fetchAugmentedPartyMemberDataAsync(String playerName, String dungeonMode, int floorNum, Consumer<AugmentedPartyMemberData> callback) {
        // Step 1 & 2: Get UUID and then DungeonStats
        getUuidAsync(playerName)
                .thenCompose(uuid -> {
                    if (uuid == null) return CompletableFuture.completedFuture(null);
                    return getDungeonsDataAsync(uuid);
                })
                .thenAccept(dungeonStats -> {
                    if (dungeonStats == null) {
                        // Player not found or no dungeon stats
                        callback.accept(null);
                        return;
                    }

                    // Step 3-5: Format the augmented lore string
                    String augmentedLore = formatAugmentedLore(dungeonStats, dungeonMode, floorNum, playerName);
                    callback.accept(new AugmentedPartyMemberData(augmentedLore));
                })
                .exceptionally(e -> {
                    Sbdf.print("FATAL API CHAIN ERROR (fetchAugmentedPartyMemberDataAsync): " + e.getMessage());
                    e.printStackTrace();
                    callback.accept(null); // Send null back on error
                    return null;
                });
    }

    /**
     * Formats the detailed lore string for a party member.
     * Expected format: (cata level) [secrets/secret per run] [fastest s+ of that floor and if master mode the master mode version]
     */
    private static String formatAugmentedLore(DungeonStats stats, String dungeonMode, int floorNum, String playerName) {
        try {
            // 1. Determine run type key and mode prefix
            boolean isMasterMode = dungeonMode.toLowerCase().contains("master");
            String pbKey = isMasterMode ? "master_catacombs" : "catacombs";

            // 2. Cata Level
            int achievedLevel = (int) Math.floor(stats.cataLevel);
            String cataLevelPart = String.format("§a(%d)", achievedLevel);

            // 3. Secrets per Run / Total Secrets
            int totalRuns = stats.runs.getOrDefault("total", 0);

            double secretsPerRun = (double) stats.secrets / totalRuns;
            String secretsPart = String.format("§b[%s/%.2f]", stats.secrets ,secretsPerRun);

            // 4. Fastest S+ Time
            // Safely retrieve PB map, then floor data.
            PBData pb = stats.pb.getOrDefault(pbKey, new HashMap<>()).get(floorNum);
            String sPlusTime = (pb != null && pb.sPlusTime != null && !pb.sPlusTime.equals("N/A")) ? pb.sPlusTime : "N/A";

            String pbPart = String.format("§e[%s]", sPlusTime);

            // 5. Combine everything
            // Note: The Mixin should append this to the existing Name (class level) line.
            return String.format(" %s %s %s", cataLevelPart, secretsPart, pbPart);
        } catch (Exception e) {
            Sbdf.print("Error formatting augmented lore for " + playerName + ": " + e.getMessage());
            // Return a safe error message in the lore instead of crashing
            return " §c(API Error)";
        }
    }
}