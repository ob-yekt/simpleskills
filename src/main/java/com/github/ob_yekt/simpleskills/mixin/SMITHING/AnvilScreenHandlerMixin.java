package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
    @Final
    @Shadow
    private Property levelCost;

    @Shadow
    public abstract int getLevelCost();

    @Shadow
    private int repairItemUsage;

    @Shadow
    private boolean keepSecondSlot;

    // Store durability repaired for XP calculation
    @Unique
    private int durabilityRepaired;

    protected AnvilScreenHandlerMixin(int syncId, PlayerEntity player) {
        super(null, syncId, player.getInventory(), null, getForgingSlotsManager());
    }

    @Unique
    private static ForgingSlotsManager getForgingSlotsManager() {
        return ForgingSlotsManager.builder()
                .input(0, 27, 47, stack -> true)
                .input(1, 76, 47, stack -> true)
                .output(2, 134, 47)
                .build();
    }

    /**
     * Calculate repair efficiency based on smithing level with tier-based scaling
     * Novice (1-24): 20% to 30%
     * Journeyman (25-49): 35% to 45%
     * Artisan (50-74): 50% to 60%
     * Expert (75-98): 65% to 75%
     * Grandmaster (99): 100%
     */
    @Unique
    private float calculateRepairEfficiency(int smithingLevel) {
        if (smithingLevel >= 99) {
            return 1.0f; // Grandmaster: 100%
        } else if (smithingLevel >= 75) {
            // Expert (75-98): 65% to 75%
            float progress = (smithingLevel - 75) / 23.0f;
            return 0.65f + (0.10f * progress);
        } else if (smithingLevel >= 50) {
            // Artisan (50-74): 50% to 60%
            float progress = (smithingLevel - 50) / 24.0f;
            return 0.50f + (0.10f * progress);
        } else if (smithingLevel >= 25) {
            // Journeyman (25-49): 35% to 45%
            float progress = (smithingLevel - 25) / 24.0f;
            return 0.35f + (0.10f * progress);
        } else {
            // Novice (1-24): 20% to 30%
            float progress = (smithingLevel - 1) / 23.0f;
            return 0.20f + (0.10f * progress);
        }
    }

    @Inject(
            method = "updateResult",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/screen/AnvilScreenHandler;repairItemUsage:I",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void scaleMaterialRepair(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        PlayerEntity player = this.player;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        Slot inputSlot = handler.getSlot(0);
        Slot materialSlot = handler.getSlot(1);
        Slot outputSlot = handler.getSlot(2);
        ItemStack inputStack = inputSlot.getStack();
        ItemStack materialStack = materialSlot.getStack();
        ItemStack outputStack = outputSlot.getStack();

        // Only for material repair (repairItemUsage > 0 after vanilla loop)
        if (this.repairItemUsage <= 0 || outputStack.isEmpty() || inputStack.isEmpty() || materialStack.isEmpty() ||
                !inputStack.isDamageable() || !inputStack.canRepairWith(materialStack)) {
            this.durabilityRepaired = 0;
            return;
        }

        // Adjust repair based on smithing level with new tier system
        int playerSmithingLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.SMITHING);
        float repairFraction = calculateRepairEfficiency(playerSmithingLevel);

        int maxDamage = inputStack.getMaxDamage();
        int inputDamage = inputStack.getDamage();
        // Base repair per material is 25% of max damage (vanilla default)
        int baseRepairPerMaterial = Math.round(maxDamage * 0.25f);
        // Scale repair amount based on smithing level
        int repairPerMaterial = Math.round(baseRepairPerMaterial * repairFraction);
        int availableMaterials = materialStack.getCount();

        // Calculate total durability to repair
        int newRepaired = Math.min(inputDamage, availableMaterials * repairPerMaterial);
        int newDamage = inputDamage - newRepaired;
        // Calculate material usage based on durability repaired
        int newUsage = (newRepaired >= inputDamage)
                ? (int) Math.ceil((double) inputDamage / repairPerMaterial)
                : availableMaterials;
        newUsage = Math.min(newUsage, availableMaterials);

        // Store durability repaired for XP calculation
        this.durabilityRepaired = inputDamage - newDamage;

        // Override vanilla results
        outputStack.setDamage(newDamage);
        this.repairItemUsage = newUsage;
        outputSlot.setStack(outputStack);

        // Set level cost to 0 for material repairs
        this.levelCost.set(1);

        // Determine tier name for logging
        String tierName;
        if (playerSmithingLevel >= 99) tierName = "Grandmaster";
        else if (playerSmithingLevel >= 75) tierName = "Expert";
        else if (playerSmithingLevel >= 50) tierName = "Artisan";
        else if (playerSmithingLevel >= 25) tierName = "Journeyman";
        else tierName = "Novice";

        Simpleskills.LOGGER.debug(
                "Scaled material repair for player {} ({} lvl {}): efficiency {}%, usage {} -> {}, damage {} -> {}, durabilityRepaired {}, cost -> 1",
                serverPlayer.getName().getString(), tierName, playerSmithingLevel, repairFraction * 100,
                this.repairItemUsage, newUsage, inputDamage, newDamage, this.durabilityRepaired
        );
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void handleEnchantRequirementsAndRepairCost(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        PlayerEntity player = this.player;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        Slot inputSlot = handler.getSlot(0);
        Slot materialSlot = handler.getSlot(1);
        Slot outputSlot = handler.getSlot(2);
        ItemStack inputStack = inputSlot.getStack();
        ItemStack materialStack = materialSlot.getStack();
        ItemStack outputStack = outputSlot.getStack();

        if (outputStack.isEmpty()) return;

        // ================================
        // 1. Enchantment requirement check
        // ================================
        boolean hasRestrictedEnchantment = false;
        for (RegistryEntry<Enchantment> enchantmentEntry : outputStack.getEnchantments().getEnchantments()) {
            Enchantment enchantment = enchantmentEntry.value();
            int enchantmentLevel = outputStack.getEnchantments().getLevel(enchantmentEntry);
            Identifier enchantmentId = serverPlayer.getEntityWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getId(enchantment);
            if (enchantmentId == null) continue;

            SkillRequirement requirement = ConfigManager.getEnchantmentRequirement(enchantmentId.toString());
            int playerEnchantingLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.ENCHANTING);

            if (requirement != null && enchantmentLevel >= requirement.getEnchantmentLevel()
                    && playerEnchantingLevel < requirement.getLevel()) {
                hasRestrictedEnchantment = true;
                serverPlayer.sendMessage(Text.literal("§6[simpleskills]§f You need ENCHANTING level "
                        + requirement.getLevel() + " to apply " + enchantmentId.getPath()
                        + " level " + enchantmentLevel + "!"), true);
                break;
            }
        }

        if (hasRestrictedEnchantment) {
            this.levelCost.set(9999);
            outputSlot.setStack(ItemStack.EMPTY);
            return;
        }

        // ================================
        // 2. Smithing repair scaling with new tier system
        // ================================
        if (this.repairItemUsage > 0 && !inputStack.isEmpty() && !materialStack.isEmpty()
                && inputStack.isDamageable() && inputStack.canRepairWith(materialStack)) {

            int smithingLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.SMITHING);
            float repairFraction = calculateRepairEfficiency(smithingLevel);

            int maxDamage = inputStack.getMaxDamage();
            int inputDamage = inputStack.getDamage();
            int repairPerMaterial = Math.round(maxDamage * repairFraction);

            int repaired = Math.min(inputDamage, this.repairItemUsage * repairPerMaterial);
            int newDamage = inputDamage - repaired;

            this.durabilityRepaired = repaired;
            outputStack.setDamage(newDamage);

            // keep repairItemUsage aligned with scaling
            this.repairItemUsage = (int) Math.ceil((double) repaired / repairPerMaterial);

            // keep repair cost at 0 and level cost at 1
            outputStack.set(DataComponentTypes.REPAIR_COST, 0);
            this.levelCost.set(1);

            // Determine tier name for logging
            String tierName;
            if (smithingLevel >= 99) tierName = "Grandmaster";
            else if (smithingLevel >= 75) tierName = "Expert";
            else if (smithingLevel >= 50) tierName = "Artisan";
            else if (smithingLevel >= 25) tierName = "Journeyman";
            else tierName = "Novice";

            Simpleskills.LOGGER.debug(
                    "Scaled material repair for {} ({} lvl {}): efficiency {}%, repaired {}, usage {}, damage {} -> {}",
                    serverPlayer.getName().getString(), tierName, smithingLevel, repairFraction * 100,
                    repaired, this.repairItemUsage, inputDamage, newDamage
            );
        }
    }

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void onTakeOutputHead(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        ItemStack input1 = handler.getSlot(0).getStack();
        ItemStack input2 = handler.getSlot(1).getStack();

        // Detect material repair
        boolean isMaterialRepair = !input1.isEmpty() && !input2.isEmpty() &&
                input1.getItem() == stack.getItem() &&
                input1.getDamage() > stack.getDamage() &&
                this.repairItemUsage > 0;

        // Detect enchantment combining
        boolean isEnchantCombining = !input1.isEmpty() && !input2.isEmpty() &&
                input2.contains(DataComponentTypes.STORED_ENCHANTMENTS) &&
                !stack.getEnchantments().getEnchantments().isEmpty();

        if (isMaterialRepair) {
            Identifier materialId = serverPlayer.getEntityWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.ITEM)
                    .getId(input2.getItem());
            if (materialId == null) {
                Simpleskills.LOGGER.warn("Invalid material item {} in slot 1 for player {}", input2.getItem(), serverPlayer.getName().getString());
                return;
            }
            String action = "repair:" + materialId.toString();
            if (!ConfigManager.getSmithingXPMap().containsKey(action)) {
                Simpleskills.LOGGER.debug("No XP multiplier defined for {} in smithing_xp.json, skipping XP for player {}", action, serverPlayer.getName().getString());
                this.durabilityRepaired = 0;
                return;
            }
            float xpMultiplier = ConfigManager.getSmithingXP(action, Skills.SMITHING);
            int playerSmithingLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.SMITHING);
            float skillScaling = 0.5f + 1.5f * (playerSmithingLevel / 99.0f);
            int smithingXP = Math.round(this.durabilityRepaired * xpMultiplier * skillScaling*10);
            if (smithingXP > 0) {
                XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, smithingXP);
                Simpleskills.LOGGER.debug(
                        "Granted {} Smithing XP for material repair with {} (durability {}, multiplier {}, scaling {}) by player {} (level {})",
                        smithingXP, materialId, this.durabilityRepaired, xpMultiplier, skillScaling,
                        serverPlayer.getName().getString(), playerSmithingLevel
                );
            }
            this.durabilityRepaired = 0;
        } else if (isEnchantCombining) {
            // Grant enchanting XP based on vanilla level cost
            int enchantingXP = this.levelCost.get();
            if (enchantingXP > 1) {
                XPManager.addXPWithNotification(serverPlayer, Skills.ENCHANTING, enchantingXP*100);
                Simpleskills.LOGGER.debug(
                        "Granted {} Enchanting XP for combining enchantments by player {} (level {})",
                        enchantingXP, serverPlayer.getName().getString(),
                        XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.ENCHANTING)
                );
            }
        }
    }
}