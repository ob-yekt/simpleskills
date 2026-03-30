package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager.SkillData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.Map;

/**
 * Manages player attribute modifications based on skills and Ironman mode.
 */
public class AttributeManager {
    private static final Identifier IRONMAN_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("simpleskills", "ironman_health_reduction");

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

    public static void refreshAllAttributes(ServerPlayer player) {
        if (player == null) {
            Simpleskills.LOGGER.warn("Null player in refreshAllAttributes.");
            return;
        }
        String playerUuid = player.getStringUUID();
        clearSkillAttributes(player);
        clearIronmanAttributes(player);
        refreshSkillAttributes(player);
        if (DatabaseManager.getInstance().isPlayerInIronmanMode(playerUuid)) {
            applyIronmanAttributes(player);
        }
        Simpleskills.LOGGER.debug("Refreshed all attributes for player: {}", player.getName().getString());
    }

    public static void clearSkillAttributes(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) moveSpeed.removeModifier(Identifier.parse("simpleskills:agility_bonus"));
    }

    public static void applyIronmanAttributes(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(IRONMAN_HEALTH_MODIFIER_ID);
            double healthReduction = ConfigManager.getFeatureConfig().get("ironman_health_reduction") != null ?
                    ConfigManager.getFeatureConfig().get("ironman_health_reduction").getAsDouble() : -10.0;
            healthAttribute.addPermanentModifier(
                    new AttributeModifier(
                            IRONMAN_HEALTH_MODIFIER_ID,
                            healthReduction,
                            AttributeModifier.Operation.ADD_VALUE
                    )
            );
        }
    }

    public static void clearIronmanAttributes(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance healthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.removeModifier(IRONMAN_HEALTH_MODIFIER_ID);
        }
    }

    public static void refreshSkillAttributes(ServerPlayer player) {
        if (player == null) return;
        String playerUuid = player.getStringUUID();
        Map<String, SkillData> skills = DatabaseManager.getInstance().getAllSkills(playerUuid);
        for (Skills skill : Skills.values()) {
            updatePlayerAttributes(player, skill, skills.getOrDefault(skill.getId(), new SkillData(0, 1)));
        }
    }

    public static void updatePlayerAttributes(ServerPlayer player, Skills skill, SkillData skillData) {
        if (player == null || skillData == null) return;
        int skillLevel = skillData.level();

        switch (skill) {
            case AGILITY -> {
                AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (moveSpeed != null) {
                    moveSpeed.removeModifier(Identifier.parse("simpleskills:agility_bonus"));
                    double bonusSpeed = skillLevel * 0.001;
                    moveSpeed.addPermanentModifier(new AttributeModifier(
                            Identifier.parse("simpleskills:agility_bonus"),
                            bonusSpeed,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                }
            }
            case SLAYING, DEFENSE, EXCAVATING, FARMING, FISHING, SMITHING, ALCHEMY, COOKING, CRAFTING, RANGED, ENCHANTING, PRAYER -> {
                // No direct attribute bonuses for these skills
            }
        }
    }
}