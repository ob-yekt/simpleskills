package com.github.ob_yekt.simpleskills.ui;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SkillTabMenu {
    private static final Map<UUID, Boolean> playerTabMenuVisibility = new HashMap<>();
    private static final int MAX_SKILL_NAME_LENGTH = getMaxSkillNameLength();
    private static final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);

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
            int prestige = db.getPrestige(playerUuidStr);
            if (isIronman) {
                skillInfo.append("§cIronman Mode: §aENABLED\n\n");
            }
            if (prestige > 0) {
                skillInfo.append(String.format("§6Prestige: §e★%d\n\n", prestige));
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
            line = String.format("§6⭐ §e%s §eLevel %2d §7[§f%s§7]",
                    skillDisplayName,
                    skill.level(),
                    numberFormat.format(skill.xp())
            );
        } else {
            String progressBar = createProgressBar(skill);
            int xpForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
            int xpToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - xpForCurrentLevel;
            int progressToNextLevel = skill.xp() - xpForCurrentLevel;

            line = String.format("§a%s §fLevel §b%2d %s §7[§f%s§7/§f%s§7]",
                    skillDisplayName,
                    skill.level(),
                    progressBar,
                    numberFormat.format(progressToNextLevel),
                    numberFormat.format(xpToNextLevel)
            );
        }
        String leftAlignedLine = addLeftAlignmentPadding(line);
        skillInfo.append(leftAlignedLine).append("\n");
    }

    private static String addLeftAlignmentPadding(String line) {
        int estimatedWidth = calculateApproximateWidth(line);
        int tabMenuWidth = 70;
        int padding = Math.max(0, (tabMenuWidth - estimatedWidth) / 2);
        padding = Math.max(0, padding - 20);
        return " ".repeat(padding) + line;
    }

    private static int calculateApproximateWidth(String text) {
        String cleanText = text.replaceAll("§[0-9a-fklmnor]", "");
        int width = 0;
        for (char c : cleanText.toCharArray()) {
            switch (c) {
                case 'i', 'l', 't', 'f', 'I', '!', '|', '.', ':', ';', ',' -> width += 2;
                case 'W', 'M', 'm', 'w' -> width += 6;
                case ' ', 'r', 's' -> width += 3;
                case '⭐' -> width += 8;
                case '█', '▒' -> width += 4;
                case '[', ']', '(', ')' -> width += 3;
                case '/' -> width += 2;
                default -> width += 4;
            }
        }
        return width;
    }

    private static String createProgressBar(DatabaseManager.SkillData skill) {
        int barLength = 10;
        if (skill.level() == XPManager.getMaxLevel()) {
            return "";
        }
        int xpForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
        int xpToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - xpForCurrentLevel;
        int progressToNextLevel = skill.xp() - xpForCurrentLevel;
        if (xpToNextLevel <= 0) xpToNextLevel = 1;
        progressToNextLevel = Math.max(0, Math.min(progressToNextLevel, xpToNextLevel));
        int filled = (int) ((double) progressToNextLevel / xpToNextLevel * barLength);
        int empty = barLength - filled;
        return "§a" + "█".repeat(filled) + "§7" + "▒".repeat(empty);
    }

    public static void clearPlayerVisibility(UUID playerUuid) {
        playerTabMenuVisibility.remove(playerUuid);
        Simpleskills.LOGGER.debug("Cleared tab menu visibility for player UUID: {}", playerUuid);
    }
}
