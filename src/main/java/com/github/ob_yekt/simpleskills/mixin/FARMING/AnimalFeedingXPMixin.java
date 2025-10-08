package com.github.ob_yekt.simpleskills.mixin.FARMING;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimalEntity.class)
public abstract class AnimalFeedingXPMixin {
    @Inject(method = "interactMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/AnimalEntity;lovePlayer(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void addXPOnBreed(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer && !serverPlayer.getEntityWorld().isClient()) {
            int xp = ConfigManager.getFarmingActionXP("animal_feed_breed", Skills.FARMING);
            XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        }
    }

    @Inject(method = "interactMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/AnimalEntity;growUp(IZ)V"))
    private void addXPOnGrow(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer && !serverPlayer.getEntityWorld().isClient()) {
            int xp = ConfigManager.getFarmingActionXP("animal_feed_grow", Skills.FARMING);
            XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        }
    }
}