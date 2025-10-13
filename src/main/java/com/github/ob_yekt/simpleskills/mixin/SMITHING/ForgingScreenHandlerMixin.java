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
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
                // Anvil operations are now fully handled in AnvilScreenHandlerMixin's onTakeOutput,
                // which is implicitly called by quickMove, so no extra logic is needed here.
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
    private boolean isNetheriteToolUpgrade(SmithingScreenHandler handler, ItemStack outputStack) {
        Slot inputSlot1 = handler.getSlot(2); // Material (e.g., netherite ingot)
        return inputSlot1.getStack().isOf(Items.NETHERITE_INGOT);
    }

    @Unique
    private ItemStack applySmithingDurabilityScaling(ItemStack stack, ServerPlayerEntity player, SmithingScreenHandler handler) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) {
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
        if (stack.isEmpty()) return;
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