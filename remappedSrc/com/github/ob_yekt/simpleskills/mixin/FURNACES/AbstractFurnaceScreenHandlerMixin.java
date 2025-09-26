
package com.github.ob_yekt.simpleskills.mixin.FURNACES;

import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import com.github.ob_yekt.simpleskills.utils.FurnaceCommon;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(AbstractFurnaceScreenHandler.class)
public abstract class AbstractFurnaceScreenHandlerMixin {

    @Inject(
            method = "quickMove",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/screen/AbstractFurnaceScreenHandler;insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z",
                    shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveBeforeInsert(PlayerEntity player, int slotIndex,
                                         CallbackInfoReturnable<ItemStack> cir,
                                         @Local(ordinal = 0) ItemStack itemStack,
                                         @Local Slot slot,
                                         @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 2 && player instanceof ServerPlayerEntity serverPlayer
                && FurnaceCommon.isValidStack(itemStack2)) {

            if (FurnaceCommon.isCookableFoodItem(itemStack2)) {
                CraftingCommon.applyCookingLore(itemStack2, serverPlayer);
                CraftingCommon.applyCookingScaling(itemStack2, serverPlayer);
            }
        }
    }

    @Inject(
            method = "quickMove",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/screen/slot/Slot;onQuickTransfer(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveAfterTransfer(PlayerEntity player, int slotIndex,
                                          CallbackInfoReturnable<ItemStack> cir,
                                          @Local(ordinal = 0) ItemStack itemStack,
                                          @Local Slot slot,
                                          @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 2 && player instanceof ServerPlayerEntity serverPlayer
                && FurnaceCommon.isValidStack(itemStack)) {

            int movedCount = itemStack.getCount() - (itemStack2.isEmpty() ? 0 : itemStack2.getCount());
            if (movedCount > 0) {
                ItemStack movedStack = itemStack.copy();
                movedStack.setCount(movedCount);

                if (FurnaceCommon.isCookableFoodItem(movedStack)) {
                    FurnaceCommon.grantCookingXP(serverPlayer, movedStack);
                }
                FurnaceCommon.grantSmeltingCraftingXP(serverPlayer, movedStack);
            }
        }
    }
}