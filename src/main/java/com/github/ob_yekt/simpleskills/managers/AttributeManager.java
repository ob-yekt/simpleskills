package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager.SkillData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Manages player attribute modifications based on skills and Ironman mode.
 */
public class AttributeManager {
    private static final Identifier IRONMAN_HEALTH_MODIFIER_ID = Identifier.of("simpleskills", "ironman_health_reduction");

    public static void registerPlayerEvents() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer == null) {
                Simpleskills.LOGGER.warn("Null newPlayer in respawn event.");
                return;
            }
            refreshAllAttributes(newPlayer);
            Simpleskills.LOGGER.debug("Reapplied attributes for player {} on respawn", newPlayer.getName().getString());
        });
    }

    public static void refreshAllAttributes(ServerPlayerEntity player) {
        if (player == null) {
            Simpleskills.LOGGER.warn("Null player in refreshAllAttributes.");
            return;
        }
        String playerUuid = player.getUuidAsString();
        clearSkillAttributes(player);
        clearIronmanAttributes(player);
        refreshSkillAttributes(player);
        if (DatabaseManager.getInstance().isPlayerInIronmanMode(playerUuid)) {
            applyIronmanAttributes(player);
        }
        Simpleskills.LOGGER.debug("Refreshed all attributes for player: {}", player.getName().getString());
    }

    public static void clearSkillAttributes(ServerPlayerEntity player) {
        if (player == null) return;
        EntityAttributeInstance moveSpeed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (moveSpeed != null) moveSpeed.removeModifier(Identifier.of("simpleskills:agility_bonus"));
    }

    public static void applyIronmanAttributes(ServerPlayerEntity player) {
        if (player == null) return;
        EntityAttributeInstance healthAttribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(IRONMAN_HEALTH_MODIFIER_ID);
            double healthReduction = ConfigManager.getFeatureConfig().get("ironman_health_reduction") != null ?
                    ConfigManager.getFeatureConfig().get("ironman_health_reduction").getAsDouble() : -10.0;
            healthAttribute.addPersistentModifier(
                    new EntityAttributeModifier(
                            IRONMAN_HEALTH_MODIFIER_ID,
                            healthReduction,
                            EntityAttributeModifier.Operation.ADD_VALUE
                    )
            );
        }
    }

    public static void clearIronmanAttributes(ServerPlayerEntity player) {
        if (player == null) return;
        EntityAttributeInstance healthAttribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(IRONMAN_HEALTH_MODIFIER_ID);
        }
    }

    public static void refreshSkillAttributes(ServerPlayerEntity player) {
        if (player == null) return;
        String playerUuid = player.getUuidAsString();
        Map<String, SkillData> skills = DatabaseManager.getInstance().getAllSkills(playerUuid);
        for (Skills skill : Skills.values()) {
            updatePlayerAttributes(player, skill, skills.getOrDefault(skill.getId(), new SkillData(0, 1)));
        }
    }

    public static void updatePlayerAttributes(ServerPlayerEntity player, Skills skill, SkillData skillData) {
        if (player == null || skillData == null) return;
        int skillLevel = skillData.level();

        switch (skill) {
            case AGILITY -> {
                EntityAttributeInstance moveSpeed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (moveSpeed != null) {
                    moveSpeed.removeModifier(Identifier.of("simpleskills:agility_bonus"));
                    double bonusSpeed = skillLevel * 0.001;
                    moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                            Identifier.of("simpleskills:agility_bonus"),
                            bonusSpeed,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                }
            }
            case SLAYING, DEFENSE, EXCAVATING, FARMING, FISHING, SMITHING, ALCHEMY, COOKING, CRAFTING, RANGED, ENCHANTING, PRAYER -> {
                // No direct attribute bonuses for these skills
            }
        }
    }
}