package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.component.DataComponentTypes;
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
import net.minecraft.component.type.LoreComponent;
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

        // Check if this is a netherite tool or armor upgrade
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

        Simpleskills.LOGGER.debug("SmithingScreenHandlerMixin: onTakeOutput triggered for player {}, output: {}",
                serverPlayer.getName().getString(), stack.getItem());

        // Grant 6000 XP for completing smithing action
        XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, 6000);

        // Apply lore to the output stack
        applySmithingLore(stack, serverPlayer);

        // Double-check durability for netherite tool or armor upgrades
        if (isNetheriteToolUpgrade(stack)) {
            ItemStack newStack = applySmithingDurabilityScaling(stack, serverPlayer);
            SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
            handler.getSlot(3).setStack(newStack);
        } else {
            Simpleskills.LOGGER.debug("onTakeOutput: Not a netherite tool or armor upgrade for output: {}", stack.getItem());
        }
    }

    @Unique
    private boolean isNetheriteToolUpgrade(ItemStack outputStack) {
        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        Slot inputSlot0 = handler.getSlot(1); // Equipment (base item, e.g., diamond tool or armor)
        Slot inputSlot1 = handler.getSlot(2); // Material (e.g., netherite ingot)
        Item outputItem = outputStack.getItem();
        Item inputItem0 = inputSlot0.getStack().getItem();
        Item inputItem1 = inputSlot1.getStack().getItem();

        boolean isUpgrade = inputItem1 == Items.NETHERITE_INGOT;

        Simpleskills.LOGGER.debug("isNetheriteToolUpgrade: output={}, input0={}, input1={}, result={}",
                outputItem, inputItem0, inputItem1, isUpgrade);

        return isUpgrade;
    }

    @Unique
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: Empty stack or no MAX_DAMAGE for {}", stack.getItem());
            return stack;
        }

        // Get the vanilla durability for the output Netherite item and input diamond item
        int vanillaNetheriteDurability = getVanillaDurability(stack.getItem());
        if (vanillaNetheriteDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid vanilla durability for {}", stack.getItem());
            return stack;
        }

        // Get the input item's current max durability
        Integer inputDurability = getInputDurability();
        if (inputDurability == null || inputDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid input durability for input item");
            return stack;
        }

        // Get the vanilla durability of the corresponding diamond item
        Item diamondEquivalent = getDiamondEquivalent(stack.getItem());
        int vanillaDiamondDurability = diamondEquivalent != null ? getVanillaDurability(diamondEquivalent) : 0;
        if (vanillaDiamondDurability == 0) {
            Simpleskills.LOGGER.debug("applySmithingDurabilityScaling: No valid vanilla diamond durability for {}", diamondEquivalent);
            return stack;
        }

        // Calculate the crafting bonus (difference between input durability and vanilla diamond durability)
        int craftingBonus = inputDurability - vanillaDiamondDurability;

        // Get the smithing level and multiplier
        int smithingLevel = XPManager.getSkillLevel(player.getUuidAsString(), Skills.SMITHING);
        float smithingMultiplier = ConfigManager.getSmithingMultiplier(smithingLevel);

        // Calculate the new durability: (vanilla Netherite durability + crafting bonus) * smithing multiplier
        int newMax = Math.max(1, Math.round((vanillaNetheriteDurability + craftingBonus) * smithingMultiplier));

        // Log the input and output durability for debugging
        Integer currentOutputDurability = stack.getOrDefault(DataComponentTypes.MAX_DAMAGE, null);

        Simpleskills.LOGGER.debug(
                "applySmithingDurabilityScaling: Input durability={}, Vanilla diamond durability={}, Crafting bonus={}, Vanilla Netherite durability={}, Final durability={} for {} (player={}, smithing lvl={}, smithing multiplier={})",
                inputDurability,
                vanillaDiamondDurability,
                craftingBonus,
                vanillaNetheriteDurability,
                newMax,
                Registries.ITEM.getId(stack.getItem()).toString(),
                player.getName().getString(),
                smithingLevel,
                smithingMultiplier
        );

        // Create a new ItemStack to avoid immutability issues
        ItemStack newStack = stack.copy();
        newStack.set(DataComponentTypes.MAX_DAMAGE, newMax);
        return newStack;
    }

    @Unique
    private Integer getInputDurability() {
        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        ItemStack inputStack = handler.getSlot(1).getStack(); // Equipment slot (index 1)
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

        // Get existing lore, if any
        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new smithing lore with colored tier
        Text smithingLore = Text.literal("Upgraded by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Smith)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(smithingLore);

        // Set the combined lore back to the stack
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }
}