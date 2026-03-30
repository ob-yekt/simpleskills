package com.github.ob_yekt.simpleskills.mixin.FARMING;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalFeedingXPMixin {
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;setInLove(Lnet/minecraft/world/entity/player/Player;)V"))
    private void addXPOnBreed(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            int xp = ConfigManager.getFarmingActionXP("animal_feed_breed", Skills.FARMING);
            XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        }
    }

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;ageUp(IZ)V"))
    private void addXPOnGrow(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            int xp = ConfigManager.getFarmingActionXP("animal_feed_grow", Skills.FARMING);
            XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        }
    }
}