package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractCraftingScreenHandler.class)
public interface AbstractCraftingScreenHandlerAccessor {
    @Accessor("craftingInventory")
    RecipeInputInventory getCraftingInventory();
}