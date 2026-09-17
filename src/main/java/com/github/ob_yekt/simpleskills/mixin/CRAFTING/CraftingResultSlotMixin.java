package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.utils.CraftingCommon;
import net.minecraft.util.Prediction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Shadow @Final private Player player;
    @Shadow @Final private CraftingContainer craftSlots;

    @Unique private final ItemStack[] originalInputs = new ItemStack[9];

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeItemHead(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || !CraftingCommon.isValidStack(stack)) {
            return;
        }

        // Cache original inputs for material recovery
        for (int i = 0; i < 9; i++) {
            originalInputs[i] = craftSlots.getItem(i).copy();
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

    @Inject(method = "onTake", at = @At("TAIL"))
    private void onTakeItemTail(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && CraftingCommon.isValidStack(stack)) {
            applyMaterialRecovery(serverPlayer, stack);
        }
    }

    @Unique
    private void applyMaterialRecovery(ServerPlayer player, ItemStack outputStack) {
        String itemId = BuiltInRegistries.ITEM.getKey(outputStack.getItem()).toString();

        if (ConfigManager.isRecipeBlacklisted(itemId)) {
            return;
        }

        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.CRAFTING);
        float recoveryChance = ConfigManager.getCraftingRecoveryChance(level);
        if (recoveryChance <= 0) return;

        for (int i = 0; i < 9; i++) {
            ItemStack original = originalInputs[i];
            ItemStack current = craftSlots.getItem(i);

            if (!original.isEmpty() && current.getCount() < original.getCount()
                    && !isUnsafeForRecovery(original)
                    && player.getRandom().nextFloat() < recoveryChance) {

                ItemStack recovered = original.copy();
                recovered.setCount(1);

                if (!player.getInventory().add(recovered)) {
                    player.drop(recovered, false, Prediction.SERVER_ONLY);
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

    /**
     * Returns true if this stack carries nested item/container data that would be
     * unsafe to "recover" via copy() — e.g. a loaded Shulker Box, a Bundle, a written
     * book, or a charged crossbow. Recovering these would duplicate their entire
     * stored contents rather than just refunding a plain material, so they must never
     * be eligible for the crafting material-recovery perk regardless of blacklist config.
     */
    @Unique
    private static boolean isUnsafeForRecovery(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.BLOCK_ENTITY_DATA)
                || stack.has(DataComponents.CHARGED_PROJECTILES)
                || stack.has(DataComponents.WRITTEN_BOOK_CONTENT)
                || stack.has(DataComponents.WRITABLE_BOOK_CONTENT)
                || stack.has(DataComponents.CONTAINER_LOOT);
    }
}