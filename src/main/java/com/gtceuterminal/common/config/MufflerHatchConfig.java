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

public class MufflerHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MufflerHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "muffler_hatches.json";
    
    private static List<MufflerHatchEntry> mufflerHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class MufflerHatchEntry {
        public String blockId;           // "gtceu:lv_muffler_hatch"
        public String displayName;       // LV Muffler Hatch"
        public String tierName;          // "LV"
        public int tier;                 // 1
        public int pollutionReduction;   // percentage of pollution reduced
        
        public MufflerHatchEntry() {}
        
        public MufflerHatchEntry(String blockId, String displayName, String tierName, int tier) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
        }

        @Override
        public String toString() {
            return String.format("MufflerHatchEntry{tier=%d (%s), block=%s}", 
                tier, tierName, blockId);
        }
    }

    private static class MufflerHatchConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Muffler Hatch Configuration";
        public String note = "Muffler hatches reduce pollution from multiblock operations. Required for most multiblocks.";
        public List<MufflerHatchEntry> mufflerHatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Muffler hatch config already initialized");
            return;
        }

        LOGGER.info("Initializing muffler hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Muffler hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        organizeHatches();
        LOGGER.info("Muffler hatch configuration initialized: {} hatches", mufflerHatches.size());
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        MufflerHatchConfiguration config = new MufflerHatchConfiguration();
        
        // Default GTCEu muffler hatches
        String[] tiers = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv"};
        int startTier = 1; // LV = 1
        
        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            String tierUpper = tier.toUpperCase();
            int tierNum = startTier + i;

            config.mufflerHatches.add(new MufflerHatchEntry(
                "gtceu:" + tier + "_muffler_hatch",
                tierUpper + " Muffler Hatch",
                tierUpper,
                tierNum
            ));
        }
        
        saveConfig(configPath, config);
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            MufflerHatchConfiguration config = GSON.fromJson(json, MufflerHatchConfiguration.class);
            
            if (config != null && config.mufflerHatches != null) {
                mufflerHatches.clear();
                mufflerHatches.addAll(config.mufflerHatches);
                
                LOGGER.info("Loaded {} muffler hatches from config", config.mufflerHatches.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load muffler hatch config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, MufflerHatchConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved muffler hatch config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save muffler hatch config: {}", e.getMessage());
        }
    }

    private static void organizeHatches() {
        mufflerHatches.sort(Comparator.comparingInt(h -> h.tier));
    }

    // Public API
    public static List<MufflerHatchEntry> getAllMufflerHatches() {
        return new ArrayList<>(mufflerHatches);
    }

    public static MufflerHatchEntry getMufflerHatchByBlock(String blockId) {
        for (MufflerHatchEntry hatch : mufflerHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        return null;
    }

    public static MufflerHatchEntry getMufflerHatchByTier(int tier) {
        for (MufflerHatchEntry hatch : mufflerHatches) {
            if (hatch.tier == tier) return hatch;
        }
        return null;
    }

    public static List<Integer> getAvailableTiers() {
        List<Integer> tiers = new ArrayList<>();
        for (MufflerHatchEntry hatch : mufflerHatches) {
            if (!tiers.contains(hatch.tier)) {
                tiers.add(hatch.tier);
            }
        }
        Collections.sort(tiers);
        return tiers;
    }
}