package com.github.ob_yekt.simpleskills.mixin.LORE;

import com.github.ob_yekt.simpleskills.managers.LoreManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class PlayerInventoryMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        LoreManager.applyLoreToStack(stack);
    }

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void onMarkDirty(CallbackInfo ci) {
        Inventory inventory = (Inventory) (Object) this;
        LoreManager.applyLoreToInventory(inventory);
    }
}