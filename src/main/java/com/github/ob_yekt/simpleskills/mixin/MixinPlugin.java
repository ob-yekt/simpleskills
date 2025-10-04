package com.github.ob_yekt.simpleskills.mixin;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    private boolean customLootEnabled = true;  // Default if config missing or invalid

    @Override
    public void onLoad(String mixinPackage) {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("simpleskills");
        Path configPath = configDir.resolve("config.json");

        if (Files.exists(configPath)) {
            try (FileReader reader = new FileReader(configPath.toFile())) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json.has("custom_fishing_loot_enabled")) {
                    customLootEnabled = json.get("custom_fishing_loot_enabled").getAsBoolean();
                } else if (json.has("features")) {  // Fallback to check "features" object if present
                    JsonObject features = json.getAsJsonObject("features");
                    if (features != null && features.has("custom_fishing_loot_enabled")) {
                        customLootEnabled = features.get("custom_fishing_loot_enabled").getAsBoolean();
                    }
                }
                Simpleskills.LOGGER.info("Loaded custom_fishing_loot_enabled: {}", customLootEnabled);  // Early log for debug
            } catch (Exception e) {
                Simpleskills.LOGGER.error("Error reading config.json in mixin plugin: {}", e.getMessage());
            }
        } else {
            Simpleskills.LOGGER.warn("config.json not found; defaulting custom_fishing_loot_enabled to true");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("com.github.ob_yekt.simpleskills.mixin.FISHING.FishingBobberLootMixin")) {
            return customLootEnabled;
        }
        return true;  // Apply all other mixins unconditionally
    }

    // Other required methods (leave empty)
    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}