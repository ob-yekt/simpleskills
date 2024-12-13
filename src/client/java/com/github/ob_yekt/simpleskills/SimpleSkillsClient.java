package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ClientModInitializer;

public class SimpleSkillsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		System.out.println("[SimpleSkillsClient] Client initialized!");

		// Register the HUD renderer
		HudRenderer.registerHud();
	}
}