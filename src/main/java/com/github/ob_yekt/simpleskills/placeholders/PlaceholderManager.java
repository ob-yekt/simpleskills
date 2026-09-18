package com.github.ob_yekt.simpleskills.placeholders;

import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.Identifier;

import java.util.function.BiFunction;

import static com.github.ob_yekt.simpleskills.Simpleskills.MOD_ID;

public class PlaceholderManager {
	private static final DatabaseManager db = DatabaseManager.getInstance();

	public static void init() {
		registerTotalLevel();
		registerPrestige();
		registerIsIronman();
	}

	private static void registerTotalLevel() {
		register("total_level", (uuid, _) -> PlaceholderResult.value(Integer.toString(db.getTotalSkillLevel(uuid))));
	}

	private static void registerPrestige() {
		register("prestige", (uuid, _) -> PlaceholderResult.value(Integer.toString(db.getPrestige(uuid))));
	}

	private static void registerIsIronman() {
		register("is_ironman", (uuid, _) -> PlaceholderResult.value(Boolean.toString(db.isPlayerInIronmanMode(uuid))));
	}

	private static void register(String path, BiFunction<String, String, PlaceholderResult> function) {
		Placeholders.registerServer(Identifier.fromNamespaceAndPath(MOD_ID, path), (ctx, arg) -> {
			if (!ctx.hasPlayer()) {
				return PlaceholderResult.invalid("No player!");
			}

			return function.apply(ctx.player().getStringUUID(), arg);
		});
	}

}
