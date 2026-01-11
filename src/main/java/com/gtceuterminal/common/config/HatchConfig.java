package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "hatches.json";
    
    private static List<HatchEntry> inputHatches = new ArrayList<>();
    private static List<HatchEntry> outputHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class HatchEntry {
        public String blockId;           // "gtceu:uev_input_hatch"
        public String displayName;       // "UEV Input Hatch"
        public String tierName;          // "UEV"
        public int tier;                 // 10
        public String hatchType;         // "INPUT" or "OUTPUT"
        
        public HatchEntry() {}
        
        public HatchEntry(String blockId, String displayName, String tierName, int tier, String hatchType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.hatchType = hatchType;
        }

        @Override
        public String toString() {
            return String.format("HatchEntry{type=%s, tier=%d (%s), block=%s}", 
                hatchType, tier, tierName, blockId);
        }
    }

    private static class HatchConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Hatch Configuration (Fluid I/O)";
        public List<HatchEntry> hatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Hatch config already initialized");
            return;
        }

        LOGGER.info("Initializing hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        organizeHatches();
        LOGGER.info("Hatch configuration initialized: {} input, {} output", 
            inputHatches.size(), outputHatches.size());
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        HatchConfiguration config = new HatchConfiguration();
        
        // Default GTCEu hatches
        String[] tiers = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"};
        int startTier = 1; // LV = 1
        
        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            String tierUpper = tier.toUpperCase();
            int tierNum = startTier + i;
            
            // Input Hatches
            config.hatches.add(new HatchEntry(
                "gtceu:" + tier + "_input_hatch",
                tierUpper + " Input Hatch",
                tierUpper,
                tierNum,
                "INPUT"
            ));
            
            // Output Hatches
            config.hatches.add(new HatchEntry(
                "gtceu:" + tier + "_output_hatch",
                tierUpper + " Output Hatch",
                tierUpper,
                tierNum,
                "OUTPUT"
            ));
        }
        
        saveConfig(configPath, config);
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            HatchConfiguration config = GSON.fromJson(json, HatchConfiguration.class);
            
            if (config != null && config.hatches != null) {
                inputHatches.clear();
                outputHatches.clear();
                
                for (HatchEntry entry : config.hatches) {
                    if ("INPUT".equalsIgnoreCase(entry.hatchType)) {
                        inputHatches.add(entry);
                    } else if ("OUTPUT".equalsIgnoreCase(entry.hatchType)) {
                        outputHatches.add(entry);
                    }
                }
                
                LOGGER.info("Loaded {} hatches from config", config.hatches.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load hatch config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, HatchConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved hatch config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save hatch config: {}", e.getMessage());
        }
    }

    private static void organizeHatches() {
        inputHatches.sort(Comparator.comparingInt(h -> h.tier));
        outputHatches.sort(Comparator.comparingInt(h -> h.tier));
    }

    // Public API
    public static List<HatchEntry> getInputHatches() {
        return new ArrayList<>(inputHatches);
    }

    public static List<HatchEntry> getOutputHatches() {
        return new ArrayList<>(outputHatches);
    }

    public static List<HatchEntry> getAllHatches() {
        List<HatchEntry> all = new ArrayList<>();
        all.addAll(inputHatches);
        all.addAll(outputHatches);
        return all;
    }

    public static HatchEntry getHatchByBlock(String blockId) {
        for (HatchEntry hatch : inputHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        for (HatchEntry hatch : outputHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        return null;
    }
}