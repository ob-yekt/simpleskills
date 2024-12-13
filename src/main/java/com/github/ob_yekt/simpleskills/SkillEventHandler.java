package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.data.PlayerSkillComponent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class SkillEventHandler {
    public static void registerEvents() {
        // Register the AFTER block break event
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // Ensure we run this logic on the server side (world.isClient == false)
            if (!world.isClient && player instanceof PlayerEntity) {
                // Obtain the skill component from the player
                PlayerSkillComponent skillComponent = SimpleSkillsComponents.getSkillComponent(player);

                // Track XP gains depending on block type broken

                if (state.getBlock() == Blocks.STONE) {
                    // Add XP to Mining skill
                    skillComponent.addXp("Mining", 10);
                    // Send player feedback
                    player.sendMessage(Text.literal("Mining XP: " + skillComponent.getLevel
                            ("Mining")
                            + "/100 | Level: " + skillComponent.getSkillLevel("Mining")), true);

                } else if (state.getBlock() == Blocks.OAK_LOG) {
                    // Add XP to Woodcutting skill
                    skillComponent.addXp("Woodcutting", 10);
                    // Send player feedback
                    player.sendMessage(Text.literal("Woodcutting XP: " + skillComponent.getLevel
                            ("Woodcutting")
                            + "/100 | Level: " + skillComponent.getSkillLevel("Woodcutting")), true);

                } else if (state.getBlock() == Blocks.DIRT || state.getBlock() == Blocks.SAND || state.getBlock() == Blocks.GRASS_BLOCK) {
                    // Add XP to Excavating
                    skillComponent.addXp("Excavating", 10);
                    player.sendMessage(Text.literal("Excavating XP: " + skillComponent.getLevel("Excavating")
                            + "/100 | Level: " + skillComponent.getSkillLevel("Excavating")), true);
                }
            }
        });
    }
}