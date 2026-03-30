package com.github.ob_yekt.simpleskills.events;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.*;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import java.util.Objects;

/**
 * Consolidates all event handlers for the simpleskills mod (block, combat, join/leave, prayer).
 */
public class EventHandlers {
    public static void registerAll() {
        registerBlockHandlers();
        registerCombatHandlers();
        registerJoinLeaveHandlers();
        registerPrayerHandlers();
    }

// Replace the farming-related methods in EventHandlers class:

    private static void registerBlockHandlers() {
        // BEFORE block break event
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }

            String blockTranslationKey = state.getBlock().getDescriptionId();
            String toolId = "minecraft:air"; // Default if empty hand
            ItemStack mainHandStack = serverPlayer.getMainHandItem();
            if (!mainHandStack.isEmpty()) {
                toolId = BuiltInRegistries.ITEM.getKey(mainHandStack.getItem()).toString();
            }

            // --- 1) Check tool requirement first ---
            SkillRequirement requirement = ConfigManager.getToolRequirement(toolId);
            if (requirement != null) {
                Skills requiredSkill = requirement.getSkill();
                int requiredLevel = requirement.getLevel();
                int requiredPrestige = requirement.getRequiredPrestige();

                int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), requiredSkill);
                if (playerLevel < requiredLevel) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format(
                            "§6[simpleskills]§f You need %s level %d to use this tool!",
                            requiredSkill.getDisplayName(), requiredLevel
                    )), true);
                    return false; // Block breaking canceled
                }

                // Prestige gate (optional)
                if (requiredPrestige > 0) {
                    int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                    if (playerPrestige < requiredPrestige) {
                        serverPlayer.sendSystemMessage(Component.literal(String.format(
                                "§6[simpleskills]§f You need Prestige ★%d to use this tool!",
                                requiredPrestige
                        )), true);
                        return false;
                    }
                }
            }

            // --- 2) Then check if block itself has a skill requirement ---
            // Crops bypass tool requirement check
            if (ConfigManager.isCropBlock(blockTranslationKey)) {
                return true;
            }

            return true;
        });

        // AFTER block break event
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            String blockTranslationKey = state.getBlock().getDescriptionId();

            // Check for Silk Touch on ores, melons (before doing anything else)
            if ((blockTranslationKey.contains("_ore") || blockTranslationKey.contains("melon")) && hasSilkTouch(serverPlayer)) {
                Simpleskills.LOGGER.debug("No XP granted for {} to player {} due to Silk Touch", blockTranslationKey, serverPlayer.getName().getString());
                return;
            }

            // Check if it's a configured farming block
            if (ConfigManager.isCropBlock(blockTranslationKey)) {
                grantFarmingXP((ServerLevel) world, serverPlayer, pos, state, blockTranslationKey);
                return;
            }

            // Determine skill: prefer explicit config; otherwise infer by mineable tool tag
            Skills relevantSkill = ConfigManager.getBlockSkill(blockTranslationKey);
            Integer overrideXP = ConfigManager.getBlockXPOverride(blockTranslationKey);
            if (relevantSkill == null) {
                if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                    relevantSkill = Skills.MINING;
                } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                    relevantSkill = Skills.EXCAVATING;
                } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
                    relevantSkill = Skills.WOODCUTTING;
                } else {
                    return;
                }
            }

            // Grant XP for non-farming skills
            if (relevantSkill != Skills.FARMING) {
                int xp;
                if (overrideXP != null) {
                    xp = overrideXP;
                } else {
                    // Hardness-based default: hardness 1.5 -> 100 XP
                    float hardness = state.getDestroySpeed(world, pos);
                    xp = Math.max(0, Math.round(hardness * (100.0f / 1.5f)));
                }
                XPManager.addXPWithNotification(serverPlayer, relevantSkill, xp);
            }
        });
    }

    /**
     * Grants farming XP for harvesting crops based on configuration.
     * Now fully config-driven to support custom crops from other mods.
     */
    private static void grantFarmingXP(ServerLevel world, ServerPlayer serverPlayer, BlockPos pos, BlockState state, String blockTranslationKey) {
        ConfigManager.CropConfig cropConfig = ConfigManager.getCropConfig(blockTranslationKey);
        if (cropConfig == null) {
            return;
        }

        boolean isMatured = false;

        // Check maturity based on configured age property
        switch (cropConfig.ageProperty()) {
            case "AGE_7":
                if (state.hasProperty(BlockStateProperties.AGE_7)) {
                    int age = state.getValue(BlockStateProperties.AGE_7);
                    isMatured = (age >= cropConfig.maturityAge());
                }
                break;
            case "AGE_3":
                if (state.hasProperty(BlockStateProperties.AGE_3)) {
                    int age = state.getValue(BlockStateProperties.AGE_3);
                    isMatured = (age >= cropConfig.maturityAge());
                }
                break;
            case "AGE_2":
                if (state.hasProperty(BlockStateProperties.AGE_2)) {
                    int age = state.getValue(BlockStateProperties.AGE_2);
                    isMatured = (age >= cropConfig.maturityAge());
                }
                break;
            case "AGE_5":
                if (state.hasProperty(BlockStateProperties.AGE_5)) {
                    int age = state.getValue(BlockStateProperties.AGE_5);
                    isMatured = (age >= cropConfig.maturityAge());
                }
                break;
            case "AGE_25":
                if (state.hasProperty(BlockStateProperties.AGE_25)) {
                    int age = state.getValue(BlockStateProperties.AGE_25);
                    isMatured = (age >= cropConfig.maturityAge());
                }
                break;
            case "NONE":
                // No age property - always mature (e.g., melon blocks)
                isMatured = true;
                break;
            default:
                Simpleskills.LOGGER.warn("Unknown age property '{}' for crop {}", cropConfig.ageProperty(), blockTranslationKey);
                return;
        }

        if (!isMatured) {
            return;
        }

        int xp = cropConfig.xp();
        XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        applyBonusDrops(world, serverPlayer, pos, state, blockTranslationKey);
        Simpleskills.LOGGER.debug("Granted {} XP for harvesting {} to player {}", xp, blockTranslationKey, serverPlayer.getName().getString());
    }

    private static void applyBonusDrops(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, String blockTranslationKey) {
        // Calculate bonus drop chance: 1% per farming level, capped at 99%
        int farmingLevel = XPManager.getSkillLevel(player.getStringUUID(), Skills.FARMING);
        double dropChance = Math.min(farmingLevel, 99) / 100.0;

        if (world.getRandom().nextDouble() < dropChance) {
            spawnBonusDrops(world, pos, state);
            Simpleskills.LOGGER.debug("Granted bonus drops to {} for {} (farming level: {})",
                    player.getName().getString(), blockTranslationKey, farmingLevel);
        }
    }

    private static void spawnBonusDrops(ServerLevel world, BlockPos pos, BlockState state) {
        // Get the drops that would normally be produced
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, world, pos, null);

        Vec3 dropPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            ItemStack bonus = drop.copy();
            ItemEntity itemEntity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, bonus);

            // Add slight random velocity for visual effect
            itemEntity.setDeltaMovement(
                    (world.getRandom().nextDouble() - 0.5) * 0.1,
                    world.getRandom().nextDouble() * 0.1,
                    (world.getRandom().nextDouble() - 0.5) * 0.1
            );

            world.addFreshEntity(itemEntity);
        }
    }

    private static void registerCombatHandlers() {
        // Prevent melee attacks if weapon requirement not met
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof LivingEntity)) {
                return InteractionResult.PASS;
            }

            ItemStack weapon = serverPlayer.getItemInHand(hand);
            if (weapon.isEmpty()) {
                return InteractionResult.PASS;
            }

            String weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
            SkillRequirement requirement = ConfigManager.getWeaponRequirement(weaponId);
            if (requirement == null) {
                return InteractionResult.PASS;
            }

            Skills skill = requirement.getSkill();
            int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), skill);
            if (playerLevel < requirement.getLevel()) {
                serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this weapon!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                return InteractionResult.FAIL;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                if (playerPrestige < requiredPrestige) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use this weapon!",
                            requiredPrestige)), true);
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.PASS;
        });

        // Prevent ranged weapon use (bow/crossbow/trident throw) if requirement not met
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                return InteractionResult.PASS;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            SkillRequirement requirement = ConfigManager.getWeaponRequirement(itemId);
            if (requirement == null) {
                return InteractionResult.PASS;
            }

            Skills skill = requirement.getSkill();
            int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), skill);
            if (playerLevel < requirement.getLevel()) {
                serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need %s level %d to use this weapon!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                return InteractionResult.FAIL;
            }

            int requiredPrestige = requirement.getRequiredPrestige();
            if (requiredPrestige > 0) {
                int playerPrestige = DatabaseManager.getInstance().getPrestige(player.getStringUUID());
                if (playerPrestige < requiredPrestige) {
                    serverPlayer.sendSystemMessage(Component.literal(String.format("§6[simpleskills]§f You need Prestige ★%d to use this weapon!",
                            requiredPrestige)), true);
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.PASS;
        });

        // Grant XP on successful damage for Slaying, Ranged, and Defense
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.level().isClientSide() || amount <= 0) {
                return true;
            }

            // Exclude explosion damage
            if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION) ||
                    source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)) {
                return true;
            }

            JsonObject config = ConfigManager.getCombatConfig();
            float xpPerDamageSlaying = config.get("slaying_xp_per_damage") != null ? config.get("slaying_xp_per_damage").getAsFloat() : 100.0f;
            float xpPerDamageRanged = config.get("ranged_xp_per_damage") != null ? config.get("ranged_xp_per_damage").getAsFloat() : 100.0f;
            float minDamageSlaying = config.get("slaying_min_damage_threshold") != null ? config.get("slaying_min_damage_threshold").getAsFloat() : 2.0f;
            float minDamageRanged = config.get("ranged_min_damage_threshold") != null ? config.get("ranged_min_damage_threshold").getAsFloat() : 2.0f;

            // Slaying/Ranged: Player dealing damage to non-player mob
            if (entity instanceof LivingEntity target && !(target instanceof Player) && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
                ServerPlayer attacker = null;
                Skills skill = null;
                float xpMultiplier = 0.0f;
                float minDamage = 0.0f;
                int xp = 0;

                // Melee (Slaying) attack
                if (source.getEntity() instanceof ServerPlayer) {
                    attacker = (ServerPlayer) source.getEntity();
                    ItemStack weapon = attacker.getMainHandItem();
                    String weaponId = weapon.isEmpty() ? "minecraft:empty" : BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
                    SkillRequirement requirement = ConfigManager.getWeaponRequirement(weaponId);
                    skill = (requirement != null && requirement.getSkill() == Skills.RANGED) ? Skills.RANGED : Skills.SLAYING;
                    xpMultiplier = (skill == Skills.RANGED) ? xpPerDamageRanged : xpPerDamageSlaying;
                    minDamage = (skill == Skills.RANGED) ? minDamageRanged : minDamageSlaying;
                    xp = (int) (amount * xpMultiplier);
                }
                // Ranged attack
                else if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer) {
                    attacker = (ServerPlayer) projectile.getOwner();
                    skill = Skills.RANGED;
                    xpMultiplier = xpPerDamageRanged;
                    minDamage = minDamageRanged;
                    xp = (int) (amount * xpMultiplier);
                }

                if (attacker != null && amount >= minDamage) {
                    XPManager.addXPWithNotification(attacker, skill, xp);
                    Simpleskills.LOGGER.debug("Granted {} XP in {} to {} for dealing {} damage to {}", xp, skill.getId(), attacker.getName().getString(), amount, target.getType().toString());
                }
            }

            return true; // Allow damage to proceed normally
        });

        // Defense XP: Handle after damage is processed to detect actual blocking/armor absorption
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, originalAmount, actualAmount, blocked) -> {
            if (entity.level().isClientSide()) {
                return;
            }

            // Exclude explosion damage
            if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION) ||
                    source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)) {
                return;
            }

            // Defense: Player taking damage from non-player mob
            if (entity instanceof ServerPlayer defender && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
                JsonObject config = ConfigManager.getCombatConfig();
                float xpPerDamageDefense = config.get("defense_xp_per_damage") != null ? config.get("defense_xp_per_damage").getAsFloat() : 100.0f;
                float minDamageDefense = config.get("defense_min_damage_threshold") != null ? config.get("defense_min_damage_threshold").getAsFloat() : 2.0f;
                float armorMultiplierPerPiece = config.get("defense_xp_armor_multiplier_per_piece") != null ? config.get("defense_xp_armor_multiplier_per_piece").getAsFloat() : 0.25f;
                float shieldXPMultiplier = config.get("defense_shield_xp_multiplier") != null ? config.get("defense_shield_xp_multiplier").getAsFloat() : 0.3f;

                if (originalAmount >= minDamageDefense) {
                    // Check if damage was blocked (shield blocking)
                    boolean wasBlocked = blocked || (originalAmount > actualAmount && defender.isBlocking() &&
                            defender.getUseItem().getItem() == net.minecraft.world.item.Items.SHIELD);

                    if (wasBlocked) {
                        // Shield blocking XP - based on original damage amount
                        int xp = (int) (originalAmount * xpPerDamageDefense * shieldXPMultiplier);
                        XPManager.addXPWithNotification(defender, Skills.DEFENSE, xp);
                        Simpleskills.LOGGER.debug("Granted {} XP in {} to {} for blocking {} damage from {} with shield",
                                xp, Skills.DEFENSE.getId(), defender.getName().getString(), originalAmount, attacker.getType().toString());
                    } else {
                        // Armor absorption XP - check for armor pieces
                        int armorCount = 0;
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            if (slot.isArmor() && !defender.getItemBySlot(slot).isEmpty()) {
                                armorCount++;
                            }
                        }

                        if (armorCount > 0) {
                            float armorMultiplier = armorCount * armorMultiplierPerPiece;
                            int xp = (int) (actualAmount * xpPerDamageDefense * armorMultiplier);
                            XPManager.addXPWithNotification(defender, Skills.DEFENSE, xp);
                            Simpleskills.LOGGER.debug("Granted {} XP in {} to {} for taking {} damage from {} with {} armor pieces",
                                    xp, Skills.DEFENSE.getId(), defender.getName().getString(), actualAmount, attacker.getType().toString(), armorCount);
                        }
                    }
                }
            }
        });
    }

    private static void registerJoinLeaveHandlers() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player == null) {
                Simpleskills.LOGGER.warn("Null player in join event.");
                return;
            }

            String playerUuid = player.getStringUUID();
            String playerName = player.getName().getString();
            DatabaseManager db = DatabaseManager.getInstance();

            // Critical: ensure player exists in DB first
            db.ensurePlayerInitialized(playerUuid);
            db.updatePlayerName(playerUuid, playerName);

            boolean isForceIronman = ConfigManager.isForceIronmanModeEnabled();
            boolean isCurrentlyIronman = db.isPlayerInIronmanMode(playerUuid);

            // FORCE IRONMAN MODE: Make sure it's enabled in DB and applied
            if (isForceIronman) {
                if (!isCurrentlyIronman) {
                    db.setIronmanMode(playerUuid, true);
                    Simpleskills.LOGGER.info("Force-enabled Ironman Mode for {} due to server config.", playerName);
                }
                IronmanManager.applyIronmanMode(player); // This applies attributes, prefix, etc.
            }
            // Normal behavior: only apply if player already has Ironman flag
            else if (isCurrentlyIronman) {
                IronmanManager.applyIronmanMode(player);
            }

            // Always refresh everything (safe even if not Ironman)
            AttributeManager.refreshAllAttributes(player);
            SkillTabMenu.updateTabMenu(player);
            com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(player);

            // Optional: Welcome message for forced Ironman servers
            if (isForceIronman) {
                player.sendSystemMessage(Component.literal("§6[simpleskills]§f §cThis server is running in permanent Ironman Mode.")
                        .withStyle(ChatFormatting.RED), false);
            }

            Simpleskills.LOGGER.debug("Player joined and initialized: {} (Ironman: {}, Forced: {})",
                    playerName, isCurrentlyIronman || isForceIronman, isForceIronman);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player == null) {
                Simpleskills.LOGGER.warn("Null player in disconnect event.");
                return;
            }

            String playerName = player.getName().getString();
            String playerUuid = player.getStringUUID();

            // Clear temporary attributes and tab menu state
            AttributeManager.clearSkillAttributes(player);
            AttributeManager.clearIronmanAttributes(player);
            SkillTabMenu.clearPlayerVisibility(player.getUUID());

            Simpleskills.LOGGER.debug("Player disconnected: {}", playerName);
        });
    }

    private static void registerPrayerHandlers() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (state.is(BlockTags.CANDLES) && state.getValue(BlockStateProperties.LIT)) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                ConfigManager.PrayerSacrifice sacrifice = ConfigManager.getPrayerSacrifice(itemId);

                if (sacrifice == null) {
                    return InteractionResult.PASS;
                }

                SkillRequirement requirement = sacrifice.requirement();
                int playerLevel = XPManager.getSkillLevel(serverPlayer.getStringUUID(), Skills.PRAYER);
                if (playerLevel < requirement.getLevel()) {
                    serverPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f You need Prayer level " + requirement.getLevel() + " to offer this sacrifice!"), true);
                    return InteractionResult.FAIL;
                }

                // Remove all existing Prayer-related status effects
                for (ConfigManager.PrayerSacrifice existingSacrifice : ConfigManager.PRAYER_SACRIFICES.values()) {
                    BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(existingSacrifice.effect())).ifPresent(serverPlayer::removeEffect);
                }

                // Consume item
                stack.shrink(1);

                // Grant XP
                XPManager.addXPWithNotification(serverPlayer, Skills.PRAYER, sacrifice.xp());

                // Apply status effect
                Holder<MobEffect> effectEntry = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(sacrifice.effect())).orElse(null);
                if (effectEntry != null) {
                    serverPlayer.addEffect(new MobEffectInstance(
                            effectEntry,
                            sacrifice.durationTicks(),
                            sacrifice.effectLevel() - 1, // Use effectLevel, subtract 1 for 0-based amplifier
                            sacrifice.isAmbient(),      // Use isAmbient for particle visibility
                            true                       // showIcon
                    ));
                    serverPlayer.sendSystemMessage(Component.literal("§6[simpleskills]§f You offer a sacrifice and gain " + sacrifice.displayName() + "!"), false);
                } else {
                    Simpleskills.LOGGER.warn("Invalid status effect {} for item {} in prayer_sacrifices.json", sacrifice.effect(), itemId);
                }

                // Visual feedback
                ServerLevel serverWorld = (ServerLevel) world;
                serverWorld.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        hitResult.getBlockPos().getX() + 0.4,
                        hitResult.getBlockPos().getY() + 1.0,
                        hitResult.getBlockPos().getZ() + 0.5,
                        20, 0.2, 0.2, 0.2, 0.05
                );
                serverWorld.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.COPPER_FIRE_FLAME,
                        hitResult.getBlockPos().getX() + 0.5,
                        hitResult.getBlockPos().getY() + 1.0,
                        hitResult.getBlockPos().getZ() + 0.4,
                        20, 0.2, 0.2, 0.2, 0.05
                );
                serverWorld.playSound(
                        null,
                        hitResult.getBlockPos(),
                        SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.BLOCKS,
                        1.0f,
                        1.0f
                );

                Simpleskills.LOGGER.debug("Player {} offered {} for {} XP and {} effect (level {}, ambient {})",
                        serverPlayer.getName().getString(), itemId, sacrifice.xp(), sacrifice.displayName(),
                        sacrifice.effectLevel(), sacrifice.isAmbient());
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    private static boolean hasSilkTouch(ServerPlayer player) {
        ItemStack toolStack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        var enchantmentRegistry = Objects.requireNonNull(player.level().getServer())
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> silkTouchEntry = enchantmentRegistry
                .get(Enchantments.SILK_TOUCH)
                .orElse(null);

        if (silkTouchEntry == null) {
            Simpleskills.LOGGER.warn("Silk Touch enchantment not found in registry for player {}", player.getName().getString());
            return false;
        }

        return EnchantmentHelper.getItemEnchantmentLevel(silkTouchEntry, toolStack) > 0;
    }
}