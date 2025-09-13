package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import com.github.ob_yekt.simpleskills.requirements.SkillRequirement;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.fabricmc.loader.impl.util.StringUtil.capitalize;

/**
 * Manages all JSON-based configurations for the SimpleSkills mod, including base XP, block mappings, requirements, and prayer sacrifices.
 */
public class ConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("simpleskills");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Skills, Integer> BASE_XP_MAP = new HashMap<>();
    private static final Map<String, Skills> BLOCK_SKILL_MAP = new HashMap<>();
    private static final Map<String, Integer> BLOCK_XP_MAP = new HashMap<>();
    private static final Map<String, SkillRequirement> TOOL_REQUIREMENTS = new HashMap<>();
    private static final Map<String, SkillRequirement> ARMOR_REQUIREMENTS = new HashMap<>();
    private static final Map<String, SkillRequirement> WEAPON_REQUIREMENTS = new HashMap<>();
    private static final Map<String, SkillRequirement> ENCHANTMENT_REQUIREMENTS = new HashMap<>();
    public static final Map<String, PrayerSacrifice> PRAYER_SACRIFICES = new HashMap<>();
    private static final Map<String, Integer> COOKING_XP_MAP = new HashMap<>();
    private static final Map<String, Float> COOKING_MULTIPLIER_MAP = new HashMap<>();
    private static final Map<String, Integer> CRAFTING_XP_MAP = new HashMap<>();
    private static final Map<String, Integer> SMELTING_CRAFTING_XP_MAP = new HashMap<>();

    private static JsonObject craftingMultipliersConfig = new JsonObject();
    private static final Set<String> CRAFTING_RECOVERY_BLACKLIST = new HashSet<>();
    private static final Map<String, Integer> ALCHEMY_XP_MAP = new HashMap<>();
    private static final Map<String, Float> ALCHEMY_MULTIPLIER_MAP = new HashMap<>();
    private static final Map<String, Integer> AGILITY_XP_MAP = new HashMap<>();
    private static final Map<String, Float> SMITHING_XP_MAP = new HashMap<>();
    private static final Map<String, Float> SMITHING_MULTIPLIER_MAP = new HashMap<>();
    private static final Map<String, Integer> FISHING_XP_MAP = new HashMap<>();
    private static final Map<String, Identifier> FISHING_LOOT_TABLES = new HashMap<>();
    private static JsonObject featureConfig = new JsonObject();
    private static JsonObject combatConfig = new JsonObject();

    /**
     * Initializes all configurations.
     */
    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadBaseXPConfig();
            loadFeatureConfig();
            loadAgilityXPConfig();
            loadBlockMappings();
            loadToolRequirements();
            loadArmorRequirements();
            loadWeaponRequirements();
            loadEnchantmentRequirements();
            loadPrayerSacrifices();
            loadCookingXPConfig();
            loadCookingMultipliersConfig();
            loadCraftingXPConfig();
            loadCraftingMultipliersConfig();
            loadCraftingRecoveryBlacklist();

            loadSmeltingCraftingXPConfig();
            loadAlchemyXPConfig();
            loadAlchemyMultiplierConfig();
            loadSmithingXPConfig();
            loadSmithingMultiplierConfig();
            loadFishingXPConfig();
            loadFishingLootConfig();
            loadCombatConfig();
            Simpleskills.LOGGER.info("All configurations initialized successfully.");
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error initializing configurations: {}", e.getMessage());
        }
    }

    /**
     * Loads the base XP configuration from base_xp.json.
     */
    private static void loadBaseXPConfig() {
        Path filePath = CONFIG_DIR.resolve("base_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultBaseXPConfig());
            for (Skills skill : Skills.values()) {
                String skillId = skill.getId();
                if (json.has(skillId)) {
                    int xp = json.get(skillId).getAsInt();
                    if (xp >= 0) {
                        BASE_XP_MAP.put(skill, xp);
                    } else {
                        Simpleskills.LOGGER.warn("Invalid XP value for skill {} in base_xp.json, using default", skillId);
                    }
                } else {
                    Simpleskills.LOGGER.warn("Skill {} missing in base_xp.json, using default XP", skillId);
                }
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in base_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading base_xp.json: {}", e.getMessage());
        }
    }

    /**
     * Loads the feature configuration from config.json.
     */
    private static void loadFeatureConfig() {
        Path filePath = CONFIG_DIR.resolve("config.json");
        try {
            featureConfig = loadJsonFile(filePath, getDefaultFeatureConfig());
            Simpleskills.LOGGER.info("Loaded config.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in config.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading config.json: {}", e.getMessage());
        }
    }

    /**
     * Default base XP configuration.
     */
    private static JsonObject getDefaultBaseXPConfig() {
        JsonObject json = new JsonObject();
        for (Skills skill : Skills.values()) {
            json.addProperty(skill.getId(), 100);
        }
        return json;
    }

    /**
     * Provides the default feature configuration for config.json.
     */
    private static JsonObject getDefaultFeatureConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("xp_notifications_enabled", true);
        json.addProperty("xp_notification_threshold", 10);
        json.addProperty("standard_xp_multiplier", 1.0);
        json.addProperty("ironman_xp_multiplier", 0.5);
        json.addProperty("ironman_health_reduction", -6.0);
        json.addProperty("broadcast_ironman_death", true);
        return json;
    }

    /**
     * Loads block mappings from block_mappings.json.
     */
    private static void loadBlockMappings() {
        Path filePath = CONFIG_DIR.resolve("block_mappings.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultBlockMappings());
            var mappings = json.getAsJsonArray("block_mappings");
            BLOCK_SKILL_MAP.clear();
            BLOCK_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String block = mapping.get("block").getAsString();
                String skillId = mapping.get("skill").getAsString();
                int xp = mapping.get("xp").getAsInt();

                try {
                    Skills skill = Skills.valueOf(skillId.toUpperCase());
                    if (xp < 0) {
                        Simpleskills.LOGGER.warn("Invalid XP value {} for block {} in block_mappings.json, skipping", xp, block);
                        continue;
                    }
                    // Handle pattern-based blocks (e.g., block.minecraft.*_planks)
                    if (block.contains("*")) {
                        String regex = block.replace("*", ".*");
                        for (var blockEntry : Registries.BLOCK.getEntrySet()) {
                            String translationKey = blockEntry.getValue().getTranslationKey();
                            if (translationKey.matches(regex)) {
                                BLOCK_SKILL_MAP.put(translationKey, skill);
                                BLOCK_XP_MAP.put(translationKey, xp);
                            }
                        }
                    } else {
                        BLOCK_SKILL_MAP.put(block, skill);
                        BLOCK_XP_MAP.put(block, xp);
                    }
                } catch (IllegalArgumentException e) {
                    Simpleskills.LOGGER.warn("Invalid skill {} for block {} in block_mappings.json, skipping", skillId, block);
                }
            }
            Simpleskills.LOGGER.info("Loaded {} block mappings.", BLOCK_SKILL_MAP.size());
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in block_mappings.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading block_mappings.json: {}", e.getMessage());
        }
    }


    /**
     * Loads agility XP mappings from agility_xp.json.
     */
    private static void loadAgilityXPConfig() {
        Path filePath = CONFIG_DIR.resolve("agility_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultAgilityXPConfig());
            var mappings = json.getAsJsonArray("agility_mappings");
            AGILITY_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String action = mapping.get("action").getAsString();
                int xp = mapping.get("xp").getAsInt();

                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Invalid XP value {} for action {} in agility_xp.json, skipping", xp, action);
                    continue;
                }
                AGILITY_XP_MAP.put(action, xp);
            }
            Simpleskills.LOGGER.info("Loaded agility_xp.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in agility_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading agility_xp.json: {}", e.getMessage());
        }
    }

    private static void loadToolRequirements() {
        Path filePath = CONFIG_DIR.resolve("tool_requirements.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultToolRequirements());
            for (var entry : json.entrySet()) {
                String toolId = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();
                String skillId = data.get("skill").getAsString();
                int level = data.get("level").getAsInt();
                try {
                    Skills skill = Skills.valueOf(skillId.toUpperCase());
                    if (level >= 0) {
                        TOOL_REQUIREMENTS.put(toolId, new SkillRequirement(skill, level, null));
                    } else {
                        Simpleskills.LOGGER.warn("Invalid level for tool {} in tool_requirements.json", toolId);
                    }
                } catch (IllegalArgumentException e) {
                    Simpleskills.LOGGER.warn("Invalid skill {} for tool {} in tool_requirements.json", skillId, toolId);
                }
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in tool_requirements.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading tool_requirements.json: {}", e.getMessage());
        }
    }

    private static void loadArmorRequirements() {
        Path filePath = CONFIG_DIR.resolve("armor_requirements.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultArmorRequirements());
            for (var entry : json.entrySet()) {
                String armorId = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();
                String skillId = data.get("skill").getAsString();
                int level = data.get("level").getAsInt();
                try {
                    Skills skill = Skills.valueOf(skillId.toUpperCase());
                    if (level >= 0) {
                        ARMOR_REQUIREMENTS.put(armorId, new SkillRequirement(skill, level, null));
                    } else {
                        Simpleskills.LOGGER.warn("Invalid level for armor {} in armor_requirements.json", armorId);
                    }
                } catch (IllegalArgumentException e) {
                    Simpleskills.LOGGER.warn("Invalid skill {} for armor {} in armor_requirements.json", skillId, armorId);
                }
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in armor_requirements.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading armor_requirements.json: {}", e.getMessage());
        }
    }

    private static void loadWeaponRequirements() {
        Path filePath = CONFIG_DIR.resolve("weapon_requirements.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultWeaponRequirements());
            for (var entry : json.entrySet()) {
                String weaponId = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();
                String skillId = data.get("skill").getAsString();
                int level = data.get("level").getAsInt();
                try {
                    Skills skill = Skills.valueOf(skillId.toUpperCase());
                    if (level >= 0) {
                        WEAPON_REQUIREMENTS.put(weaponId, new SkillRequirement(skill, level, null));
                    } else {
                        Simpleskills.LOGGER.warn("Invalid level for weapon {} in weapon_requirements.json", weaponId);
                    }
                } catch (IllegalArgumentException e) {
                    Simpleskills.LOGGER.warn("Invalid skill {} for weapon {} in weapon_requirements.json", skillId, weaponId);
                }
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in weapon_requirements.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading weapon_requirements.json: {}", e.getMessage());
        }
    }

    private static void loadEnchantmentRequirements() {
        Path filePath = CONFIG_DIR.resolve("enchantment_requirements.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultEnchantmentRequirements());
            for (var entry : json.entrySet()) {
                String enchantmentId = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();
                String skillId = data.get("skill").getAsString();
                int level = data.get("level").getAsInt();
                Integer enchantmentLevel = data.has("enchantmentLevel") ? data.get("enchantmentLevel").getAsInt() : null;
                try {
                    Skills skill = Skills.valueOf(skillId.toUpperCase());
                    if (level >= 0) {
                        ENCHANTMENT_REQUIREMENTS.put(enchantmentId, new SkillRequirement(skill, level, enchantmentLevel));
                    } else {
                        Simpleskills.LOGGER.warn("Invalid level for enchantment {} in enchantment_requirements.json", enchantmentId);
                    }
                } catch (IllegalArgumentException e) {
                    Simpleskills.LOGGER.warn("Invalid skill {} for enchantment {} in enchantment_requirements.json", skillId, enchantmentId);
                }
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in enchantment_requirements.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading enchantment_requirements.json: {}", e.getMessage());
        }
    }

    private static void loadCombatConfig() {
        Path filePath = CONFIG_DIR.resolve("combat_config.json");
        try {
            combatConfig = loadJsonFile(filePath, getDefaultCombatConfig());
            Simpleskills.LOGGER.info("Loaded combat_config.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in combat_config.json: {}", e.getMessage());
            combatConfig = getDefaultCombatConfig(); // Fallback to defaults
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading combat_config.json: {}", e.getMessage());
            combatConfig = getDefaultCombatConfig(); // Fallback to defaults
        }
    }

    private static JsonObject getDefaultCombatConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("slaying_xp_per_damage", 100.0f);
        json.addProperty("ranged_xp_per_damage", 100.0f);
        json.addProperty("defense_xp_per_damage", 100.0f);
        json.addProperty("slaying_min_damage_threshold", 2.0f);
        json.addProperty("ranged_min_damage_threshold", 2.0f);
        json.addProperty("defense_min_damage_threshold", 2.0f);
        json.addProperty("defense_xp_armor_multiplier_per_piece", 0.25f);
        json.addProperty("defense_shield_xp_multiplier", 0.3f);
        return json;
    }

    public static JsonObject getCombatConfig() {
        if (combatConfig == null) {
            Simpleskills.LOGGER.warn("Combat config is null, returning default config");
            return getDefaultCombatConfig();
        }
        return combatConfig;
    }

    /**
     * Loads cooking multipliers from cooking_multipliers.json.
     */
    private static void loadCookingMultipliersConfig() {
        Path filePath = CONFIG_DIR.resolve("cooking_multipliers.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultCookingMultipliersConfig());
            COOKING_MULTIPLIER_MAP.clear();
            for (var entry : json.entrySet()) {
                String range = entry.getKey();
                float multiplier = entry.getValue().getAsFloat();
                if (multiplier >= 0) {
                    COOKING_MULTIPLIER_MAP.put(range, multiplier);
                } else {
                    Simpleskills.LOGGER.warn("Invalid multiplier {} for range {} in cooking_multipliers.json, skipping", multiplier, range);
                }
            }
            Simpleskills.LOGGER.info("Loaded cooking_multipliers.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in cooking_multipliers.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading cooking_multipliers.json: {}", e.getMessage());
        }
    }

    private static JsonObject getDefaultCookingMultipliersConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("0-24", 0.875f);
        json.addProperty("25-49", 1.0f);
        json.addProperty("50-74", 1.125f);
        json.addProperty("75-98", 1.25f);
        json.addProperty("99-99", 1.5f);
        return json;
    }

    /**
     * Gets the cooking multiplier for a given level.
     */
    public static float getCookingMultiplier(int level) {
        if (level >= 99) {
            return COOKING_MULTIPLIER_MAP.getOrDefault("99-99", 1.5f);
        } else if (level >= 75) {
            return COOKING_MULTIPLIER_MAP.getOrDefault("75-98", 1.25f);
        } else if (level >= 50) {
            return COOKING_MULTIPLIER_MAP.getOrDefault("50-74", 1.125f);
        } else if (level >= 25) {
            return COOKING_MULTIPLIER_MAP.getOrDefault("25-49", 1.0f);
        } else {
            return COOKING_MULTIPLIER_MAP.getOrDefault("0-24", 0.875f);
        }
    }

    /**
     * Loads cooking XP mappings from cooking_xp.json.
     */
    private static void loadCookingXPConfig() {
        Path filePath = CONFIG_DIR.resolve("cooking_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultCookingXPConfig());
            var mappings = json.getAsJsonArray("cooking_mappings");
            COOKING_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String item = mapping.get("item").getAsString();
                int xp = mapping.get("xp").getAsInt();

                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Invalid XP value {} for item {} in cooking_xp.json, skipping", xp, item);
                    continue;
                }
                COOKING_XP_MAP.put(item, xp);
            }
            Simpleskills.LOGGER.info("Loaded cooking_xp.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in cooking_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading cooking_xp.json: {}", e.getMessage());
        }
    }

    /**
     * Loads crafting XP mappings from crafting_xp.json.
     */
    private static void loadCraftingXPConfig() {
        Path filePath = CONFIG_DIR.resolve("crafting_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultCraftingXPConfig());
            var mappings = json.getAsJsonArray("crafting_mappings");
            CRAFTING_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String item = mapping.get("item").getAsString();
                int xp = mapping.get("xp").getAsInt();

                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Invalid XP value {} for item {} in crafting_xp.json, skipping", xp, item);
                    continue;
                }
                CRAFTING_XP_MAP.put(item, xp);
            }
            Simpleskills.LOGGER.info("Loaded crafting_xp.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in crafting_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading crafting_xp.json: {}", e.getMessage());
        }
    }



    /**
     * Default cooking XP configuration.
     */
    private static JsonObject getDefaultCookingXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record CookingMapping(String item, int xp) {
        }
        CookingMapping[] defaults = {
                new CookingMapping("item.minecraft.cooked_porkchop", 250),
                new CookingMapping("item.minecraft.cooked_beef", 250),
                new CookingMapping("item.minecraft.cooked_chicken", 250),
                new CookingMapping("item.minecraft.cooked_mutton", 250),
                new CookingMapping("item.minecraft.cooked_salmon", 200),
                new CookingMapping("item.minecraft.cooked_cod", 200),
                new CookingMapping("item.minecraft.cooked_rabbit", 300),
                new CookingMapping("item.minecraft.baked_potato", 150)
        };
        for (CookingMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", mapping.item);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }
        json.add("cooking_mappings", mappings);
        return json;
    }

    /**
     * Gets the XP for cooking an item.
     */
    public static int getCookingXP(String itemTranslationKey, Skills skill) {
        return COOKING_XP_MAP.getOrDefault(itemTranslationKey, getBaseXP(skill));
    }

    /**
     * Default cooking XP configuration.
     */
    private static JsonObject getDefaultCraftingXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record CraftingMapping(String item, int xp) {
        }
        CraftingMapping[] defaults = {
                // Wood
                new CraftingMapping("minecraft:wood_shovel", 300),
                new CraftingMapping("minecraft:wood_hoe", 600),
                new CraftingMapping("minecraft:wood_sword", 600),
                new CraftingMapping("minecraft:wood_pickaxe", 900),
                new CraftingMapping("minecraft:wood_axe", 900),

                // Leather/Stone
                new CraftingMapping("minecraft:leather_helmet", 2500),
                new CraftingMapping("minecraft:leather_chestplate", 4000),
                new CraftingMapping("minecraft:leather_leggings", 3500),
                new CraftingMapping("minecraft:leather_boots", 2000),
                new CraftingMapping("minecraft:stone_shovel", 1000),
                new CraftingMapping("minecraft:stone_hoe", 2000),
                new CraftingMapping("minecraft:stone_sword", 2000),
                new CraftingMapping("minecraft:stone_pickaxe", 3000),
                new CraftingMapping("minecraft:stone_axe", 3000),

                // Gold
                new CraftingMapping("minecraft:golden_helmet", 25000),
                new CraftingMapping("minecraft:golden_chestplate", 40000),
                new CraftingMapping("minecraft:golden_leggings", 35000),
                new CraftingMapping("minecraft:golden_boots", 20000),
                new CraftingMapping("minecraft:golden_shovel", 5000),
                new CraftingMapping("minecraft:golden_hoe", 10000),
                new CraftingMapping("minecraft:golden_sword", 10000),
                new CraftingMapping("minecraft:golden_pickaxe", 15000),
                new CraftingMapping("minecraft:golden_axe", 15000),

                // Copper
                new CraftingMapping("minecraft:copper_helmet", 12500),
                new CraftingMapping("minecraft:copper_chestplate", 20000),
                new CraftingMapping("minecraft:copper_leggings", 17500),
                new CraftingMapping("minecraft:copper_boots", 10000),
                new CraftingMapping("minecraft:copper_shovel", 2500),
                new CraftingMapping("minecraft:copper_hoe", 5000),
                new CraftingMapping("minecraft:copper_sword", 5000),
                new CraftingMapping("minecraft:copper_pickaxe", 7500),
                new CraftingMapping("minecraft:copper_axe", 7500),

                // Iron
                new CraftingMapping("minecraft:iron_helmet", 15000),
                new CraftingMapping("minecraft:iron_chestplate", 24000),
                new CraftingMapping("minecraft:iron_leggings", 21000),
                new CraftingMapping("minecraft:iron_boots", 12000),
                new CraftingMapping("minecraft:iron_shovel", 3000),
                new CraftingMapping("minecraft:iron_hoe", 6000),
                new CraftingMapping("minecraft:iron_sword", 6000),
                new CraftingMapping("minecraft:iron_pickaxe", 9000),
                new CraftingMapping("minecraft:iron_axe", 9000),

                // Diamond
                new CraftingMapping("minecraft:diamond_helmet", 40000),
                new CraftingMapping("minecraft:diamond_chestplate", 80000),
                new CraftingMapping("minecraft:diamond_leggings", 70000),
                new CraftingMapping("minecraft:diamond_boots", 40000),
                new CraftingMapping("minecraft:diamond_shovel", 10000),
                new CraftingMapping("minecraft:diamond_hoe", 20000),
                new CraftingMapping("minecraft:diamond_sword", 20000),
                new CraftingMapping("minecraft:diamond_pickaxe", 30000),
                new CraftingMapping("minecraft:diamond_axe", 30000),

                new CraftingMapping("minecraft:mace", 50000),

                new CraftingMapping("minecraft:crossbow", 7500),
                new CraftingMapping("minecraft:bow", 2500),
        };
        for (CraftingMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", mapping.item);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }
        json.add("crafting_mappings", mappings);
        return json;
    }

    /**
     * Gets the XP for crafting an item.
     */
    public static int getCraftingXP(String itemTranslationKey, Skills skill) {
        return CRAFTING_XP_MAP.getOrDefault(itemTranslationKey, getBaseXP(skill));
    }

    /**
     * Loads a JSON file or creates it with the default content if it doesn't exist.
     */
    private static JsonObject loadJsonFile(Path filePath, JsonObject defaultJson) throws IOException {
        if (Files.exists(filePath)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                return GSON.fromJson(reader.lines().collect(Collectors.joining("\n")), JsonObject.class);
            }
        } else {
            Files.createDirectories(filePath.getParent());
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                GSON.toJson(defaultJson, writer);
            }
            return defaultJson;
        }
    }

    // New method to load crafting multipliers
    private static void loadCraftingMultipliersConfig() {
        Path filePath = CONFIG_DIR.resolve("crafting_multipliers.json");
        try {
            craftingMultipliersConfig = loadJsonFile(filePath, getDefaultCraftingMultipliersConfig());
            Simpleskills.LOGGER.info("Loaded crafting_multipliers.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in crafting_multipliers.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading crafting_multipliers.json: {}", e.getMessage());
        }
    }

    // Default crafting multipliers configuration
    private static JsonObject getDefaultCraftingMultipliersConfig() {
        JsonObject json = new JsonObject();
        JsonObject durability = new JsonObject();
        durability.addProperty("level_25", 1.05f);
        durability.addProperty("level_50", 1.10f);
        durability.addProperty("level_75", 1.15f);
        durability.addProperty("level_99", 1.25f);
        JsonObject recovery = new JsonObject();
        recovery.addProperty("level_25", 0.05f);
        recovery.addProperty("level_50", 0.10f);
        recovery.addProperty("level_75", 0.15f);
        recovery.addProperty("level_99", 0.25f);
        json.add("durability_multipliers", durability);
        json.add("recovery_chances", recovery);
        return json;
    }

    // Get durability multiplier based on level
    public static float getCraftingDurabilityMultiplier(int level) {
        JsonObject durability = craftingMultipliersConfig.getAsJsonObject("durability_multipliers");
        if (durability == null) {
            Simpleskills.LOGGER.warn("Missing durability_multipliers in crafting_multipliers.json, using defaults");
            return level >= 99 ? 1.20f : level >= 75 ? 1.15f : level >= 50 ? 1.10f : level >= 25 ? 1.05f : 1.0f;
        }
        if (level >= 99 && durability.has("level_99")) return durability.get("level_99").getAsFloat();
        else if (level >= 75 && durability.has("level_75")) return durability.get("level_75").getAsFloat();
        else if (level >= 50 && durability.has("level_50")) return durability.get("level_50").getAsFloat();
        else if (level >= 25 && durability.has("level_25")) return durability.get("level_25").getAsFloat();
        return 1.0f;
    }

    // Get recovery chance based on level
    public static float getCraftingRecoveryChance(int level) {
        JsonObject recovery = craftingMultipliersConfig.getAsJsonObject("recovery_chances");
        if (recovery == null) {
            Simpleskills.LOGGER.warn("Missing recovery_chances in crafting_multipliers.json, using defaults");
            return level >= 99 ? 0.15f : level >= 75 ? 0.075f : level >= 50 ? 0.05f : level >= 25 ? 0.025f : 0.0f;
        }
        if (level >= 99 && recovery.has("level_99")) return recovery.get("level_99").getAsFloat();
        else if (level >= 75 && recovery.has("level_75")) return recovery.get("level_75").getAsFloat();
        else if (level >= 50 && recovery.has("level_50")) return recovery.get("level_50").getAsFloat();
        else if (level >= 25 && recovery.has("level_25")) return recovery.get("level_25").getAsFloat();
        return 0.0f;
    }

    // New method to load crafting recovery blacklist
    private static void loadCraftingRecoveryBlacklist() {
        Path filePath = CONFIG_DIR.resolve("crafting_recovery_blacklist.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultCraftingRecoveryBlacklist());
            CRAFTING_RECOVERY_BLACKLIST.clear();
            JsonArray blacklist = json.getAsJsonArray("blacklist");
            if (blacklist != null) {
                for (JsonElement element : blacklist) {
                    String itemId = element.getAsString();
                    CRAFTING_RECOVERY_BLACKLIST.add(itemId);
                }
            }
            Simpleskills.LOGGER.info("Loaded crafting_recovery_blacklist.json with {} entries", CRAFTING_RECOVERY_BLACKLIST.size());
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in crafting_recovery_blacklist.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading crafting_recovery_blacklist.json: {}", e.getMessage());
        }
    }

    // Default crafting recovery blacklist configuration
    private static JsonObject getDefaultCraftingRecoveryBlacklist() {
        JsonObject json = new JsonObject();
        JsonArray blacklist = new JsonArray();
        String[] blacklistedItems = {
                "minecraft:gold_nugget",
                "minecraft:gold_ingot",
                "minecraft:gold_block",
                "minecraft:iron_nugget",
                "minecraft:iron_ingot",
                "minecraft:iron_block",
                "minecraft:copper_ingot",
                "minecraft:copper_block",
                "minecraft:copper_nugget",
                "minecraft:waxed_copper_block",
                "minecraft:netherite_ingot",
                "minecraft:netherite_block",
                "minecraft:raw_iron",
                "minecraft:raw_iron_block",
                "minecraft:raw_gold",
                "minecraft:raw_gold_block",
                "minecraft:raw_copper",
                "minecraft:raw_copper_block",
                "minecraft:diamond",
                "minecraft:diamond_block",
                "minecraft:emerald",
                "minecraft:emerald_block",
                "minecraft:coal",
                "minecraft:coal_block",
                "minecraft:lapis_lazuli",
                "minecraft:lapis_lazuli_block",
                "minecraft:resin",
                "minecraft:resin_block",
                "minecraft:redstone",
                "minecraft:redstone_block",
                "minecraft:amethyst_shard",
                "minecraft:amethyst_block",
                "minecraft:wheat",
                "minecraft:hay_block",
                "minecraft:slime_ball",
                "minecraft:slime_block",
                "minecraft:honey_bottle",
                "minecraft:honey_block",
                "minecraft:dried_kelp",
                "minecraft:dried_kelp_block",
                "minecraft:bamboo",
                "minecraft:bamboo_block",
                "minecraft:snowball",
                "minecraft:snow_block",
                "minecraft:clay_ball",
                "minecraft:clay",
                "minecraft:glowstone_dust",
                "minecraft:glowstone",
                "minecraft:bone_meal",
                "minecraft:bone_block",
                "minecraft:wool",
                "minecraft:string",
                "minecraft:quartz_block",
                "minecraft:quartz,",
                "minecraft:nether_wart",
                "minecraft:nether_wart_block",
                "minecraft:ice",
                "minecraft:packed_ice",
                "minecraft:blue_ice",
        };
        for (String item : blacklistedItems) {
            blacklist.add(item);
        }
        json.add("blacklist", blacklist);
        return json;
    }

    // Check if a recipe's output item is blacklisted
    public static boolean isRecipeBlacklisted(String itemId) {
        return CRAFTING_RECOVERY_BLACKLIST.contains(itemId);
    }

    /**
     * Loads smelting crafting XP mappings from smelting_crafting_xp.json.
     */
    private static void loadSmeltingCraftingXPConfig() {
        Path filePath = CONFIG_DIR.resolve("smelting_crafting_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultSmeltingCraftingXPConfig());
            var mappings = json.getAsJsonArray("smelting_crafting_mappings");
            SMELTING_CRAFTING_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String item = mapping.get("item").getAsString();
                int xp = mapping.get("xp").getAsInt();

                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Invalid XP value {} for item {} in smelting_crafting_xp.json, skipping", xp, item);
                    continue;
                }
                SMELTING_CRAFTING_XP_MAP.put(item, xp);
            }
            Simpleskills.LOGGER.info("Loaded smelting_crafting_xp.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in smelting_crafting_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading smelting_crafting_xp.json: {}", e.getMessage());
        }
    }

    private static JsonObject getDefaultSmeltingCraftingXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record SmeltingCraftingXP(String item, int xp) {
        }
        SmeltingCraftingXP[] defaults = {
                new SmeltingCraftingXP("item.minecraft.copper_ingot", 100),
                new SmeltingCraftingXP("item.minecraft.copper_nugget", 70),
                new SmeltingCraftingXP("item.minecraft.iron_ingot", 150),
                new SmeltingCraftingXP("item.minecraft.iron_nugget", 70),
                new SmeltingCraftingXP("item.minecraft.gold_ingot", 200),
                new SmeltingCraftingXP("item.minecraft.gold_nugget", 80),
                new SmeltingCraftingXP("item.minecraft.netherite_scrap", 500),
                new SmeltingCraftingXP("item.minecraft.redstone", 50),
                new SmeltingCraftingXP("item.minecraft.coal", 30),
                new SmeltingCraftingXP("item.minecraft.emerald", 300),
                new SmeltingCraftingXP("item.minecraft.lapis_lazuli", 100),
                new SmeltingCraftingXP("item.minecraft.diamond", 400),
                new SmeltingCraftingXP("item.minecraft.quartz", 80)
        };
        for (SmeltingCraftingXP config : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", config.item);
            entry.addProperty("xp", config.xp);
            mappings.add(entry);
        }
        json.add("smelting_crafting_mappings", mappings);
        return json;
    }

    /**
     * Gets the smelting crafting XP for an item.
     */
    public static int getSmeltingCraftingXP(String itemKey, Skills skill) {
        return SMELTING_CRAFTING_XP_MAP.getOrDefault(itemKey, 0);
    }

    private static JsonObject getDefaultBlockMappings() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();

        // Block mapping record for simplicity
        record BlockMapping(String block, String skill, int xp) {
        }

        // Wood types for pattern matching
        String[] woodTypes = {
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "bamboo", "pale_oak", "crimson", "warped"
        };

        // Stone/mineral types for pattern matching
        String[] stoneTypes = {
                "stone", "cobblestone", "mossy_cobblestone", "smooth_stone",
                "granite", "polished_granite", "diorite", "polished_diorite",
                "andesite", "polished_andesite", "deepslate", "cobbled_deepslate",
                "polished_deepslate", "deepslate_bricks", "cracked_deepslate_bricks",
                "deepslate_tiles", "cracked_deepslate_tiles", "chiseled_deepslate",
                "tuff", "polished_tuff", "tuff_bricks", "chiseled_tuff",
                "basalt", "smooth_basalt", "polished_basalt", "blackstone",
                "polished_blackstone", "polished_blackstone_bricks",
                "chiseled_polished_blackstone", "cracked_polished_blackstone",
                "gilded_blackstone", "obsidian", "crying_obsidian",
                "quartz_block", "smooth_quartz", "chiseled_quartz_block",
                "quartz_bricks", "prismarine", "prismarine_bricks", "dark_prismarine",
                "purpur_block", "purpur_pillar", "end_stone", "end_stone_bricks",
                "sandstone", "red_sandstone", "smooth_sandstone", "smooth_red_sandstone",
                "chiseled_sandstone", "chiseled_red_sandstone", "cut_sandstone",
                "cut_red_sandstone", "calcite", "dripstone_block"
        };

        List<BlockMapping> defaults = new ArrayList<>();

        // === WOODCUTTING MAPPINGS ===
        for (String wood : woodTypes) {
            if (wood.equals("crimson") || wood.equals("warped")) {
                defaults.add(new BlockMapping("block.minecraft." + wood + "_stem", "WOODCUTTING", 150));
                defaults.add(new BlockMapping("block.minecraft.stripped_" + wood + "_stem", "WOODCUTTING", 150));
            } else {
                defaults.add(new BlockMapping("block.minecraft." + wood + "_log", "WOODCUTTING", 150));
                defaults.add(new BlockMapping("block.minecraft.stripped_" + wood + "_log", "WOODCUTTING", 150));
            }
        }

        for (String wood : woodTypes) {
            defaults.add(new BlockMapping("block.minecraft." + wood + "_planks", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_stairs", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_slab", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_fence", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_fence_gate", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_door", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_trapdoor", "WOODCUTTING", 100));
            defaults.add(new BlockMapping("block.minecraft." + wood + "_shelf", "WOODCUTTING", 100));

            if (!wood.equals("crimson") && !wood.equals("warped")) {
                defaults.add(new BlockMapping("block.minecraft." + wood + "_wood", "WOODCUTTING", 100));
                defaults.add(new BlockMapping("block.minecraft.stripped_" + wood + "_wood", "WOODCUTTING", 100));
            } else {
                defaults.add(new BlockMapping("block.minecraft." + wood + "_hyphae", "WOODCUTTING", 100));
                defaults.add(new BlockMapping("block.minecraft.stripped_" + wood + "_hyphae", "WOODCUTTING", 100));
            }
        }

        defaults.add(new BlockMapping("block.minecraft.bamboo_block", "WOODCUTTING", 100));
        defaults.add(new BlockMapping("block.minecraft.stripped_bamboo_block", "WOODCUTTING", 100));

        // === MINING MAPPINGS ===
        for (String stone : stoneTypes) {
            int xp = 100;
            if (stone.contains("sandstone")) {
                xp = 30; // override sandstone family
            }

            defaults.add(new BlockMapping("block.minecraft." + stone, "MINING", xp));

            if (!stone.equals("crying_obsidian") && !stone.equals("calcite")) {
                defaults.add(new BlockMapping("block.minecraft." + stone + "_slab", "MINING", xp));
            }

            if (!stone.equals("crying_obsidian") && !stone.equals("calcite") &&
                    !stone.equals("obsidian") && !stone.endsWith("_pillar")) {
                defaults.add(new BlockMapping("block.minecraft." + stone + "_stairs", "MINING", xp));
            }

            if (!stone.equals("crying_obsidian") && !stone.equals("calcite") &&
                    !stone.equals("obsidian") && !stone.endsWith("_pillar") &&
                    !stone.contains("smooth") && !stone.contains("cut")) {
                defaults.add(new BlockMapping("block.minecraft." + stone + "_wall", "MINING", xp));
            }
        }

        defaults.add(new BlockMapping("block.minecraft.netherrack", "MINING", 10));

        // Mining: Overworld and Deepslate Ores (keeping original values)
        defaults.add(new BlockMapping("block.minecraft.coal_ore", "MINING", 200));           // 100 * 2.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_coal_ore", "MINING", 200));
        defaults.add(new BlockMapping("block.minecraft.copper_ore", "MINING", 250));         // 100 * 2.5
        defaults.add(new BlockMapping("block.minecraft.deepslate_copper_ore", "MINING", 250));
        defaults.add(new BlockMapping("block.minecraft.iron_ore", "MINING", 300));           // 100 * 3.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_iron_ore", "MINING", 300));
        defaults.add(new BlockMapping("block.minecraft.redstone_ore", "MINING", 400));       // 100 * 4.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_redstone_ore", "MINING", 400));
        defaults.add(new BlockMapping("block.minecraft.gold_ore", "MINING", 500));           // 100 * 5.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_gold_ore", "MINING", 500));
        defaults.add(new BlockMapping("block.minecraft.lapis_ore", "MINING", 550));          // 100 * 5.5
        defaults.add(new BlockMapping("block.minecraft.deepslate_lapis_ore", "MINING", 550));
        defaults.add(new BlockMapping("block.minecraft.emerald_ore", "MINING", 800));        // 100 * 8.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_emerald_ore", "MINING", 800));
        defaults.add(new BlockMapping("block.minecraft.diamond_ore", "MINING", 1000));       // 100 * 10.0
        defaults.add(new BlockMapping("block.minecraft.deepslate_diamond_ore", "MINING", 1000));

        // Mining: Nether Ores
        defaults.add(new BlockMapping("block.minecraft.nether_quartz_ore", "MINING", 150));  // 100 * 1.5
        defaults.add(new BlockMapping("block.minecraft.nether_gold_ore", "MINING", 150));    // More common than overworld gold

        // === EXCAVATION MAPPINGS ===

        // Excavation: Dirt-type blocks
        defaults.add(new BlockMapping("block.minecraft.dirt", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.grass_block", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.podzol", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.mycelium", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.farmland", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.dirt_path", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.mud", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.clay", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.sand", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.gravel", "EXCAVATING", 50));
        defaults.add(new BlockMapping("block.minecraft.red_sand", "EXCAVATING", 50));

        // Concrete powder blocks
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"};
        for (String color : colors) {
            defaults.add(new BlockMapping("block.minecraft." + color + "_concrete_powder", "EXCAVATING", 50));
        }

        // === FARMING MAPPINGS ===

        // Farming: Crops
        defaults.add(new BlockMapping("block.minecraft.wheat", "FARMING", 300));
        defaults.add(new BlockMapping("block.minecraft.carrots", "FARMING", 300));
        defaults.add(new BlockMapping("block.minecraft.potatoes", "FARMING", 300));
        defaults.add(new BlockMapping("block.minecraft.beetroots", "FARMING", 250));
        defaults.add(new BlockMapping("block.minecraft.melon", "FARMING", 300));
        defaults.add(new BlockMapping("block.minecraft.bamboo", "FARMING", 10));
        defaults.add(new BlockMapping("block.minecraft.kelp", "FARMING", 10));
        defaults.add(new BlockMapping("block.minecraft.nether_wart", "FARMING", 350));
        defaults.add(new BlockMapping("block.minecraft.cocoa", "FARMING", 250));

        // Convert to JSON
        for (BlockMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("block", mapping.block);
            entry.addProperty("skill", mapping.skill);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }

        json.add("block_mappings", mappings);
        return json;
    }

    /**
     * Loads smithing XP mappings from smithing_xp.json.
     */
    private static void loadSmithingXPConfig() {
        Path filePath = CONFIG_DIR.resolve("smithing_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultSmithingXPConfig());
            var mappings = json.getAsJsonArray("smithing_mappings");
            SMITHING_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String action = mapping.get("action").getAsString();
                if (!mapping.has("xp") || !mapping.get("xp").isJsonPrimitive() || !mapping.get("xp").getAsJsonPrimitive().isNumber()) {
                    Simpleskills.LOGGER.warn("Invalid or missing 'xp' value for action {} in smithing_xp.json, skipping", action);
                    continue;
                }
                float xp = mapping.get("xp").getAsFloat();
                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Negative XP value {} for action {} in smithing_xp.json, skipping", xp, action);
                    continue;
                }
                SMITHING_XP_MAP.put(action, xp);
            }
            Simpleskills.LOGGER.info("Loaded {} smithing XP mappings.", SMITHING_XP_MAP.size());
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in smithing_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading smithing_xp.json: {}", e.getMessage());
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Unexpected error loading smithing_xp.json: {}", e.getMessage());
        }
    }

    /**
     * Default smithing XP configuration.
     */
    private static JsonObject getDefaultSmithingXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record SmithingMapping(String action, float xp) {}
        SmithingMapping[] defaults = {
                new SmithingMapping("repair:minecraft:leather", 9.0f),
                new SmithingMapping("repair:minecraft:copper_ingot", 9.5f),
                new SmithingMapping("repair:minecraft:gold_ingot", 9.5f),
                new SmithingMapping("repair:minecraft:turtle_scute", 10.0f),
                new SmithingMapping("repair:minecraft:iron_ingot", 10.0f),
                new SmithingMapping("repair:minecraft:phantom_membrane", 11.5f),
                new SmithingMapping("repair:minecraft:diamond", 11.0f),
                new SmithingMapping("repair:minecraft:netherite_ingot", 11.5f)
        };
        for (SmithingMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("action", mapping.action);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }
        json.add("smithing_mappings", mappings);
        return json;
    }

    /**
     * Default smithing multiplier configuration.
     */
    public static JsonObject getSmithingMultiplierConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record SmithingMultiplierMapping(String levelRange, float multiplier) {}
        SmithingMultiplierMapping[] defaults = {
                new SmithingMultiplierMapping("1-24", 1.00f),
                new SmithingMultiplierMapping("25-49", 1.050f),
                new SmithingMultiplierMapping("50-74", 1.075f),
                new SmithingMultiplierMapping("75-98", 1.10f),
                new SmithingMultiplierMapping("99-99", 1.20f)
        };
        for (SmithingMultiplierMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("level_range", mapping.levelRange);
            entry.addProperty("multiplier", mapping.multiplier);
            mappings.add(entry);
        }
        json.add("multiplier_mappings", mappings);
        return json;
    }

    /**
     * Loads smithing multiplier mappings from smithing_multipliers.json.
     */
    private static void loadSmithingMultiplierConfig() {
        Path filePath = CONFIG_DIR.resolve("smithing_multipliers.json");
        try {
            JsonObject json = loadJsonFile(filePath, getSmithingMultiplierConfig());
            var mappings = json.getAsJsonArray("multiplier_mappings");
            SMITHING_MULTIPLIER_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String levelRange = mapping.get("level_range").getAsString();
                float multiplier = mapping.get("multiplier").getAsFloat();
                if (multiplier < 0) {
                    Simpleskills.LOGGER.warn("Invalid multiplier value {} for level range {} in smithing_multipliers.json, skipping", multiplier, levelRange);
                    continue;
                }
                SMITHING_MULTIPLIER_MAP.put(levelRange, multiplier);
            }
            Simpleskills.LOGGER.info("Loaded smithing_multipliers.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in smithing_multipliers.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading smithing_multipliers.json: {}", e.getMessage());
        }
    }

    /**
     * Gets the smithing multiplier for a given level.
     */
    public static float getSmithingMultiplier(int level) {
        for (Map.Entry<String, Float> entry : SMITHING_MULTIPLIER_MAP.entrySet()) {
            String[] range = entry.getKey().split("-");
            int minLevel = Integer.parseInt(range[0]);
            int maxLevel = Integer.parseInt(range[1]);
            if (level >= minLevel && level <= maxLevel) {
                return entry.getValue();
            }
        }
        return 1.0f; // Default multiplier if no range matches
    }

    /**
     * Default agility XP configuration.
     */
    private static JsonObject getDefaultAgilityXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record AgilityMapping(String action, int xp) {
        }
        AgilityMapping[] defaults = {
                new AgilityMapping("fall_damage", 60), // Base XP per heart of damage
                new AgilityMapping("jump", 20),
                new AgilityMapping("sprint", 20),
                new AgilityMapping("swim", 25),
                new AgilityMapping("sneak", 20)
        };
        for (AgilityMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("action", mapping.action);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }
        json.add("agility_mappings", mappings);
        return json;
    }

    /**
     * Loads alchemy multiplier mappings from alchemy_multipliers.json.
     */
    private static void loadAlchemyMultiplierConfig() {
        Path filePath = CONFIG_DIR.resolve("alchemy_multipliers.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultAlchemyMultiplierConfig());
            var mappings = json.getAsJsonArray("multiplier_mappings");
            ALCHEMY_MULTIPLIER_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String levelRange = mapping.get("level_range").getAsString();
                float multiplier = mapping.get("multiplier").getAsFloat();
                if (multiplier < 0) {
                    Simpleskills.LOGGER.warn("Invalid multiplier value {} for level range {} in alchemy_multipliers.json, skipping", multiplier, levelRange);
                    continue;
                }
                ALCHEMY_MULTIPLIER_MAP.put(levelRange, multiplier);
            }
            Simpleskills.LOGGER.info("Loaded alchemy_multipliers.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in alchemy_multipliers.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading alchemy_multipliers.json: {}", e.getMessage());
        }
    }

    /**
     * Default alchemy multiplier configuration.
     */
    private static JsonObject getDefaultAlchemyMultiplierConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record AlchemyMultiplierMapping(String levelRange, float multiplier) {}
        AlchemyMultiplierMapping[] defaults = {
                new AlchemyMultiplierMapping("1-24", 1.00f),
                new AlchemyMultiplierMapping("25-49", 1.25f),
                new AlchemyMultiplierMapping("50-74", 1.50f),
                new AlchemyMultiplierMapping("75-98", 1.75f),
                new AlchemyMultiplierMapping("99-99", 3.00f)
        };
        for (AlchemyMultiplierMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("level_range", mapping.levelRange);
            entry.addProperty("multiplier", mapping.multiplier);
            mappings.add(entry);
        }
        json.add("multiplier_mappings", mappings);
        return json;
    }

    /**
     * Gets the multiplier for an alchemy level.
     */
    public static float getAlchemyMultiplier(int level) {
        if (level >= 99) return ALCHEMY_MULTIPLIER_MAP.getOrDefault("99-99", 2.00f);
        else if (level >= 75) return ALCHEMY_MULTIPLIER_MAP.getOrDefault("75-98", 1.75f);
        else if (level >= 50) return ALCHEMY_MULTIPLIER_MAP.getOrDefault("50-74", 1.50f);
        else if (level >= 25) return ALCHEMY_MULTIPLIER_MAP.getOrDefault("25-49", 1.25f);
        else return ALCHEMY_MULTIPLIER_MAP.getOrDefault("1-24", 1.00f);
    }

    // Existing methods (unchanged, included for context)
    private static void loadAlchemyXPConfig() {
        Path filePath = CONFIG_DIR.resolve("alchemy_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultAlchemyXPConfig());
            var mappings = json.getAsJsonArray("alchemy_mappings");
            ALCHEMY_XP_MAP.clear();
            for (var element : mappings) {
                JsonObject mapping = element.getAsJsonObject();
                String potion = mapping.get("potion").getAsString();
                int xp = mapping.get("xp").getAsInt();
                if (xp < 0) {
                    Simpleskills.LOGGER.warn("Invalid XP value {} for potion {} in alchemy_xp.json, skipping", xp, potion);
                    continue;
                }
                ALCHEMY_XP_MAP.put(potion, xp);
            }
            Simpleskills.LOGGER.info("Loaded alchemy_xp.json");
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in alchemy_xp.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading alchemy_xp.json: {}", e.getMessage());
        }
    }

    public static int getAlchemyXP(String potionTranslationKey, Skills skill) {
        return ALCHEMY_XP_MAP.getOrDefault(potionTranslationKey, getBaseXP(skill));
    }

    private static JsonObject getDefaultAlchemyXPConfig() {
        JsonObject json = new JsonObject();
        JsonArray mappings = new JsonArray();
        record AlchemyMapping(String potion, int xp) {}
        AlchemyMapping[] defaults = {
                new AlchemyMapping("potion.minecraft.turtle_master", 2500),
                new AlchemyMapping("potion.minecraft.long_turtle_master", 2600),
                new AlchemyMapping("potion.minecraft.strong_turtle_master", 2900),
                new AlchemyMapping("potion.minecraft.oozing", 2400),
                new AlchemyMapping("potion.minecraft.wind_charged", 2250),
                new AlchemyMapping("potion.minecraft.weaving", 2150),
                new AlchemyMapping("potion.minecraft.slow_falling", 2000),
                new AlchemyMapping("potion.minecraft.long_slow_falling", 2150),
                new AlchemyMapping("potion.minecraft.invisibility", 1850),
                new AlchemyMapping("potion.minecraft.long_invisibility", 2000),
                new AlchemyMapping("potion.minecraft.regeneration", 1750),
                new AlchemyMapping("potion.minecraft.long_regeneration", 1850),
                new AlchemyMapping("potion.minecraft.strong_regeneration", 2150),
                new AlchemyMapping("potion.minecraft.fire_resistance", 1600),
                new AlchemyMapping("potion.minecraft.long_fire_resistance", 1750),
                new AlchemyMapping("potion.minecraft.leaping", 1500),
                new AlchemyMapping("potion.minecraft.long_leaping", 1600),
                new AlchemyMapping("potion.minecraft.strong_leaping", 1900),
                new AlchemyMapping("potion.minecraft.night_vision", 1400),
                new AlchemyMapping("potion.minecraft.long_night_vision", 1500),
                new AlchemyMapping("potion.minecraft.slowness", 1250),
                new AlchemyMapping("potion.minecraft.long_slowness", 1400),
                new AlchemyMapping("potion.minecraft.strong_slowness", 1600),
                new AlchemyMapping("potion.minecraft.healing", 1150),
                new AlchemyMapping("potion.minecraft.strong_healing", 1500),
                new AlchemyMapping("potion.minecraft.harming", 1150),
                new AlchemyMapping("potion.minecraft.strong_harming", 1500),
                new AlchemyMapping("potion.minecraft.swiftness", 1150),
                new AlchemyMapping("potion.minecraft.long_swiftness", 1250),
                new AlchemyMapping("potion.minecraft.strong_swiftness", 1500),
                new AlchemyMapping("potion.minecraft.poison", 1150),
                new AlchemyMapping("potion.minecraft.long_poison", 1250),
                new AlchemyMapping("potion.minecraft.strong_poison", 1500),
                new AlchemyMapping("potion.minecraft.strength", 1150),
                new AlchemyMapping("potion.minecraft.long_strength", 1250),
                new AlchemyMapping("potion.minecraft.strong_strength", 1500),
                new AlchemyMapping("splash_potion.minecraft.turtle_master", 2750),
                new AlchemyMapping("splash_potion.minecraft.long_turtle_master", 2900),
                new AlchemyMapping("splash_potion.minecraft.strong_turtle_master", 3150),
                new AlchemyMapping("splash_potion.minecraft.oozing", 2600),
                new AlchemyMapping("splash_potion.minecraft.wind_charged", 2500),
                new AlchemyMapping("splash_potion.minecraft.weaving", 2400),
                new AlchemyMapping("splash_potion.minecraft.slow_falling", 2250),
                new AlchemyMapping("splash_potion.minecraft.long_slow_falling", 2400),
                new AlchemyMapping("splash_potion.minecraft.invisibility", 2150),
                new AlchemyMapping("splash_potion.minecraft.long_invisibility", 2250),
                new AlchemyMapping("splash_potion.minecraft.regeneration", 2000),
                new AlchemyMapping("splash_potion.minecraft.long_regeneration", 2150),
                new AlchemyMapping("splash_potion.minecraft.strong_regeneration", 2400),
                new AlchemyMapping("splash_potion.minecraft.fire_resistance", 1900),
                new AlchemyMapping("splash_potion.minecraft.long_fire_resistance", 2000),
                new AlchemyMapping("splash_potion.minecraft.leaping", 1750),
                new AlchemyMapping("splash_potion.minecraft.long_leaping", 1900),
                new AlchemyMapping("splash_potion.minecraft.strong_leaping", 2150),
                new AlchemyMapping("splash_potion.minecraft.night_vision", 1600),
                new AlchemyMapping("splash_potion.minecraft.long_night_vision", 1750),
                new AlchemyMapping("splash_potion.minecraft.slowness", 1500),
                new AlchemyMapping("splash_potion.minecraft.long_slowness", 1600),
                new AlchemyMapping("splash_potion.minecraft.strong_slowness", 1900),
                new AlchemyMapping("splash_potion.minecraft.healing", 1450),
                new AlchemyMapping("splash_potion.minecraft.strong_healing", 1750),
                new AlchemyMapping("splash_potion.minecraft.harming", 1450),
                new AlchemyMapping("splash_potion.minecraft.strong_harming", 1750),
                new AlchemyMapping("splash_potion.minecraft.swiftness", 1450),
                new AlchemyMapping("splash_potion.minecraft.long_swiftness", 1500),
                new AlchemyMapping("splash_potion.minecraft.strong_swiftness", 1750),
                new AlchemyMapping("splash_potion.minecraft.poison", 1450),
                new AlchemyMapping("splash_potion.minecraft.long_poison", 1500),
                new AlchemyMapping("splash_potion.minecraft.strong_poison", 1750),
                new AlchemyMapping("splash_potion.minecraft.strength", 1450),
                new AlchemyMapping("splash_potion.minecraft.long_strength", 1500),
                new AlchemyMapping("splash_potion.minecraft.strong_strength", 1750),
                new AlchemyMapping("lingering_potion.minecraft.turtle_master", 3150),
                new AlchemyMapping("lingering_potion.minecraft.long_turtle_master", 3250),
                new AlchemyMapping("lingering_potion.minecraft.strong_turtle_master", 3500),
                new AlchemyMapping("lingering_potion.minecraft.oozing", 3000),
                new AlchemyMapping("lingering_potion.minecraft.wind_charged", 2900),
                new AlchemyMapping("lingering_potion.minecraft.weaving", 2750),
                new AlchemyMapping("lingering_potion.minecraft.slow_falling", 2600),
                new AlchemyMapping("lingering_potion.minecraft.long_slow_falling", 2750),
                new AlchemyMapping("lingering_potion.minecraft.invisibility", 2500),
                new AlchemyMapping("lingering_potion.minecraft.long_invisibility", 2600),
                new AlchemyMapping("lingering_potion.minecraft.regeneration", 2400),
                new AlchemyMapping("lingering_potion.minecraft.long_regeneration", 2500),
                new AlchemyMapping("lingering_potion.minecraft.strong_regeneration", 2750),
                new AlchemyMapping("lingering_potion.minecraft.fire_resistance", 2250),
                new AlchemyMapping("lingering_potion.minecraft.long_fire_resistance", 2400),
                new AlchemyMapping("lingering_potion.minecraft.leaping", 2150),
                new AlchemyMapping("lingering_potion.minecraft.long_leaping", 2250),
                new AlchemyMapping("lingering_potion.minecraft.strong_leaping", 2500),
                new AlchemyMapping("lingering_potion.minecraft.night_vision", 2000),
                new AlchemyMapping("lingering_potion.minecraft.long_night_vision", 2150),
                new AlchemyMapping("lingering_potion.minecraft.slowness", 1900),
                new AlchemyMapping("lingering_potion.minecraft.long_slowness", 2000),
                new AlchemyMapping("lingering_potion.minecraft.strong_slowness", 2250),
                new AlchemyMapping("lingering_potion.minecraft.healing", 1750),
                new AlchemyMapping("lingering_potion.minecraft.strong_healing", 2150),
                new AlchemyMapping("lingering_potion.minecraft.harming", 1750),
                new AlchemyMapping("lingering_potion.minecraft.strong_harming", 2150),
                new AlchemyMapping("lingering_potion.minecraft.swiftness", 1750),
                new AlchemyMapping("lingering_potion.minecraft.long_swiftness", 1850),
                new AlchemyMapping("lingering_potion.minecraft.strong_swiftness", 2150),
                new AlchemyMapping("lingering_potion.minecraft.poison", 1750),
                new AlchemyMapping("lingering_potion.minecraft.long_poison", 1850),
                new AlchemyMapping("lingering_potion.minecraft.strong_poison", 2150),
                new AlchemyMapping("lingering_potion.minecraft.strength", 1750),
                new AlchemyMapping("lingering_potion.minecraft.long_strength", 1850),
                new AlchemyMapping("lingering_potion.minecraft.strong_strength", 2150)
        };
        for (AlchemyMapping mapping : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("potion", mapping.potion);
            entry.addProperty("xp", mapping.xp);
            mappings.add(entry);
        }
        json.add("alchemy_mappings", mappings);
        return json;
    }

    /**
     * Default tool requirements configuration.
     */
    private static JsonObject getDefaultToolRequirements() {
        JsonObject json = new JsonObject();
        record ToolRequirement(String id, String skill, int level) {
        }
        ToolRequirement[] defaults = {
                // Wooden Tools
                new ToolRequirement("minecraft:wooden_pickaxe", "MINING", 0),
                new ToolRequirement("minecraft:wooden_axe", "WOODCUTTING", 0),
                new ToolRequirement("minecraft:wooden_shovel", "EXCAVATING", 0),
                new ToolRequirement("minecraft:wooden_hoe", "FARMING", 0),

                // Golden Tools
                new ToolRequirement("minecraft:golden_pickaxe", "MINING", 5),
                new ToolRequirement("minecraft:golden_axe", "WOODCUTTING", 5),
                new ToolRequirement("minecraft:golden_shovel", "EXCAVATING", 5),
                new ToolRequirement("minecraft:golden_hoe", "FARMING", 5),

                // Stone Tools
                new ToolRequirement("minecraft:stone_pickaxe", "MINING", 15),
                new ToolRequirement("minecraft:stone_axe", "WOODCUTTING", 15),
                new ToolRequirement("minecraft:stone_shovel", "EXCAVATING", 15),
                new ToolRequirement("minecraft:stone_hoe", "FARMING", 15),

                // Copper Tools
                new ToolRequirement("minecraft:copper_pickaxe", "MINING", 25),
                new ToolRequirement("minecraft:copper_axe", "WOODCUTTING", 25),
                new ToolRequirement("minecraft:copper_shovel", "EXCAVATING", 25),
                new ToolRequirement("minecraft:copper_hoe", "FARMING", 25),

                // Iron Tools
                new ToolRequirement("minecraft:iron_pickaxe", "MINING", 50),
                new ToolRequirement("minecraft:iron_axe", "WOODCUTTING", 50),
                new ToolRequirement("minecraft:iron_shovel", "EXCAVATING", 50),
                new ToolRequirement("minecraft:iron_hoe", "FARMING", 50),

                // Diamond Tools
                new ToolRequirement("minecraft:diamond_pickaxe", "MINING", 75),
                new ToolRequirement("minecraft:diamond_axe", "WOODCUTTING", 75),
                new ToolRequirement("minecraft:diamond_shovel", "EXCAVATING", 75),
                new ToolRequirement("minecraft:diamond_hoe", "FARMING", 75),

                // Netherite Tools
                new ToolRequirement("minecraft:netherite_pickaxe", "MINING", 99),
                new ToolRequirement("minecraft:netherite_axe", "WOODCUTTING", 99),
                new ToolRequirement("minecraft:netherite_shovel", "EXCAVATING", 99),
                new ToolRequirement("minecraft:netherite_hoe", "FARMING", 99)
        };

        for (ToolRequirement req : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", req.skill);
            entry.addProperty("level", req.level);
            json.add(req.id, entry);
        }
        return json;
    }

    /**
     * Default armor requirements configuration.
     */
    private static JsonObject getDefaultArmorRequirements() {
        JsonObject json = new JsonObject();
        record ArmorRequirement(String id, String skill, int level) {
        }
        ArmorRequirement[] defaults = {
                // Leather (starter)
                new ArmorRequirement("minecraft:leather_helmet", "DEFENSE", 0),
                new ArmorRequirement("minecraft:leather_chestplate", "DEFENSE", 0),
                new ArmorRequirement("minecraft:leather_leggings", "DEFENSE", 0),
                new ArmorRequirement("minecraft:leather_boots", "DEFENSE", 0),

                // Gold (early unlock, decorative but weak)
                new ArmorRequirement("minecraft:golden_helmet", "DEFENSE", 5),
                new ArmorRequirement("minecraft:golden_chestplate", "DEFENSE", 5),
                new ArmorRequirement("minecraft:golden_leggings", "DEFENSE", 5),
                new ArmorRequirement("minecraft:golden_boots", "DEFENSE", 5),

                // Copper (solid early-mid)
                new ArmorRequirement("minecraft:copper_helmet", "DEFENSE", 25),
                new ArmorRequirement("minecraft:copper_chestplate", "DEFENSE", 25),
                new ArmorRequirement("minecraft:copper_leggings", "DEFENSE", 25),
                new ArmorRequirement("minecraft:copper_boots", "DEFENSE", 25),

                // Chainmail (true mid-game)
                new ArmorRequirement("minecraft:chainmail_helmet", "DEFENSE", 35),
                new ArmorRequirement("minecraft:chainmail_chestplate", "DEFENSE", 35),
                new ArmorRequirement("minecraft:chainmail_leggings", "DEFENSE", 35),
                new ArmorRequirement("minecraft:chainmail_boots", "DEFENSE", 35),

                // Iron (late mid-game)
                new ArmorRequirement("minecraft:iron_helmet", "DEFENSE", 50),
                new ArmorRequirement("minecraft:iron_chestplate", "DEFENSE", 50),
                new ArmorRequirement("minecraft:iron_leggings", "DEFENSE", 50),
                new ArmorRequirement("minecraft:iron_boots", "DEFENSE", 50),

                // Diamond (late game grind)
                new ArmorRequirement("minecraft:diamond_helmet", "DEFENSE", 75),
                new ArmorRequirement("minecraft:diamond_chestplate", "DEFENSE", 75),
                new ArmorRequirement("minecraft:diamond_leggings", "DEFENSE", 75),
                new ArmorRequirement("minecraft:diamond_boots", "DEFENSE", 75),

                // Netherite (prestige / max)
                new ArmorRequirement("minecraft:netherite_helmet", "DEFENSE", 99),
                new ArmorRequirement("minecraft:netherite_chestplate", "DEFENSE", 99),
                new ArmorRequirement("minecraft:netherite_leggings", "DEFENSE", 99),
                new ArmorRequirement("minecraft:netherite_boots", "DEFENSE", 99)
        };
        for (ArmorRequirement req : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", req.skill);
            entry.addProperty("level", req.level);
            json.add(req.id, entry);
        }
        return json;
    }

    /**
     * Default weapon requirements configuration.
     */
    private static JsonObject getDefaultWeaponRequirements() {
        JsonObject json = new JsonObject();
        record WeaponRequirement(String id, String skill, int level) {
        }
        WeaponRequirement[] defaults = {
                // Wooden Weapons
                new WeaponRequirement("minecraft:wooden_sword", "SLAYING", 0),
                new WeaponRequirement("minecraft:wooden_axe", "SLAYING", 0),

                // Golden Weapons
                new WeaponRequirement("minecraft:golden_sword", "SLAYING", 5),
                new WeaponRequirement("minecraft:golden_axe", "SLAYING", 5),

                // Stone Weapons
                new WeaponRequirement("minecraft:stone_sword", "SLAYING", 15),
                new WeaponRequirement("minecraft:stone_axe", "SLAYING", 15),

                // Copper Weapons
                new WeaponRequirement("minecraft:copper_sword", "SLAYING", 25),
                new WeaponRequirement("minecraft:copper_axe", "SLAYING", 25),

                // Iron Weapons
                new WeaponRequirement("minecraft:iron_sword", "SLAYING", 50),
                new WeaponRequirement("minecraft:iron_axe", "SLAYING", 50),

                // Diamond Weapons
                new WeaponRequirement("minecraft:diamond_sword", "SLAYING", 75),
                new WeaponRequirement("minecraft:diamond_axe", "SLAYING", 75),

                // Netherite Weapons
                new WeaponRequirement("minecraft:netherite_sword", "SLAYING", 99),
                new WeaponRequirement("minecraft:netherite_axe", "SLAYING", 99),

                // Unique / Misc Weapons
                new WeaponRequirement("minecraft:mace", "SLAYING", 80),

                // Ranged Weapons (grouped separately)
                new WeaponRequirement("minecraft:crossbow", "RANGED", 0),
                new WeaponRequirement("minecraft:bow", "RANGED", 50),
                new WeaponRequirement("minecraft:trident", "RANGED", 99)
        };

        for (WeaponRequirement req : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", req.skill);
            entry.addProperty("level", req.level);
            json.add(req.id, entry);
        }
        return json;
    }

    /**
     * Loads fishing XP mappings from fishing_xp.json.
     */
    private static void loadFishingXPConfig() {
        Path filePath = CONFIG_DIR.resolve("fishing_xp.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultFishingXPConfig());
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String action = entry.getKey();
                int xp = entry.getValue().getAsInt();
                if (xp >= 0) {
                    FISHING_XP_MAP.put(action, xp);
                }
            }
        } catch (Exception e) {
            Simpleskills.LOGGER.warn("Error loading fishing_xp.json: {}", e.getMessage());
        }
    }

    private static JsonObject getDefaultFishingXPConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("catch", 550);
        return json;
    }

    private static void loadFishingLootConfig() {
        Path filePath = CONFIG_DIR.resolve("fishing_loot.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultFishingLootConfig());
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String range = entry.getKey();
                String lootTableStr = entry.getValue().getAsString();
                try {
                    Identifier id = Identifier.of(lootTableStr);
                    FISHING_LOOT_TABLES.put(range, id);
                } catch (Exception e) {
                    Simpleskills.LOGGER.warn("Invalid loot table ID {} for range {} in fishing_loot.json", lootTableStr, range);
                }
            }
        } catch (Exception e) {
            Simpleskills.LOGGER.warn("Error loading fishing_loot.json: {}", e.getMessage());
        }
    }

    private static JsonObject getDefaultFishingLootConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("1-24", "simpleskills:fishing/simpleskills_fishing_novice");
        json.addProperty("25-49", "simpleskills:fishing/simpleskills_fishing_journeyman");
        json.addProperty("50-74", "simpleskills:fishing/simpleskills_fishing_artisan");
        json.addProperty("75-98", "simpleskills:fishing/simpleskills_fishing_expert");
        json.addProperty("99-99", "simpleskills:fishing/simpleskills_fishing_grandmaster");
        return json;
    }

    /**
     * Default enchantment requirements configuration.
     */
    private static JsonObject getDefaultEnchantmentRequirements() {
        JsonObject json = new JsonObject();
        record EnchantmentRequirement(String id, String skill, int level, int enchantmentLevel) {
        }
        EnchantmentRequirement[] defaults = {
                new EnchantmentRequirement("minecraft:fortune", "ENCHANTING", 25, 3),
                new EnchantmentRequirement("minecraft:protection", "ENCHANTING", 50, 4),
                new EnchantmentRequirement("minecraft:efficiency", "ENCHANTING", 75, 5),
                new EnchantmentRequirement("minecraft:mending", "ENCHANTING", 99, 1)
        };
        for (EnchantmentRequirement req : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", req.skill);
            entry.addProperty("level", req.level);
            if (req.enchantmentLevel > 0) {
                entry.addProperty("enchantmentLevel", req.enchantmentLevel);
            }
            json.add(req.id, entry);
        }
        return json;
    }
    /**
     * Represents a prayer sacrifice configuration with effect level and ambient status.
     */
    public record PrayerSacrifice(
            Skills skill,
            int xp,
            SkillRequirement requirement,
            String effect,
            String displayName,
            int durationTicks,
            int effectLevel, // New field for effect level (e.g., 2 for Haste 2)
            boolean isAmbient // New field for ambient status
    ) {
    }

    /**
     * Loads prayer sacrifices from prayer_sacrifices.json.
     */
    private static void loadPrayerSacrifices() {
        Path filePath = CONFIG_DIR.resolve("prayer_sacrifices.json");
        try {
            JsonObject json = loadJsonFile(filePath, getDefaultPrayerSacrifices());
            PRAYER_SACRIFICES.clear();
            for (Map.Entry<String, JsonElement> entryElement : json.entrySet()) {
                String itemId = entryElement.getKey();
                JsonObject entry = entryElement.getValue().getAsJsonObject();
                String skillId = entry.get("skill").getAsString();
                Skills skill = Skills.valueOf(skillId.toUpperCase());
                int xp = entry.get("xp").getAsInt();
                int level = entry.get("level").getAsInt();
                String effect = entry.get("effect").getAsString();
                String displayName = entry.has("name") ? entry.get("name").getAsString() : capitalize(effect.split(":")[1].replace("_", " "));
                int duration = entry.get("duration").getAsInt();
                int effectLevel = entry.has("effectLevel") ? entry.get("effectLevel").getAsInt() : 1;
                boolean isAmbient = entry.has("isAmbient") && entry.get("isAmbient").getAsBoolean();
                SkillRequirement requirement = new SkillRequirement(skill, level, null);
                PRAYER_SACRIFICES.put(itemId, new PrayerSacrifice(skill, xp, requirement, effect, displayName, duration, effectLevel, isAmbient));
                Simpleskills.LOGGER.debug("Loaded prayer sacrifice: {} -> skill: {}, xp: {}, requirement: {}, effect: {}, displayName: {}, durationTicks: {}, effectLevel: {}, isAmbient: {}",
                        itemId, skill, xp, requirement, effect, displayName, duration, effectLevel, isAmbient);
            }
        } catch (JsonSyntaxException e) {
            Simpleskills.LOGGER.error("JSON syntax error in prayer_sacrifices.json: {}", e.getMessage());
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Error loading prayer_sacrifices.json: {}", e.getMessage());
        }
    }

    /**
     * Default prayer sacrifices configuration.
     */
    private static JsonObject getDefaultPrayerSacrifices() {
        JsonObject json = new JsonObject();
        record PrayerSacrificeConfig(String item, String skill, int xp, int level, String effect, int duration,
                                     String name, int effectLevel, boolean isAmbient) {
        }
        PrayerSacrificeConfig[] defaults = new PrayerSacrificeConfig[]{
                // Tier 1: 2h (7200s = 144000 ticks), novice buffs
                new PrayerSacrificeConfig("minecraft:rabbit_foot", "PRAYER", 4000, 0, "minecraft:luck", 144000, "Prayer I: Luck", 1, true),
                new PrayerSacrificeConfig("minecraft:blue_orchid", "PRAYER", 1000, 0, "minecraft:absorption", 144000, "Prayer I: Absorption", 3, true),
                new PrayerSacrificeConfig("minecraft:glow_ink_sac", "PRAYER", 1000, 0, "minecraft:dolphins_grace", 144000, "Prayer I: Dolphin's Grace", 1, true),
                // Tier 2: 4h (14400s = 288000 ticks), journeyman buffs
                new PrayerSacrificeConfig("minecraft:heart_of_the_sea", "PRAYER", 6000, 25, "minecraft:conduit_power", 288000, "Prayer II: Conduit Power", 1, true),
                new PrayerSacrificeConfig("minecraft:golden_apple", "PRAYER", 5000, 25, "minecraft:health_boost", 288000, "Prayer II: Health Boost", 1, true),
                new PrayerSacrificeConfig("minecraft:nautilus_shell", "PRAYER", 5000, 25, "minecraft:water_breathing", 288000, "Prayer II: Water Breathing", 1, true),
                // Tier 3: 6h (21600s = 432000 ticks), expert buffs
                new PrayerSacrificeConfig("minecraft:phantom_membrane", "PRAYER", 7000, 50, "minecraft:slow_falling", 432000, "Prayer III: Slow Falling", 1, true),
                new PrayerSacrificeConfig("minecraft:diamond", "PRAYER", 6500, 50, "minecraft:speed", 432000, "Prayer II: Speed", 2, true),
                new PrayerSacrificeConfig("minecraft:goat_horn", "PRAYER", 8000, 50, "minecraft:jump_boost", 432000, "Prayer III: Jump Boost", 2, true),
                // Tier 4: 8h (28800s = 576000 ticks), artisan buffs
                new PrayerSacrificeConfig("minecraft:pitcher_plant", "PRAYER", 9500, 75, "minecraft:strength", 576000, "Prayer IV: Strength", 2, true),
                new PrayerSacrificeConfig("minecraft:enchanted_golden_apple", "PRAYER", 9500, 75, "minecraft:resistance", 576000, "Prayer IV: Resistance", 2, true),
                new PrayerSacrificeConfig("minecraft:wither_skeleton_skull", "PRAYER", 9500, 75, "minecraft:fire_resistance", 576000, "Prayer IV: Fire Resistance", 1, true),
                // Tier 5: 12h (43200 = 864000 ticks), grandmaster buffs
                new PrayerSacrificeConfig("minecraft:torchflower", "PRAYER", 10000, 99, "minecraft:night_vision", 864000, "Prayer V: Night Vision", 1, true),
                new PrayerSacrificeConfig("minecraft:totem_of_undying", "PRAYER", 10000, 99, "minecraft:invisibility", 864000, "Prayer V: Invisibility", 1, true),
                new PrayerSacrificeConfig("minecraft:nether_star", "PRAYER", 10000, 99, "minecraft:haste", 864000, "Prayer V: Haste 2", 2, true)
        };
        for (PrayerSacrificeConfig config : defaults) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", config.skill);
            entry.addProperty("xp", config.xp);
            entry.addProperty("level", config.level);
            entry.addProperty("effect", config.effect);
            entry.addProperty("duration", config.duration);
            entry.addProperty("name", config.name);
            entry.addProperty("effectLevel", config.effectLevel);
            entry.addProperty("isAmbient", config.isAmbient);
            json.add(config.item, entry);
        }
        return json;
    }

    /**
     * Gets the prayer display name for a given status effect ID.
     *
     * @param effectId The ID of the status effect (e.g., "minecraft:luck").
     * @return The custom prayer name, or null if not found.
     */
    public static String getPrayerName(String effectId) {
        for (PrayerSacrifice sacrifice : PRAYER_SACRIFICES.values()) {
            if (sacrifice.effect().equals(effectId)) {
                return sacrifice.displayName();
            }
        }
        return null;
    }

    /**
     * Gets the prayer sacrifice configuration for an item.
     */
    public static PrayerSacrifice getPrayerSacrifice(String itemId) {
        return PRAYER_SACRIFICES.get(itemId);
    }

    /**
     * Gets the base XP for a skill.
     */
    public static int getBaseXP(Skills skill) {
        return BASE_XP_MAP.getOrDefault(skill, 100);
    }

    /**
     * Gets the skill associated with a block.
     */
    public static Skills getBlockSkill(String blockTranslationKey) {
        return BLOCK_SKILL_MAP.get(blockTranslationKey);
    }

    /**
     * Gets the XP for breaking a block.
     */
    public static int getBlockXP(String blockTranslationKey, Skills skill) {
        return BLOCK_XP_MAP.getOrDefault(blockTranslationKey, getBaseXP(skill));
    }

    /**
     * Gets the requirement for a tool.
     */
    public static SkillRequirement getToolRequirement(String id) {
        return TOOL_REQUIREMENTS.get(id);
    }

    /**
     * Gets the requirement for an armor piece.
     */
    public static SkillRequirement getArmorRequirement(String id) {
        return ARMOR_REQUIREMENTS.get(id);
    }

    /**
     * Gets the requirement for a weapon.
     */
    public static SkillRequirement getWeaponRequirement(String id) {
        return WEAPON_REQUIREMENTS.get(id);
    }

    /**
     * Gets the requirement for an enchantment or related block.
     */
    public static SkillRequirement getEnchantmentRequirement(String id) {
        return ENCHANTMENT_REQUIREMENTS.get(id);
    }

    /**
     * Gets the XP for an agility action.
     */
    public static int getAgilityXP(String action, Skills skill) {
        return AGILITY_XP_MAP.getOrDefault(action, getBaseXP(skill));
    }

    /**
     * Gets the XP for a smithing action.
     */
    public static float getSmithingXP(String action, Skills skill) {
        return SMITHING_XP_MAP.getOrDefault(action, (float) getBaseXP(skill));
    }

    public static Map<String, Float> getSmithingXPMap() {
        return SMITHING_XP_MAP;
    }

    /**
     * Gets the XP for a fishing action.
     */
    public static int getFishingXP(String action, Skills skill) {
        return FISHING_XP_MAP.getOrDefault(action, getBaseXP(skill));
    }

    /**
     * Gets the fishing loot table for a given skill level.
     */
    public static Identifier getFishingLootTable(int fishingLevel) {
        if (fishingLevel >= 99) {
            return FISHING_LOOT_TABLES.getOrDefault("99-99", Identifier.of("simpleskills", "fishing/simpleskills_fishing_grandmaster"));
        } else if (fishingLevel >= 75) {
            return FISHING_LOOT_TABLES.getOrDefault("75-98", Identifier.of("simpleskills", "fishing/simpleskills_fishing_expert"));
        } else if (fishingLevel >= 50) {
            return FISHING_LOOT_TABLES.getOrDefault("50-74", Identifier.of("simpleskills", "fishing/simpleskills_fishing_artisan"));
        } else if (fishingLevel >= 25) {
            return FISHING_LOOT_TABLES.getOrDefault("25-49", Identifier.of("simpleskills", "fishing/simpleskills_fishing_journeyman"));
        } else {
            return FISHING_LOOT_TABLES.getOrDefault("1-24", Identifier.of("simpleskills", "fishing/simpleskills_fishing_novice"));
        }
    }

    /**
     * Gets the feature configuration.
     */
    public static JsonObject getFeatureConfig() {
        return featureConfig;
    }
}