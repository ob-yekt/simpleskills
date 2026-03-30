package com.github.ob_yekt.simpleskills.mixin.SMITHING;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.github.ob_yekt.simpleskills.utils.AnvilScreenHandlerAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

@Mixin(AnvilMenu.class)
public abstract class AnvilScreenHandlerMixin extends ItemCombinerMenu implements AnvilScreenHandlerAccessor {
    @Final
    @Shadow
    private DataSlot cost;
    @Shadow
    private int repairItemCountCost;
    @Shadow
    @Nullable
    private String itemName;
    @Unique
    private int durabilityRepaired;
    @Unique
    private boolean simpleskills$isPureRepair = false;

    protected AnvilScreenHandlerMixin(MenuType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context) {
        super(type, syncId, playerInventory, context, getForgingSlotsManager());
    }

    @Unique
    private static ItemCombinerMenuSlotDefinition getForgingSlotsManager() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(0, 27, 47, stack -> true)
                .withSlot(1, 76, 47, stack -> true)
                .withResultSlot(2, 134, 47)
                .build();
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    protected void simpleskills_canTakeOutput(Player player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        // If it's a pure repair, we set the cost to -1 to hide the client text, so we must allow taking the output.
        if (simpleskills$isPureRepair) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void simpleskills_handlePureRepair(CallbackInfo ci) {
        ItemStack input1 = this.inputSlots.getItem(0);
        ItemStack input2 = this.inputSlots.getItem(1);

        // Reset state
        this.simpleskills$isPureRepair = false;
        this.durabilityRepaired = 0;

        if (input1.isEmpty() || !input1.isDamageableItem() || input1.getDamageValue() <= 0) {
            return;
        }

        boolean isMaterialRepair = input1.isValidRepairItem(input2);
        boolean isItemCombinationRepair = input1.is(input2.getItem()) && input2.isDamageableItem();

        // Exclude enchanted books unless we are strictly repairing
        if (input2.is(Items.ENCHANTED_BOOK) && !isMaterialRepair) {
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
        int initialDamage = resultStack.getDamageValue();
        this.repairItemCountCost = 0; // units of input2 used

        if (isMaterialRepair) {
            int smithingLevel = XPManager.getSkillLevel(this.player.getStringUUID(), Skills.SMITHING);
            float efficiency = calculateRepairEfficiency(smithingLevel);

            // Calculate repair amount based on efficiency (vanilla is typically 25% of max damage per material unit)
            // scaledRepair = amount of damage one unit of material repairs
            float scaledRepair = maxDamage * efficiency;

            int unitsAvailable = input2.getCount();
            float damageToRepair = (float)initialDamage;

            // calculate how many material units are needed
            float unitsNeededF = damageToRepair / scaledRepair;
            int unitsNeeded = (int) Math.ceil(unitsNeededF);

            this.repairItemCountCost = Math.min(unitsAvailable, unitsNeeded);

            // total durability restored, cannot exceed current damage
            this.durabilityRepaired = Math.min((int) (this.repairItemCountCost * scaledRepair), initialDamage);

        } else if (isItemCombinationRepair) {
            // Vanilla Item Combination Repair Logic
            // Input 1 is the main item, Input 2 is the repair item (same item type)
            // Repair amount = durability of input2 + 12% of max durability
            int durabilityFromInput2 = maxDamage - input2.getDamageValue();
            int bonusRepair = maxDamage * 12 / 100;
            int totalRepair = durabilityFromInput2 + bonusRepair;

            // Repair is capped at the current damage of Input 1
            this.durabilityRepaired = Math.min(totalRepair, initialDamage);
            this.repairItemCountCost = 1; // Always uses 1 item
        }

        // If no durability was repaired and no name was set, clear output
        boolean hasNameChange = this.itemName != null && !StringUtil.isBlank(this.itemName) && !this.itemName.equals(input1.getHoverName().getString());

        if (this.durabilityRepaired <= 0 && !hasNameChange) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
            this.broadcastChanges();
            return;
        }

        // Apply repair and name change
        resultStack.setDamageValue(initialDamage - this.durabilityRepaired);

        if (hasNameChange) {
            resultStack.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
        } else if (input1.has(DataComponents.CUSTOM_NAME)) {
            // If the user cleared the name, apply it (cost logic for this is handled by vanilla fallthrough)
            // However, since we are canceling CI, we must explicitly remove it if the original item had one
            resultStack.remove(DataComponents.CUSTOM_NAME);
        }

        // Set level cost to -1 to hide the cost message on the client!
        this.cost.set(-1);
        this.resultSlots.setItem(0, resultStack);
        this.broadcastChanges(); // sync to client immediately
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void simpleskills_checkEnchantmentRequirements(CallbackInfo ci) {
        if (this.simpleskills$isPureRepair) {
            return; // skip checks for repair
        }

        if (!(this.player instanceof ServerPlayer serverPlayer)) return;

        Slot outputSlot = this.getSlot(2);
        ItemStack outputStack = outputSlot.getItem();
        if (outputStack.isEmpty()) return;

        boolean hasRestrictedEnchantment = false;
        for (Holder<Enchantment> enchantmentEntry : outputStack.getEnchantments().keySet()) {
            Enchantment enchantment = enchantmentEntry.value();
            int enchantmentLevel = outputStack.getEnchantments().getLevel(enchantmentEntry);
            Identifier enchantmentId = serverPlayer.level()
                    .registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getKey(enchantment);
            if (enchantmentId == null) continue;

            // Pass the enchantment level to check for the appropriate requirement
            SkillRequirement requirement = ConfigManager.getEnchantmentRequirement(enchantmentId.toString(), enchantmentLevel);
            int playerEnchantingLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), Skills.ENCHANTING);

            if (requirement != null && playerEnchantingLevel < requirement.getLevel()) {
                hasRestrictedEnchantment = true;
                String enchantName = enchantmentId.getPath().replace("_", " ");
                serverPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f You need ENCHANTING level " +
                        requirement.getLevel() + " to apply " + enchantName + " " + enchantmentLevel + "!"), true);
                break;
            }
        }

        if (hasRestrictedEnchantment) {
            outputSlot.setByPlayer(ItemStack.EMPTY);
        }
    }

    // NEW INJECTION: Intercepts the player level deduction in onTakeOutput.
    @Inject(method = "onTake",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    private void simpleskills_preventLevelChangeOnPureRepair(Player player, ItemStack stack, CallbackInfo ci) {
        // If it's a pure repair, the cost is -1. We must cancel the level application
        // to prevent the player from gaining a level (losing -1 levels).
        if (this.simpleskills$isPureRepair && this.cost.get() == -1) {
            ci.cancel();
        }
    }


    // MODIFIED REDIRECT: Control Anvil Damage to prevent ALL damage.
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void simpleskills_preventAllAnvilDamage(ContainerLevelAccess instance, java.util.function.BiConsumer<net.minecraft.world.level.Level, net.minecraft.core.BlockPos> action) {
        // Instead of running the vanilla 'action' (which handles damage),
        // we always run a simpler action that only syncs the sound/particle event (1030).
        // This effectively makes the anvil block indestructible.
        instance.execute((world, pos) -> {
            // Event 1030 is the sound/particle effect without damage/degradation
            world.levelEvent(1030, pos, 0);
        });
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void simpleskills_grantXpOnTakeOutput(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (this.simpleskills$isPureRepair) {
            // Grant Smithing XP for the repair
            grantSmithingXP(serverPlayer, this.inputSlots.getItem(1));
            this.durabilityRepaired = 0;
            this.simpleskills$isPureRepair = false;
            // Note: repairItemUsage is handled in the original onTakeOutput after this head inject
            return;
        }

        // The remaining logic below is for enchantment applications/renaming (not pure repair)
        if (this.cost.get() > 0 && hasNewEnchantsApplied(this.inputSlots.getItem(0), stack, this.inputSlots.getItem(1))) {
            int enchantingXP = this.cost.get();
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
        ItemEnchantments enchantsOnMaterial;
        // Check for stored enchantments (books) or regular enchantments (tools)
        if (materialItem.is(Items.ENCHANTED_BOOK)) {
            enchantsOnMaterial = materialItem.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        } else {
            enchantsOnMaterial = materialItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }

        // If the material item has no enchantments, we can't be applying new ones
        if (enchantsOnMaterial.isEmpty()) return false;

        ItemEnchantments enchantsOnBase = EnchantmentHelper.getEnchantmentsForCrafting(baseItem);

        for (var entry : enchantsOnMaterial.entrySet()) {
            Holder<Enchantment> enchantmentEntry = entry.getKey();

            // Check if the enchantment is applicable to the base item
            if (enchantmentEntry.value().canEnchant(baseItem)) {
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
        ItemEnchantments enchantsOnMaterial;
        if (material.is(Items.ENCHANTED_BOOK)) {
            enchantsOnMaterial = material.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        } else {
            enchantsOnMaterial = material.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        // If it's a non-enchanted book material, and the items are not the same, return false.
        if (enchantsOnMaterial.isEmpty() && !material.is(input.getItem())) return false;
        var outputEnchants = output.getEnchantments();
        var inputEnchants = input.getEnchantments();
        for (Holder<Enchantment> enchantEntry : outputEnchants.keySet()) {
            int outputLevel = outputEnchants.getLevel(enchantEntry);
            int inputLevel = inputEnchants.getLevel(enchantEntry);
            if (outputLevel > inputLevel) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void grantSmithingXP(ServerPlayer serverPlayer, ItemStack material) {
        if (this.durabilityRepaired <= 0) return;

        // Try getting XP action for the material used for repair
        Identifier materialId = BuiltInRegistries.ITEM.getKey(material.getItem());
        String action = "repair:" + materialId;

        // If no XP rule for material, try getting XP action for the item being repaired
        if (!ConfigManager.getSmithingXPMap().containsKey(action)) {
            action = "repair:" + BuiltInRegistries.ITEM.getKey(this.inputSlots.getItem(0).getItem());
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
    public int simpleskills$getRepairItemUsage() { return this.repairItemCountCost; }
    @Override
    public int simpleskills$getDurabilityRepaired() { return this.durabilityRepaired; }
    @Override
    public int simpleskills$getLevelCost() { return this.cost.get(); }
}
