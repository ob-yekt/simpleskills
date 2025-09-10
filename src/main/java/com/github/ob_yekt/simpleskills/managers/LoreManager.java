package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages lore application for items, including requirements for tools, armor, weapons, and prayer sacrifices.
 * Consolidated functionality from LoreApplier into a single class for simplicity.
 * Modified to be server-side only, avoiding custom components and client-side dependencies.
 */
public class LoreManager {
    private static final String LORE_PREFIX = "Requires";
    private static final String TO_USE = " to use";
    private static final String TO_SACRIFICE = " to sacrifice";

    // Record to hold tier name and its color (as integer RGB)
    public record TierInfo(String name, int color) {}

    public static void initialize() {
        // No component registration needed, as we avoid custom components
    }

    /**
     * Applies skill-related lore to the given item stack.
     * Checks for tool, armor, weapon, enchantment, and prayer sacrifice requirements.
     */
    public static void applySkillLore(ItemStack stack) {
        applyLoreToStack(stack);
    }

    public static void applyLoreToStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        List<Text> newLore = new ArrayList<>();

        // Handle item requirements (tools, armor, weapons, sacrifices)
        SkillRequirement requirement = getItemRequirement(itemId);
        if (requirement != null) {
            boolean isSacrifice = ConfigManager.PRAYER_SACRIFICES.containsKey(itemId);
            String action = isSacrifice ? TO_SACRIFICE : TO_USE;
            String skillName = requirement.getSkill().getId().substring(0, 1).toUpperCase() +
                    requirement.getSkill().getId().substring(1).toLowerCase();
            String loreText = String.format("Requires %d %s%s", requirement.getLevel(), skillName, action);

            // Check existing lore to avoid duplicates
            LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
            boolean loreExists = currentLoreComponent.lines().stream()
                    .anyMatch(text -> text.getString().equals(loreText));
            if (loreExists) {
                return;
            }

            newLore.add(Text.literal(loreText).formatted(Formatting.GRAY));
        }

        if (!newLore.isEmpty()) {
            List<Text> currentLore = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of())).lines();
            List<Text> filteredLore = currentLore.stream()
                    .filter(text -> !text.getString().contains(LORE_PREFIX))
                    .toList();
            newLore.addAll(0, filteredLore);

            stack.set(DataComponentTypes.LORE, new LoreComponent(newLore));
            Simpleskills.LOGGER.debug("Applied lore to item: {}", itemId);
        }
    }

    /**
     * Applies lore to all stacks in the given inventory.
     */
    public static void applyLoreToInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            applyLoreToStack(stack);
        }
    }

    /**
     * Helper method to retrieve the appropriate SkillRequirement for an item.
     * Checks tools, armor, weapons, and prayer sacrifices in order.
     */
    private static SkillRequirement getItemRequirement(String itemId) {
        SkillRequirement toolReq = ConfigManager.getToolRequirement(itemId);
        if (toolReq != null) return toolReq;

        SkillRequirement armorReq = ConfigManager.getArmorRequirement(itemId);
        if (armorReq != null) return armorReq;

        SkillRequirement weaponReq = ConfigManager.getWeaponRequirement(itemId);
        if (weaponReq != null) return weaponReq;

        if (ConfigManager.PRAYER_SACRIFICES.containsKey(itemId)) {
            return ConfigManager.PRAYER_SACRIFICES.get(itemId).requirement();
        }

        return null;
    }

    /**
     * Returns the tier name and color based on the skill level.
     * @param level The skill level.
     * @return TierInfo containing the tier name and its RGB color as an integer.
     */
    public static TierInfo getTierName(int level) {
        if (level >= 99) return new TierInfo("Grandmaster", 0xFF8000); // #ff8000 (RGB: 255, 128, 0)
        else if (level >= 75) return new TierInfo("Expert", 0xA335EE); // #a335ee (RGB: 163, 53, 238)
        else if (level >= 50) return new TierInfo("Artisan", 0x0070DD); // #0070dd (RGB: 0, 112, 221)
        else if (level >= 25) return new TierInfo("Journeyman", 0x1EFF00); // #1eff00 (RGB: 30, 255, 0)
        else return new TierInfo("Novice", 0x9D9D9D); // #9d9d9d (RGB: 157, 157, 157)
    }
}