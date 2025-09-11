package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Mixin to check skill requirements and grant Excavation XP when using a shovel to create path blocks.
 */
@Mixin(ShovelItem.class)
public class ShovelItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return;
        }

        String toolName = context.getStack().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == Skills.EXCAVATING) {
            int playerLevel = XPManager.getSkillLevel(player.getUuidAsString(), Skills.EXCAVATING);
            if (playerLevel < requirement.getLevel()) {
                player.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        Skills.EXCAVATING.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using shovel {} due to insufficient Excavation level (required: {}, actual: {})",
                        player.getName().getString(), toolName, requirement.getLevel(), playerLevel);
            }
        }
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void grantExcavationXPOnPathCreation(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player) || cir.getReturnValue() != ActionResult.SUCCESS) {
            return;
        }

        String blockTranslationKey = world.getBlockState(context.getBlockPos()).getBlock().getTranslationKey();
        if (isPathableBlock(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually converted to a dirt path
                if (world.getBlockState(context.getBlockPos()).isOf(Blocks.DIRT_PATH)) {
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