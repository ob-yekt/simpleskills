package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Objects;

/**
 * Maintains per-player scoreboard team to show chat/nametag/tab-list prefix: "★X [username]" and skull for Ironman.
 */
public class NamePrefixManager {
    private static final String TEAM_PREFIX = "simpleskills_prefix_";

    public static void updatePlayerNameDecorations(ServerPlayerEntity player) {
        if (player == null) return;
        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        boolean isIronman = db.isPlayerInIronmanMode(playerUuid);
        int prestige = db.getPrestige(playerUuid);

        String starPart = prestige > 0 ? ("§6★" + prestige + " §f") : "";
        String skullPart = isIronman ? "§c§l☠ §f" : "";

        String prefix = skullPart + starPart;

        ServerScoreboard scoreboard = Objects.requireNonNull(player.getEntityWorld().getServer()).getScoreboard();
        String teamName = TEAM_PREFIX + player.getUuid().toString().substring(0, 16);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
        }

        team.setPrefix(Text.literal(prefix));
        team.setColor(Formatting.WHITE);

        // Ensure membership
        scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);

        Simpleskills.LOGGER.debug("Updated name decorations for {}: {}", player.getName().getString(), prefix.replace("§", ""));
    }
}


