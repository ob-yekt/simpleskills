package com.github.ob_yekt.simpleskills;

public class ExcavatingSkill extends Skill {
    private int xp = 0;

    public ExcavatingSkill() {
        super("Excavating");
    }

    @Override
    public void addXp(int amount) {
        this.xp += amount;
    }

    @Override
    public int getLevel() {
        return xp / 100; // 100 XP per level
    }

    @Override
    public int getXp() {
        return xp;
    }
}
