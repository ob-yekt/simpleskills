package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgingScreenHandler.class)
public abstract class ForgingScreenHandlerMixin {
    @Inject(
            method = "quickMove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableOutputQuickMove(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (this instanceof ForgingScreenHandlerMixin && slotIndex == 2) {
            // Return an empty stack to indicate no transfer occurred
            cir.setReturnValue(ItemStack.EMPTY);
            cir.cancel();
        }
    }
}