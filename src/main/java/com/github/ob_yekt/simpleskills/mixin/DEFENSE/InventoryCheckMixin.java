package com.github.ob_yekt.simpleskills.mixin.DEFENSE;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to validate equipped armor requirements when the inventory screen is closed.
 */
@Mixin(ScreenHandler.class)
public abstract class InventoryCheckMixin {

    @Inject(method = "onClosed", at = @At("HEAD"))
    private void validateArmorRequirementsOnInventoryClose(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) {
                continue;
            }

            ItemStack equippedItem = serverPlayer.getEquippedStack(slot);
            if (equippedItem.isEmpty()) {
                continue;
            }

            Identifier itemId = Registries.ITEM.getId(equippedItem.getItem());
            SkillRequirement requirement = ConfigManager.getArmorRequirement(itemId.toString());
            if (requirement == null) {
                continue;
            }

            int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), requirement.getSkill());
            if (playerLevel < requirement.getLevel()) {
                String removalReason = String.format("(requires %s level %d)", requirement.getSkill().getDisplayName(), requirement.getLevel());
                serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f Removed invalid armor: %s %s",
                        equippedItem.getName().getString(), removalReason)), true);

                ItemStack armorToMove = equippedItem.copy();
                serverPlayer.equipStack(slot, ItemStack.EMPTY);
                boolean addedToInventory = serverPlayer.getInventory().insertStack(armorToMove);

                if (!addedToInventory) {
                    serverPlayer.dropItem(armorToMove, false);
                }

                Simpleskills.LOGGER.debug("Removed armor {} from player {} due to insufficient {} level (required: {}, actual: {})",
                        itemId, serverPlayer.getName().getString(), requirement.getSkill().getDisplayName(), requirement.getLevel(), playerLevel);
                continue;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getUuidAsString());
                if (playerPrestige < requiredPrestige) {
                    String removalReason = String.format("(requires Prestige ★%d)", requiredPrestige);
                    serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f Removed invalid armor: %s %s",
                            equippedItem.getName().getString(), removalReason)), true);

                    ItemStack armorToMove = equippedItem.copy();
                    serverPlayer.equipStack(slot, ItemStack.EMPTY);
                    boolean addedToInventory = serverPlayer.getInventory().insertStack(armorToMove);

                    if (!addedToInventory) {
                        serverPlayer.dropItem(armorToMove, false);
                    }

                    Simpleskills.LOGGER.debug("Removed armor {} from player {} due to insufficient prestige (required: ★{}, actual: ★{})",
                            itemId, serverPlayer.getName().getString(), requiredPrestige, playerPrestige);
                }
            }
        }
    }
}