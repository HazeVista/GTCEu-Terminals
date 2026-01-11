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

public class BusConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(BusConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "buses.json";
    
    private static List<BusEntry> inputBuses = new ArrayList<>();
    private static List<BusEntry> outputBuses = new ArrayList<>();
    private static boolean initialized = false;

    public static class BusEntry {
        public String blockId;           // "gtceu:uev_input_bus"
        public String displayName;       // "UEV Input Bus"
        public String tierName;          // "UEV"
        public int tier;                 // 10
        public String busType;           // "INPUT" or "OUTPUT"
        
        public BusEntry() {}
        
        public BusEntry(String blockId, String displayName, String tierName, int tier, String busType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.busType = busType;
        }

        @Override
        public String toString() {
            return String.format("BusEntry{type=%s, tier=%d (%s), block=%s}", 
                busType, tier, tierName, blockId);
        }
    }

    private static class BusConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Bus Configuration (Item I/O)";
        public List<BusEntry> buses = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Bus config already initialized");
            return;
        }

        LOGGER.info("Initializing bus configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Bus config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        organizeBuses();
        LOGGER.info("Bus configuration initialized: {} input, {} output", 
            inputBuses.size(), outputBuses.size());
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        BusConfiguration config = new BusConfiguration();
        
        // Default GTCEu buses
        String[] tiers = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"};
        int startTier = 1; // LV = 1
        
        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            String tierUpper = tier.toUpperCase();
            int tierNum = startTier + i;
            
            // Input Buses
            config.buses.add(new BusEntry(
                "gtceu:" + tier + "_input_bus",
                tierUpper + " Input Bus",
                tierUpper,
                tierNum,
                "INPUT"
            ));
            
            // Output Buses
            config.buses.add(new BusEntry(
                "gtceu:" + tier + "_output_bus",
                tierUpper + " Output Bus",
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
            BusConfiguration config = GSON.fromJson(json, BusConfiguration.class);
            
            if (config != null && config.buses != null) {
                inputBuses.clear();
                outputBuses.clear();
                
                for (BusEntry entry : config.buses) {
                    if ("INPUT".equalsIgnoreCase(entry.busType)) {
                        inputBuses.add(entry);
                    } else if ("OUTPUT".equalsIgnoreCase(entry.busType)) {
                        outputBuses.add(entry);
                    }
                }
                
                LOGGER.info("Loaded {} buses from config", config.buses.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load bus config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, BusConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved bus config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save bus config: {}", e.getMessage());
        }
    }

    private static void organizeBuses() {
        inputBuses.sort(Comparator.comparingInt(b -> b.tier));
        outputBuses.sort(Comparator.comparingInt(b -> b.tier));
    }

    // Public API
    public static List<BusEntry> getInputBuses() {
        return new ArrayList<>(inputBuses);
    }

    public static List<BusEntry> getOutputBuses() {
        return new ArrayList<>(outputBuses);
    }

    public static List<BusEntry> getAllBuses() {
        List<BusEntry> all = new ArrayList<>();
        all.addAll(inputBuses);
        all.addAll(outputBuses);
        return all;
    }

    public static BusEntry getBusByBlock(String blockId) {
        for (BusEntry bus : inputBuses) {
            if (bus.blockId.equals(blockId)) return bus;
        }
        for (BusEntry bus : outputBuses) {
            if (bus.blockId.equals(blockId)) return bus;
        }
        return null;
    }
}