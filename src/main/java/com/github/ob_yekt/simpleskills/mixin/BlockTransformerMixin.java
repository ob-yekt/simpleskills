package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Mixin to check skill requirements and grant XP when using an axe (strip logs),
 * hoe (till blocks) or shovel (create paths). Since 26.3 these actions are driven by
 * the block_transformer item component instead of AxeItem/HoeItem/ShovelItem.
 */
@Mixin(BlockTransformer.class)
public class BlockTransformerMixin {

    @Unique
    private static String lastBlockKey;

    @Inject(method = "transformBlock", at = @At("HEAD"), cancellable = true)
    private void checkToolAndSkillRequirement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        Skills skill = getSkillForTransformer(context);
        if (skill == null) {
            return;
        }

        lastBlockKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();

        String toolName = context.getItemInHand().getItem().toString();
        SkillRequirement requirement = ConfigManager.getToolRequirement(toolName);
        if (requirement != null && requirement.getSkill() == skill) {
            int playerLevel = XPManager.getSkillLevel(player.getStringUUID(), skill);
            if (playerLevel < requirement.getLevel()) {
                player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this tool!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
                Simpleskills.LOGGER.debug("Prevented player {} from using tool {} due to insufficient {} level (required: {}, actual: {})",
                        player.getName().getString(), toolName, skill.getDisplayName(), requirement.getLevel(), playerLevel);
                return;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                if (playerPrestige < requiredPrestige) {
                    player.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use this tool!",
                            requiredPrestige)), true);
                    cir.setReturnValue(InteractionResult.FAIL);
                    cir.cancel();
                    Simpleskills.LOGGER.debug("Prevented player {} from using tool {} due to insufficient Prestige (required: ★{}, actual: ★{})",
                            player.getName().getString(), toolName, requiredPrestige, playerPrestige);
                }
            }
        }
    }

    @Inject(method = "transformBlock", at = @At("RETURN"))
    private void grantXPOnTransform(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player) || cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        Skills skill = getSkillForTransformer(context);
        if (skill == Skills.WOODCUTTING) {
            grantWoodcuttingXPOnStripLog(world, context, player);
        } else if (skill == Skills.FARMING) {
            grantFarmingXPOnTill(world, context, player);
        } else if (skill == Skills.EXCAVATING) {
            grantExcavationXPOnPathCreation(world, context, player);
        }
    }

    @Unique
    private static void grantWoodcuttingXPOnStripLog(Level world, UseOnContext context, ServerPlayer player) {
        String blockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
        if (isStrippableLog(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually stripped (i.e., is now a stripped log)
                String newBlockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
                if (newBlockTranslationKey.contains("stripped_")) {
                    int xp = ConfigManager.getBlockXP(blockTranslationKey, Skills.WOODCUTTING);
                    XPManager.addXPWithNotification(player, Skills.WOODCUTTING, xp/6);
                    Simpleskills.LOGGER.debug("Granted {} Woodcutting XP to {} for stripping log {}",
                            xp, player.getName().getString(), blockTranslationKey);
                }
            });
        }
    }

    @Unique
    private static void grantFarmingXPOnTill(Level world, UseOnContext context, ServerPlayer player) {
        String tilledBlockKey = lastBlockKey;
        Objects.requireNonNull(world.getServer()).execute(() -> {
            if (world.getBlockState(context.getClickedPos()).is(Blocks.FARMLAND)) {
                int xp = ConfigManager.getBlockXP(tilledBlockKey, Skills.FARMING);
                XPManager.addXPWithNotification(player, Skills.FARMING, xp/5);
                Simpleskills.LOGGER.debug("Granted {} Farming XP to {} for tilling {}",
                        xp, player.getName().getString(), tilledBlockKey);
            }
        });
    }

    @Unique
    private static void grantExcavationXPOnPathCreation(Level world, UseOnContext context, ServerPlayer player) {
        String blockTranslationKey = world.getBlockState(context.getClickedPos()).getBlock().getDescriptionId();
        if (isPathableBlock(blockTranslationKey)) {
            Objects.requireNonNull(world.getServer()).execute(() -> {
                // Check if the block was actually converted to a dirt path
                if (world.getBlockState(context.getClickedPos()).is(Blocks.DIRT_PATH)) {
                    int xp = ConfigManager.getBlockXP(blockTranslationKey, Skills.EXCAVATING);
                    XPManager.addXPWithNotification(player, Skills.EXCAVATING, xp/5);
                    Simpleskills.LOGGER.debug("Granted {} Excavation XP to {} for creating path from {}",
                            xp, player.getName().getString(), blockTranslationKey);
                }
            });
        }
    }

    @Unique
    private static Skills getSkillForTransformer(UseOnContext context) {
        Holder<BlockTransformer> transformer = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
        if (transformer == null) return null;
        if (transformer.is(BlockTransformers.AXE)) return Skills.WOODCUTTING;
        if (transformer.is(BlockTransformers.HOE)) return Skills.FARMING;
        if (transformer.is(BlockTransformers.SHOVEL)) return Skills.EXCAVATING;
        return null;
    }

    @Unique
    private static boolean isStrippableLog(String blockTranslationKey) {
        return blockTranslationKey.contains("log") || blockTranslationKey.contains("stem") ||
                blockTranslationKey.contains("wood") || blockTranslationKey.contains("hyphae") &&
                !blockTranslationKey.contains("copper");
    }

    @Unique
    private static boolean isPathableBlock(String blockTranslationKey) {
        return blockTranslationKey.contains("dirt") || blockTranslationKey.contains("grass_block") ||
                blockTranslationKey.contains("podzol") || blockTranslationKey.contains("mycelium") ||
                blockTranslationKey.contains("coarse_dirt");
    }
}
