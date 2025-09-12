package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemUsageContext;
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
 * Mixin to check skill requirements and grant Woodcutting XP when using an axe to strip logs.
 */
@Mixin(AxeItem.class)
public class AxeItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return;
        }

        String toolName = context.getStack().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == Skills.WOODCUTTING) {
            int playerLevel = XPManager.getSkillLevel(player.getUuidAsString(), Skills.WOODCUTTING);
            if (playerLevel < requirement.getLevel()) {
                player.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        Skills.WOODCUTTING.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using axe {} due to insufficient Woodcutting level (required: {}, actual: {})",
                        player.getName().getString(), toolName, requirement.getLevel(), playerLevel);
            }
        }
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void grantWoodcuttingXPOnStripLog(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player) || cir.getReturnValue() != ActionResult.SUCCESS) {
            return;
        }

        String blockTranslationKey = world.getBlockState(context.getBlockPos()).getBlock().getTranslationKey();
        if (isStrippableLog(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually stripped (i.e., is now a stripped log)
                String newBlockTranslationKey = world.getBlockState(context.getBlockPos()).getBlock().getTranslationKey();
                if (newBlockTranslationKey.contains("stripped_")) {
                    int xp = ConfigManager.getBlockXP(blockTranslationKey, Skills.WOODCUTTING);
                    XPManager.addXPWithNotification(player, Skills.WOODCUTTING, xp/6);
                    Simpleskills.LOGGER.debug("Granted {} Woodcutting XP to {} for stripping log {}",
                            xp, player.getName().getString(), blockTranslationKey);
                }
            });
        }
    }

    @Unique
    private static boolean isStrippableLog(String blockTranslationKey) {
        return blockTranslationKey.contains("log") || blockTranslationKey.contains("stem") ||
                blockTranslationKey.contains("wood") || blockTranslationKey.contains("hyphae") &&
                !blockTranslationKey.contains("copper");
    }
}