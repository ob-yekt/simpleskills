package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public class EnchantingScreenHandlerMixin {
    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void onButtonClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer) {
            EnchantmentMenu handler = (EnchantmentMenu) (Object) this;
            grantEnchantingXP((ServerPlayer) player, handler, id);
        }
    }

    @Unique
    private void grantEnchantingXP(ServerPlayer player, EnchantmentMenu handler, int buttonId) {
        // Get the enchantment level for the selected option (buttonId is 0-2)
        int level = handler.costs[buttonId];
        if (level > 0) {
            // Grant XP based on the enchantment level using the configurable multiplier from config
            int multiplier = ConfigManager.getEnchantmentXpPerLevelSquared();
            int XP = multiplier * level * level;
            XPManager.addXPWithNotification(player, Skills.ENCHANTING, XP);
        }
    }
}