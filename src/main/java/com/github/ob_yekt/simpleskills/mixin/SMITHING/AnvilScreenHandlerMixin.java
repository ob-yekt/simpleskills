package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.github.ob_yekt.simpleskills.utils.AnvilScreenHandlerAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler implements AnvilScreenHandlerAccessor {
    @Final
    @Shadow
    private Property levelCost;
    @Shadow
    private int repairItemUsage;
    @Shadow
    @Nullable
    private String newItemName;
    @Unique
    private int durabilityRepaired;
    @Unique
    private boolean simpleskills$isPureRepair = false;

    protected AnvilScreenHandlerMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context, getForgingSlotsManager());
    }

    @Unique
    private static ForgingSlotsManager getForgingSlotsManager() {
        return ForgingSlotsManager.builder()
                .input(0, 27, 47, stack -> true)
                .input(1, 76, 47, stack -> true)
                .output(2, 134, 47)
                .build();
    }

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    protected void simpleskills_canTakeOutput(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        // If it's a pure repair, we set the cost to -1 to hide the client text, so we must allow taking the output.
        if (simpleskills$isPureRepair) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void simpleskills_handlePureRepair(CallbackInfo ci) {
        ItemStack input1 = this.input.getStack(0);
        ItemStack input2 = this.input.getStack(1);

        // Reset state
        this.simpleskills$isPureRepair = false;
        this.durabilityRepaired = 0;

        if (input1.isEmpty() || !input1.isDamageable() || input1.getDamage() <= 0) {
            return;
        }

        boolean isMaterialRepair = input1.canRepairWith(input2);
        boolean isItemCombinationRepair = input1.isOf(input2.getItem()) && input2.isDamageable();

        // Exclude enchanted books unless we are strictly repairing
        if (input2.isOf(Items.ENCHANTED_BOOK) && !isMaterialRepair) {
            return;
        }

        // If neither repair type, let vanilla handle it (likely enchanting)
        if (!isMaterialRepair && !isItemCombinationRepair) {
            return;
        }

        // If enchantments are being combined/applied, it's not a PURE repair, let vanilla take over
        if (hasNewEnchantsApplied(input1, input2)) {
            return;
        }

        // --- PURE REPAIR CASE: Skip vanilla logic ---
        this.simpleskills$isPureRepair = true;
        ci.cancel();

        ItemStack resultStack = input1.copy();
        int maxDamage = resultStack.getMaxDamage();
        int initialDamage = resultStack.getDamage();
        this.repairItemUsage = 0; // units of input2 used

        if (isMaterialRepair) {
            int smithingLevel = XPManager.getSkillLevel(this.player.getUuidAsString(), Skills.SMITHING);
            float efficiency = calculateRepairEfficiency(smithingLevel);

            // Calculate repair amount based on efficiency (vanilla is typically 25% of max damage per material unit)
            // scaledRepair = amount of damage one unit of material repairs
            float scaledRepair = maxDamage * efficiency;

            int unitsAvailable = input2.getCount();
            float damageToRepair = (float)initialDamage;

            // calculate how many material units are needed
            float unitsNeededF = damageToRepair / scaledRepair;
            int unitsNeeded = (int) Math.ceil(unitsNeededF);

            this.repairItemUsage = Math.min(unitsAvailable, unitsNeeded);

            // total durability restored, cannot exceed current damage
            this.durabilityRepaired = Math.min((int) (this.repairItemUsage * scaledRepair), initialDamage);

        } else if (isItemCombinationRepair) {
            // Vanilla Item Combination Repair Logic
            // Input 1 is the main item, Input 2 is the repair item (same item type)
            // Repair amount = durability of input2 + 12% of max durability
            int durabilityFromInput2 = maxDamage - input2.getDamage();
            int bonusRepair = maxDamage * 12 / 100;
            int totalRepair = durabilityFromInput2 + bonusRepair;

            // Repair is capped at the current damage of Input 1
            this.durabilityRepaired = Math.min(totalRepair, initialDamage);
            this.repairItemUsage = 1; // Always uses 1 item
        }

        // If no durability was repaired and no name was set, clear output
        boolean hasNameChange = this.newItemName != null && !StringHelper.isBlank(this.newItemName) && !this.newItemName.equals(input1.getName().getString());

        if (this.durabilityRepaired <= 0 && !hasNameChange) {
            this.output.setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            this.sendContentUpdates();
            return;
        }

        // Apply repair and name change
        resultStack.setDamage(initialDamage - this.durabilityRepaired);

        if (hasNameChange) {
            resultStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(this.newItemName));
        } else if (input1.contains(DataComponentTypes.CUSTOM_NAME)) {
            // If the user cleared the name, apply it (cost logic for this is handled by vanilla fallthrough)
            // However, since we are canceling CI, we must explicitly remove it if the original item had one
            resultStack.remove(DataComponentTypes.CUSTOM_NAME);
        }

        // Set level cost to -1 to hide the cost message on the client!
        this.levelCost.set(-1);
        this.output.setStack(0, resultStack);
        this.sendContentUpdates(); // sync to client immediately
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void simpleskills_checkEnchantmentRequirements(CallbackInfo ci) {
        if (this.simpleskills$isPureRepair) {
            return; // skip checks for repair
        }

        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) return;

        Slot outputSlot = this.getSlot(2);
        ItemStack outputStack = outputSlot.getStack();
        if (outputStack.isEmpty()) return;

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

            if (requirement != null && enchantmentLevel >= requirement.getEnchantmentLevel() && playerEnchantingLevel < requirement.getLevel()) {
                hasRestrictedEnchantment = true;
                serverPlayer.sendMessage(Text.literal("§6[simpleskills]§f You need ENCHANTING level " + requirement.getLevel() + " to apply " + enchantmentId.getPath() + " level " + enchantmentLevel + "!"), true);
                break;
            }
        }

        if (hasRestrictedEnchantment) {
            outputSlot.setStack(ItemStack.EMPTY);
        }
    }

    // NEW INJECTION: Intercepts the player level deduction in onTakeOutput.
    @Inject(method = "onTakeOutput",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;addExperienceLevels(I)V",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    private void simpleskills_preventLevelChangeOnPureRepair(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        // If it's a pure repair, the cost is -1. We must cancel the level application
        // to prevent the player from gaining a level (losing -1 levels).
        if (this.simpleskills$isPureRepair && this.levelCost.get() == -1) {
            ci.cancel();
        }
    }


    // MODIFIED REDIRECT: Control Anvil Damage to prevent ALL damage.
    @Redirect(method = "onTakeOutput", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandlerContext;run(Ljava/util/function/BiConsumer;)V"))
    private void simpleskills_preventAllAnvilDamage(ScreenHandlerContext instance, java.util.function.BiConsumer<net.minecraft.world.World, net.minecraft.util.math.BlockPos> action) {
        // Instead of running the vanilla 'action' (which handles damage),
        // we always run a simpler action that only syncs the sound/particle event (1030).
        // This effectively makes the anvil block indestructible.
        instance.run((world, pos) -> {
            // Event 1030 is the sound/particle effect without damage/degradation
            world.syncWorldEvent(1030, pos, 0);
        });
    }

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void simpleskills_grantXpOnTakeOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        if (this.simpleskills$isPureRepair) {
            // Grant Smithing XP for the repair
            grantSmithingXP(serverPlayer, this.input.getStack(1));
            this.durabilityRepaired = 0;
            this.simpleskills$isPureRepair = false;
            // Note: repairItemUsage is handled in the original onTakeOutput after this head inject
            return;
        }

        // The remaining logic below is for enchantment applications/renaming (not pure repair)
        if (this.levelCost.get() > 0 && hasNewEnchantsApplied(this.input.getStack(0), stack, this.input.getStack(1))) {
            int enchantingXP = this.levelCost.get();
            XPManager.addXPWithNotification(serverPlayer, Skills.ENCHANTING, enchantingXP * 500);
        }

        this.durabilityRepaired = 0;
        this.simpleskills$isPureRepair = false;
    }

    // === Utilities ===

    /**
     * Checks if applying input2 (material or item) to input1 (tool) would result in a new/higher enchantment level.
     * Used to determine if the operation is "pure repair" or "enchanting/combining".
     */
    @Unique
    private boolean hasNewEnchantsApplied(ItemStack baseItem, ItemStack materialItem) {
        ItemEnchantmentsComponent enchantsOnMaterial;
        // Check for stored enchantments (books) or regular enchantments (tools)
        if (materialItem.isOf(Items.ENCHANTED_BOOK)) {
            enchantsOnMaterial = materialItem.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        } else {
            enchantsOnMaterial = materialItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        }

        // If the material item has no enchantments, we can't be applying new ones
        if (enchantsOnMaterial.isEmpty()) return false;

        ItemEnchantmentsComponent enchantsOnBase = EnchantmentHelper.getEnchantments(baseItem);

        for (var entry : enchantsOnMaterial.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantmentEntry = entry.getKey();

            // Check if the enchantment is applicable to the base item
            if (enchantmentEntry.value().isAcceptableItem(baseItem)) {
                int baseLevel = enchantsOnBase.getLevel(enchantmentEntry);
                int materialLevel = entry.getIntValue();

                // If the material level is higher, a new level is being applied
                if (materialLevel > baseLevel) return true;

                // If levels are equal, a combination can still increase the level (e.g., Sharpness I + Sharpness I = Sharpness II)
                // This is determined by vanilla logic where a combination is considered "new" if maxLevel > currentLevel.
                // We check if the enchantment has room to upgrade.
                if (materialLevel == baseLevel && materialLevel < enchantmentEntry.value().getMaxLevel()) return true;
            }
        }
        return false;
    }

    // This method seems to check if the output has higher enchantments than the input.
    // It's used for Enchanting XP calculation and is slightly different from the check above. I'll leave it as is.
    @Unique
    private boolean hasNewEnchantsApplied(ItemStack input, ItemStack output, ItemStack material) {
        ItemEnchantmentsComponent enchantsOnMaterial;
        if (material.isOf(Items.ENCHANTED_BOOK)) {
            enchantsOnMaterial = material.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        } else {
            enchantsOnMaterial = material.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        }
        // If it's a non-enchanted book material, and the items are not the same, return false.
        if (enchantsOnMaterial.isEmpty() && !material.isOf(input.getItem())) return false;
        var outputEnchants = output.getEnchantments();
        var inputEnchants = input.getEnchantments();
        for (RegistryEntry<Enchantment> enchantEntry : outputEnchants.getEnchantments()) {
            int outputLevel = outputEnchants.getLevel(enchantEntry);
            int inputLevel = inputEnchants.getLevel(enchantEntry);
            if (outputLevel > inputLevel) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void grantSmithingXP(ServerPlayerEntity serverPlayer, ItemStack material) {
        if (this.durabilityRepaired <= 0) return;

        // Try getting XP action for the material used for repair
        Identifier materialId = Registries.ITEM.getId(material.getItem());
        String action = "repair:" + materialId;

        // If no XP rule for material, try getting XP action for the item being repaired
        if (!ConfigManager.getSmithingXPMap().containsKey(action)) {
            action = "repair:" + Registries.ITEM.getId(this.input.getStack(0).getItem());
            if (!ConfigManager.getSmithingXPMap().containsKey(action)) {
                return;
            }
        }

        float xpMultiplier = ConfigManager.getSmithingXP(action, Skills.SMITHING);
        int smithingXP = Math.round(this.durabilityRepaired * xpMultiplier);
        if (smithingXP > 0) {
            XPManager.addXPWithNotification(serverPlayer, Skills.SMITHING, smithingXP);
        }
    }

    @Unique
    private float calculateRepairEfficiency(int smithingLevel) {
        if (smithingLevel >= 99) {
            return 1.0f; // 100%
        } else if (smithingLevel >= 75) {
            return 0.55f; // 55%
        } else if (smithingLevel >= 50) {
            return 0.45f; // 45%
        } else if (smithingLevel >= 25) {
            return 0.35f; // 35%
        } else {
            return 0.25f; // 25% (vanilla baseline)
        }
    }


    @Override
    public int simpleskills$getRepairItemUsage() { return this.repairItemUsage; }
    @Override
    public int simpleskills$getDurabilityRepaired() { return this.durabilityRepaired; }
    @Override
    public int simpleskills$getLevelCost() { return this.levelCost.get(); }
}
