package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

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
            // Skip if stack is empty or represents air
            if (itemStack2.isEmpty() || Registries.ITEM.getId(itemStack2.getItem()).toString().equals("minecraft:air")) {
                //Simpleskills.LOGGER.debug("Skipping onQuickMoveCraft for empty or air stack: {}", itemStack2);
                return;
            }
            // Capture original input stacks
            for (int i = 0; i < 9; i++) {
                originalInputs[i] = craftingInventory.getStack(i).copy();
            }
            // Apply lore and scaling based on item type
            if (itemStack2.get(DataComponentTypes.MAX_DAMAGE) != null) {
                applyCraftingLore(itemStack2, serverPlayer);
                applyCraftingScaling(itemStack2, serverPlayer);
            } else if (isCookableFoodItem(itemStack2)) {
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
        if (slotIndex == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if itemStack is empty or represents air
            if (itemStack.isEmpty() || Registries.ITEM.getId(itemStack.getItem()).toString().equals("minecraft:air")) {
                //Simpleskills.LOGGER.debug("Skipping onQuickMoveAfterTransfer for empty or air stack: {}", itemStack);
                return;
            }
            int movedCount = itemStack.getCount() - (itemStack2.isEmpty() ? 0 : itemStack2.getCount());
            if (movedCount > 0) {
                ItemStack movedStack = itemStack.copy();
                movedStack.setCount(movedCount);
                grantCraftingXP(serverPlayer, movedStack);
                if (isCookableFoodItem(movedStack)) {
                    grantCookingXP(serverPlayer, movedStack);
                }
                applyMaterialRecovery(serverPlayer, itemStack);
            }
        }
    }

    @Unique
    private boolean isCookableFoodItem(ItemStack stack) {
        String itemKey = stack.getItem().getTranslationKey();
        return ConfigManager.getCookingXP(itemKey, Skills.COOKING) > 0;
    }

    @Unique
    private void grantCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        String itemKey = Registries.ITEM.getId(stack.getItem()).toString();
        int xpPerItem = ConfigManager.getCraftingXP(itemKey, Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

        //Simpleskills.LOGGER.debug("Granted {} Crafting XP for {}x {} to player {}", totalXP, stack.getCount(), itemKey, player.getName().getString());
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
    private void applyCookingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        Text cookingLore = Text.literal("Cooked by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Cook)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        currentLore.addFirst(cookingLore);
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

    @Unique
    private void applyMaterialRecovery(ServerPlayerEntity player, ItemStack outputStack) {
        String itemId = Registries.ITEM.getId(outputStack.getItem()).toString();
        //Simpleskills.LOGGER.info("=== MATERIAL RECOVERY DEBUG (Shift-Click) ===");
        //Simpleskills.LOGGER.info("Player: {}", player.getName().getString());
        //Simpleskills.LOGGER.info("Output item: {}", itemId);
        //Simpleskills.LOGGER.info("Is blacklisted: {}", ConfigManager.isRecipeBlacklisted(itemId));

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            //Simpleskills.LOGGER.info("Skipping material recovery for blacklisted item: {}", itemId);
            return;
        }

        //Simpleskills.LOGGER.info("Proceeding with recovery check for: {}", itemId);

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float recoveryChance = getRecoveryChance(level);
        //Simpleskills.LOGGER.info("Recovery chance: {} for level {}", recoveryChance, level);
        if (recoveryChance <= 0) {
            //Simpleskills.LOGGER.info("No recovery possible (chance <= 0)");
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = craftingInventory.getStack(i);
            //Simpleskills.LOGGER.info("Slot {}: Original count: {}, Current count: {}", i, original.getCount(), current.getCount());
            if (!original.isEmpty() && current.getCount() < original.getCount()) {
                if (player.getRandom().nextFloat() < recoveryChance) {
                    ItemStack recovered = original.copy();
                    recovered.setCount(1);
                    if (!player.getInventory().insertStack(recovered)) {
                        player.dropItem(recovered, false);
                    }
                    //Simpleskills.LOGGER.info("Recovered {} for player {} (lvl {}, chance {})", recovered.getItem().getTranslationKey(), player.getName().getString(), level recoveryChance);
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