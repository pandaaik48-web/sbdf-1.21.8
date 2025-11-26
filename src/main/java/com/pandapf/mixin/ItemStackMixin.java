package com.pandapf.mixin;

import com.pandapf.Sbdf;
import com.pandapf.SbdfAPI;
import com.pandapf.SbdfAPI.AugmentedPartyMemberData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * Mixin to inject augmented dungeon stats into the lore of player skulls
 * in the Party Finder menu by intercepting the standard tooltip generation.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Unique
    private static final Map<String, String> playerDataCache = new HashMap<>();

    @Unique
    private static final Set<String> fetchingPlayers = new HashSet<>();

    /**
     * Injects into the getTooltip method to add Dungeon Stats information
     * specifically for player heads in the Party Finder menu.
     */
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void sbdf_augmentSkullLore(
            Item.TooltipContext context,
            net.minecraft.entity.player.PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        ItemStack stack = (ItemStack)(Object)this;

        // 1. Check if the item is a Player Head
        if (stack.getItem() != Items.PLAYER_HEAD) {
            return;
        }

        // 2. Get Dungeon Context
        String dungeonMode = Sbdf.getCurrentDungeonMode();
        int floorNum = Sbdf.getCurrentFloorNum();

        // Only proceed if we are in a Party Finder screen with known dungeon context
        if (dungeonMode == null || floorNum == -1) return;


        List<Text> tooltip = cir.getReturnValue();

        // 3. Find the "Members:" line and process the next 5 lines
        boolean foundMembers = false;

        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();

            // Find the Members: section
            if (line.contains("Members:")) {
                foundMembers = true;
                continue;
            }

            // Process the 5 lines after Members:
            if (foundMembers) {
                String cleanLine = line.replaceAll("§.", "").trim();

                // Skip empty lines
                if (cleanLine.isEmpty() || cleanLine.equalsIgnoreCase("Empty")) {
                    continue;
                }

                // Check if this line has a player name with class level: "PlayerName (ClassLevel)"
                if (cleanLine.contains("(") && cleanLine.contains(")")) {
                    int openParen = cleanLine.indexOf('(');
                    if (openParen <= 0) continue;

                    String playerName = cleanLine.substring(0, openParen).trim();

                    if (playerName.contains(":")) {
                        playerName = playerName.substring(0, playerName.indexOf(":")).trim();
                    }

                    // Already fetched?
                    if (playerDataCache.containsKey(playerName)) {

                        String augmentedLine = line + playerDataCache.get(playerName);
                        tooltip.set(i, Text.literal(augmentedLine));

                    }
                    // Not fetched yet
                    else if (!fetchingPlayers.contains(playerName)) {

                        fetchingPlayers.add(playerName);
                        tooltip.set(i, Text.literal(line + " §8[...]"));

                        final String finalPlayerName = playerName;

                        // Start async fetch
                        SbdfAPI.fetchAugmentedPartyMemberDataAsync(
                                finalPlayerName,
                                dungeonMode,
                                floorNum,
                                (AugmentedPartyMemberData data) -> {
                                    // THREAD-SAFETY FIX: Schedule cache update on Minecraft's client thread
                                    // to prevent race conditions during Alt-Tab events
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client != null) {
                                        client.execute(() -> {
                                            fetchingPlayers.remove(finalPlayerName);

                                            if (data != null) {
                                                playerDataCache.put(finalPlayerName, data.augmentedLore);
                                                Sbdf.print("Cached data for " + finalPlayerName + ": " + data.augmentedLore);
                                            } else {
                                                playerDataCache.put(finalPlayerName, " §c[?]");
                                                Sbdf.print("Fetch failed for " + finalPlayerName);
                                            }
                                        });
                                    }
                                }
                        );

                    }
                    // Still fetching
                    else {
                        tooltip.set(i, Text.literal(line + " §8[...]"));
                    }
                }
            }
        }
    }

    /**
     * Clears cache safely.
     * Must be private or mixin will crash on modern versions.
     */
    @Unique
    private static void clearCache() {
        playerDataCache.clear();
        fetchingPlayers.clear();
        Sbdf.print("Cleared ItemStackMixin cache");
    }
}
