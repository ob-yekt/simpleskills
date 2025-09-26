
package com.github.ob_yekt.simpleskills.mixin.FURNACES;

import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import com.github.ob_yekt.simpleskills.utils.FurnaceCommon;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceOutputSlot.class)
public abstract class FurnaceOutputSlotMixin {
    @Shadow @Final private PlayerEntity player;

    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !FurnaceCommon.isValidStack(stack)) {
            return;
        }

        if (FurnaceCommon.isCookableFoodItem(stack)) {
            FurnaceCommon.grantCookingXP(serverPlayer, stack);
            CraftingCommon.applyCookingLore(stack, serverPlayer);
            CraftingCommon.applyCookingScaling(stack, serverPlayer);
        }

        // Crafting XP can apply to non-food items too
        FurnaceCommon.grantSmeltingCraftingXP(serverPlayer, stack);
    }
}