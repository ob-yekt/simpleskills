
package com.github.ob_yekt.simpleskills.utils;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

public final class FurnaceCommon {
    private static final String AIR_ID = "minecraft:air";

    private FurnaceCommon() {} // Utility class

    public static boolean isValidStack(ItemStack stack) {
        return !stack.isEmpty() && !AIR_ID.equals(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean isCookableFoodItem(ItemStack stack) {
        String itemKey = stack.getItem().getTranslationKey();
        return ConfigManager.getCookingXP(itemKey, Skills.COOKING) > 0
                && stack.get(DataComponentTypes.FOOD) != null;
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

    public static void grantSmeltingCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (!isValidStack(stack)) return;

        String itemKey = stack.getItem().getTranslationKey();
        int xpPerItem = ConfigManager.getSmeltingCraftingXP(itemKey, Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

//        Simpleskills.LOGGER.debug(
//                "Granted {} Crafting XP for {}x {} to player {}",
//                totalXP, stack.getCount(), itemKey, player.getName().getString()
//        );
    }
}