package com.github.ob_yekt.simpleskills.mixin.COOKING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(FurnaceOutputSlot.class)
public abstract class CookingXPMixin {
    @Shadow @Final private PlayerEntity player;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            // Skip if stack is empty or represents air
            if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) {
                Simpleskills.LOGGER.debug("Skipping onTakeItem for empty or air stack: {}", stack);
                return;
            }
            grantCookingXP(serverPlayer, stack);
            applyCookingLore(stack, serverPlayer);
            applyCookingScaling(stack, serverPlayer);
        }
    }

    @Unique
    private void grantCookingXP(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        String itemKey = stack.getItem().getTranslationKey();
        int xpPerItem = ConfigManager.getCookingXP(itemKey, Skills.COOKING);
        if (xpPerItem <= 0) return;

        int totalXP = xpPerItem * stack.getCount();
        XPManager.addXPWithNotification(player, Skills.COOKING, totalXP);

        Simpleskills.LOGGER.debug(
                "Granted {} Cooking XP for {}x {} to player {}",
                totalXP, stack.getCount(), itemKey, player.getName().getString()
        );
    }

    @Unique
    private void applyCookingLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        // Get existing lore, if any
        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new cooking lore with colored tier
        Text cookingLore = Text.literal("Cooked by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Cook)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(cookingLore);

        // Set the combined lore back to the stack
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));
    }

    @Unique
    private void applyCookingScaling(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.COOKING);
        float multiplier = getMultiplier(level);

        FoodComponent original = stack.get(DataComponentTypes.FOOD);
        if (original == null) return;

        int newHunger = Math.max(1, Math.round(original.nutrition() * multiplier));
        float newSaturation = original.saturation() * multiplier;

        FoodComponent scaledFood = new FoodComponent.Builder()
                .nutrition(newHunger)
                .saturationModifier(newSaturation)
                .build();

        stack.set(DataComponentTypes.FOOD, scaledFood);

        Simpleskills.LOGGER.debug(
                "Scaled food {} -> hunger {} sat {} for player {} (lvl {}, multiplier {})",
                stack.getItem().getTranslationKey(),
                newHunger, newSaturation,
                player.getName().getString(),
                level, multiplier
        );
    }

    @Unique
    private float getMultiplier(int level) {
        if (level >= 99) return 1.5f;
        else if (level >= 75) return 1.25f;
        else if (level >= 50) return 1.125f;
        else if (level >= 25) return 1.0f;
        else return 0.875f;
    }
}