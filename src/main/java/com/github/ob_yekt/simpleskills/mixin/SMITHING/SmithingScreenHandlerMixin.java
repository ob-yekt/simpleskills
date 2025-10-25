package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(SmithingScreenHandler.class)
public abstract class SmithingScreenHandlerMixin extends ForgingScreenHandler {

    protected SmithingScreenHandlerMixin(ScreenHandlerType<?> type, int syncId, PlayerEntity player) {
        super(type, syncId, player.getInventory(), null, null);
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void onUpdateResult(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) return;

        Simpleskills.LOGGER.debug("SmithingScreenHandlerMixin: updateResult triggered for player {}", serverPlayer.getName().getString());

        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        ItemStack outputStack = handler.getSlot(3).getStack(); // Output slot (index 3)

        if (isNetheriteToolUpgrade(outputStack)) {
            ItemStack newStack = applySmithingDurabilityScaling(outputStack, serverPlayer);
            handler.getSlot(3).setStack(newStack);
        } else {
            Simpleskills.LOGGER.debug("updateResult: Not a netherite tool or armor upgrade for output: {}", outputStack.getItem());
        }
    }

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void onTakeOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        Simpleskills.LOGGER.debug(
                "SmithingScreenHandlerMixin: onTakeOutput triggered for player {}, output: {}",
                serverPlayer.getName().getString(), stack.getItem()
        );

        processSmithingOperation(serverPlayer, stack);
    }

    @Unique
    private void processSmithingOperation(ServerPlayerEntity serverPlayer, ItemStack stack) {
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
        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        handler.getSlot(3).setStack(newStack);

        if (stack != newStack) {
            stack.set(DataComponentTypes.MAX_DAMAGE, newStack.getOrDefault(DataComponentTypes.MAX_DAMAGE, null));
            stack.set(DataComponentTypes.LORE, newStack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of())));
        }
    }

    @Unique
    private boolean isNetheriteToolUpgrade(ItemStack outputStack) {
        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        Slot templateSlot = handler.getSlot(0); // Template slot (index 0)
        Slot materialSlot = handler.getSlot(2); // Material slot (index 2)

        Item templateItem = templateSlot.getStack().getItem();
        Item materialItem = materialSlot.getStack().getItem();

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

        LoreComponent loreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> loreLines = loreComponent.lines();

        // Check if any lore line contains "Upgraded by" to detect existing smithing lore
        for (Text line : loreLines) {
            String loreText = line.getString();
            if (loreText.contains("Upgraded by") && loreText.contains("Smith)")) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) {
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
        int smithingLevel = XPManager.getSkillLevel(player.getUuidAsString(), Skills.SMITHING);
        float smithingMultiplier = ConfigManager.getSmithingMultiplier(smithingLevel);

        int newMax = Math.max(1, Math.round((vanillaNetheriteDurability + craftingBonus) * smithingMultiplier));

        Simpleskills.LOGGER.debug(
                "applySmithingDurabilityScaling: Input durability={}, Vanilla diamond durability={}, Crafting bonus={}, Vanilla Netherite durability={}, Final durability={} for {} (player={}, smithing lvl={}, smithing multiplier={})",
                inputDurability, vanillaDiamondDurability, craftingBonus, vanillaNetheriteDurability, newMax,
                Registries.ITEM.getId(stack.getItem()).toString(), player.getName().getString(), smithingLevel, smithingMultiplier
        );

        ItemStack newStack = stack.copy();
        newStack.set(DataComponentTypes.MAX_DAMAGE, newMax);
        return newStack;
    }

    @Unique
    private Integer getInputDurability() {
        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        ItemStack inputStack = handler.getSlot(1).getStack();
        return inputStack.getOrDefault(DataComponentTypes.MAX_DAMAGE, null);
    }

    @Unique
    private int getVanillaDurability(Item item) {
        ItemStack tempStack = new ItemStack(item);
        Integer durability = tempStack.getOrDefault(DataComponentTypes.MAX_DAMAGE, null);
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
    private void applySmithingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.SMITHING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        Text smithingLore = Text.literal("Upgraded by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Smith)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        currentLore.addFirst(smithingLore);
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }
}