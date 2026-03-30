package com.github.ob_yekt.simpleskills.mixin.FARMING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Mixin to check skill requirements and grant Farming XP when using a compostable item in a composter.
 * XP is based on the item's levelIncreaseChance from ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.
 */
@Mixin(ComposterBlock.class)
public class ComposterBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void captureUsedItem(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        lastItemKey = stack.getItem().getDescriptionId();
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void grantFarmingXPOnCompost(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
        if (cir.getReturnValue() != InteractionResult.SUCCESS) return;

        // Check if the item is compostable
        if (ComposterBlock.COMPOSTABLES.containsKey(stack.getItem())) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                float levelIncreaseChance = ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
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