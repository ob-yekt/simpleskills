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
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.function.BiConsumer;

public class XPManager {
    private static final int MAX_LEVEL = 99;
    private static BiConsumer<ServerPlayerEntity, Skills> onXPChangeListener;

    // Provide access to MAX_LEVEL
    public static int getMaxLevel() {
        return MAX_LEVEL;
    }

    // Set an optional listener for XP and level changes
    public static void setOnXPChangeListener(BiConsumer<ServerPlayerEntity, Skills> listener) {
        onXPChangeListener = listener;
    }

    // Get a player's skill level
    public static int getSkillLevel(String playerUuid, Skills skill) {
        if (playerUuid == null || skill == null) {
            Simpleskills.LOGGER.error("Invalid input for getSkillLevel: playerUuid or skill is null");
            return 1;
        }
        DatabaseManager db = DatabaseManager.getInstance();
        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuid);
        DatabaseManager.SkillData skillData = skills.get(skill.getId());
        if (skillData != null) {
            return skillData.level();
        }
        // Return default level without forcing initialization
        return 1;
    }

    // Updated XP LOGIC with refined curve
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

    // Add XP to a player's skill silently (no notifications or level-up effects)
    public static void addXPSilent(ServerPlayerEntity player, Skills skill, int xpToAdd) {
        updatePlayerSkill(player, skill, xpToAdd, false, false);
    }

    // Add XP to a player's skill with notifications
    public static void addXPWithNotification(ServerPlayerEntity player, Skills skill, int xpToAdd) {
        updatePlayerSkill(player, skill, xpToAdd, true, true);
    }

    // Shared logic for updating player skill
    private static void updatePlayerSkill(ServerPlayerEntity player, Skills skill, int xpToAdd, boolean notifyXP, boolean notifyLevelUp) {
        if (player == null || skill == null) {
            Simpleskills.LOGGER.error("Invalid input: player or skill is null");
            return;
        }
        if (xpToAdd <= 0) {
            Simpleskills.LOGGER.warn("Invalid xpToAdd: {} for player UUID: {}, skill: {}", xpToAdd, player.getUuidAsString(), skill.getId());
            return;
        }

        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        JsonObject config = ConfigManager.getFeatureConfig();

        // Apply multiplier with fallback
        double multiplier = getConfigDouble(config, db.isPlayerInIronmanMode(playerUuid)
                ? "ironman_xp_multiplier" : "standard_xp_multiplier", 1.0);
        xpToAdd = (int) (xpToAdd * multiplier);

        // Ensure player is initialized
        db.initializePlayer(playerUuid);
        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuid);
        DatabaseManager.SkillData skillData = skills.getOrDefault(skill.getId(), new DatabaseManager.SkillData(0, 0));
        int currentXP = skillData.xp();
        int currentLevel = skillData.level();
        int newXP = currentXP + xpToAdd;
        int newLevel = getLevelForExperience(newXP);

        // Save to database
        try {
            db.savePlayerSkill(playerUuid, skill.getId(), newXP, newLevel);
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Failed to save player skill data for UUID: {}, skill: {}", playerUuid, skill.getId(), e);
            return;
        }

        // Send XP gain notification if enabled
        if (notifyXP) {
            boolean notificationsEnabled = getConfigBoolean(config, "xp_notifications_enabled", false);
            int threshold = getConfigInt(config, "xp_notification_threshold", 1);
            if (notificationsEnabled && xpToAdd >= threshold) {
                player.sendMessage(Text.literal(String.format("Gained %d XP in %s!", xpToAdd, skill.getDisplayName()))
                        .formatted(Formatting.GOLD), true);
            }
        }

        // Handle level-up if enabled
        if (notifyLevelUp && newLevel > currentLevel) {
            triggerLevelUpEffects(player, skill, newLevel);
        }

        // Update attributes and UI
        AttributeManager.updatePlayerAttributes(player, skill, new DatabaseManager.SkillData(newXP, newLevel));
        SkillTabMenu.updateTabMenu(player);

        // Notify listener
        if (onXPChangeListener != null) {
            onXPChangeListener.accept(player, skill);
        }
    }

    // Handle level-up effects
    private static void triggerLevelUpEffects(ServerPlayerEntity player, Skills skill, int newLevel) {
        ServerWorld serverWorld = player.getEntityWorld();
        String levelUpMessage;
        boolean isMaxLevel = newLevel == MAX_LEVEL;
        if (isMaxLevel) {
            levelUpMessage = String.format("Congratulations! You have reached max level in %s!", skill.getDisplayName());
            player.sendMessage(Text.literal(levelUpMessage).formatted(Formatting.GOLD), false);
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.PLAYERS, 0.9f, 1.2f);
        } else {
            levelUpMessage = String.format("You leveled up in %s! New level: %d", skill.getDisplayName(), newLevel);
            player.sendMessage(Text.literal(levelUpMessage).formatted(Formatting.GOLD), false);
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.7f, 1.3f);
        }
        spawnConfettiParticles(serverWorld, player.getX(), player.getY(), player.getZ(), isMaxLevel);
    }

    // Spawn confetti particles with hardcoded values
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

    // Config helper methods
    private static double getConfigDouble(JsonObject config, String key, double defaultValue) {
        if (config.has(key) && config.get(key).isJsonPrimitive() && config.getAsJsonPrimitive(key).isNumber()) {
            return config.get(key).getAsDouble();
        }
        Simpleskills.LOGGER.warn("Invalid or missing config key '{}', using default: {}", key, defaultValue);
        return defaultValue;
    }

    private static int getConfigInt(JsonObject config, String key, int defaultValue) {
        if (config.has(key) && config.get(key).isJsonPrimitive() && config.getAsJsonPrimitive(key).isNumber()) {
            return config.get(key).getAsInt();
        }
        Simpleskills.LOGGER.warn("Invalid or missing config key '{}', using default: {}", key, defaultValue);
        return defaultValue;
    }

    private static boolean getConfigBoolean(JsonObject config, String key, boolean defaultValue) {
        if (config.has(key) && config.get(key).isJsonPrimitive() && config.getAsJsonPrimitive(key).isBoolean()) {
            return config.get(key).getAsBoolean();
        }
        Simpleskills.LOGGER.warn("Invalid or missing config key '{}', using default: {}", key, defaultValue);
        return defaultValue;
    }
}