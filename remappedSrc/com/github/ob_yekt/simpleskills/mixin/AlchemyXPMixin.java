package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.LoreManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(BrewingStandBlockEntity.class)
public abstract class AlchemyXPMixin {
    @Unique
    private ServerPlayerEntity lastPlayer;

    // Track last interacting player
    @Inject(method = "createScreenHandler", at = @At("RETURN"))
    private void onCreateScreenHandler(int syncId, PlayerInventory playerInventory, CallbackInfoReturnable<ScreenHandler> cir) {
        if (playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
            this.lastPlayer = serverPlayer;
            Simpleskills.LOGGER.debug("Updated last player for BrewingStand at {} to {}",
                    ((BrewingStandBlockEntity)(Object)this).getPos(), serverPlayer.getName().getString());
        }
    }

    // Trigger when brewing finishes
    @Inject(method = "craft", at = @At("TAIL"))
    private static void onCraft(World world, BlockPos pos, DefaultedList<ItemStack> slots, CallbackInfo ci) {
        if (world.isClient()) return;

        BrewingStandBlockEntity blockEntity = (BrewingStandBlockEntity) world.getBlockEntity(pos);
        if (blockEntity == null) return;

        ServerPlayerEntity player = ((AlchemyXPMixin)(Object)blockEntity).lastPlayer;
        if (player == null) {
            Simpleskills.LOGGER.debug("No player associated with BrewingStand at {}, no Alchemy XP granted", pos);
            return;
        }

        // Process brewed potions
        for (int i = 0; i < 3; i++) {
            ItemStack stack = slots.get(i);
            if (!(stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION))) {
                continue;
            }

            // Identify potion
            PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
            String potionTranslationKey = "potion.minecraft.unknown";
            String potionPrefix = stack.isOf(Items.POTION) ? "potion" :
                    stack.isOf(Items.SPLASH_POTION) ? "splash_potion" :
                            "lingering_potion";
            if (potionContents != null) {
                RegistryEntry<Potion> potionEntry = potionContents.potion().orElse(null);
                if (potionEntry != null) {
                    RegistryKey<Potion> potionKey = potionEntry.getKey().orElse(null);
                    if (potionKey != null) {
                        String effectId = potionKey.getValue().toString(); // e.g., minecraft:regeneration
                        potionTranslationKey = potionPrefix + ".minecraft." + effectId.replace("minecraft:", "");
                    }
                }
            }
            Simpleskills.LOGGER.debug("Identified potion at slot {}: {} (item: {})", i, potionTranslationKey, stack.getItem().getTranslationKey());

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
    private static void applyPotionScalingAndLore(ItemStack stack, ServerPlayerEntity player) {
        if (stack.isEmpty()) return;
        int level = XPManager.getSkillLevel(player.getUuidAsString(), Skills.ALCHEMY);
        float multiplier = ConfigManager.getAlchemyMultiplier(level);
        if (multiplier == 1.0f) return; // Skip scaling and lore if multiplier is 1.0
        LoreManager.TierInfo tierInfo = LoreManager.getTierName(level);

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return;

        // --- Collect base potion effects ---
        List<StatusEffectInstance> baseEffects = contents.potion()
                .map(potionEntry -> potionEntry.value().getEffects())
                .orElse(List.of());

        // --- Scale both base and custom effects ---
        List<StatusEffectInstance> scaledEffects = new ArrayList<>();
        for (StatusEffectInstance effect : baseEffects) {
            scaledEffects.add(new StatusEffectInstance(
                    effect.getEffectType(),
                    Math.max(1, Math.round(effect.getDuration() * multiplier)),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.shouldShowParticles(),
                    effect.shouldShowIcon()
            ));
        }
        for (StatusEffectInstance effect : contents.customEffects()) {
            scaledEffects.add(new StatusEffectInstance(
                    effect.getEffectType(),
                    Math.max(1, Math.round(effect.getDuration() * multiplier)),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.shouldShowParticles(),
                    effect.shouldShowIcon()
            ));
        }

        // --- Build new potion contents ---
        PotionContentsComponent scaled = new PotionContentsComponent(
                contents.potion(),
                contents.customColor(),
                scaledEffects,
                contents.customName()
        );

        stack.set(DataComponentTypes.POTION_CONTENTS, scaled);

        // --- Add lore ---
        // Get existing lore, if any
        LoreComponent currentLoreComponent = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(List.of()));
        List<Text> currentLore = new ArrayList<>(currentLoreComponent.lines());

        // Create the new alchemy lore with colored tier
        Text alchemyLore = Text.literal("Brewed by " + player.getName().getString() +
                        " (" + tierInfo.name() + " Alchemist)")
                .setStyle(Style.EMPTY.withItalic(false).withColor(tierInfo.color()));

        // Add the new lore at the beginning of the list
        currentLore.addFirst(alchemyLore);

        // Set the combined lore back to the stack
        stack.set(DataComponentTypes.LORE, new LoreComponent(currentLore));

        Simpleskills.LOGGER.debug(
                "Scaled potion {} effects x{} and added lore for player {} (lvl {}, tier {})",
                stack.getItem().getTranslationKey(),
                multiplier,
                player.getName().getString(),
                level, tierInfo.name()
        );
    }
}