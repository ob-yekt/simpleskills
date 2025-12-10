package com.github.ob_yekt.simpleskills.requirements;

import com.github.ob_yekt.simpleskills.Skills;

/**
 * Represents a skill requirement for an item or action.
 */
public class SkillRequirement {
    private final Skills skill; // Enum for valid skills
    private final int level;   // Required level
    private final Integer enchantmentLevel; // Required enchantment level (nullable)
    private final int requiredPrestige; // Optional prestige requirement (default 0)

    /**
     * Constructs a SkillRequirement.
     * @param skill The skill type.
     * @param level The required skill level.
     * @param enchantmentLevel The required enchantment level, or null if not applicable.
     */
    public SkillRequirement(Skills skill, int level, Integer enchantmentLevel) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        if (level < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }
        this.skill = skill;
        this.level = level;
        this.enchantmentLevel = enchantmentLevel;
        this.requiredPrestige = 0; // default
    }

    /**
     * Constructs a SkillRequirement with an explicit prestige requirement.
     * @param skill The skill type.
     * @param level The required skill level.
     * @param enchantmentLevel The required enchantment level, or null if not applicable.
     * @param requiredPrestige The required prestige level (defaults to 0 if not used).
     */
    public SkillRequirement(Skills skill, int level, Integer enchantmentLevel, int requiredPrestige) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        if (level < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }
        if (requiredPrestige < 0) {
            throw new IllegalArgumentException("Prestige cannot be negative");
        }
        this.skill = skill;
        this.level = level;
        this.enchantmentLevel = enchantmentLevel;
        this.requiredPrestige = requiredPrestige;
    }

    public Skills getSkill() {
        return skill;
    }

    public int getLevel() {
        return level;
    }

    public Integer getEnchantmentLevel() {
        return enchantmentLevel;
    }

    public int getRequiredPrestige() {
        return requiredPrestige;
    }
}