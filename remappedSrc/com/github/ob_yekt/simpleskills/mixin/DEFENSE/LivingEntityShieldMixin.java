package com.github.ob_yekt.simpleskills.mixin.DEFENSE;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldMixin {

    @Shadow
    public abstract ItemStack getActiveItem();

    @Unique
    private static final String SHIELD_ID = "minecraft:shield";
    @Unique
    private int lastShieldMessageTick = 0;

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void onIsBlocking(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack activeStack = this.getActiveItem();
        if (activeStack.isEmpty() || !activeStack.isOf(Items.SHIELD)) {
            return;
        }

        // Check shield requirement
        SkillRequirement requirement = ConfigManager.getToolRequirement(SHIELD_ID);
        if (requirement == null) {
            return;
        }

        Skills skill = requirement.getSkill();
        int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), skill);

        if (playerLevel < requirement.getLevel()) {
            // Send message only once every 60 ticks (3 seconds) to avoid spam
            int currentTick = serverPlayer.age;
            if (currentTick - lastShieldMessageTick > 60) {
                serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need %s level %d to use a shield!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                lastShieldMessageTick = currentTick;
            }

            // Stop blocking
            serverPlayer.stopUsingItem();
            cir.setReturnValue(false);
            return;
        }

        int requiredPrestige = requirement.getRequiredPrestige();
        if (requiredPrestige > 0) {
            int playerPrestige = DatabaseManager.getInstance().getPrestige(serverPlayer.getUuidAsString());
            if (playerPrestige < requiredPrestige) {
                int currentTick = serverPlayer.age;
                if (currentTick - lastShieldMessageTick > 60) {
                    serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use a shield!",
                            requiredPrestige)), true);
                    lastShieldMessageTick = currentTick;
                }
                serverPlayer.stopUsingItem();
                cir.setReturnValue(false);
            }
        }
    }
}