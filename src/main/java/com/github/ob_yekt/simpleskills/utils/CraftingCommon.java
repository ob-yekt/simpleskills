package com.github.ob_yekt.simpleskills.utils;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class CraftingCommon {
    private static final String AIR_ID = "minecraft:air";

    private CraftingCommon() {} // Utility class

    public static boolean isValidStack(ItemStack stack) {
        return !stack.isEmpty() && !AIR_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean isCraftableItem(ItemStack stack) {
        return stack.get(DataComponents.MAX_DAMAGE) != null;
    }

    public static boolean isCookableFoodItem(ItemStack stack) {
        String itemKey = stack.getItem().getDescriptionId();
        return ConfigManager.getCookingXP(itemKey, Skills.COOKING) > 0;
    }

    public static void grantCraftingXP(ServerPlayer player, ItemStack stack) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        // Prevent durability bonus on "cosmetic re-crafting" (dyeing armor, applying banner to shield, etc.)
        if (stack.has(DataComponents.DYED_COLOR) || stack.has(DataComponents.BANNER_PATTERNS)) {
            return;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int xpPerItem = ConfigManager.getCraftingXP(itemId.toString(), Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);
    }

    public static void grantCookingXP(ServerPlayer player, ItemStack stack) {
        if (!isValidStack(stack)) return;

        String itemKey = stack.getItem().getDescriptionId();
        int xpPerItem = ConfigManager.getCookingXP(itemKey, Skills.COOKING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.COOKING, totalXP);
    }

    public static void applyCraftingLore(ItemStack stack, ServerPlayer player) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        // Check if crafting lore should be displayed in tooltips
        if (!isCraftingLoreInTooltipsEnabled()) return;

        // Prevent durability bonus on "cosmetic re-crafting" (dyeing armor, applying banner to shield, etc.)
        if (stack.has(DataComponents.DYED_COLOR) || stack.has(DataComponents.BANNER_PATTERNS)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.CRAFTING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        applyPlayerLore(stack, player, tierInfo, "Crafter");
    }

    public static void applyCookingLore(ItemStack stack, ServerPlayer player) {
        if (!isValidStack(stack)) return;

        // Check if cooking lore should be displayed in tooltips
        if (!isCraftingLoreInTooltipsEnabled()) return;

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.COOKING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        applyPlayerLore(stack, player, tierInfo, "Cook");
    }

    private static void applyPlayerLore(ItemStack stack, ServerPlayer player,
                                        LoreManager.TierInfo tierInfo, String profession) {
        ItemLore currentLore = stack.getOrDefault(DataComponents.LORE, new ItemLore(List.of()));
        List<Component> loreLines = new ArrayList<>(currentLore.lines());

        Component newLore = Component.literal(String.format("%sed by %s (%s %s)",
                        profession.equals("Cook") ? "Cook" : "Craft",
                        player.getName().getString(),
                        tierInfo.name(),
                        profession))
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        loreLines.addFirst(newLore);
        stack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    public static void applyCraftingScaling(ItemStack stack, ServerPlayer player) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        // Prevent durability bonus on "cosmetic re-crafting" (dyeing armor, applying banner to shield, etc.)
        if (stack.has(DataComponents.DYED_COLOR) || stack.has(DataComponents.BANNER_PATTERNS)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.CRAFTING);
        float multiplier = ConfigManager.getCraftingDurabilityMultiplier(level);

        Integer originalDurability = stack.get(DataComponents.MAX_DAMAGE);
        if (originalDurability == null) return;

        int newDurability = Math.max(1, Math.round(originalDurability * multiplier));
        stack.set(DataComponents.MAX_DAMAGE, newDurability);
    }

    public static void applyCookingScaling(ItemStack stack, ServerPlayer player) {
        if (!isValidStack(stack)) return;

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.COOKING);
        float multiplier = ConfigManager.getCookingMultiplier(level);

        FoodProperties originalFood = stack.get(DataComponents.FOOD);
        if (originalFood == null) return;

        // FoodComponent stores: nutrition (int) and saturation (float)
        // The saturation is the ACTUAL saturation value, not a modifier
        int originalNutrition = originalFood.nutrition();
        float originalSaturation = originalFood.saturation();

        // Scale both values directly
        int newNutrition = Math.max(1, Math.round(originalNutrition * multiplier));
        float newSaturation = originalSaturation * multiplier;

        // Cap saturation at reasonable values (Minecraft's max is 20.0)
        newSaturation = Math.min(20.0f, newSaturation);

        // Create new FoodComponent directly with the scaled values
        // We bypass the Builder since it would apply HungerConstants.calculateSaturation again
        FoodProperties scaledFood = new FoodProperties(
                newNutrition,
                newSaturation,
                originalFood.canAlwaysEat()
        );

        stack.set(DataComponents.FOOD, scaledFood);
    }

    /**
     * Checks if crafting and cooking lore should be displayed in tooltips.
     * This is controlled by a config option.
     * @return True if the crafting/cooking lore should be displayed, false otherwise.
     */
    private static boolean isCraftingLoreInTooltipsEnabled() {
        return ConfigManager.getFeatureConfig().get("crafting_lore_in_tooltips_enabled").getAsBoolean();
    }
}