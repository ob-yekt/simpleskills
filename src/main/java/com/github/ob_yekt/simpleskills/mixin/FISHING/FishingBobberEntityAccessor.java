package com.github.ob_yekt.simpleskills.mixin.FISHING;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface FishingBobberEntityAccessor {
    @Accessor("timeUntilLured")
    int getWaitCountdown();

    @Accessor("timeUntilLured")
    void setWaitCountdown(int waitCountdown);

    @Accessor("luck")
    int getLuckBonus();
}