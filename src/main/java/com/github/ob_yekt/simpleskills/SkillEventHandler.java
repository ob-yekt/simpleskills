package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.data.PlayerSkillComponent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class SkillEventHandler {
    public static void registerEvents() {
        // Register BEFORE block break event (Restrict block breaking based on tool requirements)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            // Only run logic on the server
            if (!world.isClient && player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

                // Obtain player's skill component
                PlayerSkillComponent skillComponent = SimpleSkillsComponents.getSkillComponent(serverPlayer);

                // Get the tool the player is using
                ItemStack mainHandItem = serverPlayer.getMainHandStack();
                if (mainHandItem.isEmpty()) {
                    return true; // Allow block breaking without a tool
                }

                // Check the tool requirements
                String toolName = mainHandItem.getItem().toString(); // Get the tool name (e.g., "minecraft:wooden_pickaxe")
                SkillRequirement requirement = ToolRequirementLoader.getRequirement(toolName);

                if (requirement != null) {
                    String skillName = requirement.getSkillName(); // e.g., "Mining"
                    int requiredLevel = requirement.getRequiredLevel();
                    int playerLevel = skillComponent.getSkillLevel(skillName); // Get the player's skill level from Cardinal Components

                    // Check if the player meets the requirement
                    if (playerLevel < requiredLevel) {
                        // Send message to the player and cancel block breaking
                        serverPlayer.sendMessage(
                                Text.of("[SimpleSkills] You need " + skillName + " level " + requiredLevel + " to use this tool!"),
                                true
                        );
                        return false; // Cancel the block break event
                    }
                }
            }
            return true; // Allow block breaking if no restrictions apply
        });

        // Register AFTER block break event (Grant XP for skills)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // Only run logic on the server
            if (!world.isClient && player instanceof PlayerEntity) {
                PlayerSkillComponent skillComponent = SimpleSkillsComponents.getSkillComponent(player);

                // Grant experience based on the block type broken
                if (state.getBlock().getTranslationKey().contains("stone")) {
                    skillComponent.addXp("Mining", 10); // Add XP to Mining
                    sendXpUpdateMessage(player, skillComponent, "Mining");
                } else if (state.getBlock().getTranslationKey().contains("log")) {
                    skillComponent.addXp("Woodcutting", 10); // Add XP to Woodcutting
                    sendXpUpdateMessage(player, skillComponent, "Woodcutting");
                } else if (state.getBlock().getTranslationKey().contains("dirt")
                        || state.getBlock().getTranslationKey().contains("sand")
                        || state.getBlock().getTranslationKey().contains("grass")) {
                    skillComponent.addXp("Excavating", 10); // Add XP to Excavating
                    sendXpUpdateMessage(player, skillComponent, "Excavating");
                }
            }
        });
    }

    /**
     * Sends a message to the player with their current XP and level progress for the given skill.
     *
     * @param player The player to send the message to.
     * @param component The player's skill component.
     * @param skillName The skill being updated (e.g., "Mining").
     */
    private static void sendXpUpdateMessage(PlayerEntity player, PlayerSkillComponent component, String skillName) {
        int currentLevel = component.getSkillLevel(skillName);
        int currentXp = component.getLevel(skillName);
        player.sendMessage(
                Text.literal(skillName + " XP: " + currentXp + "/100 | Level: " + currentLevel),
                true
        );
    }
}