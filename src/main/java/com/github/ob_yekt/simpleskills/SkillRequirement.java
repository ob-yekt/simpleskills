package com.github.ob_yekt.simpleskills;

public class SkillRequirement {
    private final String skillName;
    private final int requiredLevel;

    public SkillRequirement(String skillName, int requiredLevel) {
        this.skillName = skillName;
        this.requiredLevel = requiredLevel;
    }

    public String getSkillName() {
        return skillName;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }
}