package com.pandapf.mixin;

import com.pandapf.Sbdf;
import com.pandapf.util.RomanNumeralUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected ScreenHandler handler;

    @Unique
    private boolean hasProcessedGate = false;

    @Unique
    private final Set<Integer> slotsToHighlight = new HashSet<>();

    @Unique
    private boolean hasExtractedMetadata = false;

    @Unique
    private static final Pattern FLOOR_PATTERN = Pattern.compile("Floor ([IVX]+)");

    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        String title = screen.getTitle().getString();

        if (title.isEmpty()) return;

        ScreenHandler screenHandler = this.handler;
        ClientPlayerEntity player = client.player;
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(client.world);

        // 1. Catacombs Gate Logic (Class Selection)
        if (title.equals("Catacombs Gate") && !hasProcessedGate) {
            for (Slot slot : screenHandler.slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                List<Text> lore = stack.getTooltip(tooltipContext, player, TooltipType.BASIC);
                for (Text line : lore) {
                    String s = line.getString();
                    if (s.startsWith("Currently Selected:")) {
                        String value = s.substring("Currently Selected:".length()).trim();
                        Sbdf.setCurrentlySelected(value);
                        Sbdf.print("Detected class: " + value);
                        hasProcessedGate = true;
                        return;
                    }
                }
            }
        }

        // 2. Party Finder Logic
        else if (title.startsWith("Party Finder")) {
            String myClass = Sbdf.getCurrentlySelected();
            slotsToHighlight.clear();

            // Extract dungeon metadata from Nether Star (only once per open screen)
            if (!hasExtractedMetadata) {
                extractDungeonMetadata(screenHandler, tooltipContext, player);
            }

            if (myClass == null) return;

            for (Slot slot : screenHandler.slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                String name = stack.getName().getString();

                // Party Listing Logic (Highlighting)
                if (name.contains("Party")) {
                    List<Text> lore = stack.getTooltip(tooltipContext, player, TooltipType.BASIC);
                    boolean containsMyClass = false;

                    for (Text line : lore) {
                        if (line.getString().contains(myClass)) {
                            containsMyClass = true;
                            break;
                        }
                    }

                    // Highlight if party doesn't need my class
                    if (!containsMyClass) {
                        slotsToHighlight.add(slot.id);
                    }
                }
            }
        }
    }

    /**
     * Extracts dungeon mode and floor number from the Nether Star in Party Finder
     * and sets the context using Sbdf.setCurrentDungeonContext()
     */
    @Unique
    private void extractDungeonMetadata(ScreenHandler screenHandler, Item.TooltipContext tooltipContext, ClientPlayerEntity player) {
        String dungeonMode = null;
        int floorNum = -1;

        for (Slot slot : screenHandler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || stack.getItem() != Items.NETHER_STAR) continue;

            List<Text> lore = stack.getTooltip(tooltipContext, player, TooltipType.BASIC);

            for (Text line : lore) {
                String text = line.getString();

                // Extract dungeon mode
                if (text.startsWith("Dungeon:")) {
                    String dungeonInfo = text.substring("Dungeon:".length()).trim();
                    if (dungeonInfo.contains("Master Mode")) {
                        dungeonMode = "Master Mode";
                    } else {
                        dungeonMode = "The Catacombs";
                    }
                }

                // Extract floor: "Floor: Floor VII" -> 7
                else if (text.startsWith("Floor:")) {
                    String floorInfo = text.substring("Floor:".length()).trim();
                    Matcher matcher = FLOOR_PATTERN.matcher(floorInfo);
                    if (matcher.find()) {
                        floorNum = RomanNumeralUtil.romanToInt(matcher.group(1));
                    }
                }
            }

            // Found the nether star
            if (dungeonMode != null && floorNum != -1) {
                hasExtractedMetadata = true;
                Sbdf.setCurrentDungeonContext(dungeonMode, floorNum);
                Sbdf.print("Detected dungeon: " + dungeonMode + " Floor " + floorNum);
                return;
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!slotsToHighlight.isEmpty()) {
            int color = 0x8000FF00; // Semi-transparent green

            for (Integer slotId : slotsToHighlight) {
                if (slotId >= this.handler.slots.size()) continue;

                Slot slot = this.handler.slots.get(slotId);
                int renderX = this.x + slot.x;
                int renderY = this.y + slot.y;

                context.fill(renderX, renderY, renderX + 16, renderY + 16, color);
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        hasProcessedGate = false;
        slotsToHighlight.clear();
        hasExtractedMetadata = false;

        // Clear all dungeon context and tooltip cache
        Sbdf.clearAllContext();
    }
}