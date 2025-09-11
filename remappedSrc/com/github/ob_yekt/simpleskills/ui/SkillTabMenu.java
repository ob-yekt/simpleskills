package com.github.ob_yekt.simpleskills.ui;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillTabMenu {
    private static final Map<UUID, Boolean> playerTabMenuVisibility = new HashMap<>();
    private static final int MAX_SKILL_NAME_LENGTH = getMaxSkillNameLength();
    private static final int MAX_LINE_LENGTH = calculateMaxLineLength();

    /**
     * Determines the length of the longest skill display name for padding purposes.
     */
    private static int getMaxSkillNameLength() {
        int maxLength = 0;
        for (Skills skill : Skills.values()) {
            maxLength = Math.max(maxLength, skill.getDisplayName().length());
        }
        return maxLength;
    }

    /**
     * Calculates the maximum possible line length for consistent alignment.
     * Accounts for skill name, level, progress bar, and max XP string (e.g., 14,391,459).
     */
    private static int calculateMaxLineLength() {
        // Base components: skill name, " Level ", level number (2 digits), progress bar (10 chars), " [", XP strings, "/", "]", and spaces
        int maxXPStringLength = String.format("%,d", XPManager.getExperienceForLevel(99)).length(); // e.g., "14,391,459" = 10 chars
        return MAX_SKILL_NAME_LENGTH + " Level ".length() + 2 + 1 + 10 + 2 + maxXPStringLength + 1 + maxXPStringLength + 1;
    }

    /**
     * Toggles the visibility of the skill tab menu for the given player.
     * @param source The command source, expected to be a player.
     */
    public static void toggleTabMenuVisibility(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("§6[simpleskills]§f This command can only be used by players."));
            return;
        }

        UUID playerUuid = player.getUuid();
        boolean isVisible = playerTabMenuVisibility.getOrDefault(playerUuid, DatabaseManager.getInstance().isTabMenuVisible(playerUuid.toString()));
        playerTabMenuVisibility.put(playerUuid, !isVisible);
        DatabaseManager.getInstance().setTabMenuVisibility(playerUuid.toString(), !isVisible);

        if (!isVisible) {
            updateTabMenu(player);
            player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD enabled."), false);
        } else {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket(
                    Text.of(""),
                    Text.of("")
            ));
            player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD disabled."), false);
        }
    }

    /**
     * Updates the skill tab menu for the given player.
     * @param player The player whose tab menu should be updated.
     */
    public static void updateTabMenu(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        boolean isVisible = playerTabMenuVisibility.getOrDefault(playerUuid, DatabaseManager.getInstance().isTabMenuVisible(playerUuid.toString()));
        if (!isVisible) {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket(
                    Text.of(""),
                    Text.of("")
            ));
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuidStr = playerUuid.toString();

        // Ensure player is initialized
        if (db.getAllSkills(playerUuidStr).isEmpty()) {
            db.initializePlayer(playerUuidStr);
            Simpleskills.LOGGER.debug("Initialized skills for player {} due to tab menu update.", player.getName().getString());
        }

        StringBuilder skillInfo = new StringBuilder();
        try {
            skillInfo.append("§6§m=======================================\n")
                    .append("§c§l⚔ Skills Overview ⚔§r\n")
                    .append("§6§m=======================================\n\n");

            boolean isIronman = db.isPlayerInIronmanMode(playerUuidStr);
            if (isIronman) {
                skillInfo.append("§cIronman Mode: §aENABLED\n\n");
            }

            Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuidStr);
            int totalLevels = skills.values().stream().mapToInt(DatabaseManager.SkillData::level).sum();

            skillInfo.append("§8§m---------------------------------------\n\n");
            for (Skills skillEnum : Skills.values()) {
                String skillName = skillEnum.getId();
                DatabaseManager.SkillData skill = skills.get(skillName);
                if (skill != null) {
                    appendSkillInfo(skillInfo, skill, skillEnum.getDisplayName());
                } else {
                    Simpleskills.LOGGER.warn("Skill {} not found for player {}. Using default.", skillName, player.getName().getString());
                    appendSkillInfo(skillInfo, new DatabaseManager.SkillData(0, 1), skillEnum.getDisplayName());
                }
            }

            skillInfo.append("\n§8§m---------------------------------------\n")
                    .append(String.format("§b§lTotal Level: §a%d\n", totalLevels))
                    .append("§6§m=======================================");

            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket(
                    Text.of(skillInfo.toString()),
                    Text.of("")
            ));
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Failed to update tab menu for player {}: {}", player.getName().getString(), e.getMessage());
            player.sendMessage(Text.literal("§6[simpleskills]§f Error: Failed to load skill data."), false);
        }
    }

    private static void appendSkillInfo(StringBuilder skillInfo, DatabaseManager.SkillData skill, String skillDisplayName) {
        String line;
        if (skill.level() == XPManager.getMaxLevel()) {
            line = String.format("§6⭐ §e%-" + MAX_SKILL_NAME_LENGTH + "s §eLevel 99 §r§bXP: %,d",
                    skillDisplayName,
                    skill.xp()
            );
        } else {
            int XPForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
            int XPToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - XPForCurrentLevel;
            int progressToNextLevel = skill.xp() - XPForCurrentLevel;

            String progressBar = createProgressBar(progressToNextLevel, XPToNextLevel);

            line = String.format("§a%-" + MAX_SKILL_NAME_LENGTH + "s §fLevel §b%-2d %s §7[§f%,d§7/§f%,d§7]",
                    skillDisplayName,
                    skill.level(),
                    progressBar,
                    progressToNextLevel,
                    XPToNextLevel
            );
        }

        // Pad the line to MAX_LINE_LENGTH, stripping formatting codes for length calculation
        String plainLine = line.replaceAll("§[0-9a-fklmnor]", "");
        int paddingLength = MAX_LINE_LENGTH - plainLine.length();
        if (paddingLength > 0) {
            line += " ".repeat(paddingLength);
        }

        skillInfo.append(line).append("\n");
    }

    private static String createProgressBar(int progress, int total) {
        int barLength = 10;
        if (total <= 0) {
            total = 1; // Prevent division by zero
        }
        progress = Math.max(0, Math.min(progress, total));

        int filled = (int) ((double) progress / total * barLength);
        int empty = barLength - filled;

        return "§a" + "█".repeat(filled) + "§7" + "░".repeat(empty);
    }

    /**
     * Clears the visibility setting for a player on disconnect.
     * @param playerUuid The UUID of the player to clear.
     */
    public static void clearPlayerVisibility(UUID playerUuid) {
        playerTabMenuVisibility.remove(playerUuid);
        Simpleskills.LOGGER.debug("Cleared tab menu visibility for player UUID: {}", playerUuid);
    }
}