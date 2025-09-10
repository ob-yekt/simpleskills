package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ClientModInitializer;

public class SimpleskillsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register the HUD
        HudRenderer.registerHud();
        System.out.println("[simpleskills] HudRenderer has been registered for simpleskills.");
    }
}