package com.github.ob_yekt.simpleskills.utils;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class CraftingCommon {
    private static final String AIR_ID = "minecraft:air";

    private CraftingCommon() {} // Utility class

    public static boolean isValidStack(ItemStack stack) {
        return !stack.isEmpty() && !AIR_ID.equals(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean isCraftableItem(ItemStack stack) {
        return stack.get(DataComponentTypes.MAX_DAMAGE) != null;
    }

    public static boolean isCookableFoodItem(ItemStack stack) {
        String itemKey = stack.getItem().getTranslationKey();
        return ConfigManager.getCookingXP(itemKey, Skills.COOKING) > 0;
    }

    public static void grantCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        int xpPerItem = ConfigManager.getCraftingXP(itemId.toString(), Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

//        Simpleskills.LOGGER.debug(
//                "Granted {} Crafting XP for {}x {} to player {}",
//                totalXP, stack.getCount(), itemId, player.getName().getString()
//        );
    }

    public static void grantCookingXP(ServerPlayerEntity player, ItemStack stack) {
        if (!isValidStack(stack)) return;

        String itemKey = stack.getItem().getTranslationKey();
        int xpPerItem = ConfigManager.getCookingXP(itemKey, Skills.COOKING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.COOKING, totalXP);

//        Simpleskills.LOGGER.debug(
//                "Granted {} Cooking XP for {}x {} to player {}",
//                totalXP, stack.getCount(), itemKey, player.getName().getString()
//        );
    }

    public static void applyCraftingLore(ItemStack stack, ServerPlayerEntity player) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        applyPlayerLore(stack, player, tierInfo, "Crafter");
    }

    public static void applyCookingLore(ItemStack stack, ServerPlayerEntity player) {
        if (!isValidStack(stack)) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        applyPlayerLore(stack, player, tierInfo, "Cook");
    }

    private static void applyPlayerLore(ItemStack stack, ServerPlayerEntity player,
                                        LoreManager.TierInfo tierInfo, String profession) {
        LoreComponent currentLore = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> loreLines = new ArrayList<>(currentLore.lines());

        Text newLore = Text.literal(String.format("%sed by %s (%s %s)",
                        profession.equals("Cook") ? "Cook" : "Craft",
                        player.getName().getString(),
                        tierInfo.name(),
                        profession))
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        loreLines.addFirst(newLore);
        stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
    }

    public static void applyCraftingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (!isValidStack(stack) || !isCraftableItem(stack)) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float multiplier = ConfigManager.getCraftingDurabilityMultiplier(level);

        Integer originalDurability = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (originalDurability == null) return;

        int newDurability = Math.max(1, Math.round(originalDurability * multiplier));
        stack.set(DataComponentTypes.MAX_DAMAGE, newDurability);

//        Simpleskills.LOGGER.debug(
//                "Scaled durability for {} from {} -> {} for player {} (lvl {}, multiplier {})",
//                Registries.ITEM.getId(stack.getItem()),
//                originalDurability, newDurability,
//                player.getName().getString(),
//                level, multiplier
//        );
    }

    public static void applyCookingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (!isValidStack(stack)) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        float multiplier = ConfigManager.getCookingMultiplier(level);

        FoodComponent originalFood = stack.get(DataComponentTypes.FOOD);
        if (originalFood == null) return;

        int newHunger = Math.max(1, Math.round(originalFood.nutrition() * multiplier));
        float newSaturation = originalFood.saturation() * multiplier;

        FoodComponent scaledFood = new FoodComponent.Builder()
                .nutrition(newHunger)
                .saturationModifier(newSaturation)
                .build();

        stack.set(DataComponentTypes.FOOD, scaledFood);

//        Simpleskills.LOGGER.debug(
//                "Scaled food {} -> hunger {} sat {} for player {} (lvl {}, multiplier {})",
//                stack.getItem().getTranslationKey(),
//                newHunger, newSaturation,
//                player.getName().getString(),
//                level, multiplier
//        );
    }
}