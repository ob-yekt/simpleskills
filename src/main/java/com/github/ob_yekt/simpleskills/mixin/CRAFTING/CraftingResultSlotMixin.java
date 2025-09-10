package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Shadow
    @Final private PlayerEntity player;
    @Shadow @Final private RecipeInputInventory input;

    @Unique
    private final ItemStack[] originalInputs = new ItemStack[9];

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeItemHead(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            // Capture original input stacks
            for (int i = 0; i < 9; i++) {
                originalInputs[i] = input.getStack(i).copy();
            }
            grantCraftingXP(serverPlayer, stack);
            applyCraftingLore(stack, serverPlayer);
            applyCraftingScaling(stack, serverPlayer);
        }
    }

    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void onTakeItemTail(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            int level = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.CRAFTING);
            float recoveryChance = getRecoveryChance(level);
            if (recoveryChance <= 0) return;

            for (int i = 0; i < 9; i++) {
                ItemStack original = originalInputs[i];
                ItemStack current = input.getStack(i);
                if (!original.isEmpty() && current.getCount() < original.getCount()) {
                    if (serverPlayer.getRandom().nextFloat() < recoveryChance) {
                        ItemStack recovered = original.copy();
                        recovered.setCount(1);
                        if (!serverPlayer.getInventory().insertStack(recovered)) {
                            serverPlayer.dropItem(recovered, false);
                        }
                        Simpleskills.LOGGER.debug(
                                "Recovered {} for player {} (lvl {}, chance {})",
                                recovered.getItem().getTranslationKey(),
                                serverPlayer.getName().getString(),
                                level,
                                recoveryChance
                        );
                    }
                }
            }
        }
    }

    @Unique
    private void grantCraftingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        String itemKey = Registries.ITEM.getId(stack.getItem()).toString();
        int xpPerItem = ConfigManager.getCraftingXP(itemKey, Skills.CRAFTING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.CRAFTING, totalXP);

        Simpleskills.LOGGER.debug(
                "Granted {} Crafting XP for {}x {} to player {}",
                totalXP, stack.getCount(), itemKey, player.getName().getString()
        );
    }

    @Unique
    private void applyCraftingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        // Get existing lore, if any
        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new crafting lore with colored tier
        Text craftingLore = Text.literal("Crafted by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Crafter)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(craftingLore);

        // Set the combined lore back to the stack
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }

    @Unique
    private void applyCraftingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.MAX_DAMAGE) == null) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float multiplier = getDurabilityMultiplier(level);

        Integer original = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (original == null) return;

        int newMax = Math.max(1, Math.round(original * multiplier));
        stack.set(DataComponentTypes.MAX_DAMAGE, newMax);

        Simpleskills.LOGGER.debug(
                "Scaled durability for {} from {} -> {} for player {} (lvl {}, multiplier {})",
                Registries.ITEM.getId(stack.getItem()).toString(),
                original, newMax,
                player.getName().getString(),
                level, multiplier
        );
    }

    @Unique
    private float getDurabilityMultiplier(int level) {
        if (level >= 99) return 1.20f;
        else if (level >= 75) return 1.15f;
        else if (level >= 50) return 1.10f;
        else if (level >= 25) return 1.05f;
        else return 1.0f;
    }

    @Unique
    private float getRecoveryChance(int level) {
        if (level >= 99) return 0.25f;
        else if (level >= 75) return 0.15f;
        else if (level >= 50) return 0.10f;
        else if (level >= 25) return 0.05f;
        else return 0.0f;
    }
}