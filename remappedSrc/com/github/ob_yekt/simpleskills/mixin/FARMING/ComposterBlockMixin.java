package com.github.ob_yekt.simpleskills.mixin.FARMING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Mixin to check skill requirements and grant Farming XP when using a compostable item in a composter.
 * XP is based on the item's levelIncreaseChance from ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.
 */
@Mixin(ComposterBlock.class)
public class ComposterBlockMixin {

    @Inject(method = "onUseWithItem", at = @At("HEAD"))
    private void captureUsedItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        lastItemKey = stack.getItem().getTranslationKey();
    }

    @Inject(method = "onUseWithItem", at = @At("RETURN"))
    private void grantFarmingXPOnCompost(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (cir.getReturnValue() != ActionResult.SUCCESS) return;

        // Check if the item is compostable
        if (ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.containsKey(stack.getItem())) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                float levelIncreaseChance = ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(stack.getItem());
                // Scale XP: base XP of 100 multiplied by levelIncreaseChance
                int xp = Math.round(10.0F * levelIncreaseChance);
                XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
                Simpleskills.LOGGER.debug("Granted {} Farming XP to {} for composting {} (levelIncreaseChance: {})",
                        xp, serverPlayer.getName().getString(), lastItemKey, levelIncreaseChance);
            });
        }
    }

    @Unique
    private String lastItemKey;
}