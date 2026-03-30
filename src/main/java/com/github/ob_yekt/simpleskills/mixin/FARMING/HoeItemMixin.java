package com.github.ob_yekt.simpleskills.mixin.FARMING;

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
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Mixin to check skill requirements and grant Farming XP when using a hoe to till blocks.
 */
@Mixin(HoeItem.class)
public class HoeItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        String toolName = context.getItemInHand().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == Skills.FARMING) {
            int playerLevel = XPManager.getSkillLevel(player.getStringUUID(), Skills.FARMING);
            if (playerLevel < requirement.getLevel()) {
                player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        Skills.FARMING.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using hoe {} due to insufficient Farming level (required: {}, actual: {})",
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
                    Simpleskills.LOGGER.debug("Prevented player {} from using hoe {} due to insufficient Prestige (required: ★{}, actual: ★{})",
                            player.getName().getString(), toolName, requiredPrestige, playerPrestige);
                }
            }
        }
    }

    @Inject(method = "useOn", at = @At("HEAD"))
    private void captureOriginalBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        lastBlockKey = context.getLevel().getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void grantFarmingXPOnTill(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) return;
        if (cir.getReturnValue() != InteractionResult.SUCCESS) return;

        Objects.requireNonNull(world.getServer()).execute(() -> {
            if (world.getBlockState(context.getClickedPos()).is(Blocks.FARMLAND)) {
                int xp = ConfigManager.getBlockXP(lastBlockKey, Skills.FARMING);
                XPManager.addXPWithNotification(player, Skills.FARMING, xp/5);
                Simpleskills.LOGGER.debug("Granted {} Farming XP to {} for tilling {}",
                        xp, player.getName().getString(), lastBlockKey);
            }
        });
    }

    @Unique
    private String lastBlockKey;
}