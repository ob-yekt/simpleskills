
package com.github.ob_yekt.simpleskills.mixin.FURNACES;

import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import com.github.ob_yekt.simpleskills.utils.FurnaceCommon;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceScreenHandlerMixin {

    @Inject(
            method = "quickMoveStack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z",
                    shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveBeforeInsert(Player player, int slotIndex,
                                         CallbackInfoReturnable<ItemStack> cir,
                                         @Local(ordinal = 0) ItemStack itemStack,
                                         @Local Slot slot,
                                         @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 2 && player instanceof ServerPlayer serverPlayer
                && FurnaceCommon.isValidStack(itemStack2)) {

            if (FurnaceCommon.isCookableFoodItem(itemStack2)) {
                CraftingCommon.applyCookingLore(itemStack2, serverPlayer);
                CraftingCommon.applyCookingScaling(itemStack2, serverPlayer);
            }
        }
    }

    @Inject(
            method = "quickMoveStack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;onQuickCraft(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveAfterTransfer(Player player, int slotIndex,
                                          CallbackInfoReturnable<ItemStack> cir,
                                          @Local(ordinal = 0) ItemStack itemStack,
                                          @Local Slot slot,
                                          @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 2 && player instanceof ServerPlayer serverPlayer
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