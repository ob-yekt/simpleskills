package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Manages Ironman mode functionality
 * Consolidated team management and attribute application.
 */
public class IronmanManager {

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String uuid = player.getUuidAsString();
            DatabaseManager db = DatabaseManager.getInstance();

            boolean isCurrentlyIronman = db.isPlayerInIronmanMode(uuid);



            // If force ironman is enabled globally
            if (ConfigManager.isForceIronmanModeEnabled()) {
                // Always set to Ironman, even if they were before
                if (!isCurrentlyIronman) {
                    db.setIronmanMode(uuid, true);
                    Simpleskills.LOGGER.info("Force-enabled Ironman Mode for player {} due to server config.", player.getName().getString());
                }
                applyIronmanMode(player);
            }
            // Normal behavior: only apply if they already have Ironman flag
            else if (isCurrentlyIronman) {
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

        if (ConfigManager.isForceIronmanModeEnabled()) {
            Simpleskills.LOGGER.warn("Attempted to disable Ironman Mode for {} but force_ironman_mode is enabled!", player.getName().getString());
            return;
        }

        DatabaseManager.getInstance().setIronmanMode(player.getUuidAsString(), false);
        AttributeManager.clearIronmanAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        NamePrefixManager.updatePlayerNameDecorations(player);
        Simpleskills.LOGGER.debug("Disabled Ironman mode for player: {}", player.getName().getString());
    }

    // Legacy team methods removed in favor of per-player decorated team in NamePrefixManager.
}