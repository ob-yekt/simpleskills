package com.github.ob_yekt.simpleskills.mixin.FISHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

    // 1) Grant fishing XP when a catch spawns - using WrapOperation for compatibility
    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean simpleskills$grantFishingXp(World world, Entity entity, Operation<Boolean> original) {
        boolean result = original.call(world, entity);

        // Only grant XP for ItemEntity (the caught item), not ExperienceOrbEntity
        if (result && entity instanceof ItemEntity) {
            FishingBobberEntity self = (FishingBobberEntity) (Object) this;
            PlayerEntity player = self.getPlayerOwner();
            if (player instanceof ServerPlayerEntity sp && !world.isClient()) {
                int xp = ConfigManager.getFishingXP("catch", Skills.FISHING);
                XPManager.addXPWithNotification(sp, Skills.FISHING, xp);
                Simpleskills.LOGGER.debug("Granted {} Fishing XP for {}.", xp, player.getName().getString());
            }
        }

        return result;
    }

    // 2) Faster bites based on level (apply after vanilla logic for stability)
    @Inject(method = "tickFishingLogic", at = @At("TAIL"))
    private void simpleskills$applyFishingLevelBonus(CallbackInfo ci) {
        if (!ConfigManager.isFishingSpeedBonusEnabled()) {
            return;
        }

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
}