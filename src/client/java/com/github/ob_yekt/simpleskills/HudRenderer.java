package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class HudRenderer {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static int cachedMiningLevel = 0;
    private static int cachedMiningXP = 0;
    private static int cachedWoodcuttingLevel = 0;
    private static int cachedWoodcuttingXP = 0;

    public static void registerHud() {
        // Update skill data on each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                var skillComponent = SimpleSkillsComponents.getSkillComponent(client.player);
                if (skillComponent != null) {
                    cachedMiningLevel = skillComponent.getSkillLevel("Mining");
                    cachedMiningXP = skillComponent.getLevel("Mining");
                    cachedWoodcuttingLevel = skillComponent.getSkillLevel("Woodcutting");
                    cachedWoodcuttingXP = skillComponent.getLevel("Woodcutting");
                }
            }
        });

        // Render the HUD
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (client.player == null) return;

            int x = 10; // Starting X position
            int y = 10; // Starting Y position

            // Render Mining stats
            String miningText = String.format("Mining - Level: %d XP: %d/100", cachedMiningLevel, cachedMiningXP);
            drawContext.drawText(client.textRenderer, miningText, x, y, 0xFFFFFF, false);
            y += 12; // Move to the next line

            // Render Woodcutting stats
            String woodcuttingText = String.format("Woodcutting - Level: %d XP: %d/100", cachedWoodcuttingLevel, cachedWoodcuttingXP);
            drawContext.drawText(client.textRenderer, woodcuttingText, x, y, 0xFFFFFF, false);
        });
    }
}
