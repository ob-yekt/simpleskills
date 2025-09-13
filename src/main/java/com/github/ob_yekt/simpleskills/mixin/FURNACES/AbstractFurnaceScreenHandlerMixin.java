package com.github.ob_yekt.simpleskills.mixin.FURNACES;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
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

@Mixin(AbstractFurnaceScreenHandler.class)
public abstract class AbstractFurnaceScreenHandlerMixin {

    @Inject(
            method = "quickMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/screen/AbstractFurnaceScreenHandler;insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z",
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onQuickMoveBeforeInsert(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) ItemStack itemStack, @Local Slot slot, @Local(ordinal = 1) ItemStack itemStack2) {
        if (slotIndex == 2 && player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if stack is empty or represents air
            if (itemStack2.isEmpty() || Registries.ITEM.getId(itemStack2.getItem()).toString().equals("minecraft:air")) {
                Simpleskills.LOGGER.debug("Skipping onQuickMoveBeforeInsert for empty or air stack: {}", itemStack2);
                return;
            }
            if (isCookableFoodItem(itemStack2)) {
                applyCookingLore(itemStack2, serverPlayer);
                applyCookingScaling(itemStack2, serverPlayer);
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
        if (slotIndex == 2 && player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if itemStack is empty or represents air
            if (itemStack.isEmpty() || Registries.ITEM.getId(itemStack.getItem()).toString().equals("minecraft:air")) {
                Simpleskills.LOGGER.debug("Skipping onQuickMoveAfterTransfer for empty or air stack: {}", itemStack);
                return;
            }
            int movedCount = itemStack.getCount() - (itemStack2.isEmpty() ? 0 : itemStack2.getCount());
            if (movedCount > 0) {
                ItemStack movedStack = itemStack.copy();
                movedStack.setCount(movedCount);
                grantCookingXP(serverPlayer, movedStack);
                grantCraftingXP(serverPlayer, movedStack);
            }
        }
    }

    @Unique
    private boolean isCookableFoodItem(ItemStack stack) {
        String itemKey = stack.getItem().getTranslationKey();
        return ConfigManager.getCookingXP(itemKey, Skills.COOKING) > 0 && stack.get(DataComponentTypes.FOOD) != null;
    }

    @Unique
    private void grantCookingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        String itemKey = stack.getItem().getTranslationKey();
        int xpPerItem = ConfigManager.getCookingXP(itemKey, Skills.COOKING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.COOKING, totalXP);

        Simpleskills.LOGGER.debug(
                "Granted {} Cooking XP for {}x {} to player {}",
                totalXP, stack.getCount(), itemKey, player.getName().getString()
        );
    }

    @Unique
    private void grantCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        String itemKey = stack.getItem().getTranslationKey();
        int xpPerItem = ConfigManager.getSmeltingCraftingXP(itemKey, Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

        Simpleskills.LOGGER.debug(
                "Granted {} Crafting XP for {}x {} to player {}",
                totalXP, stack.getCount(), itemKey, player.getName().getString()
        );
    }

    @Unique
    private void applyCookingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        // Get existing lore, if any
        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new cooking lore with colored tier
        Text cookingLore = Text.literal("Cooked by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Cook)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(cookingLore);

        // Set the combined lore back to the stack
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }

    @Unique
    private void applyCookingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        float multiplier = ConfigManager.getCookingMultiplier(level);

        FoodComponent original = stack.get(DataComponentTypes.FOOD);
        if (original == null) return;

        int newHunger = Math.max(1, Math.round(original.nutrition() * multiplier));
        float newSaturation = original.saturation() * multiplier;

        FoodComponent scaledFood = new FoodComponent.Builder()
                .nutrition(newHunger)
                .saturationModifier(newSaturation)
                .build();

        stack.set(DataComponentTypes.FOOD, scaledFood);

        Simpleskills.LOGGER.debug(
                "Scaled food {} -> hunger {} sat {} for player {} (lvl {}, multiplier {})",
                stack.getItem().getTranslationKey(),
                newHunger, newSaturation,
                player.getName().getString(),
                level, multiplier
        );
    }
}