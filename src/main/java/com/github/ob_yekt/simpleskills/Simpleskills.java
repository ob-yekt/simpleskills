package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.data.DatabaseManager;
import com.github.ob_yekt.simpleskills.requirements.RequirementLoader;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simpleskills implements ModInitializer {
	public static final String MOD_ID = "simpleskills";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing simpleskills");

		// Load requirements on startup
		RequirementLoader.loadRequirements();
		LOGGER.info("Loaded requirements from JSON files!");

		// Load XP values on startup
		com.github.ob_yekt.simpleskills.requirements.ConfigLoader.loadBaseXpConfig(); // Load the JSON configuration

		// Register event handlers
		PlayerEventHandlers.registerEvents();
		LOGGER.info("Events registered!");

		// Register attributes on respawn
		AttributeUpdater.registerPlayerEvents();
		LOGGER.info("Attributes on respawn registered!");

		// Register commands
		SimpleskillsCommands.registerCommands();
		LOGGER.info("Commands registered!");

		// Register trades
		VillagerTrades.registerCustomTrades();
		LOGGER.info("Trades registered!");

		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			DatabaseManager.getInstance().close();
			LOGGER.info("Database connection closed.");
		}));
	}
}