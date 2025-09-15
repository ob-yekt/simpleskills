package com.github.ob_yekt.simpleskills.commands;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.AttributeManager;
import com.github.ob_yekt.simpleskills.managers.IronmanManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.particle.ParticleTypes;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registers commands for the simpleskills mod.
 * Consolidated Ironman commands and added leaderboard functionality.
 */
public class SimpleskillsCommands {

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("simpleskills")
                                .requires(source -> source.hasPermissionLevel(0))
                                .then(CommandManager.literal("togglehud")
                                        .executes(context -> {
                                            SkillTabMenu.toggleTabMenuVisibility(context.getSource());
                                            return 1;
                                        }))
                                .then(CommandManager.literal("ironman")
                                        .then(CommandManager.literal("enable")
                                                .executes(SimpleskillsCommands::enableIronman))
                                        .then(CommandManager.literal("disable")
                                                .requires(source -> source.hasPermissionLevel(2))
                                                .executes(SimpleskillsCommands::disableIronman)))
                                .then(CommandManager.literal("reload")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> {
                                            ConfigManager.initialize();
                                            XPManager.reloadConfig();
                                            context.getSource().sendFeedback(() -> Text.literal("§6[simpleskills]§f Configs reloaded."), true);
                                            return 1;
                                        }))
                                .then(CommandManager.literal("reset")
                                        .then(CommandManager.argument("username", StringArgumentType.string())
                                                .requires(source -> source.hasPermissionLevel(2))
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getOnlinePlayerNames(context), builder))
                                                .executes(SimpleskillsCommands::resetSkillsForPlayer))
                                        .executes(SimpleskillsCommands::resetSkillsForPlayer))
                                .then(CommandManager.literal("addxp")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .then(CommandManager.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getOnlinePlayerNames(context), builder))
                                                .then(CommandManager.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(getValidSkills(), builder))
                                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(SimpleskillsCommands::addXP)))))
                                .then(CommandManager.literal("setlevel")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .then(CommandManager.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getOnlinePlayerNames(context), builder))
                                                .then(CommandManager.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(getValidSkills(), builder))
                                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, XPManager.getMaxLevel()))
                                                                .executes(SimpleskillsCommands::setLevel)))))
                                .then(CommandManager.literal("query")
                                        .then(CommandManager.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getOnlinePlayerNames(context), builder))
                                                .then(CommandManager.literal("TOTAL")
                                                        .executes(SimpleskillsCommands::queryTotalLevel))
                                                .then(CommandManager.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(getValidSkills(), builder))
                                                        .executes(SimpleskillsCommands::querySkill))))
                                .then(CommandManager.literal("leaderboard")
                                        .then(CommandManager.literal("TOTAL")
                                                .executes(SimpleskillsCommands::showTotalLevelLeaderboard))
                                        .then(CommandManager.argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getValidSkills(), builder))
                                                .executes(SimpleskillsCommands::showSkillLeaderboard)))
                                .then(CommandManager.literal("leaderboardironman")
                                        .then(CommandManager.literal("TOTAL")
                                                .executes(SimpleskillsCommands::showIronmanTotalLevelLeaderboard))
                                        .then(CommandManager.argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(getValidSkills(), builder))
                                                .executes(SimpleskillsCommands::showIronmanSkillLeaderboard)))
                ));
    }

    private static int enableIronman(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("§6[simpleskills]§f This command can only be used by players.").formatted(Formatting.RED), false);
            return 0;
        }

        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        db.ensurePlayerInitialized(playerUuid);

        int totalLevels = db.getTotalSkillLevel(playerUuid);
        int expectedTotalLevels = Skills.values().length;
        if (totalLevels > expectedTotalLevels) {
            source.sendFeedback(() -> Text.literal("§6[simpleskills]§f You must reset your skills using /simpleskills reset before enabling Ironman Mode.").formatted(Formatting.RED), false);
            Simpleskills.LOGGER.debug("Player {} attempted to enable Ironman Mode but has {} total levels (expected {}).", player.getName().getString(), totalLevels, expectedTotalLevels);
            return 0;
        }

        if (db.isPlayerInIronmanMode(playerUuid)) {
            source.sendFeedback(() -> Text.literal("§6[simpleskills]§f You have already enabled Ironman Mode.").formatted(Formatting.RED), false);
            return 0;
        }

        db.setIronmanMode(playerUuid, true);
        IronmanManager.applyIronmanMode(player);
        player.sendMessage(Text.literal("§6[simpleskills]§f You have enabled Ironman Mode!").formatted(Formatting.YELLOW), false);

        ServerWorld world = player.getEntityWorld();
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 1.3f);
        Simpleskills.LOGGER.info("Player {} enabled Ironman Mode.", player.getName().getString());
        return 1;
    }

    private static int disableIronman(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendFeedback(() -> Text.literal("§6[simpleskills]§f This command can only be used by players.").formatted(Formatting.RED), false);
            return 0;
        }

        if (!DatabaseManager.getInstance().isPlayerInIronmanMode(player.getUuidAsString())) {
            source.sendFeedback(() -> Text.literal("§6[simpleskills]§f You are not in Ironman Mode.").formatted(Formatting.RED), false);
            return 0;
        }

        IronmanManager.disableIronmanMode(player);
        player.sendMessage(Text.literal("§6[simpleskills]§f You have disabled Ironman Mode.").formatted(Formatting.YELLOW), false);
        Simpleskills.LOGGER.info("Player {} disabled Ironman Mode.", player.getName().getString());
        return 1;
    }

    private static int resetSkillsForPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = source.getEntity() instanceof ServerPlayerEntity ? Objects.requireNonNull(source.getPlayer()).getGameProfile().name() : StringArgumentType.getString(context, "username");
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
        if (targetPlayer == null) {
            source.sendError(Text.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getUuidAsString();
        db.resetPlayerSkills(playerUuid);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);

        source.sendFeedback(() -> Text.literal("§6[simpleskills]§f Reset skills for " + playerName + "."), true);
        targetPlayer.sendMessage(Text.literal("§6[simpleskills]§f Your skills have been reset!"), false);
        Simpleskills.LOGGER.debug("Reset skills for player {}", playerName);
        return 1;
    }

    private static int addXP(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
        if (targetPlayer == null) {
            source.sendError(Text.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getUuidAsString();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill;
        try {
            skill = Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return 0;
        }

        XPManager.addXPWithNotification(targetPlayer, skill, amount);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);

        source.sendFeedback(() -> Text.literal("§6[simpleskills]§f Added " + amount + " XP to " + playerName + "'s '" + skill.getDisplayName() + "'."), true);
        targetPlayer.sendMessage(Text.literal("§6[simpleskills]§f You gained " + amount + " XP in " + skill.getDisplayName() + "!"), false);
        Simpleskills.LOGGER.debug("Added {} XP to skill {} for player {}", amount, skill.getDisplayName(), playerName);
        return 1;
    }

    private static int setLevel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");
        int newLevel = IntegerArgumentType.getInteger(context, "level");

        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
        if (targetPlayer == null) {
            source.sendError(Text.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getUuidAsString();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill;
        try {
            skill = Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return 0;
        }

        int newXP = XPManager.getExperienceForLevel(newLevel);
        db.savePlayerSkill(playerUuid, skill.getId(), newXP, newLevel);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);

        source.sendFeedback(() -> Text.literal("§6[simpleskills]§f Set " + playerName + "'s '" + skill.getDisplayName() + "' to level " + newLevel + "."), true);
        targetPlayer.sendMessage(Text.literal("§6[simpleskills]§f Your skill '" + skill.getDisplayName() + "' is now level " + newLevel + "!"), false);
        Simpleskills.LOGGER.debug("Set skill {} to level {} for player {}", skill.getDisplayName(), newLevel, playerName);
        return 1;
    }

    private static int querySkill(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");

        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
        if (targetPlayer == null) {
            source.sendError(Text.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getUuidAsString();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill;
        try {
            skill = Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return 0;
        }

        int level = XPManager.getSkillLevel(playerUuid, skill);
        source.sendFeedback(() -> Text.literal("§6[simpleskills]§f " + playerName + "'s '" + skill.getDisplayName() + "' level: " + level), false);
        Simpleskills.LOGGER.debug("Queried skill {} for player {}: level {}", skill.getDisplayName(), playerName, level);
        return 1;
    }

    private static int queryTotalLevel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);

        if (targetPlayer == null) {
            source.sendError(Text.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getUuidAsString();
        db.ensurePlayerInitialized(playerUuid);

        int totalLevel = db.getTotalSkillLevel(playerUuid);
        source.sendFeedback(() -> Text.literal("§6[simpleskills]§f " + playerName + "'s total skill level: " + totalLevel), false);
        Simpleskills.LOGGER.debug("Queried total level for player {}: {}", playerName, totalLevel);
        return 1;
    }

    private static int showSkillLeaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String skillName = StringArgumentType.getString(context, "skill");

        Skills skill;
        try {
            skill = Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getSkillLeaderboard(skill.getId(), 5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - ").append(skill.getDisplayName()).append(" Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            boolean isIronman = db.isPlayerInIronmanMode(entry.playerUuid());
            String namePrefix = isIronman ? "§c§l☠ §f" : "§f"; // White name for all, skull for Ironman
            message.append(String.format("§e%d. %s%s - Level §b%d §7[§f%,d XP§7]\n",
                    i + 1, namePrefix, entry.playerName(), entry.level(), entry.xp()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No players found for this skill.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendFeedback(() -> Text.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed leaderboard for skill {}", skill.getDisplayName());
        return 1;
    }

    private static int showTotalLevelLeaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getTotalLevelLeaderboard(5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Total Level Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            boolean isIronman = db.isPlayerInIronmanMode(entry.playerUuid());
            String namePrefix = isIronman ? "§c§l☠ §f" : "§f"; // White name for all, skull for Ironman
            message.append(String.format("§e%d. %s%s - Total Level §b%d\n",
                    i + 1, namePrefix, entry.playerName(), entry.level()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No players found.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendFeedback(() -> Text.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed total level leaderboard");
        return 1;
    }

    private static int showIronmanSkillLeaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String skillName = StringArgumentType.getString(context, "skill");

        Skills skill;
        try {
            skill = Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getIronmanSkillLeaderboard(skill.getId(), 5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Ironman ").append(skill.getDisplayName()).append(" Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            message.append(String.format("§e%d. §c§l☠ §f%s - Level §b%d §7[§f%,d XP§7]\n",
                    i + 1, entry.playerName(), entry.level(), entry.xp()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No Ironman players found for this skill.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendFeedback(() -> Text.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed Ironman leaderboard for skill {}", skill.getDisplayName());
        return 1;
    }

    private static int showIronmanTotalLevelLeaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getIronmanTotalLevelLeaderboard(5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Ironman Total Level Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            message.append(String.format("§e%d. §c§l☠ §f%s - Total Level §b%d\n",
                    i + 1, entry.playerName(), entry.level()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No Ironman players found.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendFeedback(() -> Text.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed Ironman total level leaderboard");
        return 1;
    }

    private static List<String> getOnlinePlayerNames(CommandContext<ServerCommandSource> context) {
        return context.getSource().getServer().getPlayerManager().getPlayerList().stream()
                .map(player -> player.getGameProfile().name())
                .collect(Collectors.toList());
    }

    private static List<String> getValidSkills() {
        return Stream.of(Skills.values())
                .map(Skills::getId)
                .toList();
    }
}