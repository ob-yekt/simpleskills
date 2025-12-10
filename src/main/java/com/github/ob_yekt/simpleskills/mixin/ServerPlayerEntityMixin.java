package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.AttributeManager;
import com.github.ob_yekt.simpleskills.managers.IronmanManager;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Mixin for ServerPlayerEntity to handle Ironman mode death penalties.
 * Respects force_ironman_mode config — never disables Ironman if enforced.
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();
        boolean isForceIronman = ConfigManager.isForceIronmanModeEnabled();

        if (!db.isPlayerInIronmanMode(playerUuid)) {
            // Normal player death — just refresh attributes
            AttributeManager.clearSkillAttributes(player);
            AttributeManager.clearIronmanAttributes(player);
            AttributeManager.refreshAllAttributes(player);
            SkillTabMenu.updateTabMenu(player);
            com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(player);
            return;
        }

        // === PLAYER IS IN IRONMAN MODE ===

        // Always apply hardcore death penalties
        player.getInventory().clear();
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);

        int totalLevels = db.getAllSkills(playerUuid).values().stream()
                .mapToInt(DatabaseManager.SkillData::level)
                .sum();
        int prestige = db.getPrestige(playerUuid);

        // Always reset skills and prestige on death
        db.resetPlayerSkills(playerUuid);
        db.setPrestige(playerUuid, 0);
        db.ensurePlayerInitialized(playerUuid);

        // Broadcast death if enabled
        if (ConfigManager.getFeatureConfig().get("broadcast_ironman_death") != null &&
                ConfigManager.getFeatureConfig().get("broadcast_ironman_death").getAsBoolean()) {
            String prestigePart = prestige > 0 ? String.format(" at §6★%d§f", prestige) : "";
            String message = String.format("§6[simpleskills]§f %s has died in Ironman mode with a total level of §6%d§f%s.",
                    player.getName().getString(), totalLevels, prestigePart);

            Objects.requireNonNull(player.getEntityWorld().getServer()).getPlayerManager().broadcast(
                    Text.literal(message), false);
        }

        // === ONLY disable Ironman if NOT forced by server ===
        if (!isForceIronman) {
            IronmanManager.disableIronmanMode(player);
            player.sendMessage(Text.literal("§6[simpleskills]§f Your deal with death has cost you all your levels and items. Ironman mode has been disabled.")
                    .formatted(Formatting.YELLOW), false);
        } else {
            // Force Ironman: keep mode active, just re-apply visuals/attributes
            player.sendMessage(Text.literal("§6[simpleskills]§f Your deal with death has cost you all your levels and items.")
                    .formatted(Formatting.YELLOW), false);

            // Re-apply Ironman attributes since inventory was cleared
            AttributeManager.applyIronmanAttributes(player);
        }

        // Always refresh UI and attributes after death
        AttributeManager.refreshAllAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(player);

        Simpleskills.LOGGER.info("Ironman death processed for {} (Total Level: {}, Prestige: {}, Force Mode: {})",
                player.getName().getString(), totalLevels, prestige, isForceIronman);
    }
}