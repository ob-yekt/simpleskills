package com.github.ob_yekt.simpleskills;

public abstract class Skill {
    private final String name;

    public Skill(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void addXp(int amount);

    public abstract int getLevel();

    public abstract int getXp();
}
