package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public class SimpleskillsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keybinds.register();
        SkillHudRenderer.register();
        ClientConfig.load();
        Simpleskills.LOGGER.info("[simpleskills] Skill HUD & keybinds registered.");
    }

    public static boolean isMultiplayer() {
        return !Minecraft.getInstance().hasSingleplayerServer();
    }
}