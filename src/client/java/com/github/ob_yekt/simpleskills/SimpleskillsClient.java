package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class SimpleskillsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register HUD and keybinds without restriction here
        SkillHudRenderer.register();
        Keybinds.register();
        Simpleskills.LOGGER.info("[simpleskills] Skill HUD & keybinds registered.");
    }
}