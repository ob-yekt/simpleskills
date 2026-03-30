package com.github.ob_yekt.simpleskills.mixin.DEFENSE;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to validate equipped armor requirements when the inventory screen is closed.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class InventoryCheckMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void validateArmorRequirementsOnInventoryClose(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }

            ItemStack equippedItem = serverPlayer.getItemBySlot(slot);
            if (equippedItem.isEmpty()) {
                continue;
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(equippedItem.getItem());
            SkillRequirement requirement = ConfigManager.getArmorRequirement(itemId.toString());
            if (requirement == null) {
                continue;
            }

            int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), requirement.getSkill());
            if (playerLevel < requirement.getLevel()) {
                String removalReason = String.format("(requires %s level %d)", requirement.getSkill().getDisplayName(), requirement.getLevel());
                serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f Removed invalid armor: %s %s",
                        equippedItem.getHoverName().getString(), removalReason)), true);

                ItemStack armorToMove = equippedItem.copy();
                serverPlayer.setItemSlot(slot, ItemStack.EMPTY);
                boolean addedToInventory = serverPlayer.getInventory().add(armorToMove);

                if (!addedToInventory) {
                    serverPlayer.drop(armorToMove, false);
                }

                Simpleskills.LOGGER.debug("Removed armor {} from player {} due to insufficient {} level (required: {}, actual: {})",
                        itemId, serverPlayer.getName().getString(), requirement.getSkill().getDisplayName(), requirement.getLevel(), playerLevel);
                continue;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                if (playerPrestige < requiredPrestige) {
                    String removalReason = String.format("(requires Prestige ★%d)", requiredPrestige);
                    serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f Removed invalid armor: %s %s",
                            equippedItem.getHoverName().getString(), removalReason)), true);

                    ItemStack armorToMove = equippedItem.copy();
                    serverPlayer.setItemSlot(slot, ItemStack.EMPTY);
                    boolean addedToInventory = serverPlayer.getInventory().add(armorToMove);

                    if (!addedToInventory) {
                        serverPlayer.drop(armorToMove, false);
                    }

                    Simpleskills.LOGGER.debug("Removed armor {} from player {} due to insufficient prestige (required: ★{}, actual: ★{})",
                            itemId, serverPlayer.getName().getString(), requiredPrestige, playerPrestige);
                }
            }
        }
    }
}