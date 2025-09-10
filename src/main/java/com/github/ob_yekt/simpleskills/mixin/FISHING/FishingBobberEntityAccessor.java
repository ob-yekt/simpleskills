package com.github.ob_yekt.simpleskills.mixin.FISHING;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingBobberEntity.class)
public interface FishingBobberEntityAccessor {
    @Accessor("waitCountdown")
    int getWaitCountdown();

    @Accessor("waitCountdown")
    void setWaitCountdown(int waitCountdown);

    @Accessor("luckBonus")
    int getLuckBonus();
}