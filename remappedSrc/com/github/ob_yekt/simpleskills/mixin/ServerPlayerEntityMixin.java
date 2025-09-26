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
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        String playerUuid = player.getUuidAsString();
        DatabaseManager db = DatabaseManager.getInstance();

        if (db.isPlayerInIronmanMode(playerUuid)) {
            // Apply death penalties
            player.getInventory().clear();
            player.setExperienceLevel(0);
            player.setExperiencePoints(0);

            int totalLevels = db.getAllSkills(playerUuid).values().stream()
                    .mapToInt(DatabaseManager.SkillData::level)
                    .sum();

            // Disable Ironman mode and reset skills
            IronmanManager.disableIronmanMode(player);
            db.resetPlayerSkills(playerUuid);
            db.ensurePlayerInitialized(playerUuid);

            player.sendMessage(Text.literal("§6[simpleskills]§f Your deal with death has cost you all skill levels. Ironman mode has been disabled.").formatted(Formatting.YELLOW), false);

            if (ConfigManager.getFeatureConfig().get("broadcast_ironman_death") != null &&
                    ConfigManager.getFeatureConfig().get("broadcast_ironman_death").getAsBoolean()) {
                Objects.requireNonNull(player.getEntityWorld().getServer()).getPlayerManager().broadcast(
                        Text.literal(String.format("§6[simpleskills]§f %s has died in Ironman mode with a total level of §6%d§f.",
                                player.getName().getString(), totalLevels)), false);
            }

            Simpleskills.LOGGER.debug("Processed Ironman death for player: {}", player.getName().getString());
        } else {
            AttributeManager.clearSkillAttributes(player);
            AttributeManager.clearIronmanAttributes(player);
        }

        AttributeManager.refreshAllAttributes(player);
        SkillTabMenu.updateTabMenu(player);
        Simpleskills.LOGGER.debug("Processed death for player: {}", player.getName().getString());
    }
}