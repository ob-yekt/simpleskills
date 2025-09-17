package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    // Use an existing category, e.g., MISC
    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.MISC;

    public static final KeyBinding TOGGLE_HUD_KEY = new KeyBinding(
            "key.simpleskills.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    public static final KeyBinding CYCLE_HUD_POSITION_KEY = new KeyBinding(
            "key.simpleskills.cycle_hud_position",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KEY_CATEGORY
    );

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD_KEY);
        KeyBindingHelper.registerKeyBinding(CYCLE_HUD_POSITION_KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (SimpleskillsClient.isMultiplayer()) {
                return;
            }

            while (TOGGLE_HUD_KEY.wasPressed()) {
                SkillHudRenderer.toggleHudVisibility();
            }

            while (CYCLE_HUD_POSITION_KEY.wasPressed()) {
                ClientConfig.cycleHudPosition();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§6[simpleskills]§f HUD position set to " + ClientConfig.getHudPosition()),
                            false
                    );
                }
            }
        });
    }
}