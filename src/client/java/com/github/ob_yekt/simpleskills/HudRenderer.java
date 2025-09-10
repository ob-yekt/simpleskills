package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.managers.XPManager;
import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class HudRenderer {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Identifier HUD_ID = Identifier.of("simpleskills", "skill_hud");

    private static boolean isHudVisible = true; // Flag to track if the HUD is visible

    private static final KeyBinding hudToggleKey = new KeyBinding(
            "key.simpleskills.toggleHud", // Translation key
            InputUtil.Type.KEYSYM,        // Input type
            GLFW.GLFW_KEY_TAB,            // Default key: TAB
            "category.simpleskills.keys"  // Category
    );

    private static String cachedHudText = ""; // Stores the formatted HUD text
    private static final List<String> SKILL_ORDER = List.of( // Predefined skill order
            "SLAYING", "DEFENSE", "MINING", "WOODCUTTING", "EXCAVATING", "FARMING", "MAGIC"
    );

    public static void registerHud() {
        // Register the keybinding
        KeyBindingHelper.registerKeyBinding(hudToggleKey);

        // Combine updates and keybinding into one tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // Handle HUD toggle key press
                if (hudToggleKey.wasPressed() && isSingleplayer()) { // Check if key was pressed
                    isHudVisible = !isHudVisible; // Toggle HUD visibility
                    System.out.println("[simpleskills] HUD Toggled: " + isHudVisible); // Debug log
                }

                // Update HUD text if visible and in singleplayer
                if (isHudVisible && isSingleplayer()) {
                    cachedHudText = generateSkillText(); // Generate and cache updated skill text
                } else {
                    cachedHudText = ""; // Clear HUD text if HUD is hidden or not in singleplayer
                }
            }
        });

        // Register the HUD element using HudElementRegistry
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR, // Render after boss bar for a clean position
                HUD_ID,
                new SkillHudElement()
        );
    }

    /**
     * HUD element implementation for rendering the skill HUD.
     */
    private static class SkillHudElement implements HudElement {
        @Override
        public void render(DrawContext drawContext, RenderTickCounter tickCounter) {
            // Check if HUD can be rendered
            if (!isHudVisible || client.player == null || !isSingleplayer() || cachedHudText.isEmpty()) {
                return;
            }

            int x = 10; // X position for rendering
            int y = 10; // Y position for rendering

            // Render each line of the cached HUD text
            for (String line : cachedHudText.split("\n")) {
                drawContext.drawText(client.textRenderer, line, x, y, 0xFFFFFF, false);
                y += 12; // Move down after each line
            }
        }
    }

    /**
     * Generates the formatted skill text for display in the HUD.
     *
     * @return A multi-line string containing all formatted skill information.
     */
    private static String generateSkillText() {
        if (!isSingleplayer() || client.player == null) {
            return ""; // Return an empty HUD if not in singleplayer or player is null
        }

        StringBuilder hudText = new StringBuilder();
        DatabaseManager db = DatabaseManager.getInstance();
        String playerUUID = client.player.getUuidAsString();

        // Check Ironman mode status and add it only if enabled
        boolean isIronman = false;
        try {
            isIronman = db.isPlayerInIronmanMode(playerUUID);
        } catch (Exception e) {
            System.out.println("[simpleskills] Failed to check Ironman mode status: " + e.getMessage());
        }

        if (isIronman) {
            hudText.append("§cIronman Mode: §aENABLED\n\n"); // Only add this line if Ironman mode is enabled
        } else {
            hudText.append("\n"); // Add one empty line for spacing, if necessary
        }

        // Header for skills section
        hudText.append("§fSkills Overview\n").append("§8§m---------------------------------------\n");

        Map<String, SkillData> skills = new HashMap<>();

        // Fetch skills from the database
        Map<String, DatabaseManager.SkillData> dbSkills = db.getAllSkills(playerUUID);
        if (dbSkills.isEmpty()) {
            System.out.println("[simpleskills] Failed to fetch skills for the player.");
            return "§cError fetching skills.";
        }
        for (Map.Entry<String, DatabaseManager.SkillData> entry : dbSkills.entrySet()) {
            String skillName = entry.getKey();
            DatabaseManager.SkillData data = entry.getValue();

            if (skillName == null || skillName.equalsIgnoreCase("None")) continue; // Skip invalid skills

            skills.put(skillName, new SkillData(skillName, data.level(), data.xp()));
        }

        // Render skills in predefined order
        for (String skillName : SKILL_ORDER) {
            SkillData skill = skills.get(skillName);
            if (skill != null) {
                appendSkillInfoStyled(hudText, skill); // Add styled skill info
            }
        }

        // Fetch total skill level from DatabaseManager
        int totalSkillLevel;
        try {
            totalSkillLevel = db.getTotalSkillLevel(playerUUID);
        } catch (Exception e) {
            System.out.println("[simpleskills] Failed to calculate total skill level: " + e.getMessage());
            totalSkillLevel = 0; // If an error occurs, assume total level 0
        }

        // Add total levels section
        hudText.append("§8§m---------------------------------------\n");
        hudText.append(String.format("§6Total Level: §f%d\n", totalSkillLevel));

        // Normalize line endings to avoid issues
        return hudText.toString().replace("\r", "");
    }

    /**
     * Appends styled skill information to the HUD text.
     */
    private static void appendSkillInfoStyled(StringBuilder hudText, SkillData skill) {
        int currentLevel = skill.currentLevel;
        int currentXP = skill.currentXP;

        if (currentLevel == XPManager.getMaxLevel()) {
            // Max-level skills are highlighted with a star
            hudText.append(String.format("§6⭐ %-15s §fLevel 99     §fXP: %,d\n", skill.name, currentXP));
        } else {
            // Calculate XP progress and create compact progress bar
            int XPForCurrentLevel = XPManager.getExperienceForLevel(currentLevel);
            int XPToNextLevel = XPManager.getExperienceForLevel(currentLevel + 1) - XPForCurrentLevel;
            int progressToNextLevel = currentXP - XPForCurrentLevel;
            hudText.append(String.format("§f%-12s §fLevel §b%-4d %s §7[§f%,d§7/§f%,d§7]\n",
                    skill.name,
                    currentLevel,
                    createCompactProgressBar(progressToNextLevel, XPToNextLevel),
                    progressToNextLevel,
                    XPToNextLevel
            ));
        }
    }

    /**
     * Creates a compact progress bar using small symbols for HUD rendering.
     *
     * @param progress current XP toward the next level
     * @param total    total XP needed for the next level
     * @return compact string representing the progress bar
     */
    private static String createCompactProgressBar(int progress, int total) {
        int barLength = 10; // Length of the progress bar

        // Prevent division by zero or invalid total
        if (total <= 0) {
            total = 1; // Avoid divide-by-zero; assume total XP should be at least 1
        }

        // Clamp progress to values between 0 and total
        progress = Math.max(0, Math.min(progress, total));

        // Calculate the filled and empty parts of the bar
        int filled = (int) ((double) progress / total * barLength); // Filled part
        int empty = barLength - filled; // Empty part

        // Ensure filled and empty are non-negative
        filled = Math.max(0, filled);
        empty = Math.max(0, empty);

        // Use solid block characters for filled and hollow blocks for empty
        return "§a" + "█".repeat(filled) + "§7" + "░".repeat(empty);
    }

    /**
     * Check if the client is in singleplayer mode.
     *
     * @return true if in singleplayer, false otherwise.
     */
    private static boolean isSingleplayer() {
        return client.getCurrentServerEntry() == null && client.isIntegratedServerRunning();
    }

    /**
     * A data class to hold skill properties.
     */
    private static class SkillData {
        String name; // Skill name
        int currentLevel; // Current level
        int currentXP; // Current XP

        SkillData(String name, int level, int XP) {
            this.name = name;
            this.currentLevel = level;
            this.currentXP = XP;
        }
    }
}