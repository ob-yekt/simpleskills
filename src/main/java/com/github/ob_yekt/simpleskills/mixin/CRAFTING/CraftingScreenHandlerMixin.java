package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.utils.CraftingCommon;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(CraftingMenu.class)
public abstract class CraftingScreenHandlerMixin extends AbstractCraftingMenu {

    @Unique
    private final CraftingContainer craftingInventory = getCraftingInventory();

    @Unique
    private final ItemStack[] originalInputs = new ItemStack[9];

    protected CraftingScreenHandlerMixin(MenuType<?> type, int syncId, int gridWidth, int gridHeight) {
        super(type, syncId, gridWidth, gridHeight);
    }

    @Unique
    private CraftingContainer getCraftingInventory() {
        return ((AbstractCraftingScreenHandlerAccessor) this).getCraftingInventory();
    }

    @Inject(
            method = "quickMoveStack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/Item;onCraftedBy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveCraft(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) ItemStack itemStack, @Local Slot slot, @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer) {
            // Skip if stack is empty or represents air
            if (itemStack2.isEmpty() || BuiltInRegistries.ITEM.getKey(itemStack2.getItem()).toString().equals("minecraft:air")) {
                return;
            }

            // Capture original input stacks for material recovery
            for (int i = 0; i < 9; i++) {
                originalInputs[i] = craftSlots.getItem(i).copy();
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
            method = "quickMoveStack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;onQuickCraft(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveAfterTransfer(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) ItemStack itemStack, @Local Slot slot, @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 0 && player instanceof ServerPlayer serverPlayer) {
            // Skip if itemStack is empty or represents air
            if (itemStack.isEmpty() || BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString().equals("minecraft:air")) {
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
    private void applyMaterialRecovery(ServerPlayer player, ItemStack outputStack) {
        String itemId = BuiltInRegistries.ITEM.getKey(outputStack.getItem()).toString();

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.CRAFTING);
        float recoveryChance = ConfigManager.getCraftingRecoveryChance(level);
        if (recoveryChance <= 0) return;

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = craftSlots.getItem(i);

            if (!original.isEmpty() && current.getCount() < original.getCount()) {
                if (player.getRandom().nextFloat() < recoveryChance) {
                    ItemStack recovered = original.copy();
                    recovered.setCount(1);

                    if (!player.getInventory().add(recovered)) {
                        player.drop(recovered, false);
                    }
                }
            }
        }
    }
}