package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

@Mixin(BrewingStandBlockEntity.class)
public abstract class AlchemyXPMixin {
    @Unique
    private ServerPlayer lastPlayer;

    // Track last interacting player
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void onCreateScreenHandler(int syncId, Inventory playerInventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            this.lastPlayer = serverPlayer;
            Simpleskills.LOGGER.debug("Updated last player for BrewingStand at {} to {}",
                    ((BrewingStandBlockEntity)(Object)this).getBlockPos(), serverPlayer.getName().getString());
        }
    }

    // Trigger when brewing finishes
    @Inject(method = "doBrew", at = @At("TAIL"))
    private static void onCraft(Level world, BlockPos pos, NonNullList<ItemStack> slots, CallbackInfo ci) {
        if (world.isClientSide()) return;

        BrewingStandBlockEntity blockEntity = (BrewingStandBlockEntity) world.getBlockEntity(pos);
        if (blockEntity == null) return;

        ServerPlayer player = ((AlchemyXPMixin)(Object)blockEntity).lastPlayer;
        if (player == null) {
            Simpleskills.LOGGER.debug("No player associated with BrewingStand at {}, no Alchemy XP granted", pos);
            return;
        }

        // Process brewed potions
        for (int i = 0; i < 3; i++) {
            ItemStack stack = slots.get(i);
            if (!(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) {
                continue;
            }

            // Identify potion
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            String potionTranslationKey = "potion.minecraft.unknown";
            String potionPrefix = stack.is(Items.POTION) ? "potion" :
                    stack.is(Items.SPLASH_POTION) ? "splash_potion" :
                            "lingering_potion";
            if (potionContents != null) {
                Holder<Potion> potionEntry = potionContents.potion().orElse(null);
                if (potionEntry != null) {
                    ResourceKey<Potion> potionKey = potionEntry.unwrapKey().orElse(null);
                    if (potionKey != null) {
                        String effectId = potionKey.identifier().toString(); // e.g., minecraft:regeneration
                        potionTranslationKey = potionPrefix + ".minecraft." + effectId.replace("minecraft:", "");
                    }
                }
            }
            Simpleskills.LOGGER.debug("Identified potion at slot {}: {} (item: {})", i, potionTranslationKey, stack.getItem().getDescriptionId());

            // Grant XP
            int xpPerItem = ConfigManager.getAlchemyXP(potionTranslationKey, Skills.ALCHEMY);
            if (xpPerItem <= 0) {
                Simpleskills.LOGGER.debug("No XP defined for potion {}, skipping XP grant", potionTranslationKey);
                continue;
            }

            int totalXP = xpPerItem * stack.getCount();
            XPManager.addXPWithNotification(player, Skills.ALCHEMY, totalXP);

            Simpleskills.LOGGER.debug(
                    "Granted {} Alchemy XP for {}x {} to player {}",
                    totalXP, stack.getCount(), potionTranslationKey, player.getName().getString()
            );

            // Apply scaling and lore
            applyPotionScalingAndLore(stack, player);
        }
    }

    @Unique
    private static void applyPotionScalingAndLore(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        int level = XPManager.getSkillLevel(player.getStringUUID(), Skills.ALCHEMY);
        float multiplier = ConfigManager.getAlchemyMultiplier(level);
        if (multiplier == 1.0f) return; // Skip scaling and lore if multiplier is 1.0
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;

        // --- Collect base potion effects ---
        List<MobEffectInstance> baseEffects = contents.potion()
                .map(potionEntry -> potionEntry.value().getEffects())
                .orElse(List.of());

        // --- Scale both base and custom effects ---
        List<MobEffectInstance> scaledEffects = new ArrayList<>();
        for (MobEffectInstance effect : baseEffects) {
            scaledEffects.add(new MobEffectInstance(
                    effect.getEffect(),
                    Math.max(1, Math.round(effect.getDuration() * multiplier)),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.isVisible(),
                    effect.showIcon()
            ));
        }
        for (MobEffectInstance effect : contents.customEffects()) {
            scaledEffects.add(new MobEffectInstance(
                    effect.getEffect(),
                    Math.max(1, Math.round(effect.getDuration() * multiplier)),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.isVisible(),
                    effect.showIcon()
            ));
        }

        // --- Build new potion contents ---
        PotionContents scaled = new PotionContents(
                contents.potion(),
                contents.customColor(),
                scaledEffects,
                contents.customName()
        );

        stack.set(DataComponents.POTION_CONTENTS, scaled);

        // --- Add lore ---
        // Get existing lore, if any
        ItemLore currentLoreComponent = stack.getOrDefault(DataComponents.LORE, new ItemLore(List.of()));
        List<Component> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new alchemy lore with colored tier
        Component alchemyLore = Component.literal("Brewed by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Alchemist)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(alchemyLore);

        // Set the combined lore back to the stack
        stack.set(DataComponents.LORE, new ItemLore(currentLore));

        Simpleskills.LOGGER.debug(
                "Scaled potion {} effects x{} and added lore for player {} (lvl {}, tier {})",
                stack.getItem().getDescriptionId(),
                multiplier,
                player.getName().getString(),
                level, tierInfo.name()
        );
    }
}