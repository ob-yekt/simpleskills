package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Mixin to check skill requirements and grant Excavation XP when using a shovel to create path blocks.
 */
@Mixin(ShovelItem.class)
public class ShovelItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        String toolName = context.getItemInHand().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == Skills.EXCAVATING) {
            int playerLevel = XPManager.getSkillLevel(player.getStringUUID(), Skills.EXCAVATING);
            if (playerLevel < requirement.getLevel()) {
                player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        Skills.EXCAVATING.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using shovel {} due to insufficient Excavation level (required: {}, actual: {})",
                        player.getName().getString(), toolName, requirement.getLevel(), playerLevel);
                return;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                if (playerPrestige < requiredPrestige) {
                    player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use this tool!",
                            requiredPrestige)), true);
                    cir.setReturnValue(InteractionResult.FAIL);
                    cir.cancel();
                    Simpleskills.LOGGER.debug("Prevented player {} from using shovel {} due to insufficient Prestige (required: ★{}, actual: ★{})",
                            player.getName().getString(), toolName, requiredPrestige, playerPrestige);
                }
            }
        }
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void grantExcavationXPOnPathCreation(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player) || cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        String blockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
        if (isPathableBlock(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually converted to a dirt path
                if (world.getBlockState(context.getClickedPos()).is(Blocks.DIRT_PATH)) {
                    int xp = ConfigManager.getBlockXP(blockTranslationKey, Skills.EXCAVATING);
                    XPManager.addXPWithNotification(player, Skills.EXCAVATING, xp/5);
                    Simpleskills.LOGGER.debug("Granted {} Excavation XP to {} for creating path from {}",
                            xp, player.getName().getString(), blockTranslationKey);
                }
            });
        }
    }

    @Unique
    private static boolean isPathableBlock(String blockTranslationKey) {
        return blockTranslationKey.contains("dirt") || blockTranslationKey.contains("grass_block") ||
                blockTranslationKey.contains("podzol") || blockTranslationKey.contains("mycelium") ||
                blockTranslationKey.contains("coarse_dirt");
    }
}