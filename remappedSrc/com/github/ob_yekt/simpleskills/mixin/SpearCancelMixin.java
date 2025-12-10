package com.github.ob_yekt.simpleskills.mixin;


import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class SpearCancelMixin {

    @Inject(method = "pierce", at = @At("HEAD"), cancellable = true)
    private void onPierce(
            EquipmentSlot slot,
            Entity target,
            float damage,
            boolean dealDamage,
            boolean knockback,
            boolean dismount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (player.getEntityWorld().isClient()) return;
        if (!(target instanceof LivingEntity)) return;

        ItemStack weapon = player.getEquippedStack(slot);
        if (weapon.isEmpty()) return;

        SkillRequirement req = ConfigManager.getWeaponRequirement(Registries.ITEM.getId(weapon.getItem()).toString());
        if (req == null) return;

        int level = XPManager.getSkillLevel(player.getUuidAsString(), req.getSkill());
        int prestige = DatabaseManager.getInstance().getPrestige(player.getUuidAsString());

        if (level < req.getLevel() || (req.getRequiredPrestige() > 0 && prestige < req.getRequiredPrestige())) {
            player.sendMessage(
                    Text.literal(String.format(
                            "§6[simpleskills]§f You need %s level %d to use this weapon!",
                            req.getSkill().getDisplayName(),
                            req.getLevel()
                    )),
                    true
            );
            cir.setReturnValue(false); // cancels the pierce attack
        }
    }
}
