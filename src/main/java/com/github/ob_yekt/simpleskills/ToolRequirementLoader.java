package com.github.ob_yekt.simpleskills;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ToolRequirementLoader {

    // Map to hold tool requirements in memory
    private static final Map<String, SkillRequirement> TOOL_REQUIREMENTS = new HashMap<>();

    /**
     * Load or copy the default config file to the mod's directory (same folder as the JAR file).
     */
    public static void loadRequirements() {
        try {
            // Get the folder where the JAR is located (working directory)
            Path jarFolderPath = Path.of(System.getProperty("user.dir"), "mods", "simpleskills_tool_requirements.json");

            // If the file doesn't exist, copy the default one
            if (!Files.exists(jarFolderPath)) {
                Files.createDirectories(jarFolderPath.getParent()); // Ensure the `mods` directory exists
                copyDefaultConfig(jarFolderPath); // Copy the default file
            }

            // Read and parse file into memory
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(jarFolderPath))) {
                Map<String, Map<String, Object>> tools = new Gson().fromJson(
                        reader,
                        new TypeToken<Map<String, Map<String, Object>>>() {}.getType()
                );

                // Populate the in-memory map
                for (Map.Entry<String, Map<String, Object>> entry : tools.entrySet()) {
                    String toolName = entry.getKey();
                    Map<String, Object> values = entry.getValue();

                    String skill = (String) values.get("skill");
                    int level = ((Double) values.get("level")).intValue();

                    TOOL_REQUIREMENTS.put(toolName, new SkillRequirement(skill, level));
                }

                System.out.println("[SimpleSkills] Tool requirements loaded successfully!");

            }

        } catch (Exception e) {
            System.err.println("[SimpleSkills] Failed to load tool requirements: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Copy the default JSON file from inside the JAR to the `mods/` directory.
     */
    private static void copyDefaultConfig(Path targetPath) {
        try (InputStream inputStream = ToolRequirementLoader.class.getResourceAsStream("/config/simpleskills_tool_requirements.json");
             OutputStream outputStream = Files.newOutputStream(targetPath)) {

            if (inputStream == null) {
                throw new FileNotFoundException("Default simpleskills_tool_requirements.json file not found in resources!");
            }

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            System.out.println("[SimpleSkills] Default simpleskills_tool_requirements.json copied to mods folder.");

        } catch (Exception e) {
            System.err.println("[SimpleSkills] Failed to copy default simpleskills_tool_requirements.json!");
            e.printStackTrace();
        }
    }

    /**
     * Get the tool requirements for a given tool.
     *
     * @param toolName Name of the tool.
     * @return The SkillRequirement object, or null if not found.
     */
    public static SkillRequirement getRequirement(String toolName) {
        return TOOL_REQUIREMENTS.get(toolName);
    }
}