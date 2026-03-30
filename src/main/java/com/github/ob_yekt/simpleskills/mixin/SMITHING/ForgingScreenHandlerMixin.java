package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

@Mixin(ItemCombinerMenu.class)
public abstract class ForgingScreenHandlerMixin {

    @Shadow protected Container inputSlots;

    @Inject(
            method = "quickMoveStack",
            at = @At("HEAD")
    )
    private void handleQuickMoveOperations(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemCombinerMenu handler = (ItemCombinerMenu) (Object) this;

        // Check if this is the result slot being shift-clicked
        if (slotIndex == handler.getResultSlot()) {
            Slot resultSlot = handler.getSlot(slotIndex);
            ItemStack resultStack = resultSlot.getItem();

            if (!resultStack.isEmpty()) {
                // Handle smithing table operations
                if (handler instanceof SmithingMenu) {
                    handleSmithingQuickMove(serverPlayer, (SmithingMenu) handler, resultStack);
                }
                // Anvil operations are now fully handled in AnvilScreenHandlerMixin's onTakeOutput,
                // which is implicitly called by quickMove, so no extra logic is needed here.
            }
        }
    }

    @Unique
    private void handleSmithingQuickMove(ServerPlayer serverPlayer, SmithingMenu handler, ItemStack resultStack) {
        // Check if this is a netherite tool upgrade
        if (isNetheriteToolUpgrade(handler, resultStack)) {
            // Check if lore already exists to prevent duplication
            if (hasSmithingLore(resultStack)) {
                Simpleskills.LOGGER.debug("ForgingScreenHandlerMixin: Item already has smithing lore, skipping");
                return;
            }

            Simpleskills.LOGGER.debug("ForgingScreenHandlerMixin: Handling smithing quick move for player {}, output: {}",
                    serverPlayer.getName().getString(), resultStack.getItem());

            // Grant 20,000 XP for completing smithing action
            XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, 20000);

            // Apply lore to the result stack
            applySmithingLore(resultStack, serverPlayer);

            // Apply durability scaling
            ItemStack scaledStack = applySmithingDurabilityScaling(resultStack, serverPlayer, handler);
            if (scaledStack != resultStack) {
                // Update the result stack with the scaled version
                resultStack.set(DataComponents.MAX_DAMAGE, scaledStack.getOrDefault(DataComponents.MAX_DAMAGE, null));
                resultStack.set(DataComponents.LORE, scaledStack.getOrDefault(DataComponents.LORE, new ItemLore(List.of())));
            }
        }
    }

    @Unique
    private boolean isNetheriteToolUpgrade(SmithingMenu handler, ItemStack outputStack) {
        Slot templateSlot = handler.getSlot(0); // Template slot (index 0)
        Slot materialSlot = handler.getSlot(2); // Material slot (index 2)

        Item templateItem = templateSlot.getItem().getItem();
        Item materialItem = materialSlot.getItem().getItem();

        // Check if it's specifically a netherite upgrade (not trims or other smithing operations)
        return templateItem == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                && materialItem == Items.NETHERITE_INGOT;
    }

    @Unique
    private boolean hasSmithingLore(ItemStack stack) {
        if (stack.isEmpty()) return false;

        ItemLore loreComponent = stack.getOrDefault(DataComponents.LORE, new ItemLore(List.of()));
        List<Component> loreLines = loreComponent.lines();

        // Check if any lore line contains "Upgraded by" to detect existing smithing lore
        for (Component line : loreLines) {
            String loreText = line.getString();
            if (loreText.contains("Upgraded by") && loreText.contains("Smith)")) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayer player, SmithingMenu handler) {
        if (stack.isEmpty() || stack.get(DataComponents.MAX_DAMAGE) == null) {
            return stack;
        }

        int vanillaNetheriteDurability = getVanillaDurability(stack.getItem());
        if (vanillaNetheriteDurability == 0) return stack;

        Integer inputDurability = getInputDurability(handler);
        if (inputDurability == null || inputDurability == 0) return stack;

        Item diamondEquivalent = getDiamondEquivalent(stack.getItem());
        int vanillaDiamondDurability = diamondEquivalent != null ? getVanillaDurability(diamondEquivalent) : 0;
        if (vanillaDiamondDurability == 0) return stack;

        int craftingBonus = inputDurability - vanillaDiamondDurability;
        int smithingLevel = XPManager.getSkillLevel(player.getStringUUID(), Skills.SMITHING);
        float smithingMultiplier = ConfigManager.getSmithingMultiplier(smithingLevel);
        int newMax = Math.max(1, Math.round((vanillaNetheriteDurability + craftingBonus) * smithingMultiplier));

        Simpleskills.LOGGER.debug(
                "applySmithingDurabilityScaling: Input durability={}, Vanilla diamond durability={}, Crafting bonus={}, Vanilla Netherite durability={}, Final durability={} for {} (player={}, smithing lvl={}, smithing multiplier={})",
                inputDurability, vanillaDiamondDurability, craftingBonus, vanillaNetheriteDurability, newMax,
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), player.getName().getString(), smithingLevel, smithingMultiplier
        );

        ItemStack newStack = stack.copy();
        newStack.set(DataComponents.MAX_DAMAGE, newMax);
        return newStack;
    }

    @Unique
    private Integer getInputDurability(SmithingMenu handler) {
        ItemStack inputStack = handler.getSlot(1).getItem(); // Equipment slot (index 1)
        return inputStack.getOrDefault(DataComponents.MAX_DAMAGE, null);
    }

    @Unique
    private int getVanillaDurability(Item item) {
        ItemStack tempStack = new ItemStack(item);
        Integer durability = tempStack.getOrDefault(DataComponents.MAX_DAMAGE, null);
        return durability != null ? durability : 0;
    }

    @Unique
    private Item getDiamondEquivalent(Item netheriteItem) {
        if (netheriteItem == Items.NETHERITE_PICKAXE) return Items.DIAMOND_PICKAXE;
        if (netheriteItem == Items.NETHERITE_AXE) return Items.DIAMOND_AXE;
        if (netheriteItem == Items.NETHERITE_SHOVEL) return Items.DIAMOND_SHOVEL;
        if (netheriteItem == Items.NETHERITE_HOE) return Items.DIAMOND_HOE;
        if (netheriteItem == Items.NETHERITE_SWORD) return Items.DIAMOND_SWORD;
        if (netheriteItem == Items.NETHERITE_HELMET) return Items.DIAMOND_HELMET;
        if (netheriteItem == Items.NETHERITE_CHESTPLATE) return Items.DIAMOND_CHESTPLATE;
        if (netheriteItem == Items.NETHERITE_LEGGINGS) return Items.DIAMOND_LEGGINGS;
        if (netheriteItem == Items.NETHERITE_BOOTS) return Items.DIAMOND_BOOTS;
        return null;
    }

    @Unique
    private void applySmithingLore(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.SMITHING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        ItemLore currentLoreComponent = stack.getOrDefault(DataComponents.LORE, new ItemLore(List.of()));
        List<Component> currentLore = new ArrayList<>(currentLoreComponent.lines());

        Component smithingLore = Component.literal("Upgraded by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Smith)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        currentLore.addFirst(smithingLore);
        stack.set(DataComponents.LORE, new ItemLore(currentLore));
    }
}