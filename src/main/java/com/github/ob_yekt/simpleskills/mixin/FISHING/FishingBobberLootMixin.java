package com.github.ob_yekt.simpleskills.mixin.FISHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberLootMixin {

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

        if (player instanceof ServerPlayerEntity sp) {
            int level = XPManager.getSkillLevel(sp.getUuidAsString(), Skills.FISHING);
            Identifier customId = ConfigManager.getFishingLootTable(level);

            if (customId != null) {
                RegistryKey<LootTable> key = RegistryKey.of(RegistryKeys.LOOT_TABLE, customId);
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
                Simpleskills.LOGGER.debug("No custom loot table for level {}, using original", level);
            }
        }

        return lookup.getLootTable(original);
    }
}