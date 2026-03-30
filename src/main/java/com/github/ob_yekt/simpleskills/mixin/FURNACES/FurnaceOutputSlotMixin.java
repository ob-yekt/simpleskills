
package com.github.ob_yekt.simpleskills.mixin.FURNACES;

import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import com.github.ob_yekt.simpleskills.utils.FurnaceCommon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceOutputSlotMixin {
    @Shadow @Final private Player player;

    @Inject(method = "onTake", at = @At("TAIL"))
    private void onTakeItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || !FurnaceCommon.isValidStack(stack)) {
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