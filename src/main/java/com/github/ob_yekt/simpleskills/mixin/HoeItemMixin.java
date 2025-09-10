package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import net.minecraft.block.Blocks;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Mixin to check skill requirements and grant Farming XP when using a hoe to till blocks.
 */
@Mixin(HoeItem.class)
public class HoeItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void captureOriginalBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        lastBlockKey = context.getWorld().getBlockState(context.getBlockPos()).getBlock().getTranslationKey();
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void grantFarmingXPOnTill(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient() || !(context.getPlayer() instanceof ServerPlayerEntity player)) return;
        if (cir.getReturnValue() != ActionResult.SUCCESS) return;

        Objects.requireNonNull(world.getServer()).execute(() -> {
            if (world.getBlockState(context.getBlockPos()).isOf(Blocks.FARMLAND)) {
                int xp = ConfigManager.getBlockXP(lastBlockKey, Skills.FARMING);
                XPManager.addXPWithNotification(player, Skills.FARMING, xp/25);
                Simpleskills.LOGGER.debug("Granted {} Farming XP to {} for tilling {}", xp, player.getName().getString(), lastBlockKey);
            }
        });
    }

    @Unique
    private String lastBlockKey;
}