package com.github.ob_yekt.simpleskills;

public enum Skills {
    MINING,
    WOODCUTTING,
    EXCAVATING,
    FARMING,
    FISHING,
    DEFENSE,
    SLAYING,
    RANGED,
    ENCHANTING,
    ALCHEMY,
    SMITHING,
    COOKING,
    CRAFTING,
    AGILITY,
    PRAYER;

    /**
     * Returns the skill ID in uppercase (e.g., "MINING").
     * @return The skill ID.
     */
    public String getId() {
        return name().toUpperCase();
    }

    /**
     * Returns a display-friendly name for the skill (e.g., "Mining").
     * @return The formatted display name.
     */
    public String getDisplayName() {
        String name = name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}