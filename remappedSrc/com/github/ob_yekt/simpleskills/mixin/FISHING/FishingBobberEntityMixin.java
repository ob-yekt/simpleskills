package com.github.ob_yekt.simpleskills.mixin.FISHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

    // 1) Grant fishing XP when a catch spawns
    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z",
                    shift = At.Shift.AFTER
            )
    )
    private void simpleskills$grantFishingXp(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        PlayerEntity player = self.getPlayerOwner();
        if (player instanceof ServerPlayerEntity sp && !player.getEntityWorld().isClient()) {
            int xp = ConfigManager.getFishingXP("catch", Skills.FISHING);
            XPManager.addXPWithNotification(sp, Skills.FISHING, xp);
            Simpleskills.LOGGER.debug("Granted {} Fishing XP for {}.", xp, player.getName().getString());
        }
    }

    // 2) Faster bites based on level (apply after vanilla logic for stability)
    @Inject(method = "tickFishingLogic", at = @At("TAIL"))
    private void simpleskills$applyFishingLevelBonus(CallbackInfo ci) {
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        PlayerEntity player = self.getPlayerOwner();
        if (!(player instanceof ServerPlayerEntity sp) || player.getEntityWorld().isClient()) return;

        int level = XPManager.getSkillLevel(sp.getUuidAsString(), Skills.FISHING);
        int wait = ((FishingBobberEntityAccessor) self).getWaitCountdown();
        if (wait > 0 && level > 0) {
            double mult = 1.0 - (level * 0.005); // 0.5% per level
            int adjusted = (int) (wait * Math.max(0.20, mult)); // Never below 20% of vanilla
            if (adjusted < wait) {
                ((FishingBobberEntityAccessor) self).setWaitCountdown(adjusted);
                Simpleskills.LOGGER.debug("Fishing waitCountdown -> {} (lvl {}) for {}",
                        adjusted, level, player.getName().getString());
            }
        }
    }

    // 3) Swap the loot table per-player (fix: owner + signature are ReloadableRegistries$Lookup)
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/ReloadableRegistries$Lookup;getLootTable(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/loot/LootTable;"
            )
    )
    private LootTable simpleskills$redirectGetLootTable(
            ReloadableRegistries.Lookup lookup,
            RegistryKey<LootTable> original
    ) {
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        PlayerEntity player = self.getPlayerOwner();
        RegistryKey<LootTable> key = original;

        Simpleskills.LOGGER.debug("Original loot table: {}", original.getValue());
        if (player instanceof ServerPlayerEntity sp) {
            int level = XPManager.getSkillLevel(sp.getUuidAsString(), Skills.FISHING);
            Identifier customId = ConfigManager.getFishingLootTable(level);
            if (customId != null) {
                key = RegistryKey.of(RegistryKeys.LOOT_TABLE, customId);
                Simpleskills.LOGGER.debug("Attempting custom loot table: {}", customId);
                LootTable lootTable = lookup.getLootTable(key);
                if (lootTable == LootTable.EMPTY) {
                    Simpleskills.LOGGER.warn("Loot table {} is empty or invalid for player {} (lvl {})",
                            customId, player.getName().getString(), level);
                    return lookup.getLootTable(original); // Fall back to vanilla
                }
                Simpleskills.LOGGER.debug("Using custom fishing loot table {} for {} (lvl {})",
                        customId, player.getName().getString(), level);
                return lootTable;
            } else {
                Simpleskills.LOGGER.warn("No custom loot table for level {}, falling back to vanilla", level);
            }
        } else {
            Simpleskills.LOGGER.debug("Player {} is not a ServerPlayerEntity, using vanilla loot table {}", player, original);
        }
        return lookup.getLootTable(key);
    }
}