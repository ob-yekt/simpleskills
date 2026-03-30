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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to restrict armor equipping for ServerPlayerEntity based on skill levels.
 */
@Mixin(LivingEntity.class)
public abstract class ArmorRestrictionMixin {

    @Inject(
            method = "setItemSlot",
            at = @At("HEAD"),
            cancellable = true
    )
    private void restrictArmorEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        // Only apply restrictions to players
        if (!((Object) this instanceof ServerPlayer player)) {
            Simpleskills.LOGGER.debug("Skipping armor restriction for non-player entity: {}", this.getClass().getName());
            return;
        }

        Simpleskills.LOGGER.info("Mixin applied to ServerPlayerEntity, class: {}", this.getClass().getName());
        Simpleskills.LOGGER.debug("Processing for player: {}", player.getName().getString());

        // Check if the slot is an armor slot and the stack is not empty
        if (!slot.isArmor() || stack.isEmpty()) {
            Simpleskills.LOGGER.debug("Not an armor slot or stack is empty, skipping: slot={}, stack={}", slot, stack);
            return;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Simpleskills.LOGGER.debug("Checking item: {}", itemId);

        SkillRequirement requirement = ConfigManager.getArmorRequirement(itemId.toString());
        if (requirement == null) {
            Simpleskills.LOGGER.debug("No skill requirement for item: {}", itemId);
            return;
        }

        String playerUuid = player.getStringUUID();
        String skill = requirement.getSkill().getId();
        int playerLevel = XPManager.getSkillLevel(playerUuid, requirement.getSkill());
        int requiredLevel = requirement.getLevel();

        Simpleskills.LOGGER.debug("Player {} level for skill {}: {}, required: {}", playerUuid, skill, playerLevel, requiredLevel);

        if (playerLevel < requiredLevel) {
            String preventReason = String.format("§6[simpleskills]§f You need %s level %d to equip this item!",
                    requirement.getSkill().getDisplayName(), requiredLevel);
            player.sendSystemMessage(Component.literal(preventReason), true);
            player.drop(stack.copy(), false);
            ci.cancel();
            Simpleskills.LOGGER.info("Prevented player {} from equipping {} due to insufficient {} level (required: {}, actual: {})",
                    player.getName().getString(), itemId, requirement.getSkill().getDisplayName(), requiredLevel, playerLevel);
            return;
        }

        int requiredPrestige = requirement.getRequiredPrestige();
        if (requiredPrestige > 0) {
            int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
            if (playerPrestige < requiredPrestige) {
                String preventReason = String.format("§6[simpleskills]§f You need Prestige ★%d to equip this item!", requiredPrestige);
                player.sendSystemMessage(Component.literal(preventReason), true);
                player.drop(stack.copy(), false);
                ci.cancel();
                Simpleskills.LOGGER.info("Prevented player {} from equipping {} due to insufficient prestige (required: ★{}, actual: ★{})",
                        player.getName().getString(), itemId, requiredPrestige, playerPrestige);
            }
        }
    }
}