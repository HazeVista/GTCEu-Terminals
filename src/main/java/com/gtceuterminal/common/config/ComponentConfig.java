package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.gtceuterminal.common.multiblock.ComponentType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Configuration system for all component types.
 * Allows users to define custom components including high-tier components beyond UHV.
 */
public class ComponentConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String COMPONENTS_FILE = "components.json";
    
    private static Map<ComponentType, List<ComponentEntry>> componentsByType = new HashMap<>();
    private static boolean initialized = false;

    public static class ComponentEntry {
        public String blockId;           // e.g., "gtceu:uev_input_hatch"
        public String displayName;       // e.g., "UEV Input Hatch"
        public String tierName;          // e.g., "UEV"
        public int tier;                 // e.g., 10
        public String componentType;     // e.g., "INPUT_HATCH"
        
        public ComponentEntry() {}
        
        public ComponentEntry(String blockId, String displayName, String tierName, int tier, String componentType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.componentType = componentType;
        }

        @Override
        public String toString() {
            return String.format("ComponentEntry{type=%s, tier=%d (%s), block=%s}", 
                componentType, tier, tierName, blockId);
        }
    }

    private static class ComponentConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Component Configuration";
        public Map<String, String> tierNames = new LinkedHashMap<>();
        public List<ComponentEntry> components = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Component config already initialized");
            return;
        }

        LOGGER.info("Initializing component configuration system...");
        
        Path configPath = Paths.get(CONFIG_DIR, COMPONENTS_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Config file not found, creating default configuration...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        // Organize components by type and sort by tier
        organizeComponents();
        
        LOGGER.info("Component configuration initialized");
        logComponentSummary();
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        ComponentConfiguration config = new ComponentConfiguration();

        config.tierNames.put("0", "ULV");
        config.tierNames.put("1", "LV");
        config.tierNames.put("2", "MV");
        config.tierNames.put("3", "HV");
        config.tierNames.put("4", "EV");
        config.tierNames.put("5", "IV");
        config.tierNames.put("6", "LuV");
        config.tierNames.put("7", "ZPM");
        config.tierNames.put("8", "UV");
        config.tierNames.put("9", "UHV");
        
        // Add default GTCEu components (LV to UHV)
        String[] tiers = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"};
        int startTier = 1; // LV = 1
        
        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            String tierUpper = tier.toUpperCase();
            int tierNum = startTier + i;
            
            // Input Hatches
            config.components.add(new ComponentEntry(
                "gtceu:" + tier + "_input_hatch",
                tierUpper + " Input Hatch",
                tierUpper,
                tierNum,
                "INPUT_HATCH"
            ));
            
            // Output Hatches
            config.components.add(new ComponentEntry(
                "gtceu:" + tier + "_output_hatch",
                tierUpper + " Output Hatch",
                tierUpper,
                tierNum,
                "OUTPUT_HATCH"
            ));
            
            // Input Buses
            config.components.add(new ComponentEntry(
                "gtceu:" + tier + "_input_bus",
                tierUpper + " Input Bus",
                tierUpper,
                tierNum,
                "INPUT_BUS"
            ));
            
            // Output Buses
            config.components.add(new ComponentEntry(
                "gtceu:" + tier + "_output_bus",
                tierUpper + " Output Bus",
                tierUpper,
                tierNum,
                "OUTPUT_BUS"
            ));
            
            // Energy Hatches
            config.components.add(new ComponentEntry(
                "gtceu:" + tier + "_energy_input_hatch",
                tierUpper + " Energy Input Hatch",
                tierUpper,
                tierNum,
                "ENERGY_HATCH"
            ));
        }
        
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Created default config file at: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create default config file", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            ComponentConfiguration config = GSON.fromJson(json, ComponentConfiguration.class);
            
            if (config == null || config.components == null) {
                LOGGER.error("Invalid config file structure, using defaults");
                createDefaultConfig(configPath);
                return;
            }
            
            componentsByType.clear();

            int validCount = 0;
            for (ComponentEntry entry : config.components) {
                if (validateEntry(entry)) {
                    ComponentType type = parseComponentType(entry.componentType);
                    if (type != null) {
                        componentsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(entry);
                        validCount++;
                    }
                } else {
                    LOGGER.warn("Skipping invalid component entry: {}", entry);
                }
            }
            
            if (validCount == 0) {
                LOGGER.warn("No valid components found in config, creating defaults");
                createDefaultConfig(configPath);
                loadConfig(configPath);
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to load config file", e);
            createDefaultConfig(configPath);
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON syntax in config file", e);
            createDefaultConfig(configPath);
        }
    }

    private static void organizeComponents() {
        for (List<ComponentEntry> components : componentsByType.values()) {
            components.sort(Comparator.comparingInt(c -> c.tier));
        }
    }

    private static boolean validateEntry(ComponentEntry entry) {
        if (entry.blockId == null || entry.blockId.isEmpty()) {
            LOGGER.error("Component entry missing blockId");
            return false;
        }
        
        if (entry.componentType == null || entry.componentType.isEmpty()) {
            LOGGER.error("Component entry missing componentType: {}", entry.blockId);
            return false;
        }
        
        if (entry.tier < 0) {
            LOGGER.error("Component entry has invalid tier: {}", entry.blockId);
            return false;
        }

        try {
            ResourceLocation blockId = new ResourceLocation(entry.blockId);
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                LOGGER.warn("Block not found in registry: {} - skipping", entry.blockId);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Invalid block ID format: {}", entry.blockId, e);
            return false;
        }
        
        return true;
    }

    private static ComponentType parseComponentType(String typeStr) {
        try {
            return ComponentType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid component type: {}", typeStr);
            return null;
        }
    }

    public static List<ComponentEntry> getComponentsOfType(ComponentType type) {
        if (!initialized) {
            initialize();
        }
        
        List<ComponentEntry> components = componentsByType.get(type);
        return components != null ? new ArrayList<>(components) : new ArrayList<>();
    }

    public static ComponentEntry getComponent(ComponentType type, int tier) {
        List<ComponentEntry> components = getComponentsOfType(type);
        
        for (ComponentEntry entry : components) {
            if (entry.tier == tier) {
                return entry;
            }
        }
        
        return null;
    }

    public static Block getComponentBlock(ComponentType type, int tier) {
        ComponentEntry entry = getComponent(type, tier);
        if (entry == null) return null;
        
        try {
            ResourceLocation blockId = new ResourceLocation(entry.blockId);
            return BuiltInRegistries.BLOCK.get(blockId);
        } catch (Exception e) {
            LOGGER.error("Failed to get block for {} tier {}", type, tier, e);
            return null;
        }
    }

    public static String getComponentDisplayName(ComponentType type, int tier) {
        ComponentEntry entry = getComponent(type, tier);
        return entry != null ? entry.displayName : "Unknown " + type.getDisplayName();
    }

    public static int getMaxTier(ComponentType type) {
        List<ComponentEntry> components = getComponentsOfType(type);
        if (components.isEmpty()) return -1;
        
        return components.stream()
            .mapToInt(c -> c.tier)
            .max()
            .orElse(-1);
    }

    public static boolean isValidTier(ComponentType type, int tier) {
        return getComponent(type, tier) != null;
    }

    public static List<Integer> getAvailableTiers(ComponentType type) {
        return getComponentsOfType(type).stream()
            .map(c -> c.tier)
            .sorted()
            .toList();
    }

    public static void reload() {
        LOGGER.info("Reloading component configuration...");
        initialized = false;
        componentsByType.clear();
        initialize();
    }

    private static void logComponentSummary() {
        LOGGER.info("Component configuration summary:");
        for (ComponentType type : ComponentType.values()) {
            List<ComponentEntry> components = componentsByType.get(type);
            if (components != null && !components.isEmpty()) {
                int minTier = components.stream().mapToInt(c -> c.tier).min().orElse(0);
                int maxTier = components.stream().mapToInt(c -> c.tier).max().orElse(0);
                LOGGER.info("  {}: {} components (Tier {} to {})", 
                    type, components.size(), minTier, maxTier);
            }
        }
    }

    public static Path getConfigPath() {
        return Paths.get(CONFIG_DIR, COMPONENTS_FILE);
    }
}