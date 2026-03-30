package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Maintains per-player scoreboard team to show chat/nametag/tab-list prefix: "★X [username]" and skull for Ironman.
 */
public class NamePrefixManager {
    private static final String TEAM_PREFIX = "simpleskills_prefix_";

    public static void updatePlayerNameDecorations(ServerPlayer player) {
        if (player == null) return;
        String playerUuid = player.getStringUUID();
        DatabaseManager db = DatabaseManager.getInstance();
        boolean isIronman = db.isPlayerInIronmanMode(playerUuid);
        int prestige = db.getPrestige(playerUuid);

        String starPart = prestige > 0 ? ("§6★" + prestige + " §f") : "";
        String skullPart = isIronman ? "§c§l☠ §f" : "";

        String prefix = skullPart + starPart;

        ServerScoreboard scoreboard = Objects.requireNonNull(player.level().getServer()).getScoreboard();
        String teamName = TEAM_PREFIX + player.getUUID().toString().substring(0, 16);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
        }

        team.setPlayerPrefix(Component.literal(prefix));
        team.setColor(ChatFormatting.WHITE);

        // Ensure membership
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

        Simpleskills.LOGGER.debug("Updated name decorations for {}: {}", player.getName().getString(), prefix.replace("§", ""));
    }
}


