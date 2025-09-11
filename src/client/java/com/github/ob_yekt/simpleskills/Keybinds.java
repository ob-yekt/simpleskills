package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    // Define the custom category using Category.method_74699
    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.method_74699("simpleskills");

    public static final KeyBinding TOGGLE_HUD_KEY = new KeyBinding(
            "key.simpleskills.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H, // Changed to H key
            KEY_CATEGORY // Use the Category instance instead of a String
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