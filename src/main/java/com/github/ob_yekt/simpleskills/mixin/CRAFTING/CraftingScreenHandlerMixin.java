package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.utils.CraftingCommon;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin extends AbstractCraftingScreenHandler {

    @Unique
    private final RecipeInputInventory craftingInventory = getCraftingInventory();

    @Unique
    private final ItemStack[] originalInputs = new ItemStack[9];

    protected CraftingScreenHandlerMixin(ScreenHandlerType<?> type, int syncId, int gridWidth, int gridHeight) {
        super(type, syncId, gridWidth, gridHeight);
    }

    @Unique
    private RecipeInputInventory getCraftingInventory() {
        return ((AbstractCraftingScreenHandlerAccessor) this).getCraftingInventory();
    }

    @Inject(
            method = "quickMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/Item;onCraftByPlayer(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveCraft(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) ItemStack itemStack, @Local Slot slot, @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if stack is empty or represents air
            if (itemStack2.isEmpty() || Registries.ITEM.getId(itemStack2.getItem()).toString().equals("minecraft:air")) {
                return;
            }

            // Capture original input stacks for material recovery
            for (int i = 0; i < 9; i++) {
                originalInputs[i] = craftingInventory.getStack(i).copy();
            }

            // Apply bonuses using CraftingCommon (which already handles both types)
            if (CraftingCommon.isCraftableItem(itemStack2)) {
                CraftingCommon.applyCraftingLore(itemStack2, serverPlayer);
                CraftingCommon.applyCraftingScaling(itemStack2, serverPlayer);
            } else if (CraftingCommon.isCookableFoodItem(itemStack2)) {
                CraftingCommon.applyCookingLore(itemStack2, serverPlayer);
                CraftingCommon.applyCookingScaling(itemStack2, serverPlayer);
            }
        }
    }

    @Inject(
            method = "quickMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/screen/slot/Slot;onQuickTransfer(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveAfterTransfer(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) ItemStack itemStack, @Local Slot slot, @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if itemStack is empty or represents air
            if (itemStack.isEmpty() || Registries.ITEM.getId(itemStack.getItem()).toString().equals("minecraft:air")) {
                return;
            }

            int movedCount = itemStack.getCount() - (itemStack2.isEmpty() ? 0 : itemStack2.getCount());
            if (movedCount > 0) {
                ItemStack movedStack = itemStack.copy();
                movedStack.setCount(movedCount);

                // Grant XP for crafting
                if (CraftingCommon.isCraftableItem(movedStack)) {
                    CraftingCommon.grantCraftingXP(serverPlayer, movedStack);
                }

                // Grant XP for cooking
                if (CraftingCommon.isCookableFoodItem(movedStack)) {
                    CraftingCommon.grantCookingXP(serverPlayer, movedStack);
                }

                // Apply material recovery
                applyMaterialRecovery(serverPlayer, itemStack);
            }
        }
    }

    @Unique
    private void applyMaterialRecovery(ServerPlayerEntity player, ItemStack outputStack) {
        String itemId = Registries.ITEM.getId(outputStack.getItem()).toString();

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float recoveryChance = ConfigManager.getCraftingRecoveryChance(level);
        if (recoveryChance <= 0) return;

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = craftingInventory.getStack(i);

            if (!original.isEmpty() && current.getCount() < original.getCount()) {
                if (player.getRandom().nextFloat() < recoveryChance) {
                    ItemStack recovered = original.copy();
                    recovered.setCount(1);

                    if (!player.getInventory().insertStack(recovered)) {
                        player.dropItem(recovered, false);
                    }
                }
            }
        }
    }
}