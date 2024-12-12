package com.github.ob_yekt.simpleskills;

import java.util.HashMap;
import java.util.Map;

public class SkillRegistry {
    private static final Map<String, Skill> skills = new HashMap<>();

    public static void registerSkills() {
        skills.put("Mining", new MiningSkill());
        skills.put("Woodcutting", new WoodcuttingSkill());
    }

    public static Skill getSkill(String name) {
        return skills.get(name);
    }
}
