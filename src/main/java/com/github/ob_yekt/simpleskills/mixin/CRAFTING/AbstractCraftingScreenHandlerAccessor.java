package com.github.ob_yekt.simpleskills.mixin.CRAFTING;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractCraftingMenu.class)
public interface AbstractCraftingScreenHandlerAccessor {
    @Accessor("craftSlots")
    CraftingContainer getCraftingInventory();
}