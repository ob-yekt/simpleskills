package com.github.ob_yekt.simpleskills.events;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.managers.ConfigManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.AttributeManager;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.github.ob_yekt.simpleskills.ui.SkillTabMenu;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;

import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import net.minecraft.util.Identifier;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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

    private static void registerBlockHandlers() {
        // BEFORE block break event
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }

            String blockTranslationKey = state.getBlock().getTranslationKey();
            String toolId = "minecraft:air"; // Default if empty hand
            ItemStack mainHandStack = serverPlayer.getMainHandStack();
            if (!mainHandStack.isEmpty()) {
                toolId = Registries.ITEM.getId(mainHandStack.getItem()).toString();
            }

            // --- 1) Check tool requirement first ---
            SkillRequirement requirement = ConfigManager.getToolRequirement(toolId);
            if (requirement != null) {
                Skills requiredSkill = requirement.getSkill();
                int requiredLevel = requirement.getLevel();

                int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), requiredSkill);
                if (playerLevel < requiredLevel) {
                    serverPlayer.sendMessage(Text.literal(String.format(
                            "§6[simpleskills]§f You need %s level %d to use this tool!",
                            requiredSkill.getDisplayName(), requiredLevel
                    )), true);
                    return false; // Block breaking canceled
                }
            }

            // --- 2) Then check if block itself has a skill requirement ---
            Skills relevantSkill = ConfigManager.getBlockSkill(blockTranslationKey);
            boolean isFarmingBlock = ConfigManager.getFarmingBlockXP(blockTranslationKey) > 0;
            if ((relevantSkill != null && isCrop(blockTranslationKey)) || isFarmingBlock) {
                return true; // Crops bypass tool requirement
            }

            return true;
        });

        // AFTER block break event
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }

            String blockTranslationKey = state.getBlock().getTranslationKey();

            // Check for Silk Touch on ores, melons (before doing anything else)
            if ((blockTranslationKey.contains("_ore") || blockTranslationKey.contains("melon")) && hasSilkTouch(serverPlayer)) {
                Simpleskills.LOGGER.debug("No XP granted for {} to player {} due to Silk Touch", blockTranslationKey, serverPlayer.getName().getString());
                return;
            }

            // Check if it's a farming block
            if (ConfigManager.getFarmingBlockXP(blockTranslationKey) > 0) {
                grantFarmingXP((ServerWorld) world, serverPlayer, pos, state, blockTranslationKey);
                return;
            }

            // Check for other skill-based blocks
            Skills relevantSkill = ConfigManager.getBlockSkill(blockTranslationKey);
            if (relevantSkill == null) {
                return;
            }

            // Grant XP for non-farming skills
            if (relevantSkill != Skills.FARMING) {
                int xp = ConfigManager.getBlockXP(blockTranslationKey, relevantSkill);
                XPManager.addXPWithNotification(serverPlayer, relevantSkill, xp);
            }
        });
    }

    private static boolean isCrop(String blockTranslationKey) {
        return blockTranslationKey.contains("wheat") || blockTranslationKey.contains("carrots") ||
                blockTranslationKey.contains("potatoes") || blockTranslationKey.contains("beetroots") ||
                blockTranslationKey.contains("nether_wart") || blockTranslationKey.contains("cocoa") ||
                blockTranslationKey.contains("melon");
    }

    private static void grantFarmingXP(ServerWorld world, ServerPlayerEntity serverPlayer, BlockPos pos, BlockState state, String blockTranslationKey) {
        if (!isCrop(blockTranslationKey)) {
            return;
        }

        boolean isMatured = false;

        if (state.contains(Properties.AGE_7)) {
            int age = state.get(Properties.AGE_7);
            if (age == 7 && (blockTranslationKey.contains("wheat") || blockTranslationKey.contains("carrots") || blockTranslationKey.contains("potatoes"))) {
                isMatured = true;
            }
        } else if (state.contains(Properties.AGE_3)) {
            int age = state.get(Properties.AGE_3);
            if (age == 3 && (blockTranslationKey.contains("nether_wart") || blockTranslationKey.contains("beetroots"))) {
                isMatured = true;
            }
        } else if (state.contains(Properties.AGE_2)) {
            int age = state.get(Properties.AGE_2);
            if (age == 2 && blockTranslationKey.contains("cocoa")) {
                isMatured = true;
            }
        } else if (blockTranslationKey.contains("melon")) {
            isMatured = true;
        }

        if (!isMatured) {
            return;
        }

        int xp = ConfigManager.getFarmingBlockXP(blockTranslationKey);
        XPManager.addXPWithNotification(serverPlayer, Skills.FARMING, xp);
        applyBonusDrops(world, serverPlayer, pos, state, blockTranslationKey);
        Simpleskills.LOGGER.debug("Granted {} XP for harvesting {} to player {}", xp, blockTranslationKey, serverPlayer.getName().getString());
    }

    private static void applyBonusDrops(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, String blockTranslationKey) {
        // Calculate bonus drop chance: 1% per farming level, capped at 99%
        int farmingLevel = XPManager.getSkillLevel(player.getUuidAsString(), Skills.FARMING);
        double dropChance = Math.min(farmingLevel, 99) / 100.0;

        if (world.random.nextDouble() < dropChance) {
            spawnBonusDrops(world, pos, state);
            Simpleskills.LOGGER.debug("Granted bonus drops to {} for {} (farming level: {})",
                    player.getName().getString(), blockTranslationKey, farmingLevel);
        }
    }

    private static void spawnBonusDrops(ServerWorld world, BlockPos pos, BlockState state) {
        // Get the drops that would normally be produced
        java.util.List<ItemStack> drops = net.minecraft.block.Block.getDroppedStacks(state, world, pos, null);

        Vec3d dropPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            ItemStack bonus = drop.copy();
            ItemEntity itemEntity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, bonus);

            // Add slight random velocity for visual effect
            itemEntity.setVelocity(
                    (world.random.nextDouble() - 0.5) * 0.1,
                    world.random.nextDouble() * 0.1,
                    (world.random.nextDouble() - 0.5) * 0.1
            );

            world.spawnEntity(itemEntity);
        }
    }

    private static void registerCombatHandlers() {
        // Prevent melee attacks if weapon requirement not met
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !(entity instanceof LivingEntity)) {
                return ActionResult.PASS;
            }

            ItemStack weapon = serverPlayer.getMainHandStack();
            if (weapon.isEmpty()) {
                return ActionResult.PASS;
            }

            String weaponId = Registries.ITEM.getId(weapon.getItem()).toString();
            SkillRequirement requirement = ConfigManager.getWeaponRequirement(weaponId);
            if (requirement == null) {
                return ActionResult.PASS;
            }

            Skills skill = requirement.getSkill();
            int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), skill);
            if (playerLevel < requirement.getLevel()) {
                serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need %s level %d to use this weapon!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // Prevent ranged weapon use (bow/crossbow/trident throw) if requirement not met
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
                return ActionResult.PASS;
            }

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            SkillRequirement requirement = ConfigManager.getWeaponRequirement(itemId);
            if (requirement == null) {
                return ActionResult.PASS;
            }

            Skills skill = requirement.getSkill();
            int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), skill);
            if (playerLevel < requirement.getLevel()) {
                serverPlayer.sendMessage(Text.literal(String.format("§6[simpleskills]§f You need %s level %d to use this!",
                        skill.getDisplayName(), requirement.getLevel())), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // Grant XP on successful damage for Slaying, Ranged, and Defense
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getEntityWorld().isClient() || amount <= 0) {
                return true;
            }

            // Exclude explosion damage
            if (source.isOf(net.minecraft.entity.damage.DamageTypes.EXPLOSION) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_EXPLOSION)) {
                return true;
            }

            JsonObject config = ConfigManager.getCombatConfig();
            float xpPerDamageSlaying = config.get("slaying_xp_per_damage") != null ? config.get("slaying_xp_per_damage").getAsFloat() : 100.0f;
            float xpPerDamageRanged = config.get("ranged_xp_per_damage") != null ? config.get("ranged_xp_per_damage").getAsFloat() : 100.0f;
            float minDamageSlaying = config.get("slaying_min_damage_threshold") != null ? config.get("slaying_min_damage_threshold").getAsFloat() : 2.0f;
            float minDamageRanged = config.get("ranged_min_damage_threshold") != null ? config.get("ranged_min_damage_threshold").getAsFloat() : 2.0f;

            // Slaying/Ranged: Player dealing damage to non-player mob
            if (entity instanceof LivingEntity target && !(target instanceof PlayerEntity) && !(entity instanceof net.minecraft.entity.decoration.ArmorStandEntity)) {
                ServerPlayerEntity attacker = null;
                Skills skill = null;
                float xpMultiplier = 0.0f;
                float minDamage = 0.0f;
                int xp = 0;

                // Melee (Slaying) attack
                if (source.getAttacker() instanceof ServerPlayerEntity) {
                    attacker = (ServerPlayerEntity) source.getAttacker();
                    ItemStack weapon = attacker.getMainHandStack();
                    String weaponId = weapon.isEmpty() ? "minecraft:empty" : Registries.ITEM.getId(weapon.getItem()).toString();
                    SkillRequirement requirement = ConfigManager.getWeaponRequirement(weaponId);
                    skill = (requirement != null && requirement.getSkill() == Skills.RANGED) ? Skills.RANGED : Skills.SLAYING;
                    xpMultiplier = (skill == Skills.RANGED) ? xpPerDamageRanged : xpPerDamageSlaying;
                    minDamage = (skill == Skills.RANGED) ? minDamageRanged : minDamageSlaying;
                    xp = (int) (amount * xpMultiplier);
                }
                // Ranged attack
                else if (source.getSource() instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity) {
                    attacker = (ServerPlayerEntity) projectile.getOwner();
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
            if (entity.getEntityWorld().isClient()) {
                return;
            }

            // Exclude explosion damage
            if (source.isOf(net.minecraft.entity.damage.DamageTypes.EXPLOSION) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_EXPLOSION)) {
                return;
            }

            // Defense: Player taking damage from non-player mob
            if (entity instanceof ServerPlayerEntity defender && source.getAttacker() instanceof LivingEntity attacker && !(attacker instanceof PlayerEntity)) {
                JsonObject config = ConfigManager.getCombatConfig();
                float xpPerDamageDefense = config.get("defense_xp_per_damage") != null ? config.get("defense_xp_per_damage").getAsFloat() : 100.0f;
                float minDamageDefense = config.get("defense_min_damage_threshold") != null ? config.get("defense_min_damage_threshold").getAsFloat() : 2.0f;
                float armorMultiplierPerPiece = config.get("defense_xp_armor_multiplier_per_piece") != null ? config.get("defense_xp_armor_multiplier_per_piece").getAsFloat() : 0.25f;
                float shieldXPMultiplier = config.get("defense_shield_xp_multiplier") != null ? config.get("defense_shield_xp_multiplier").getAsFloat() : 0.3f;

                if (originalAmount >= minDamageDefense) {
                    // Check if damage was blocked (shield blocking)
                    boolean wasBlocked = blocked || (originalAmount > actualAmount && defender.isBlocking() &&
                            defender.getActiveItem().getItem() == net.minecraft.item.Items.SHIELD);

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
                            if (slot.isArmorSlot() && !defender.getEquippedStack(slot).isEmpty()) {
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
            ServerPlayerEntity player = handler.getPlayer();
            if (player == null) {
                Simpleskills.LOGGER.warn("Null player in join event.");
                return;
            }
            String playerUuid = player.getUuidAsString();
            String playerName = player.getName().getString();
            DatabaseManager db = DatabaseManager.getInstance();

            db.ensurePlayerInitialized(playerUuid);
            db.updatePlayerName(playerUuid, playerName); // Add this line
            AttributeManager.refreshAllAttributes(player);
            SkillTabMenu.updateTabMenu(player);
            com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(player);
            Simpleskills.LOGGER.debug("Processed join for player: {}", playerName);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player == null) {
                Simpleskills.LOGGER.warn("Null player in disconnect event.");
                return;
            }
            String playerUuid = player.getUuidAsString();
            AttributeManager.clearSkillAttributes(player);
            AttributeManager.clearIronmanAttributes(player);
            SkillTabMenu.clearPlayerVisibility(player.getUuid());
            Simpleskills.LOGGER.debug("Processed disconnect for player: {}", player.getName().getString());
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer == null) {
                Simpleskills.LOGGER.warn("Null newPlayer in respawn event.");
                return;
            }
            String playerUuid = newPlayer.getUuidAsString();
            DatabaseManager db = DatabaseManager.getInstance();

            if (!alive) {
                if (db.isPlayerInIronmanMode(playerUuid)) {
                    int totalLevels = db.getAllSkills(playerUuid).values().stream()
                            .mapToInt(DatabaseManager.SkillData::level)
                            .sum();
                    int prestige = db.getPrestige(playerUuid);
                    db.setIronmanMode(playerUuid, false);
                    db.resetPlayerSkills(playerUuid);
                    db.ensurePlayerInitialized(playerUuid);
                    AttributeManager.clearSkillAttributes(newPlayer);
                    AttributeManager.clearIronmanAttributes(newPlayer);
                    newPlayer.sendMessage(Text.literal("§6[simpleskills]§f Your deal with death has cost you all skill levels. Ironman mode has been disabled.").formatted(Formatting.YELLOW), false);
                    if (ConfigManager.getFeatureConfig().get("broadcast_ironman_death") != null &&
                            ConfigManager.getFeatureConfig().get("broadcast_ironman_death").getAsBoolean()) {
                        String prestigePart = prestige > 0 ? String.format(" at §6★%d§f", prestige) : "";
                        Objects.requireNonNull(newPlayer.getEntityWorld().getServer()).getPlayerManager().broadcast(
                                Text.literal(String.format("§6[simpleskills]§f %s has died in Ironman mode with a total level of §6%d§f%s.",
                                        newPlayer.getName().getString(), totalLevels, prestigePart)), false);
                    }
                    Simpleskills.LOGGER.debug("Disabled Ironman mode and reset skills for player: {}", newPlayer.getName().getString());
                } else {
                    AttributeManager.clearSkillAttributes(newPlayer);
                    AttributeManager.clearIronmanAttributes(newPlayer);
                }
            }
            AttributeManager.refreshAllAttributes(newPlayer);
            SkillTabMenu.updateTabMenu(newPlayer);
            com.github.ob_yekt.simpleskills.managers.NamePrefixManager.updatePlayerNameDecorations(newPlayer);
            Simpleskills.LOGGER.debug("Processed respawn for player: {}, alive: {}", newPlayer.getName().getString(), alive);
        });
    }

    private static void registerPrayerHandlers() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (state.isIn(BlockTags.CANDLES) && state.get(Properties.LIT)) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                ConfigManager.PrayerSacrifice sacrifice = ConfigManager.getPrayerSacrifice(itemId);

                if (sacrifice == null) {
                    return ActionResult.PASS;
                }

                SkillRequirement requirement = sacrifice.requirement();
                int playerLevel = XPManager.getSkillLevel(serverPlayer.getUuidAsString(), Skills.PRAYER);
                if (playerLevel < requirement.getLevel()) {
                    serverPlayer.sendMessage(Text.literal("§6[simpleskills]§f You need Prayer level " + requirement.getLevel() + " to offer this sacrifice!"), true);
                    return ActionResult.FAIL;
                }

                // Remove all existing Prayer-related status effects
                for (ConfigManager.PrayerSacrifice existingSacrifice : ConfigManager.PRAYER_SACRIFICES.values()) {
                    Registries.STATUS_EFFECT.getEntry(Identifier.of(existingSacrifice.effect())).ifPresent(serverPlayer::removeStatusEffect);
                }

                // Consume item
                stack.decrement(1);

                // Grant XP
                XPManager.addXPWithNotification(serverPlayer, Skills.PRAYER, sacrifice.xp());

                // Apply status effect
                RegistryEntry<StatusEffect> effectEntry = Registries.STATUS_EFFECT.getEntry(Identifier.of(sacrifice.effect())).orElse(null);
                if (effectEntry != null) {
                    serverPlayer.addStatusEffect(new StatusEffectInstance(
                            effectEntry,
                            sacrifice.durationTicks(),
                            sacrifice.effectLevel() - 1, // Use effectLevel, subtract 1 for 0-based amplifier
                            sacrifice.isAmbient(),      // Use isAmbient for particle visibility
                            true                       // showIcon
                    ));
                    serverPlayer.sendMessage(Text.literal("§6[simpleskills]§f You offer a sacrifice and gain " + sacrifice.displayName() + "!"), false);
                } else {
                    Simpleskills.LOGGER.warn("Invalid status effect {} for item {} in prayer_sacrifices.json", sacrifice.effect(), itemId);
                }

                // Visual feedback
                ServerWorld serverWorld = (ServerWorld) world;
                serverWorld.spawnParticles(
                        net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        hitResult.getBlockPos().getX() + 0.4,
                        hitResult.getBlockPos().getY() + 1.0,
                        hitResult.getBlockPos().getZ() + 0.5,
                        20, 0.2, 0.2, 0.2, 0.05
                );
                serverWorld.spawnParticles(
                        net.minecraft.particle.ParticleTypes.COPPER_FIRE_FLAME,
                        hitResult.getBlockPos().getX() + 0.5,
                        hitResult.getBlockPos().getY() + 1.0,
                        hitResult.getBlockPos().getZ() + 0.4,
                        20, 0.2, 0.2, 0.2, 0.05
                );
                serverWorld.playSound(
                        null,
                        hitResult.getBlockPos(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.BLOCKS,
                        1.0f,
                        1.0f
                );

                Simpleskills.LOGGER.debug("Player {} offered {} for {} XP and {} effect (level {}, ambient {})",
                        serverPlayer.getName().getString(), itemId, sacrifice.xp(), sacrifice.displayName(),
                        sacrifice.effectLevel(), sacrifice.isAmbient());
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    private static boolean hasSilkTouch(ServerPlayerEntity player) {
        ItemStack toolStack = player.getEquippedStack(EquipmentSlot.MAINHAND);
        var enchantmentRegistry = Objects.requireNonNull(player.getEntityWorld().getServer())
                .getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> silkTouchEntry = enchantmentRegistry
                .getOptional(Enchantments.SILK_TOUCH)
                .orElse(null);

        if (silkTouchEntry == null) {
            Simpleskills.LOGGER.warn("Silk Touch enchantment not found in registry for player {}", player.getName().getString());
            return false;
        }

        return EnchantmentHelper.getLevel(silkTouchEntry, toolStack) > 0;
    }
}