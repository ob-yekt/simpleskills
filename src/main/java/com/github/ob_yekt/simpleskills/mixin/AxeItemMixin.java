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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Mixin to check skill requirements and grant Woodcutting XP when using an axe to strip logs.
 */
@Mixin(AxeItem.class)
public class AxeItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        String toolName = context.getItemInHand().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == Skills.WOODCUTTING) {
            int playerLevel = XPManager.getSkillLevel(player.getStringUUID(), Skills.WOODCUTTING);
            if (playerLevel < requirement.getLevel()) {
                player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        Skills.WOODCUTTING.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using axe {} due to insufficient Woodcutting level (required: {}, actual: {})",
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
                    Simpleskills.LOGGER.debug("Prevented player {} from using axe {} due to insufficient Prestige (required: ★{}, actual: ★{})",
                            player.getName().getString(), toolName, requiredPrestige, playerPrestige);
                }
            }
        }
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void grantWoodcuttingXPOnStripLog(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player) || cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        String blockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
        if (isStrippableLog(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually stripped (i.e., is now a stripped log)
                String newBlockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
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