package com.github.ob_yekt.simpleskills.mixin.FISHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingHook.class)
public abstract class FishingBobberLootMixin {

    // 3) Swap the loot table per-player (fix: owner + signature are ReloadableRegistries$Lookup)
    @Redirect(
            method = "retrieve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;"
            )
    )
    private LootTable simpleskills$redirectGetLootTable(
            ReloadableServerRegistries.Holder lookup,
            ResourceKey<LootTable> original
    ) {
        FishingHook self = (FishingHook) (Object) this;
        Player player = self.getPlayerOwner();

        if (player instanceof ServerPlayer sp) {
            int level = XPManager.getSkillLevel(sp.getStringUUID(), Skills.FISHING);
            Identifier customId = ConfigManager.getFishingLootTable(level);

            if (customId != null) {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, customId);
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