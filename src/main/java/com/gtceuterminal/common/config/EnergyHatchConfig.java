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

public class EnergyHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "energy_hatches.json";
    
    private static List<EnergyHatchEntry> inputHatches = new ArrayList<>();
    private static List<EnergyHatchEntry> outputHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class EnergyHatchEntry {
        public String blockId;           // "gtceu:uev_energy_input_hatch"
        public String displayName;       // "UEV Energy Input Hatch"
        public String tierName;          // "UEV"
        public int tier;                 // 10
        public String energyType;        // "INPUT" or "OUTPUT"
        public long capacity;            // Optional: energy capacity in EU
        public long maxAmperage;         // Optional: max amperage
        
        public EnergyHatchEntry() {}
        
        public EnergyHatchEntry(String blockId, String displayName, String tierName, int tier, String energyType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.energyType = energyType;
        }

        @Override
        public String toString() {
            return String.format("EnergyHatchEntry{type=%s, tier=%d (%s), block=%s}", 
                energyType, tier, tierName, blockId);
        }
    }

    private static class EnergyHatchConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Energy Hatch Configuration";
        public List<EnergyHatchEntry> energyHatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Energy hatch config already initialized");
            return;
        }

        LOGGER.info("Initializing energy hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Energy hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        organizeHatches();
        LOGGER.info("Energy hatch configuration initialized: {} input, {} output", 
            inputHatches.size(), outputHatches.size());
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        EnergyHatchConfiguration config = new EnergyHatchConfiguration();
        
        // Default GTCEu energy hatches
        String[] tiers = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"};
        int startTier = 1; // LV = 1
        
        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            String tierUpper = tier.toUpperCase();
            int tierNum = startTier + i;
            
            // Energy Input Hatches (4A)
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_input_hatch",
                tierUpper + " Energy Input Hatch",
                tierUpper,
                tierNum,
                "INPUT"
            ));
            
            // Energy Input Hatches 4A
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_input_hatch_4a",
                tierUpper + " 4A Energy Input Hatch",
                tierUpper,
                tierNum,
                "INPUT"
            ));
            
            // Energy Input Hatches 16A
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_input_hatch_16a",
                tierUpper + " 16A Energy Input Hatch",
                tierUpper,
                tierNum,
                "INPUT"
            ));
            
            // Energy Output Hatches (4A)
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_output_hatch",
                tierUpper + " Energy Output Hatch",
                tierUpper,
                tierNum,
                "OUTPUT"
            ));
            
            // Energy Output Hatches 4A
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_output_hatch_4a",
                tierUpper + " 4A Energy Output Hatch",
                tierUpper,
                tierNum,
                "OUTPUT"
            ));
            
            // Energy Output Hatches 16A
            config.energyHatches.add(new EnergyHatchEntry(
                "gtceu:" + tier + "_energy_output_hatch_16a",
                tierUpper + " 16A Energy Output Hatch",
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
            EnergyHatchConfiguration config = GSON.fromJson(json, EnergyHatchConfiguration.class);
            
            if (config != null && config.energyHatches != null) {
                inputHatches.clear();
                outputHatches.clear();
                
                for (EnergyHatchEntry entry : config.energyHatches) {
                    if ("INPUT".equalsIgnoreCase(entry.energyType)) {
                        inputHatches.add(entry);
                    } else if ("OUTPUT".equalsIgnoreCase(entry.energyType)) {
                        outputHatches.add(entry);
                    }
                }
                
                LOGGER.info("Loaded {} energy hatches from config", config.energyHatches.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load energy hatch config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, EnergyHatchConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved energy hatch config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save energy hatch config: {}", e.getMessage());
        }
    }

    private static void organizeHatches() {
        inputHatches.sort(Comparator.comparingInt(h -> h.tier));
        outputHatches.sort(Comparator.comparingInt(h -> h.tier));
    }

    // Public API
    public static List<EnergyHatchEntry> getInputHatches() {
        return new ArrayList<>(inputHatches);
    }

    public static List<EnergyHatchEntry> getOutputHatches() {
        return new ArrayList<>(outputHatches);
    }

    public static List<EnergyHatchEntry> getAllEnergyHatches() {
        List<EnergyHatchEntry> all = new ArrayList<>();
        all.addAll(inputHatches);
        all.addAll(outputHatches);
        return all;
    }

    public static EnergyHatchEntry getEnergyHatchByBlock(String blockId) {
        for (EnergyHatchEntry hatch : inputHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        for (EnergyHatchEntry hatch : outputHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        return null;
    }
}