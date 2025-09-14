package com.github.ob_yekt.simpleskills;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("simpleskills_client.json");

    public enum HudPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public static class Config {
        public HudPosition hudPosition = HudPosition.TOP_LEFT; // Default position
    }

    private static Config config = new Config();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Config loadedConfig = GSON.fromJson(reader, Config.class);
                if (loadedConfig != null) {
                    config = loadedConfig;
                }
            } catch (IOException e) {
                Simpleskills.LOGGER.error("Failed to load client config: {}", e.getMessage());
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Simpleskills.LOGGER.error("Failed to save client config: {}", e.getMessage());
        }
    }

    public static HudPosition getHudPosition() {
        return config.hudPosition;
    }

    public static void setHudPosition(HudPosition position) {
        config.hudPosition = position;
        save();
    }

    public static void cycleHudPosition() {
        HudPosition[] positions = HudPosition.values();
        int currentIndex = config.hudPosition.ordinal();
        int nextIndex = (currentIndex + 1) % positions.length;
        config.hudPosition = positions[nextIndex];
        save();
    }
}