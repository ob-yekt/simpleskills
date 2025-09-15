package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class SimpleskillsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register HUD and keybinds (safe to register always, controlled later)
        Keybinds.register();
        SkillHudRenderer.register();
        ClientConfig.load();
        Simpleskills.LOGGER.info("[simpleskills] Skill HUD & keybinds registered.");
    }

    // Helper to check if client is in multiplayer
    public static boolean isMultiplayer() {
        return !MinecraftClient.getInstance().isIntegratedServerRunning();
    }
}