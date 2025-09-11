package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

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
            // Capture original input stacks
            for (int i = 0; i < 9; i++) {
                originalInputs[i] = craftingInventory.getStack(i).copy();
            }
            applyCraftingLore(itemStack2, serverPlayer);
            applyCraftingScaling(itemStack2, serverPlayer);
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
            int movedCount = itemStack.getCount() - (itemStack2.isEmpty() ? 0 : itemStack2.getCount());
            if (movedCount > 0) {
                ItemStack movedStack = itemStack.copy(); // Use itemStack for consistency with crafted item
                movedStack.setCount(movedCount);
                grantCraftingXP(serverPlayer, movedStack);
                // Use itemStack for blacklist check to ensure we're checking the crafted item
                applyMaterialRecovery(serverPlayer, itemStack);
            }
        }
    }

    @Unique
    private void grantCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        String itemKey = Registries.ITEM.getId(stack.getItem()).toString();
        int xpPerItem = ConfigManager.getCraftingXP(itemKey, Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

        Simpleskills.LOGGER.debug(
                "Granted {} Crafting XP for {}x {} to player {}",
                totalXP, stack.getCount(), itemKey, player.getName().getString()
        );
    }

    @Unique
    private void applyCraftingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        Text craftingLore = Text.literal("Crafted by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Crafter)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        currentLore.addFirst(craftingLore);
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }

    @Unique
    private void applyCraftingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float multiplier = getDurabilityMultiplier(level);

        Integer original = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (original == null) return;

        int newMax = Math.max(1, Math.round(original * multiplier));
        stack.set(DataComponentTypes.MAX_DAMAGE, newMax);

        Simpleskills.LOGGER.debug(
                "Scaled durability for {} from {} -> {} for player {} (lvl {}, multiplier {})",
                Registries.ITEM.getId(stack.getItem()).toString(),
                original, newMax,
                player.getName().getString(),
                level, multiplier
        );
    }

    @Unique
    private void applyMaterialRecovery(ServerPlayerEntity player, ItemStack outputStack) {
        // Check if the output item is blacklisted
        String itemId = Registries.ITEM.getId(outputStack.getItem()).toString();

        // Enhanced debug logging
        Simpleskills.LOGGER.info("=== MATERIAL RECOVERY DEBUG (Shift-Click) ===");
        Simpleskills.LOGGER.info("Player: {}", player.getName().getString());
        Simpleskills.LOGGER.info("Output item: {}", itemId);
        Simpleskills.LOGGER.info("Is blacklisted: {}", ConfigManager.isRecipeBlacklisted(itemId));

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            Simpleskills.LOGGER.info("Skipping material recovery for blacklisted item: {}", itemId);
            return;
        }

        Simpleskills.LOGGER.info("Proceeding with recovery check for: {}", itemId);

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float recoveryChance = getRecoveryChance(level);
        Simpleskills.LOGGER.info("Recovery chance: {} for level {}", recoveryChance, level);
        if (recoveryChance <= 0) {
            Simpleskills.LOGGER.info("No recovery possible (chance <= 0)");
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = craftingInventory.getStack(i);
            Simpleskills.LOGGER.info("Slot {}: Original count: {}, Current count: {}", i, original.getCount(), current.getCount());
            if (!original.isEmpty() && current.getCount() < original.getCount()) {
                if (player.getRandom().nextFloat() < recoveryChance) {
                    ItemStack recovered = original.copy();
                    recovered.setCount(1);
                    if (!player.getInventory().insertStack(recovered)) {
                        player.dropItem(recovered, false);
                    }
                    Simpleskills.LOGGER.info(
                            "Recovered {} for player {} (lvl {}, chance {})",
                            recovered.getItem().getTranslationKey(),
                            player.getName().getString(),
                            level,
                            recoveryChance
                    );
                }
            }
        }
    }

    @Unique
    private float getDurabilityMultiplier(int level) {
        return ConfigManager.getCraftingDurabilityMultiplier(level);
    }

    @Unique
    private float getRecoveryChance(int level) {
        return ConfigManager.getCraftingRecoveryChance(level);
    }
}