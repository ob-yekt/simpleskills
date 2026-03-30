package com.github.ob_yekt.simpleskills.mixin.DEFENSE;

import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldMixin {

    @Shadow
    public abstract ItemStack getUseItem();

    @Unique
    private static final String SHIELD_ID = "minecraft:shield";
    @Unique
    private int lastShieldMessageTick = 0;

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void onIsBlocking(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack activeStack = this.getUseItem();
        if (activeStack.isEmpty() || !activeStack.is(Items.SHIELD)) {
            return;
        }

        // Check shield requirement
        SkillRequirement requirement = ConfigManager.getToolRequirement(SHIELD_ID);
        if (requirement == null) {
            return;
        }

        Skills skill = requirement.getSkill();
        int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), skill);

        if (playerLevel < requirement.getLevel()) {
            // Send message only once every 60 ticks (3 seconds) to avoid spam
            int currentTick = serverPlayer.tickCount;
            if (currentTick - lastShieldMessageTick > 60) {
                serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use a shield!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                lastShieldMessageTick = currentTick;
            }

            // Stop blocking
            serverPlayer.releaseUsingItem();
            cir.setReturnValue(false);
            return;
        }

        int requiredPrestige = requirement.getRequiredPrestige();
        if (requiredPrestige > 0) {
            int playerPrestige = DatabaseManager.getInstance().getPrestige(serverPlayer.getStringUUID());
            if (playerPrestige < requiredPrestige) {
                int currentTick = serverPlayer.tickCount;
                if (currentTick - lastShieldMessageTick > 60) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use a shield!",
                            requiredPrestige)), true);
                    lastShieldMessageTick = currentTick;
                }
                serverPlayer.releaseUsingItem();
                cir.setReturnValue(false);
            }
        }
    }
}