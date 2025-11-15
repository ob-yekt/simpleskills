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
import java.util.function.IntUnaryOperator;

public class XPManager {
    private static final int MAX_LEVEL = 99;
    private static BiConsumer<ServerPlayerEntity, Skills> onXPChangeListener;

    private static double STANDARD_XP_MULTIPLIER;
    private static double IRONMAN_XP_MULTIPLIER;
    private static boolean XP_NOTIFICATIONS_ENABLED;
    private static int XP_NOTIFICATION_THRESHOLD;
    private static boolean LEVEL_UP_EFFECTS_ENABLED;
    private static boolean LEVEL_UP_NOTIFICATIONS_ENABLED;

    static {
        loadConfig();
    }

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

    /// IMPROVED XP-SYSTEM

// Curve parameters tuned for ~45 min to level 25 (wood+stone) and exactly 20.4 hours to level 99
    private static final double SCALING_A  = 50.751;
    private static final double EXPONENT_P = 2.672;
    private static final double OFFSET_K   = 0.295;

    public static int getExperienceForLevel(int level) {
        if (level <= 1) return 0;
        double base = Math.pow(1.0 + OFFSET_K, EXPONENT_P);
        return (int) Math.floor(SCALING_A * (Math.pow((double) level + OFFSET_K, EXPONENT_P) - base));
    }

    public static int getLevelForExperience(int experience) {
        return calculateLevelForExperience(experience, XPManager::getExperienceForLevel);
    }

    /// OLD XP-SYSTEM (for migration purposes only)

    // Old curve parameters
    private static final double OLD_EXPONENT_P = 2.45;
    private static final double OLD_SCALING_A  = 300.0;
    private static final double OLD_FLOOR_B    = 500.0;

    /**
     * Old XP calculation function - used only for migration.
     * Calculates the experience required for a given level in the old system.
     */
    public static int getExperienceForLevelOld(int level) {
        if (level <= 1) return 0;
        double L = level - 1;
        return (int) Math.floor(OLD_SCALING_A * Math.pow(L, OLD_EXPONENT_P) + OLD_FLOOR_B * L);
    }

    /**
     * Old level calculation function - used only for migration.
     * Calculates the level for a given experience amount in the old system.
     */
    public static int getLevelForExperienceOld(int experience) {
        return calculateLevelForExperience(experience, XPManager::getExperienceForLevelOld);
    }

    /**
     * Generic binary search helper to calculate level from experience.
     * Uses the provided XP calculation function to determine the level.
     */
    private static int calculateLevelForExperience(int experience, IntUnaryOperator xpCalculator) {
        if (experience <= 0) return 1;
        int low = 1;
        int high = MAX_LEVEL;
        while (low < high) {
            int mid = (low + high + 1) >>> 1; // bias upward
            int xpForMid = xpCalculator.applyAsInt(mid);
            if (xpForMid <= experience) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
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

        // Apply multiplier with fallback
        double multiplier = db.isPlayerInIronmanMode(playerUuid) ? IRONMAN_XP_MULTIPLIER : STANDARD_XP_MULTIPLIER;
        xpToAdd = (int) (xpToAdd * multiplier);

        // Ensure player is initialized (checks first, avoids redundant DB ops)
        db.ensurePlayerInitialized(playerUuid);
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
            if (XP_NOTIFICATIONS_ENABLED && xpToAdd >= XP_NOTIFICATION_THRESHOLD) {
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
            if (LEVEL_UP_NOTIFICATIONS_ENABLED) {
                player.sendMessage(Text.literal(levelUpMessage).formatted(Formatting.GOLD), false);
            }
            if (LEVEL_UP_EFFECTS_ENABLED) {
                serverWorld.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundCategory.PLAYERS, 0.9f, 1.2f);
            }
        } else {
            levelUpMessage = String.format("You leveled up in %s! New level: %d", skill.getDisplayName(), newLevel);
            if (LEVEL_UP_NOTIFICATIONS_ENABLED) {
                player.sendMessage(Text.literal(levelUpMessage).formatted(Formatting.GOLD), false);
            }
            if (LEVEL_UP_EFFECTS_ENABLED) {
                serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS, 0.7f, 1.3f);
            }
        }
        if (LEVEL_UP_EFFECTS_ENABLED) {
            spawnConfettiParticles(serverWorld, player.getX(), player.getY(), player.getZ(), isMaxLevel);
        }
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

    private static void loadConfig() {
        JsonObject config = ConfigManager.getFeatureConfig();
        STANDARD_XP_MULTIPLIER = getConfigDouble(config, "standard_xp_multiplier", 1.0);
        IRONMAN_XP_MULTIPLIER = getConfigDouble(config, "ironman_xp_multiplier", 0.5);
        XP_NOTIFICATIONS_ENABLED = getConfigBoolean(config, "xp_notifications_enabled", true);
        XP_NOTIFICATION_THRESHOLD = getConfigInt(config, "xp_notification_threshold", 10);
        LEVEL_UP_EFFECTS_ENABLED = getConfigBoolean(config, "level_up_effects_enabled", true);
        LEVEL_UP_NOTIFICATIONS_ENABLED = getConfigBoolean(config, "level_up_notifications_enabled", true);
    }

    public static void reloadConfig() {
        loadConfig();
        Simpleskills.LOGGER.info("Reloaded XPManager config values.");
    }

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