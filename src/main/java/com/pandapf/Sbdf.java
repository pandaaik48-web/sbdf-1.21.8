package com.pandapf;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Sbdf implements ClientModInitializer {

	private static String currentlySelected = null;

	// --- Dungeon Context Accessors ---
	private static String currentDungeonMode = null;
	private static int currentFloorNum = -1;

	public static void setCurrentDungeonContext(String mode, int floor) {
		currentDungeonMode = mode;
		currentFloorNum = floor;
	}

	public static String getCurrentDungeonMode() {
		return currentDungeonMode;
	}

	public static int getCurrentFloorNum() {
		return currentFloorNum;
	}

	/**
	 * Clears all context and tooltip cache when Party Finder menu closes
	 */
	public static void clearAllContext() {
		currentDungeonMode = null;
		currentFloorNum = -1;

		// Clear the ItemStackMixin cache via reflection
		try {
			Class<?> mixinClass = Class.forName("com.pandapf.mixin.ItemStackMixin");
			java.lang.reflect.Method clearMethod = mixinClass.getMethod("clearCache");
			clearMethod.invoke(null);
			print("Cleared dungeon context and tooltip cache");
		} catch (Exception e) {
			print("Warning: Could not clear ItemStackMixin cache - " + e.getMessage());
		}
	}

	@Override
	public void onInitializeClient() {
		System.out.println("Client-only mod loaded!");
	}

	public static void setCurrentlySelected(String value) {
		currentlySelected = value;
	}

	public static String getCurrentlySelected() {
		return currentlySelected;
	}

	public static void print(String message) {
		System.out.println("[SBDF] " + message);
	}

	public static void printChat(Text message) {
		if (MinecraftClient.getInstance().player != null) {
			MinecraftClient.getInstance().player.sendMessage(message, false);
		}
	}

	public static class PlayerData {
		public final String fastestPB;
		public final String playerClass;

		public PlayerData(String fastestPB, String playerClass) {
			this.fastestPB = fastestPB;
			this.playerClass = playerClass;
		}
	}
}