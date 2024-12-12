package com.github.ob_yekt.simpleskills;

public class MiningSkill extends Skill {
    private int xp = 0;

    public MiningSkill() {
        super("Mining");
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
