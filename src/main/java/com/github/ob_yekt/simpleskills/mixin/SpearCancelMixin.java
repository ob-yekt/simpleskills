package com.github.ob_yekt.simpleskills.mixin;


import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class SpearCancelMixin {

    @Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
    private void onPierce(
            EquipmentSlot slot,
            Entity target,
            float damage,
            boolean dealDamage,
            boolean knockback,
            boolean dismount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Player player = (Player) (Object) this;

        if (player.level().isClientSide()) return;
        if (!(target instanceof LivingEntity)) return;

        ItemStack weapon = player.getItemBySlot(slot);
        if (weapon.isEmpty()) return;

        SkillRequirement req = ConfigManager.getWeaponRequirement(BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString());
        if (req == null) return;

        int level = XPManager.getSkillLevel(player.getStringUUID(), req.getSkill());
        int prestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());

        if (level < req.getLevel() || (req.getRequiredPrestige() > 0 && prestige < req.getRequiredPrestige())) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.literal(String.format(
                                "§6[simpleskills]§f You need %s level %d to use this weapon!",
                                req.getSkill().getDisplayName(),
                                req.getLevel()
                        )),
                        true
                );
            }
            cir.setReturnValue(false);
        }
    }
}
