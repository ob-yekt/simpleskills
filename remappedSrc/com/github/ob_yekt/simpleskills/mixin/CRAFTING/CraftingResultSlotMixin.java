
package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Shadow @Final private PlayerEntity player;
    @Shadow @Final private RecipeInputInventory input;

    @Unique private final ItemStack[] originalInputs = new ItemStack[9];

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeItemHead(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !CraftingCommon.isValidStack(stack)) {
            return;
        }

        // Cache original inputs for material recovery
        for (int i = 0; i < 9; i++) {
            originalInputs[i] = input.getStack(i).copy();
        }

        // Apply bonuses based on item type
        if (CraftingCommon.isCraftableItem(stack)) {
            CraftingCommon.grantCraftingXP(serverPlayer, stack);
            CraftingCommon.applyCraftingLore(stack, serverPlayer);
            CraftingCommon.applyCraftingScaling(stack, serverPlayer);
        } else if (CraftingCommon.isCookableFoodItem(stack)) {
            CraftingCommon.grantCookingXP(serverPlayer, stack);
            CraftingCommon.applyCookingLore(stack, serverPlayer);
            CraftingCommon.applyCookingScaling(stack, serverPlayer);
        }
    }

    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void onTakeItemTail(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && CraftingCommon.isValidStack(stack)) {
            applyMaterialRecovery(serverPlayer, stack);
        }
    }

    @Unique
    private void applyMaterialRecovery(ServerPlayerEntity player, ItemStack outputStack) {
        String itemId = Registries.ITEM.getId(outputStack.getItem()).toString();

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.CRAFTING);
        float recoveryChance = ConfigManager.getCraftingRecoveryChance(level);
        if (recoveryChance <= 0) return;

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = input.getStack(i);

            if (!original.isEmpty() && current.getCount() < original.getCount()
                    && player.getRandom().nextFloat() < recoveryChance) {

                ItemStack recovered = original.copy();
                recovered.setCount(1);

                if (!player.getInventory().insertStack(recovered)) {
                    player.dropItem(recovered, false);
                }

//                Simpleskills.LOGGER.debug(
//                        "Recovered {} for player {} (lvl {}, chance {})",
//                        recovered.getItem().getTranslationKey(),
//                        player.getName().getString(),
//                        level, recoveryChance
//                );
            }
        }
    }
}