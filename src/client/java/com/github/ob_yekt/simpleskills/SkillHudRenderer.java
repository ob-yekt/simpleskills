package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.managers.DatabaseManager;
import com.github.ob_yekt.simpleskills.managers.XPManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillHudRenderer implements HudElement {
    private static final Identifier HUD_ID = Identifier.of("simpleskills", "skill_hud");
    private static final Map<UUID, Boolean> playerHudVisibility = new HashMap<>();
    private static final int MAX_SKILL_NAME_LENGTH = getMaxSkillNameLength();
    private static final int PANEL_WIDTH = 275; // Reduced width slightly
    private static final int PANEL_HEIGHT = 220; // Significantly reduced height
    private static final int PADDING = 5; // Reduced padding
    private static final int LINE_HEIGHT = 10; // Reduced line height

    // Colors
    private static final int BACKGROUND_COLOR = 0x80000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFFFFD700; // Gold
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White
    private static final int HEADER_COLOR = 0xFFFF4444; // Red
    private static final int LEVEL_COLOR = 0xFF55FFFF; // Cyan
    private static final int XP_COLOR = 0xFF88FF88; // Light green
    private static final int PROGRESS_FILLED_COLOR = 0xFF55FF55; // Green
    private static final int PROGRESS_EMPTY_COLOR = 0xFF555555; // Gray

    /**
     * Registers the HUD element with Fabric's HUD registry
     */
    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HUD_ID, new SkillHudRenderer());
    }

    /**
     * Determines the length of the longest skill display name for padding purposes.
     */
    private static int getMaxSkillNameLength() {
        int maxLength = 0;
        for (Skills skill : Skills.values()) {
            maxLength = Math.max(maxLength, skill.getDisplayName().length());
        }
        return maxLength;
    }

    /**
     * Toggles the visibility of the skill HUD for the current player.
     */
    public static void toggleHudVisibility() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID playerUuid = client.player.getUuid();
        boolean isVisible = playerHudVisibility.getOrDefault(playerUuid,
                DatabaseManager.getInstance().isTabMenuVisible(playerUuid.toString()));

        playerHudVisibility.put(playerUuid, !isVisible);
        DatabaseManager.getInstance().setTabMenuVisibility(playerUuid.toString(), !isVisible);

        if (!isVisible) {
            client.player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD enabled."), false);
        } else {
            client.player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD disabled."), false);
        }
    }

    /**
     * Checks if the HUD should be visible for the current player
     */
    private boolean shouldRenderHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;

        // Check if the game is in singleplayer (integrated server)
        boolean isSingleplayer = client.isIntegratedServerRunning();
        if (!isSingleplayer) return false;

        UUID playerUuid = client.player.getUuid();
        return playerHudVisibility.getOrDefault(playerUuid,
                DatabaseManager.getInstance().isTabMenuVisible(playerUuid.toString()));
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!shouldRenderHud()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Position the HUD in the top-left corner with some margin
        int x = 10;
        int y = 10;

        try {
            renderSkillPanel(context, x, y, client.player.getUuid());
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Failed to render skill HUD: {}", e.getMessage());
        }
    }

    /**
     * Renders the main skill panel
     */
    private void renderSkillPanel(DrawContext context, int x, int y, UUID playerUuid) {
        DatabaseManager db = DatabaseManager.getInstance();
        String playerUuidStr = playerUuid.toString();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Ensure player is initialized
        if (db.getAllSkills(playerUuidStr).isEmpty()) {
            db.initializePlayer(playerUuidStr);
        }

        // Draw background panel
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BACKGROUND_COLOR);

        // Draw border
        context.drawHorizontalLine(x, x + PANEL_WIDTH - 1, y, BORDER_COLOR);
        context.drawHorizontalLine(x, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, BORDER_COLOR);
        context.drawVerticalLine(x, y, y + PANEL_HEIGHT - 1, BORDER_COLOR);
        context.drawVerticalLine(x + PANEL_WIDTH - 1, y, y + PANEL_HEIGHT - 1, BORDER_COLOR);

        int currentY = y + PADDING;

        // Header
        String headerText = "⚔ Skills ⚔"; // Shortened header
        int headerX = x + (PANEL_WIDTH - textRenderer.getWidth(headerText)) / 2;
        context.drawText(textRenderer, headerText, headerX, currentY, HEADER_COLOR, true);
        currentY += LINE_HEIGHT + 3;

        // Ironman mode indicator
        boolean isIronman = db.isPlayerInIronmanMode(playerUuidStr);
        if (isIronman) {
            String ironmanText = "Ironman Mode Enabled";
            context.drawText(textRenderer, ironmanText, x + PADDING, currentY, 0xFFFF4444, false);
            currentY += LINE_HEIGHT + 2;
        }

        // Separator line
        context.drawHorizontalLine(x + PADDING, x + PANEL_WIDTH - PADDING, currentY, 0xFF666666);
        currentY += 5;

        // Skills
        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuidStr);
        int totalLevels = skills.values().stream().mapToInt(DatabaseManager.SkillData::level).sum();

        for (Skills skillEnum : Skills.values()) {
            String skillName = skillEnum.getId();
            DatabaseManager.SkillData skill = skills.get(skillName);
            if (skill == null) {
                skill = new DatabaseManager.SkillData(0, 1);
            }

            currentY = renderSkillLine(context, x + PADDING, currentY, skill, skillEnum.getDisplayName());
            currentY += 1; // Smaller gap between skills
        }

        // Total level
        currentY += 3;
        context.drawHorizontalLine(x + PADDING, x + PANEL_WIDTH - PADDING, currentY, 0xFF666666);
        currentY += 5;

        String totalText = String.format("Total: %d", totalLevels);
        context.drawText(textRenderer, totalText, x + PADDING, currentY, LEVEL_COLOR, true);
    }

    /**
     * Renders a single skill line
     */
    private int renderSkillLine(DrawContext context, int x, int y, DatabaseManager.SkillData skill, String skillDisplayName) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if (skill.level() == XPManager.getMaxLevel()) {
            // Max level skill
            String skillText = String.format("★ %-" + MAX_SKILL_NAME_LENGTH + "s Lvl 99", skillDisplayName);
            String xpText = String.format("XP: %,d", skill.xp());

            context.drawText(textRenderer, skillText, x, y, BORDER_COLOR, false);
            context.drawText(textRenderer, xpText, x + 120, y, TEXT_COLOR, false); // Adjusted x offset

            return y + LINE_HEIGHT;
        } else {
            // Regular skill with progress bar
            int XPForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
            int XPToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - XPForCurrentLevel;
            int progressToNextLevel = skill.xp() - XPForCurrentLevel;

            String skillText = String.format("%-" + MAX_SKILL_NAME_LENGTH + "s Lvl %d", skillDisplayName, skill.level());
            context.drawText(textRenderer, skillText, x, y, TEXT_COLOR, false);

            // Progress bar
            int barX = x + 120; // Adjusted x offset
            int barY = y + 2;
            int barWidth = 60; // Reduced bar width
            int barHeight = 6; // Reduced bar height

            renderProgressBar(context, barX, barY, barWidth, barHeight, progressToNextLevel, XPToNextLevel);

            // XP text
            String xpText = String.format("%,d/%,d", progressToNextLevel, XPToNextLevel);
            int xpTextX = barX + barWidth + 5;
            context.drawText(textRenderer, xpText, xpTextX, y, XP_COLOR, false);

            return y + LINE_HEIGHT;
        }
    }

    /**
     * Renders a progress bar
     */
    private void renderProgressBar(DrawContext context, int x, int y, int width, int height, int progress, int total) {
        if (total <= 0) total = 1;
        progress = Math.max(0, Math.min(progress, total));

        // Background
        context.fill(x, y, x + width, y + height, PROGRESS_EMPTY_COLOR);

        // Progress fill
        int filledWidth = (int) ((double) progress / total * width);
        if (filledWidth > 0) {
            context.fill(x, y, x + filledWidth, y + height, PROGRESS_FILLED_COLOR);
        }

        // Border
        context.drawHorizontalLine(x, x + width - 1, y, 0xFF888888);
        context.drawHorizontalLine(x, x + width - 1, y + height - 1, 0xFF888888);
        context.drawVerticalLine(x, y, y + height - 1, 0xFF888888);
        context.drawVerticalLine(x + width - 1, y, y + height - 1, 0xFF888888);
    }

    /**
     * Clears the visibility setting for a player on disconnect.
     */
    public static void clearPlayerVisibility(UUID playerUuid) {
        playerHudVisibility.remove(playerUuid);
        Simpleskills.LOGGER.debug("Cleared HUD visibility for player UUID: {}", playerUuid);
    }
}