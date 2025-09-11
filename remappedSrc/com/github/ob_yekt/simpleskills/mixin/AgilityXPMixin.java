package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerPlayerEntity.class)
public abstract class AgilityXPMixin {
    @Unique
    private static final Map<UUID, Map<String, Long>> lastActionTimes = new HashMap<>();
    @Unique
    private static final Map<String, Long> COOLDOWN_TICKS = new HashMap<>();

    static {
        COOLDOWN_TICKS.put("fall_damage", 20L);
        COOLDOWN_TICKS.put("jump", 40L);
        COOLDOWN_TICKS.put("swim", 40L);
        COOLDOWN_TICKS.put("sprint", 40L);
        COOLDOWN_TICKS.put("sneak", 40L);
    }

    // Helper method to check and update cooldown for a specific action
    @Unique
    private boolean canAwardXP(ServerPlayerEntity player, String action) {
        long currentTick = player.getEntityWorld().getTime();
        Map<String, Long> playerTimes = lastActionTimes.computeIfAbsent(player.getUuid(), k -> new HashMap<>());
        long lastActionTick = playerTimes.getOrDefault(action, 0L);
        long cooldown = COOLDOWN_TICKS.getOrDefault(action, 20L);
        if (currentTick - lastActionTick >= cooldown) {
            playerTimes.put(action, currentTick);
            return true;
        }
        return false;
    }

    // Fall Damage: Hook into damage method
    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (source == player.getDamageSources().fall() && cir.getReturnValue() && canAwardXP(player, "fall_damage")) {
            // Scale XP based on damage taken (e.g., 5 XP per heart of damage)
            int xp = (int)(amount * ConfigManager.getAgilityXP("fall_damage", Skills.AGILITY) / 2);
            XPManager.addXPWithNotification(player, Skills.AGILITY, xp);
            Simpleskills.LOGGER.debug("Granted {} Agility XP for fall damage ({} hearts) to player {}",
                    xp, amount / 2, player.getName().getString());
        }
    }

    // Jumping: Hook into jump method
    @Inject(method = "jump", at = @At("TAIL"))
    private void onJump(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (canAwardXP(player, "jump")) {
            int xp = ConfigManager.getAgilityXP("jump", Skills.AGILITY);
            XPManager.addXPWithNotification(player, Skills.AGILITY, xp);
            Simpleskills.LOGGER.debug("Granted {} Agility XP for jumping to player {}",
                    xp, player.getName().getString());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickAgility(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;

        // Swimming XP
        if (player.isSwimming() && canAwardXP(player, "swim")) {
            int xp = ConfigManager.getAgilityXP("swim", Skills.AGILITY);
            XPManager.addXPWithNotification(player, Skills.AGILITY, xp);
            Simpleskills.LOGGER.debug("Granted {} Agility XP for swimming to player {}",
                    xp, player.getName().getString());
        }

        // Sprinting XP
        if (player.isSprinting() && canAwardXP(player, "sprint")) {
            int xp = ConfigManager.getAgilityXP("sprint", Skills.AGILITY);
            XPManager.addXPWithNotification(player, Skills.AGILITY, xp);
            Simpleskills.LOGGER.debug("Granted {} Agility XP for sprinting to player {}",
                    xp, player.getName().getString());
        }

        // Sneaking XP (optional: continuous like swimming/sprinting)
        if (player.isSneaking() && canAwardXP(player, "sneak")) {
            int xp = ConfigManager.getAgilityXP("sneak", Skills.AGILITY);
            XPManager.addXPWithNotification(player, Skills.AGILITY, xp);
            Simpleskills.LOGGER.debug("Granted {} Agility XP for sneaking to player {}",
                    xp, player.getName().getString());
        }
    }

    // Clean up cooldowns when player disconnects
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onDisconnect(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        lastActionTimes.remove(player.getUuid());
        Simpleskills.LOGGER.debug("Cleared agility cooldowns for player {}", player.getName().getString());
    }
}