package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.Objects;

/**
 * Manages Ironman mode functionality
 * Consolidated team management and attribute application.
 */
public class IronmanManager {
    private static final String IRONMAN_TEAM_NAME = "ironman";

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
        assignPlayerToIronmanTeam(player);
        AttributeManager.applyIronmanAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        Simpleskills.LOGGER.debug("Applied Ironman mode for player: {}", player.getName().getString());
    }

    public static void disableIronmanMode(ServerPlayerEntity player) {
        if (player == null) return;
        DatabaseManager.getInstance().setIronmanMode(player.getUuidAsString(), false);
        removePlayerFromIronmanTeam(player);
        AttributeManager.clearIronmanAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        Simpleskills.LOGGER.debug("Disabled Ironman mode for player: {}", player.getName().getString());
    }

    private static void createIronmanTeam(ServerScoreboard scoreboard) {
        Team ironmanTeam = scoreboard.getTeam(IRONMAN_TEAM_NAME);
        if (ironmanTeam == null) {
            ironmanTeam = scoreboard.addTeam(IRONMAN_TEAM_NAME);
            ironmanTeam.setPrefix(Text.literal("☠ ").formatted(Formatting.RED, Formatting.BOLD));
            ironmanTeam.setFriendlyFireAllowed(false);
            ironmanTeam.setShowFriendlyInvisibles(true);
            Simpleskills.LOGGER.debug("Created Ironman team.");
        }
    }

    public static void assignPlayerToIronmanTeam(ServerPlayerEntity player) {
        if (player == null) return;
        ServerScoreboard scoreboard = Objects.requireNonNull(player.getEntityWorld().getServer()).getScoreboard();
        createIronmanTeam(scoreboard);
        Team ironmanTeam = scoreboard.getTeam(IRONMAN_TEAM_NAME);
        if (ironmanTeam != null) {
            scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), ironmanTeam);
            Simpleskills.LOGGER.debug("Added player {} to Ironman team.", player.getName().getString());
        }
    }

    public static void removePlayerFromIronmanTeam(ServerPlayerEntity player) {
        if (player == null) return;
        ServerScoreboard scoreboard = Objects.requireNonNull(player.getEntityWorld().getServer()).getScoreboard();
        Team ironmanTeam = scoreboard.getTeam(IRONMAN_TEAM_NAME);
        if (ironmanTeam != null) {
            scoreboard.removeScoreHolderFromTeam(player.getNameForScoreboard(), ironmanTeam);
            Simpleskills.LOGGER.debug("Removed player {} from Ironman team.", player.getName().getString());
        }
    }
}