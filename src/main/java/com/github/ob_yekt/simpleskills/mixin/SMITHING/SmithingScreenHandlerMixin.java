package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

@Mixin(SmithingMenu.class)
public abstract class SmithingScreenHandlerMixin extends ItemCombinerMenu {

    protected SmithingScreenHandlerMixin(MenuType<?> type, int syncId, Player player) {
        super(type, syncId, player.getInventory(), null, null);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onUpdateResult(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;

        Simpleskills.LOGGER.debug("SmithingScreenHandlerMixin: updateResult triggered for player {}", serverPlayer.getName().getString());

        SmithingMenu handler = (SmithingMenu) (Object) this;
        ItemStack outputStack = handler.getSlot(3).getItem(); // Output slot (index 3)

        if (isNetheriteToolUpgrade(outputStack)) {
            ItemStack newStack = applySmithingDurabilityScaling(outputStack, serverPlayer);
            handler.getSlot(3).setByPlayer(newStack);
        } else {
            Simpleskills.LOGGER.debug("updateResult: Not a netherite tool or armor upgrade for output: {}", outputStack.getItem());
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeOutput(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Simpleskills.LOGGER.debug(
                "SmithingScreenHandlerMixin: onTakeOutput triggered for player {}, output: {}",
                serverPlayer.getName().getString(), stack.getItem()
        );

        processSmithingOperation(serverPlayer, stack);
    }

    @Unique
    private void processSmithingOperation(ServerPlayer serverPlayer, ItemStack stack) {
        if (!isNetheriteToolUpgrade(stack)) {
            Simpleskills.LOGGER.debug("processSmithingOperation: Not a netherite upgrade, skipping");
            return;
        }

        // Check if lore already exists to prevent duplication
        if (hasSmithingLore(stack)) {
            Simpleskills.LOGGER.debug("processSmithingOperation: Item already has smithing lore, skipping");
            return;
        }

        XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, 20000);
        applySmithingLore(stack, serverPlayer);

        ItemStack newStack = applySmithingDurabilityScaling(stack, serverPlayer);
        SmithingMenu handler = (SmithingMenu) (Object) this;
        handler.getSlot(3).setByPlayer(newStack);

        if (stack != newStack) {
            stack.set(DataComponents.MAX_DAMAGE, newStack.getOrDefault(DataComponents.MAX_DAMAGE, null));
            stack.set(DataComponents.LORE, newStack.getOrDefault(DataComponents.LORE, new ItemLore(List.of())));
        }
    }

    @Unique
    private boolean isNetheriteToolUpgrade(ItemStack outputStack) {
        SmithingMenu handler = (SmithingMenu) (Object) this;
        Slot templateSlot = handler.getSlot(0); // Template slot (index 0)
        Slot materialSlot = handler.getSlot(2); // Material slot (index 2)

        Item templateItem = templateSlot.getItem().getItem();
        Item materialItem = materialSlot.getItem().getItem();

        // Check if it's specifically a netherite upgrade (not trims or other smithing operations)
        boolean isUpgrade = templateItem == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                && materialItem == Items.NETHERITE_INGOT;

        Simpleskills.LOGGER.debug(
                "isNetheriteToolUpgrade: output={}, template={}, material={}, result={}",
                outputStack.getItem(), templateItem, materialItem, isUpgrade
        );

        return isUpgrade;
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
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty() || stack.get(DataComponents.MAX_DAMAGE) == null) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: Empty stack or no MAX_DAMAGE for {}", stack.getItem());
            return stack;
        }

        int vanillaNetheriteDurability = getVanillaDurability(stack.getItem());
        if (vanillaNetheriteDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid vanilla durability for {}", stack.getItem());
            return stack;
        }

        Integer inputDurability = getInputDurability();
        if (inputDurability == null || inputDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid input durability for input item");
            return stack;
        }

        Item diamondEquivalent = getDiamondEquivalent(stack.getItem());
        int vanillaDiamondDurability = diamondEquivalent != null ? getVanillaDurability(diamondEquivalent) : 0;
        if (vanillaDiamondDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid vanilla diamond durability for {}", diamondEquivalent);
            return stack;
        }

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
    private Integer getInputDurability() {
        SmithingMenu handler = (SmithingMenu) (Object) this;
        ItemStack inputStack = handler.getSlot(1).getItem();
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
        if (stack.isEmpty() || stack.get(DataComponents.MAX_DAMAGE) == null) return;

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