package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static final String KEY_CATEGORY = "key.categories.misc";

    public static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping(
            "key.simpleskills.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.MISC
    );

    public static final KeyMapping CYCLE_HUD_POSITION_KEY = new KeyMapping(
            "key.simpleskills.cycle_hud_position",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KeyMapping.Category.MISC
    );

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE_HUD_KEY);
        KeyMappingHelper.registerKeyMapping(CYCLE_HUD_POSITION_KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (SimpleskillsClient.isMultiplayer()) {
                return;
            }

            while (TOGGLE_HUD_KEY.consumeClick()) {
                SkillHudRenderer.toggleHudVisibility();
            }

            while (CYCLE_HUD_POSITION_KEY.consumeClick()) {
                ClientConfig.cycleHudPosition();
                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal("§6[simpleskills]§f HUD position set to " + ClientConfig.getHudPosition())
                    );
                }
            }
        });
    }
}