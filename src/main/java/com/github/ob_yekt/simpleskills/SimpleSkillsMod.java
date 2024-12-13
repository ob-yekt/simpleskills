package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ModInitializer;

public class SimpleSkillsMod implements ModInitializer {
	public static final String MOD_ID = "simpleskills";

	@Override
	public void onInitialize() {
		System.out.println("[SimpleSkills] Initializing mod...");

		// Load the tool requirements file (near the JAR in the 'mods' folder)
		ToolRequirementLoader.loadRequirements();

		// Register other components and events
		SkillRegistry.registerSkills();
		SkillEventHandler.registerEvents();

		System.out.println("[SimpleSkills] Mod initialized successfully!");
	}
}