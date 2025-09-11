package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import com.github.ob_yekt.simpleskills.Skills;

import com.google.gson.JsonObject;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.function.BiConsumer;

public class XPManager {
    private static final int MAX_LEVEL = 99;

    // XPChangeListener - triggered on XP or level change
    private static BiConsumer<ServerPlayerEntity, Skills> onXPChangeListener;

    // Provide access to MAX_LEVEL
    public static int getMaxLevel() {
        return MAX_LEVEL;
    }

    // Set an optional listener for XP and level changes
    public static void setOnXPChangeListener(BiConsumer<ServerPlayerEntity, Skills> listener) {
        onXPChangeListener = listener;
    }

    /// Updated XP LOGIC with refined curve
        public static int getExperienceForLevel(int level) {
            if (level <= 1) return 0;
            double a = 1.164;
            double b = 3.5;
            double s = 2.75;
            return (int) Math.floor(a * (Math.pow(level + s, b) - Math.pow(1 + s, b)));
        }

        // Inverse method to find the level for a given XP amount
        public static int getLevelForExperience(int experience) {
            int level = 1;
            while (level < MAX_LEVEL && getExperienceForLevel(level + 1) <= experience) {
                level++;
            }
            return level;
        }


    // Get a player's skill level
    public static int getSkillLevel(String playerUuid, Skills skill) {
        DatabaseManager db = DatabaseManager.getInstance();
        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuid);
        DatabaseManager.SkillData skillData = skills.get(skill.getId());
        if (skillData != null) {
            return skillData.level();
        }
        Simpleskills.LOGGER.warn("No data for skill {} for player UUID: {}. Initializing default.", skill.getId(), playerUuid);
        db.initializePlayer(playerUuid); // Ensure player is initialized
        return 1; // Default level for newly initialized skills
    }

    // Add XP to a player's skill silently (no notifications)
    public static void addXPSilent(ServerPlayerEntity player, Skills skill, int XPToAdd) {
        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        JsonObject config = ConfigManager.getFeatureConfig();

        // Apply multiplier based on Ironman status
        double multiplier = db.isPlayerInIronmanMode(playerUuid)
                ? config.get("ironman_xp_multiplier").getAsDouble()
                : config.get("standard_xp_multiplier").getAsDouble();
        XPToAdd = (int) (XPToAdd * multiplier);

        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuid);
        DatabaseManager.SkillData skillData = skills.getOrDefault(skill.getId(), new DatabaseManager.SkillData(0, 0));
        int newXP = skillData.xp() + XPToAdd;
        int newLevel = getLevelForExperience(newXP);
        db.savePlayerSkill(playerUuid, skill.getId(), newXP, newLevel);
        AttributeManager.updatePlayerAttributes(player, skill, new DatabaseManager.SkillData(newXP, newLevel));
        SkillTabMenu.updateTabMenu(player);
        if (onXPChangeListener != null) {
            onXPChangeListener.accept(player, skill);
        }
    }

    // Add XP to a player's skill and notify them
    public static void addXPWithNotification(ServerPlayerEntity player, Skills skill, int XPToAdd) {
        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        JsonObject config = ConfigManager.getFeatureConfig();

        // Apply multiplier based on Ironman status
        double multiplier = db.isPlayerInIronmanMode(playerUuid)
                ? config.get("ironman_xp_multiplier").getAsDouble()
                : config.get("standard_xp_multiplier").getAsDouble();
        XPToAdd = (int) (XPToAdd * multiplier);

        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuid);
        DatabaseManager.SkillData skillData = skills.getOrDefault(skill.getId(), new DatabaseManager.SkillData(0, 0));
        int currentXP = skillData.xp();
        int currentLevel = skillData.level();

        int newXP = currentXP + XPToAdd;
        int newLevel = getLevelForExperience(newXP);

        db.savePlayerSkill(playerUuid, skill.getId(), newXP, newLevel);

        boolean notificationsEnabled = config.get("xp_notifications_enabled") != null && config.get("xp_notifications_enabled").getAsBoolean();
        int threshold = config.get("xp_notification_threshold") != null ? config.get("xp_notification_threshold").getAsInt() : 1;
        if (notificationsEnabled && XPToAdd >= threshold) {
            player.sendMessage(Text.of("§6[simpleskills]§f Gained " + XPToAdd + " XP in " + skill.getDisplayName() + "!"), true);
        }

        if (newLevel > currentLevel) {
            ServerWorld serverWorld = player.getEntityWorld();
            String levelUpMessage;
            if (newLevel == MAX_LEVEL) {
                levelUpMessage = "§6[simpleskills]§f Congratulations! You have reached max level in " + skill.getDisplayName() + "!";
                player.sendMessage(Text.of(levelUpMessage), false);
                serverWorld.playSound(
                        null,
                        player.getBlockPos(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundCategory.PLAYERS,
                        0.9f,
                        1.2f
                );
                spawnConfettiParticles(serverWorld, player.getX(), player.getY(), player.getZ(), true);
            } else {
                levelUpMessage = "§6[simpleskills]§f You leveled up in " + skill.getDisplayName() + "! New level: " + newLevel;
                player.sendMessage(Text.of(levelUpMessage), false);
                serverWorld.playSound(
                        null,
                        player.getBlockPos(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS,
                        0.7f,
                        1.3f
                );
                spawnConfettiParticles(serverWorld, player.getX(), player.getY(), player.getZ(), false);
            }
        }

        if (onXPChangeListener != null) {
            onXPChangeListener.accept(player, skill);
        }
        SkillTabMenu.updateTabMenu(player);
    }

    private static void spawnConfettiParticles(ServerWorld serverWorld, double x, double y, double z, boolean isMaxLevel) {
        ParticleEffect[] particles = new ParticleEffect[]{
                ParticleTypes.HAPPY_VILLAGER,
                ParticleTypes.ENCHANTED_HIT,
                ParticleTypes.FIREWORK,
                ParticleTypes.CRIT
        };
        int count = isMaxLevel ? 200 : 75;
        double spread = isMaxLevel ? 2.0 : 1.0;

        for (ParticleEffect particle : particles) {
            serverWorld.spawnParticles(
                    particle,
                    x, y + 1.5, z,
                    count / particles.length,
                    spread, spread, spread,
                    0.05
            );
        }
        if (isMaxLevel) {
            serverWorld.spawnParticles(
                    ParticleTypes.FIREWORK,
                    x, y + 1.5, z,
                    50,
                    1.5, 1.5, 1.5,
                    0.1
            );
        }
    }
}