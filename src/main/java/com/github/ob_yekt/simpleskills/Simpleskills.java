package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.commands.SimpleskillsCommands;
import com.github.ob_yekt.simpleskills.managers.*;
import com.github.ob_yekt.simpleskills.events.EventHandlers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Simpleskills implements ModInitializer {
	public static final String MOD_ID = "simpleskills";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);



	@Override
	public void onInitialize() {
		LOGGER.info("Initializing SimpleSkills mod...");

		SimpleskillsCommands.registerCommands();
		IronmanManager.init();
		AttributeManager.registerPlayerEvents();
		LoreManager.initialize();
		ConfigManager.initialize();
		EventHandlers.registerAll();

		// Initialize database and setup server lifecycle hooks
		DatabaseManager db = DatabaseManager.getInstance();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ConfigManager.initialize();
			db.initializeDatabase(server);
			LOGGER.info("Database and config initialized for server.");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			db.close();
			LOGGER.info("Database connection closed on server stop.");
		});

		LOGGER.info("SimpleSkills mod initialized successfully!");
	}
}