package com.github.ob_yekt.simpleskills;

import net.fabricmc.api.ModInitializer;

public class SimpleSkillsMod implements ModInitializer {
	public static final String MOD_ID = "simpleskills";

	@Override
	public void onInitialize() {
		SkillRegistry.registerSkills();
		SkillEventHandler.registerEvents();
		System.out.println("SimpleSkills Mod initialized!");
	}
}
