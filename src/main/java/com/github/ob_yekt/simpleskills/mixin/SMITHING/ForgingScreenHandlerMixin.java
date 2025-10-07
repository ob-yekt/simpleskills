package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.utils.AnvilScreenHandlerAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ForgingScreenHandler.class)
public abstract class ForgingScreenHandlerMixin {

    @Shadow protected Inventory input;

    @Inject(
            method = "quickMove",
            at = @At("HEAD")
    )
    private void handleQuickMoveOperations(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        ForgingScreenHandler handler = (ForgingScreenHandler) (Object) this;

        // Check if this is the result slot being shift-clicked
        if (slotIndex == handler.getResultSlotIndex()) {
            Slot resultSlot = handler.getSlot(slotIndex);
            ItemStack resultStack = resultSlot.getStack();

            if (!resultStack.isEmpty()) {
                // Handle smithing table operations
                if (handler instanceof SmithingScreenHandler) {
                    handleSmithingQuickMove(serverPlayer, (SmithingScreenHandler) handler, resultStack);
                }
                // Handle anvil operations
                else if (handler instanceof AnvilScreenHandler) {
                    handleAnvilQuickMove(serverPlayer, (AnvilScreenHandler) handler, resultStack);
                }
            }
        }
    }

    @Unique
    private void handleSmithingQuickMove(ServerPlayerEntity serverPlayer, SmithingScreenHandler handler, ItemStack resultStack) {
        // Check if this is a netherite tool upgrade
        if (isNetheriteToolUpgrade(handler, resultStack)) {
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
                resultStack.set(DataComponentTypes.MAX_DAMAGE, scaledStack.getOrDefault(DataComponentTypes.MAX_DAMAGE, null));
                resultStack.set(DataComponentTypes.LORE, scaledStack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of())));
            }
        }
    }

    @Unique
    private void handleAnvilQuickMove(ServerPlayerEntity serverPlayer, AnvilScreenHandler anvilHandler, ItemStack resultStack) {
        Simpleskills.LOGGER.debug("ForgingScreenHandlerMixin: Handling anvil quick move for player {}, output: {}",
                serverPlayer.getName().getString(), resultStack.getItem());

        // Skip if result is empty or air
        if (resultStack.isEmpty() || Registries.ITEM.getId(resultStack.getItem()).toString().equals("minecraft:air")) {
            Simpleskills.LOGGER.debug("Skipping handleAnvilQuickMove for empty or air output: {}", resultStack);
            return;
        }

        grantXPForAnvilAction(serverPlayer, anvilHandler, resultStack);
    }

    @Unique
    private void grantXPForAnvilAction(ServerPlayerEntity serverPlayer, AnvilScreenHandler anvilHandler, ItemStack resultStack) {
        ItemStack input1 = anvilHandler.getSlot(0).getStack();
        ItemStack input2 = anvilHandler.getSlot(1).getStack();

        // Skip if inputs are empty or air
        if (input1.isEmpty() || input2.isEmpty() ||
                Registries.ITEM.getId(input1.getItem()).toString().equals("minecraft:air") ||
                Registries.ITEM.getId(input2.getItem()).toString().equals("minecraft:air")) {
            Simpleskills.LOGGER.debug("Skipping grantXPForAnvilAction for empty or air inputs: input1={}, input2={}", input1, input2);
            return;
        }

        // Get durability repaired from the anvil handler using accessor
        int durabilityRepaired = ((AnvilScreenHandlerAccessor) anvilHandler).simpleskills$getDurabilityRepaired();
        int repairItemUsage = ((AnvilScreenHandlerAccessor) anvilHandler).simpleskills$getRepairItemUsage();
        int levelCost = ((AnvilScreenHandlerAccessor) anvilHandler).simpleskills$getLevelCost();

        // Detect material repair
        boolean isMaterialRepair = input1.getItem() == resultStack.getItem() &&
                input1.getDamage() > resultStack.getDamage() &&
                repairItemUsage > 0;

        // Detect enchantment combining
        boolean isEnchantCombining = input2.contains(DataComponentTypes.STORED_ENCHANTMENTS) &&
                !resultStack.getEnchantments().getEnchantments().isEmpty();

        if (isMaterialRepair) {
            Identifier materialId = serverPlayer.getEntityWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.ITEM)
                    .getId(input2.getItem());
            if (materialId == null) {
                Simpleskills.LOGGER.warn("Invalid material item {} in slot 1 for player {}", input2.getItem(), serverPlayer.getName().getString());
                return;
            }
            String action = "repair:" + materialId.toString();
            if (!ConfigManager.getSmithingXPMap().containsKey(action)) {
                Simpleskills.LOGGER.debug("No XP multiplier defined for {} in smithing_xp.json, skipping XP for player {}", action, serverPlayer.getName().getString());
                return;
            }
            float xpMultiplier = ConfigManager.getSmithingXP(action, Skills.SMITHING);
            int smithingXP = Math.round(durabilityRepaired * xpMultiplier);
            if (smithingXP > 0) {
                XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, smithingXP);
                Simpleskills.LOGGER.debug(
                        "Granted {} Smithing XP for material repair with {} (durability {}, multiplier {}) by player {}",
                        smithingXP, materialId, durabilityRepaired, xpMultiplier,
                        serverPlayer.getName().getString()
                );
            }
        } else if (isEnchantCombining) {
            // Grant enchanting XP based on vanilla level cost
            if (levelCost > 1) {
                XPManager.addXPWithNotification(serverPlayer, Skills.ENCHANTING, levelCost * 100);
                Simpleskills.LOGGER.debug(
                        "Granted {} Enchanting XP for combining enchantments by player {} (level {})",
                        levelCost * 100, serverPlayer.getName().getString(),
                        XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.ENCHANTING)
                );
            }
        }
    }

    @Unique
    private boolean isNetheriteToolUpgrade(SmithingScreenHandler handler, ItemStack outputStack) {
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
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayerEntity player, SmithingScreenHandler handler) {
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
        Integer inputDurability = getInputDurability(handler);
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
    private Integer getInputDurability(SmithingScreenHandler handler) {
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