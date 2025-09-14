package com.github.ob_yekt.simpleskills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    // Define the custom category using Category.method_74699
    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.method_74699("simpleskills");

    public static final KeyBinding TOGGLE_HUD_KEY = new KeyBinding(
            "key.simpleskills.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H, // H key for toggling HUD
            KEY_CATEGORY
    );

    public static final KeyBinding CYCLE_HUD_POSITION_KEY = new KeyBinding(
            "key.simpleskills.cycle_hud_position",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J, // J key for cycling position
            KEY_CATEGORY
    );

    public static void register() {
        // Register keybinds with Fabric
        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD_KEY);
        KeyBindingHelper.registerKeyBinding(CYCLE_HUD_POSITION_KEY);

        // Register keybind handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Only process keybinds in singleplayer
            if (!client.isIntegratedServerRunning()) return;

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