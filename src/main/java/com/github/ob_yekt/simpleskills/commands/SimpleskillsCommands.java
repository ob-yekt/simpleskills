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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.List;
import java.util.Map;
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
                        Commands.literal("simpleskills")
                                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.ALL)))
                                .then(Commands.literal("togglehud")
                                        .executes(context -> {
                                            SkillTabMenu.toggleTabMenuVisibility(context.getSource());
                                            return 1;
                                        }))
                                .then(Commands.literal("ironman")
                                        .then(Commands.literal("enable")
                                                .executes(SimpleskillsCommands::enableIronman))
                                        .then(Commands.literal("disable")
                                                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                                .executes(SimpleskillsCommands::disableIronman)))
                                .then(Commands.literal("prestige")
                                        .executes(SimpleskillsCommands::prestige))
                                .then(Commands.literal("reload")
                                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                        .executes(context -> {
                                            ConfigManager.initialize();
                                            XPManager.reloadConfig();
                                            context.getSource().sendSuccess(() -> Component.literal("§6[simpleskills]§f Configs reloaded."), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("username", StringArgumentType.string())
                                                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOnlinePlayerNames(context), builder))
                                                .executes(SimpleskillsCommands::resetSkillsForPlayer))
                                        .executes(SimpleskillsCommands::resetSkillsForPlayer))
                                .then(Commands.literal("addxp")
                                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                        .then(Commands.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOnlinePlayerNames(context), builder))
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getValidSkills(), builder))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(SimpleskillsCommands::addXP)))))
                                .then(Commands.literal("setlevel")
                                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                        .then(Commands.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOnlinePlayerNames(context), builder))
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getValidSkills(), builder))
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, XPManager.getMaxLevel()))
                                                                .executes(SimpleskillsCommands::setLevel)))))
                                .then(Commands.literal("setprestige")
                                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.MODERATORS)))
                                        .then(Commands.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOnlinePlayerNames(context), builder))
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(SimpleskillsCommands::setPrestige))))
                                .then(Commands.literal("query")
                                        .then(Commands.argument("targets", StringArgumentType.string())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOnlinePlayerNames(context), builder))
                                                .then(Commands.literal("TOTAL")
                                                        .executes(SimpleskillsCommands::queryTotalLevel))
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getValidSkills(), builder))
                                                        .executes(SimpleskillsCommands::querySkill))))
                                .then(Commands.literal("leaderboard")
                                        .then(Commands.literal("TOTAL")
                                                .executes(SimpleskillsCommands::showTotalLevelLeaderboard))
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getValidSkills(), builder))
                                                .executes(SimpleskillsCommands::showSkillLeaderboard)))
                                .then(Commands.literal("leaderboardironman")
                                        .then(Commands.literal("TOTAL")
                                                .executes(SimpleskillsCommands::showIronmanTotalLevelLeaderboard))
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getValidSkills(), builder))
                                                .executes(SimpleskillsCommands::showIronmanSkillLeaderboard)))
                ));
    }

    private static int prestige(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f This command can only be used by players.").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String playerUuid = player.getStringUUID();
        DatabaseManager db = DatabaseManager.getInstance();
        db.ensurePlayerInitialized(playerUuid);

        // Require level cap in all skills
        boolean allMaxed = db.getAllSkills(playerUuid).values().stream()
                .allMatch(s -> s.level() >= XPManager.getMaxLevel());
        if (!allMaxed) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f You must reach level " + XPManager.getMaxLevel() + " in all skills to prestige."), false);
            return 0;
        }

        // Increment prestige and reset skill levels
        db.incrementPrestige(playerUuid);
        db.resetPlayerSkills(playerUuid);

        AttributeManager.refreshAllAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(player);

        int newPrestige = db.getPrestige(playerUuid);
        player.sendSystemMessage(Component.literal("§6[simpleskills]§f You prestiged to §6★" + newPrestige + "§f! Skills reset to 1."), false);
        Simpleskills.LOGGER.info("Player {} prestiged to {}", player.getName().getString(), newPrestige);
        return 1;
    }

    private static int enableIronman(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f This command can only be used by players.").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String playerUuid = player.getStringUUID();
        DatabaseManager db = DatabaseManager.getInstance();
        db.ensurePlayerInitialized(playerUuid);

        int totalLevels = db.getTotalSkillLevel(playerUuid);
        int expectedTotalLevels = Skills.values().length;
        if (totalLevels > expectedTotalLevels || DatabaseManager.getInstance().getPrestige(playerUuid) > 0) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f You must reset your skills using /simpleskills reset before enabling Ironman Mode.").withStyle(ChatFormatting.RED), false);
            Simpleskills.LOGGER.debug("Player {} attempted to enable Ironman Mode but has {} total levels (expected {}).", player.getName().getString(), totalLevels, expectedTotalLevels);
            return 0;
        }

        if (db.isPlayerInIronmanMode(playerUuid)) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f You have already enabled Ironman Mode.").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        db.setIronmanMode(playerUuid, true);
        IronmanManager.applyIronmanMode(player);
        player.sendSystemMessage(Component.literal("§6[simpleskills]§f You have enabled Ironman Mode!").withStyle(ChatFormatting.YELLOW), false);

        ServerLevel world = player.level();
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.3f);
        Simpleskills.LOGGER.info("Player {} enabled Ironman Mode.", player.getName().getString());
        return 1;
    }

    private static int disableIronman(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f This command can only be used by players.").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        if (ConfigManager.isForceIronmanModeEnabled()) {
            source.sendSuccess(() -> Component.literal("§6[simpleskills]§f Ironman Mode is enforced by the server and cannot be disabled.").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        IronmanManager.disableIronmanMode(player);
        player.sendSystemMessage(Component.literal("§6[simpleskills]§f Ironman Mode disabled."), false);
        return 1;
    }

    private static int resetSkillsForPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = source.getEntity() instanceof ServerPlayer ? Objects.requireNonNull(source.getPlayer()).getGameProfile().name() : StringArgumentType.getString(context, "username");
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
            return 0;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.resetPlayerSkills(playerUuid);
        db.setPrestige(playerUuid, 0);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);
        com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(targetPlayer);

        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f Reset skills and prestige for " + playerName + "."), true);
        targetPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f Your skills and prestige have been reset!"), false);
        Simpleskills.LOGGER.debug("Reset skills for player {}", playerName);
        return 1;
    }

    private static int addXP(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        ServerPlayer targetPlayer = getPlayerByName(source, playerName);
        if (targetPlayer == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill = parseSkillName(source, skillName);
        if (skill == null) return 0;

        XPManager.addXPWithNotification(targetPlayer, skill, amount);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);

        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f Added " + amount + " XP to " + playerName + "'s '" + skill.getDisplayName() + "'."), true);
        targetPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f You gained " + amount + " XP in " + skill.getDisplayName() + "!"), false);
        Simpleskills.LOGGER.debug("Added {} XP to skill {} for player {}", amount, skill.getDisplayName(), playerName);
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");
        int newLevel = IntegerArgumentType.getInteger(context, "level");

        ServerPlayer targetPlayer = getPlayerByName(source, playerName);
        if (targetPlayer == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill = parseSkillName(source, skillName);
        if (skill == null) return 0;

        int newXP = XPManager.getExperienceForLevel(newLevel);
        db.savePlayerSkill(playerUuid, skill.getId(), newXP, newLevel);
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);

        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f Set " + playerName + "'s '" + skill.getDisplayName() + "' to level " + newLevel + "."), true);
        targetPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f Your skill '" + skill.getDisplayName() + "' is now level " + newLevel + "!"), false);
        Simpleskills.LOGGER.debug("Set skill {} to level {} for player {}", skill.getDisplayName(), newLevel, playerName);
        return 1;
    }

    private static int setPrestige(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        int value = IntegerArgumentType.getInteger(context, "value");

        ServerPlayer targetPlayer = getPlayerByName(source, playerName);
        if (targetPlayer == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.ensurePlayerInitialized(playerUuid);

        db.setPrestige(playerUuid, value);

        // Refresh UI/attributes/name decorations to reflect new prestige
        AttributeManager.refreshAllAttributes(targetPlayer);
        SkillTabMenu.updateTabMenu(targetPlayer);
        com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(targetPlayer);

        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f Set prestige for " + playerName + " to §6★" + value + "§f."), true);
        targetPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f Your prestige is now §6★" + value + "§f."), false);
        Simpleskills.LOGGER.info("Set prestige for {} to {}", playerName, value);
        return 1;
    }

    private static int querySkill(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        String skillName = StringArgumentType.getString(context, "skill");

        ServerPlayer targetPlayer = getPlayerByName(source, playerName);
        if (targetPlayer == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.ensurePlayerInitialized(playerUuid);

        Skills skill = parseSkillName(source, skillName);
        if (skill == null) return 0;

        int level = XPManager.getSkillLevel(playerUuid, skill);
        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f " + playerName + "'s '" + skill.getDisplayName() + "' level: " + level), false);
        Simpleskills.LOGGER.debug("Queried skill {} for player {}: level {}", skill.getDisplayName(), playerName, level);
        return 1;
    }

    private static int queryTotalLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String playerName = StringArgumentType.getString(context, "targets");
        ServerPlayer targetPlayer = getPlayerByName(source, playerName);
        if (targetPlayer == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuid = targetPlayer.getStringUUID();
        db.ensurePlayerInitialized(playerUuid);

        int totalLevel = db.getTotalSkillLevel(playerUuid);
        source.sendSuccess(() -> Component.literal("§6[simpleskills]§f " + playerName + "'s total skill level: " + totalLevel), false);
        Simpleskills.LOGGER.debug("Queried total level for player {}: {}", playerName, totalLevel);
        return 1;
    }

    private static int showSkillLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String skillName = StringArgumentType.getString(context, "skill");

        Skills skill = parseSkillName(source, skillName);
        if (skill == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getSkillLeaderboard(skill.getId(), 5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - ").append(skill.getDisplayName()).append(" Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            boolean isIronman = db.isPlayerInIronmanMode(entry.playerUuid());
            String star = entry.prestige() > 0 ? ("§6★" + entry.prestige() + " §f") : "";
            String namePrefix = (isIronman ? "§c§l☠ §f" : "§f") + star;
            message.append(String.format("§e%d. %s%s - Level §b%d §7[§f%,d XP§7]\n",
                    i + 1, namePrefix, entry.playerName(), entry.level(), entry.xp()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No players found for this skill.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed leaderboard for skill {}", skill.getDisplayName());
        return 1;
    }

    private static int showTotalLevelLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getTotalLevelLeaderboard(5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Total Level Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            boolean isIronman = db.isPlayerInIronmanMode(entry.playerUuid());
            String star = entry.prestige() > 0 ? ("§6★" + entry.prestige() + " §f") : "";
            String namePrefix = (isIronman ? "§c§l☠ §f" : "§f") + star;
            message.append(String.format("§e%d. %s%s - Total Level §b%d\n",
                    i + 1, namePrefix, entry.playerName(), entry.level()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No players found.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed total level leaderboard");
        return 1;
    }

    private static int showIronmanSkillLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String skillName = StringArgumentType.getString(context, "skill");

        Skills skill = parseSkillName(source, skillName);
        if (skill == null) return 0;

        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getIronmanSkillLeaderboard(skill.getId(), 5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Ironman ").append(skill.getDisplayName()).append(" Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            String star = entry.prestige() > 0 ? ("§6★" + entry.prestige() + " §f") : "";
            message.append(String.format("§e%d. §c§l☠ §f%s%s - Level §b%d §7[§f%,d XP§7]\n",
                    i + 1, star, entry.playerName(), entry.level(), entry.xp()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No Ironman players found for this skill.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed Ironman leaderboard for skill {}", skill.getDisplayName());
        return 1;
    }

    private static int showIronmanTotalLevelLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DatabaseManager db = DatabaseManager.getInstance();
        List<DatabaseManager.LeaderboardEntry> leaderboard = db.getIronmanTotalLevelLeaderboard(5);

        StringBuilder message = new StringBuilder();
        message.append("§6[simpleskills]§f Top 5 - Ironman Total Level Leaderboard\n");
        message.append("§8§m---------------------------------------\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            DatabaseManager.LeaderboardEntry entry = leaderboard.get(i);
            String star = entry.prestige() > 0 ? ("§6★" + entry.prestige() + " §f") : "";
            message.append(String.format("§e%d. §c§l☠ §f%s%s - Total Level §b%d\n",
                    i + 1, star, entry.playerName(), entry.level()));
        }

        if (leaderboard.isEmpty()) {
            message.append("§7No Ironman players found.\n");
        }

        message.append("§8§m---------------------------------------");
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        Simpleskills.LOGGER.debug("Displayed Ironman total level leaderboard");
        return 1;
    }

    private static List<String> getOnlinePlayerNames(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().getPlayerList().getPlayers().stream()
                .map(player -> player.getGameProfile().name())
                .collect(Collectors.toList());
    }

    private static List<String> getValidSkills() {
        return Stream.of(Skills.values())
                .map(Skills::getId)
                .toList();
    }

    /**
     * Helper method to get a player by name with error handling.
     * @return The player if found, null otherwise (and sends error message)
     */
    private static ServerPlayer getPlayerByName(CommandSourceStack source, String playerName) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            source.sendFailure(Component.literal("§6[simpleskills]§f Player '" + playerName + "' not found."));
        }
        return player;
    }

    /**
     * Helper method to parse a skill name with error handling.
     * @return The skill if valid, null otherwise (and sends error message)
     */
    private static Skills parseSkillName(CommandSourceStack source, String skillName) {
        try {
            return Skills.valueOf(skillName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§6[simpleskills]§f Invalid skill '" + skillName + "'."));
            return null;
        }
    }
}