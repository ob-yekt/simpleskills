package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static final String KEY_CATEGORY = "key.categories.simpleskills";
    public static final KeyBinding TOGGLE_HUD_KEY = new KeyBinding(
            "key.simpleskills.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H, // Changed to H key
            KEY_CATEGORY
    );

    public static void register() {
        // Register the keybind with Fabric
        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD_KEY);

        // Register keybind handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Only process keybinds in singleplayer
            if (!client.isIntegratedServerRunning()) return;

            while (TOGGLE_HUD_KEY.wasPressed()) {
                SkillHudRenderer.toggleHudVisibility();
            }
        });
    }
}