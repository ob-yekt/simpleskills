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
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 8;
    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 4;
    private static final int ELEMENT_SPACING = 5;

    // Colors
    private static final int BORDER_COLOR = 0xFFFFD700; // Gold
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White
    private static final int HEADER_COLOR = 0xFFFF4444; // Red
    private static final int LEVEL_COLOR = 0xFF55FFFF; // Cyan
    private static final int XP_COLOR = 0xFF88FF88; // Light green
    private static final int PROGRESS_FILLED_COLOR = 0xFF55FF55; // Green
    private static final int PROGRESS_EMPTY_COLOR = 0xFF555555; // Gray

    // Cache for performance
    private static UUID cachedPlayerUuid = null;
    private static HudSize cachedSize = null;
    private static long lastSkillUpdateTime = 0;

    private static class HudSize {
        int width;
        int height;

        HudSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HUD_ID, new SkillHudRenderer());
    }

    private static int getMaxSkillNameLength() {
        int maxLength = 0;
        for (Skills skill : Skills.values()) {
            maxLength = Math.max(maxLength, skill.getDisplayName().length());
        }
        return maxLength;
    }

    public static void toggleHudVisibility() {
        // Skip in multiplayer
        if (SimpleskillsClient.isMultiplayer()) {
            Simpleskills.LOGGER.info("[simpleskills] HUD toggle ignored in multiplayer.");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID playerUuid = client.player.getUuid();
        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (dbManager == null) return;

        boolean isVisible = playerHudVisibility.getOrDefault(playerUuid,
                dbManager.isTabMenuVisible(playerUuid.toString()));

        playerHudVisibility.put(playerUuid, !isVisible);
        dbManager.setTabMenuVisibility(playerUuid.toString(), !isVisible);

        // Clear cache when visibility changes
        cachedPlayerUuid = null;
        cachedSize = null;

        if (!isVisible) {
            client.player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD enabled."), false);
        } else {
            client.player.sendMessage(Text.literal("§6[simpleskills]§f Skill HUD disabled."), false);
        }
    }

    private boolean shouldRenderHud() {
        // Disable HUD entirely in multiplayer
        if (SimpleskillsClient.isMultiplayer()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;

        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (dbManager == null) return false;

        UUID playerUuid = client.player.getUuid();
        return playerHudVisibility.getOrDefault(playerUuid,
                dbManager.isTabMenuVisible(playerUuid.toString()));
    }

    private HudSize calculateHudSize(UUID playerUuid) {
        // Use cache if available and player hasn't changed
        if (cachedPlayerUuid != null && cachedPlayerUuid.equals(playerUuid) && cachedSize != null) {
            if (System.currentTimeMillis() - lastSkillUpdateTime < 5000) {
                return cachedSize;
            }
        }

        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return new HudSize(200, 100); // Fallback size

        String playerUuidStr = playerUuid.toString();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Ensure player is initialized
        if (db.getAllSkills(playerUuidStr).isEmpty()) {
            db.initializePlayer(playerUuidStr);
        }

        boolean isIronman = db.isPlayerInIronmanMode(playerUuidStr);
        int prestige = db.getPrestige(playerUuidStr);
        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuidStr);

        int maxWidth = 0;
        int height = PADDING;

        // Header
        String headerText = "⚔ Skills ⚔";
        maxWidth = Math.max(maxWidth, textRenderer.getWidth(headerText));
        height += LINE_HEIGHT + 2;

        // Ironman mode indicator
        if (isIronman) {
            String ironmanText = "Ironman Mode";
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(ironmanText));
            height += LINE_HEIGHT + 1;
        }

        // Prestige indicator
        if (prestige > 0) {
            String prestigeText = String.format("Prestige: ★%d", prestige);
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(prestigeText));
            height += LINE_HEIGHT + 1;
        }

        // Separator
        height += 3;

        // Skills
        for (Skills skillEnum : Skills.values()) {
            String skillName = skillEnum.getId();
            DatabaseManager.SkillData skill = skills.getOrDefault(skillName, new DatabaseManager.SkillData(0, 1));
            String skillDisplayName = skillEnum.getDisplayName();
            int lineWidth;

            if (skill.level() == XPManager.getMaxLevel()) {
                String skillText = String.format("⭐ %s Lvl 99 [XP: %,d]", skillDisplayName, skill.xp());
                lineWidth = textRenderer.getWidth(skillText);
            } else {
                int xpForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
                int xpToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - xpForCurrentLevel;
                int progressToNextLevel = skill.xp() - xpForCurrentLevel;
                String skillText = String.format("%s Lvl %d [%,d/%,d]",
                        skillDisplayName, skill.level(), progressToNextLevel, xpToNextLevel);
                lineWidth = textRenderer.getWidth(skillText) + ELEMENT_SPACING + BAR_WIDTH;
            }

            maxWidth = Math.max(maxWidth, lineWidth);
            height += LINE_HEIGHT + 1;
        }

        // Total level
        int totalLevels = skills.values().stream().mapToInt(DatabaseManager.SkillData::level).sum();
        String totalText = String.format("Total: %d", totalLevels);
        maxWidth = Math.max(maxWidth, textRenderer.getWidth(totalText));
        height += 2 + 3 + LINE_HEIGHT;

        // Bottom padding
        height += PADDING;

        // Add side paddings to width with extra for borders
        int panelWidth = maxWidth + 2 * (PADDING + 5); // Extra margin for aesthetics

        // Cache the result
        cachedSize = new HudSize(panelWidth, height);
        cachedPlayerUuid = playerUuid;
        lastSkillUpdateTime = System.currentTimeMillis();

        return cachedSize;
    }

    private int[] getHudPosition(int panelWidth, int panelHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
        int margin = 10;
        ClientConfig.HudPosition position = ClientConfig.getHudPosition();
        int x, y;

        switch (position) {
            case TOP_LEFT:
                x = margin;
                y = margin;
                break;
            case TOP_CENTER:
                x = (windowWidth - panelWidth) / 2;
                y = margin;
                break;
            case TOP_RIGHT:
                x = windowWidth - panelWidth - margin;
                y = margin;
                break;
            case MIDDLE_LEFT:
                x = margin;
                y = (windowHeight - panelHeight) / 2;
                break;
            case MIDDLE_CENTER:
                x = (windowWidth - panelWidth) / 2;
                y = (windowHeight - panelHeight) / 2;
                break;
            case MIDDLE_RIGHT:
                x = windowWidth - panelWidth - margin;
                y = (windowHeight - panelHeight) / 2;
                break;
            case BOTTOM_LEFT:
                x = margin;
                y = windowHeight - panelHeight - margin;
                break;
            case BOTTOM_CENTER:
                x = (windowWidth - panelWidth) / 2;
                y = windowHeight - panelHeight - margin;
                break;
            case BOTTOM_RIGHT:
                x = windowWidth - panelWidth - margin;
                y = windowHeight - panelHeight - margin;
                break;
            default:
                x = margin;
                y = margin;
                break;
        }

        return new int[]{x, y};
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!shouldRenderHud()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID playerUuid = client.player.getUuid();
        HudSize size = calculateHudSize(playerUuid);
        int[] pos = getHudPosition(size.width, size.height);
        int x = pos[0];
        int y = pos[1];

        try {
            renderSkillPanel(context, x, y, size.width, size.height, playerUuid);
        } catch (Exception e) {
            Simpleskills.LOGGER.error("Failed to render skill HUD: {}", e.getMessage());
        }
    }

    private void renderSkillPanel(DrawContext context, int x, int y, int panelWidth, int panelHeight, UUID playerUuid) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return;

        String playerUuidStr = playerUuid.toString();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        context.drawHorizontalLine(x, x + panelWidth - 1, y, BORDER_COLOR);
        context.drawHorizontalLine(x, x + panelWidth - 1, y + panelHeight - 1, BORDER_COLOR);
        context.drawVerticalLine(x, y, y + panelHeight - 1, BORDER_COLOR);
        context.drawVerticalLine(x + panelWidth - 1, y, y + panelHeight - 1, BORDER_COLOR);

        int currentY = y + PADDING;

        String headerText = "⚔ Skills ⚔";
        context.drawText(textRenderer, headerText, x + PADDING, currentY, HEADER_COLOR, false);
        currentY += LINE_HEIGHT + 2;

        boolean isIronman = db.isPlayerInIronmanMode(playerUuidStr);
        int prestige = db.getPrestige(playerUuidStr);
        if (isIronman) {
            String ironmanText = "Ironman Mode";
            context.drawText(textRenderer, ironmanText, x + PADDING, currentY, 0xFFFF4444, false);
            currentY += LINE_HEIGHT + 1;
        }

        if (prestige > 0) {
            String prestigeText = String.format("Prestige: ★%d", prestige);
            context.drawText(textRenderer, prestigeText, x + PADDING, currentY, 0xFFFFD700, false);
            currentY += LINE_HEIGHT + 1;
        }

        context.drawHorizontalLine(x + PADDING, x + panelWidth - PADDING, currentY, 0xFF666666);
        currentY += 3;

        Map<String, DatabaseManager.SkillData> skills = db.getAllSkills(playerUuidStr);

        for (Skills skillEnum : Skills.values()) {
            String skillName = skillEnum.getId();
            DatabaseManager.SkillData skill = skills.getOrDefault(skillName, new DatabaseManager.SkillData(0, 1));

            currentY = renderSkillLine(context, x + PADDING, currentY, skill, skillEnum.getDisplayName());
            currentY += 1;
        }

        currentY += 2;
        context.drawHorizontalLine(x + PADDING, x + panelWidth - PADDING, currentY, 0xFF666666);
        currentY += 3;

        int totalLevels = skills.values().stream().mapToInt(DatabaseManager.SkillData::level).sum();
        String totalText = String.format("Total: %d", totalLevels);
        context.drawText(textRenderer, totalText, x + PADDING, currentY, LEVEL_COLOR, false);
    }

    private int renderSkillLine(DrawContext context, int x, int y, DatabaseManager.SkillData skill, String skillDisplayName) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if (skill.level() == XPManager.getMaxLevel()) {
            String skillText = String.format("⭐ %s Lvl 99 [XP: %,d]", skillDisplayName, skill.xp());
            context.drawText(textRenderer, skillText, x, y, 0xFFFFD700, false);
        } else {
            int xpForCurrentLevel = XPManager.getExperienceForLevel(skill.level());
            int xpToNextLevel = XPManager.getExperienceForLevel(skill.level() + 1) - xpForCurrentLevel;
            int progressToNextLevel = skill.xp() - xpForCurrentLevel;

            String skillText = String.format("%s Lvl %d", skillDisplayName, skill.level());
            context.drawText(textRenderer, skillText, x, y, TEXT_COLOR, false);

            int barX = x + textRenderer.getWidth(skillText) + ELEMENT_SPACING;
            int barY = y + (LINE_HEIGHT - BAR_HEIGHT) / 2;
            renderProgressBar(context, barX, barY, BAR_WIDTH, BAR_HEIGHT, progressToNextLevel, xpToNextLevel);

            String xpText = String.format("[%,d/%,d]", progressToNextLevel, xpToNextLevel);
            int xpTextX = barX + BAR_WIDTH + ELEMENT_SPACING;
            context.drawText(textRenderer, xpText, xpTextX, y, XP_COLOR, false);
        }

        return y + LINE_HEIGHT;
    }

    private void renderProgressBar(DrawContext context, int x, int y, int width, int height, int progress, int total) {
        if (total <= 0) total = 1;
        progress = Math.max(0, Math.min(progress, total));

        context.fill(x, y, x + width, y + height, PROGRESS_EMPTY_COLOR);

        int filledWidth = (int) ((double) progress / total * width);
        if (filledWidth > 0) {
            context.fill(x, y, x + filledWidth, y + height, PROGRESS_FILLED_COLOR);
        }

        context.drawHorizontalLine(x, x + width - 1, y, 0xFF888888);
        context.drawHorizontalLine(x, x + width - 1, y + height - 1, 0xFF888888);
        context.drawVerticalLine(x, y, y + height - 1, 0xFF888888);
        context.drawVerticalLine(x + width - 1, y, y + height - 1, 0xFF888888);
    }

    public static void clearPlayerVisibility(UUID playerUuid) {
        playerHudVisibility.remove(playerUuid);
        if (playerUuid.equals(cachedPlayerUuid)) {
            cachedPlayerUuid = null;
            cachedSize = null;
        }
        Simpleskills.LOGGER.debug("Cleared HUD visibility for player UUID: {}", playerUuid);
    }

    public static void refreshCache() {
        cachedPlayerUuid = null;
        cachedSize = null;
    }
}