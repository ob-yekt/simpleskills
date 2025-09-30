package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Manages Ironman mode functionality
 * Consolidated team management and attribute application.
 */
public class IronmanManager {

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (DatabaseManager.getInstance().isPlayerInIronmanMode(player.getUuidAsString())) {
                applyIronmanMode(player);
            }
        });
    }

    public static void applyIronmanMode(ServerPlayerEntity player) {
        if (player == null) return;
        AttributeManager.applyIronmanAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        NamePrefixManager.updatePlayerNameDecorations(player);
        Simpleskills.LOGGER.debug("Applied Ironman mode for player: {}", player.getName().getString());
    }

    public static void disableIronmanMode(ServerPlayerEntity player) {
        if (player == null) return;
        DatabaseManager.getInstance().setIronmanMode(player.getUuidAsString(), false);
        AttributeManager.clearIronmanAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        NamePrefixManager.updatePlayerNameDecorations(player);
        Simpleskills.LOGGER.debug("Disabled Ironman mode for player: {}", player.getName().getString());
    }

    // Legacy team methods removed in favor of per-player decorated team in NamePrefixManager.
}