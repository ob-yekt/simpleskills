package com.github.ob_yekt.simpleskills.mixin;

import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToolMaterial.class)
public abstract class ToolMaterialMixin {
    @Shadow @Final @Mutable
    public static ToolMaterial COPPER;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void modifyCopperTier(CallbackInfo ci) {
        COPPER = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 190, 5.0F, 1.0F, 13, ItemTags.COPPER_TOOL_MATERIALS);
    }
}