package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.data.DatabaseManager;
import com.github.ob_yekt.simpleskills.requirements.RequirementLoader;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PlayerEventHandlers {

    // Register all event handlers
    public static void registerEvents() {

        // Register Player Join Server events
        registerPlayerJoinEvent();

        // Register XP-related events
        registerXpGainEvent();

        // Register block-break events
        registerBlockBreakEvents();

        // Register defense-related events
        registerDefenseEvents();

        // Register slaying-related events
        registerSlayingEvents();
    }

    private static void registerPlayerJoinEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player; // Get the player
            String playerUuid = player.getUuidAsString();

            DatabaseManager db = DatabaseManager.getInstance();

            // Check and initialize skills for the player
            try (var rs = db.getPlayerSkills(playerUuid)) {
                boolean hasSkills = false;

                // Check if any skills are already present for this player
                while (rs.next()) {
                    hasSkills = true;
                    break;
                }

                // If the player doesn't have skills, initialize all at level 0 and XP 0
                if (!hasSkills) {
                    for (Skills skill : Skills.values()) {
                        db.savePlayerSkill(playerUuid, skill.name(), 0, 0);
                    }

                    Simpleskills.LOGGER.info("Initialized skills for new player: {}", player.getName().getString());
                }
            } catch (Exception e) {
                Simpleskills.LOGGER.error("Error initializing skills for player {}", player.getName().getString(), e);
            }

            // Update the tab menu with the player's skills
            SkillTabMenu.updateTabMenu(player);
        });
    }

    private static void registerXpGainEvent() {
        // Whenever XP is added, refresh the player's tab menu
        XPManager.setOnXpChangeListener((player, skill) -> {
            SkillTabMenu.updateTabMenu(player); // Update the tab menu after XP gain
        });
    }

    /// Block-breaking skills logic:

    private static void registerBlockBreakEvents() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true; // Allow breaking if not on server or player is not a server player
            }

            String playerUuid = serverPlayer.getUuidAsString();
            String toolName = serverPlayer.getMainHandStack().getItem().toString();
            String blockTranslationKey = state.getBlock().getTranslationKey();

                // Fetch the tool requirement for the tool being used
                SkillRequirement requirement = RequirementLoader.getToolRequirement(toolName);

                if (requirement != null) {
                    // Identify which skill is required (e.g., Woodcutting, Mining, Excavating)
                    Skills requiredSkill = Skills.valueOf(requirement.getSkill().toUpperCase());
                    int playerLevel = getSkillLevel(playerUuid, requiredSkill);

                    // Compare the player's level to the required level
                    if (playerLevel < requirement.getLevel()) {
                        serverPlayer.sendMessage(
                                Text.of("[SimpleSkills] You need " +
                                        requiredSkill.getDisplayName() + " level " +
                                        requirement.getLevel() + " to break this block with your tool!"),
                                true
                        );
                        return false; // Deny the block-breaking attempt
                    }
                }

            return true; // Allow breaking for blocks without restrictions
        });

        // Triggered after a block is successfully broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }

            String blockTranslationKey = state.getBlock().getTranslationKey();

            // XP multipliers for ores based on rarity
            double xpMultiplier = 1.0; // Default multiplier for non-ores (10 XP)
            boolean isOre = false;

            // Check if the block is an ore and set rarity multipliers
            if (blockTranslationKey.contains("coal_ore")) {
                xpMultiplier = 1.1;
                isOre = true;
            } else if (blockTranslationKey.contains("nether_quartz_ore")) {
                xpMultiplier = 1.3;
                isOre = true;
            } else if (blockTranslationKey.contains("copper_ore")) {
                xpMultiplier = 1.3;
                isOre = true;
            } else if (blockTranslationKey.contains("iron_ore")) {
                xpMultiplier = 1.6;
                isOre = true;
            } else if (blockTranslationKey.contains("redstone_ore")) {
                xpMultiplier = 2.1;
                isOre = true;
            } else if (blockTranslationKey.contains("gold_ore")) {
                xpMultiplier = 3.2;
                isOre = true;
            } else if (blockTranslationKey.contains("lapis_ore")) {
                xpMultiplier = 4.3;
                isOre = true;
            } else if (blockTranslationKey.contains("emerald_ore")) {
                xpMultiplier = 5.3;
                isOre = true;
            } else if (blockTranslationKey.contains("diamond_ore")) {
                xpMultiplier = 6.3;
                isOre = true;
            }

            // Check if the player is using a tool with Silk Touch, but only if the block is an ore
            boolean includesSilkTouch = false;
            if (isOre) {
                ItemStack toolStack = serverPlayer.getEquippedStack(EquipmentSlot.MAINHAND); // Get player's tool
                for (var enchantment : toolStack.getEnchantments().getEnchantments()) {
                    if (enchantment.getIdAsString().equals("minecraft:silk_touch")) {
                        includesSilkTouch = true;
                        break;
                    }
                }

                // If Silk Touch is used on an ore, do not grant XP
                if (includesSilkTouch) {
                    return;
                }
            }

            // Grant XP for ores or other blocks
            if (isOre) {
                XPManager.addXpWithNotification(serverPlayer, Skills.MINING, (int) (10 * xpMultiplier));
            } else if (blockTranslationKey.contains("stone")
                    || blockTranslationKey.contains("obsidian")
                    || blockTranslationKey.contains("Netherite")
                    || blockTranslationKey.contains("Debris")
                    || blockTranslationKey.contains("prismarine")
                    || blockTranslationKey.contains("purpur")
                    || blockTranslationKey.contains("amethyst")
                    || blockTranslationKey.contains("terracotta")
                    || blockTranslationKey.contains("basalt")
                    || blockTranslationKey.contains("deepslate")
                    || blockTranslationKey.contains("granite")
                    || blockTranslationKey.contains("diorite")
                    || blockTranslationKey.contains("andesite")
                    || blockTranslationKey.contains("brick")
                    || blockTranslationKey.contains("sandstone")
                    || blockTranslationKey.contains("blackstone")
                    || blockTranslationKey.contains("copper"))
            {
                XPManager.addXpWithNotification(serverPlayer, Skills.MINING, 10);

            } else if (blockTranslationKey.contains("log")
                    || blockTranslationKey.contains("planks")
                    || blockTranslationKey.contains("bookshelf")
                    || blockTranslationKey.contains("sign")
                    || blockTranslationKey.contains("root")
                    || blockTranslationKey.contains("door")
                    || blockTranslationKey.contains("barrel")
                    || blockTranslationKey.contains("table")
                    || blockTranslationKey.contains("chest")
                    || blockTranslationKey.contains("lectern")
                    || blockTranslationKey.contains("loom")
                    || blockTranslationKey.contains("campfire")
                    || blockTranslationKey.contains("fence")
                    || blockTranslationKey.contains("gate")
                    || blockTranslationKey.contains("wood")
                    || blockTranslationKey.contains("oak")
                    || blockTranslationKey.contains("spruce")
                    || blockTranslationKey.contains("birch")
                    || blockTranslationKey.contains("jungle")
                    || blockTranslationKey.contains("acacia")
                    || blockTranslationKey.contains("dark_oak")
                    || blockTranslationKey.contains("mangrove")
                    || blockTranslationKey.contains("cherry")
                    || blockTranslationKey.contains("bamboo")
                    || blockTranslationKey.contains("crimson")
                    || blockTranslationKey.contains("warped"))
            {
                XPManager.addXpWithNotification(serverPlayer, Skills.WOODCUTTING, 10);

            } else if (blockTranslationKey.contains("dirt") || blockTranslationKey.contains("sand")
                    || blockTranslationKey.contains("gravel")
                    || blockTranslationKey.contains("podzol")
                    || blockTranslationKey.contains("mycelium")
                    || blockTranslationKey.contains("farmland")
                    || blockTranslationKey.contains("concretePowder")
                    || blockTranslationKey.contains("mud")
                    || blockTranslationKey.contains("grass_block")
                    || blockTranslationKey.contains("soil")) {
                XPManager.addXpWithNotification(serverPlayer, Skills.EXCAVATING, 10);

            } else if (blockTranslationKey.contains("netherrack")) {
                // Netherrack-specific XP logic
                XPManager.addXpWithNotification(serverPlayer, Skills.MINING, 0); // Reduced XP for Netherrack

            } else if (blockTranslationKey.contains("snow_layer")) {
                // Snow-specific XP logic
                XPManager.addXpWithNotification(serverPlayer, Skills.EXCAVATING, 0); // No XP for Snow
            }
        });
    }


    /// Defense XP system

    private static void registerDefenseEvents() {
        // Listen for living entity damage and filter only players
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                handleDefenseXP(player, damageSource, damageAmount);
            }
            return true; // Allow the damage to proceed
        });
    }

    // Handles granting XP for defense based on damage taken
    private static void handleDefenseXP(ServerPlayerEntity player, DamageSource source, float damageAmount) {
        final float MIN_DAMAGE_THRESHOLD = 1.0F; // Ignore insignificant damage
        if (damageAmount < MIN_DAMAGE_THRESHOLD) return;

        // Prevent XP gain for invalid damage sources
        if (isInvalidDamageSource(source)) return;

        // If the player is blocking with a shield, grant shield block XP
        if (isShieldBlocking(player)) {
            if (!isInvalidShieldBlockingSource(source)) {
                float shieldXpMultiplier = 1.25f;
                int xpGained = Math.round(damageAmount * shieldXpMultiplier);
                XPManager.addXpWithNotification(player, Skills.DEFENSE, xpGained); // Add Shield Defense XP
            }
            return; // Shield block XP granted, no further Defense XP
        }

        // Calculate the number of equipped armor pieces
        int armorCount = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmorSlot() && !player.getEquippedStack(slot).isEmpty()) {
                armorCount++;
            }
        }

        // Grant Defense XP if the player has any armor equipped
        if (armorCount > 0) {
            float armorMultiplier = 1.0f + (0.25f * armorCount); // Bonus scaling for more armor
            float xpMultiplier = 1.75f; // Base multiplier for defense XP
            int xpGained = Math.round(damageAmount * xpMultiplier * armorMultiplier);

            // Add Defense XP using the centralized method
            XPManager.addXpWithNotification(player, Skills.DEFENSE, xpGained);
        }
    }

    // Checks if the player is actively blocking with a shield
    private static boolean isShieldBlocking(ServerPlayerEntity player) {
        return player.isBlocking() && player.getActiveItem().getItem() == Items.SHIELD;
    }

    // Validates whether the damage source allows granting XP
    private static boolean isInvalidDamageSource(DamageSource source) {
        // Allow XP only for damage caused by entities or projectiles
        return !(source.getSource() instanceof net.minecraft.entity.Entity
                || source.getSource() instanceof net.minecraft.entity.projectile.ProjectileEntity);
    }

    // Validates whether the shield blocking damage source allows granting XP
    private static boolean isInvalidShieldBlockingSource(DamageSource source) {
        // Allow XP only for blocking damage caused by entities or projectiles
        return !(source.getSource() instanceof net.minecraft.entity.Entity
                || source.getSource() instanceof net.minecraft.entity.projectile.ProjectileEntity);
    }

    /// Slaying restrictions and XP system using the centralized method:

    private static final float MIN_DAMAGE_THRESHOLD = 2.0F; // Minimum damage to grant XP

    private static void registerSlayingEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, damageSource, damageAmount) -> {
            if (damageAmount < MIN_DAMAGE_THRESHOLD) return true; // Ignore damage below threshold

            if (damageSource.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity attacker) {
                ItemStack weapon = attacker.getMainHandStack();

                if (weapon.isEmpty()) return true; // Allow attacks without granting XP for no weapon

                // Fetch the weapon's identifier
                String weaponName = Registries.ITEM.getId(weapon.getItem()).toString();
                SkillRequirement requirement = RequirementLoader.getWeaponRequirement(weaponName);

                if (requirement != null && "Slaying".equalsIgnoreCase(requirement.getSkill())) {
                    int requiredLevel = requirement.getLevel();
                    int playerLevel = getSkillLevel(attacker.getUuidAsString(), Skills.SLAYING);

                    if (playerLevel < requiredLevel) {
                        attacker.sendMessage(
                                Text.of("[SimpleSkills] You need Slaying level " + requiredLevel + " to use this weapon!"),
                                true
                        );
                        return false; // Block the attack
                    }
                }

                // Grant Slaying XP if the target is NOT an Armor Stand and if damage meets the threshold
                if (!(target instanceof net.minecraft.entity.decoration.ArmorStandEntity)) {
                    int xpGained = Math.round(damageAmount); // 1 XP per damage point
                    XPManager.addXpWithNotification(attacker, Skills.SLAYING, xpGained); // Grant Slaying XP
                }
            }

            return true; // Allow the damage to proceed
        });
    }

    /// Query the SQL database for a player's skill level

    private static int getSkillLevel(String playerUuid, Skills skill) {
        DatabaseManager db = DatabaseManager.getInstance();
        try (var rs = db.getPlayerSkills(playerUuid)) {
            while (rs.next()) {
                if (rs.getString("skill").equals(skill.name())) {
                    return rs.getInt("level");
                }
            }
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Error checking skill level for player {}", playerUuid, e);
        }
        return 0; // Default skill level
    }
}