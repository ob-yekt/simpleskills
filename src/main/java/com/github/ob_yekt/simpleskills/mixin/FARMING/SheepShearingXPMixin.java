package com.github.ob_yekt.simpleskills.mixin.FARMING;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Sheep.class)
public abstract class SheepShearingXPMixin {
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/sheep/Sheep;shear(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/world/item/ItemStack;)V"))
    private void addXPOnShear(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            int xp = ConfigManager.getFarmingActionXP("shear_sheep", Skills.FARMING);
            XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        }
    }
}