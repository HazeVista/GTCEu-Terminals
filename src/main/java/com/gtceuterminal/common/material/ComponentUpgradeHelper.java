package com.gtceuterminal.common.material;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.GTCEUTerminalMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public class ComponentUpgradeHelper {

     // Coil progression

    private static final String[] COIL_PROGRESSION = {
            "cupronickel_coil_block",    // 1800K - Tier 0
            "kanthal_coil_block",        // 2700K - Tier 1
            "nichrome_coil_block",       // 3600K - Tier 2
            "rtm_alloy_coil_block",      // 4500K - Tier 3
            "hssg_coil_block",          // 5400K - Tier 4
            "naquadah_coil_block",       // 7200K - Tier 5
            "trinium_coil_block",        // 9000K - Tier 6
            "tritanium_coil_block"       // 10800K - Tier 7
    };

    public static Map<Item, Integer> getUpgradeItems(ComponentInfo component, int targetTier) {
        Map<Item, Integer> items = new HashMap<>();

        // Get the block for the target tier component
        Block targetBlock = getComponentBlock(component.getType(), targetTier);

        if (targetBlock != null) {
            items.put(targetBlock.asItem(), 1);
        }

        return items;
    }

    private static Block getComponentBlock(ComponentType type, int tier) {
        if (type == ComponentType.COIL) {
            return getCoilBlock(tier);
        }

        if (tier < 0 || tier >= GTValues.VN.length) {
            return null;
        }

        String tierName = GTValues.VN[tier].toLowerCase();
        String blockPath = getBlockPath(type, tierName);

        if (blockPath == null) {
            return null;
        }

        ResourceLocation blockId = new ResourceLocation("gtceu", blockPath);
        return BuiltInRegistries.BLOCK.get(blockId);
    }

    private static String getBlockPath(ComponentType type, String tier) {
        if (type == ComponentType.MAINTENANCE) {
            return getMaintenanceHatchPath(tier);
        }
        
        if (type == ComponentType.MUFFLER) {
            return getMufflerHatchPath(tier);
        }
        
        return switch (type) {
            case INPUT_HATCH -> tier + "_input_hatch";
            case OUTPUT_HATCH -> tier + "_output_hatch";
            case INPUT_BUS -> tier + "_input_bus";
            case OUTPUT_BUS -> tier + "_output_bus";
            case ENERGY_HATCH -> tier + "_energy_input_hatch";
            case DUAL_HATCH -> tier + "_dual_input_hatch";
            default -> null;
        };
    }

    private static String getMaintenanceHatchPath(String tier) {
        return switch (tier.toLowerCase()) {
            case "lv" -> "maintenance_hatch";
            case "mv" -> "configurable_maintenance_hatch";
            case "hv" -> "cleaning_maintenance_hatch";
            case "ev" -> "auto_maintenance_hatch";
            default -> null;
        };
    }

    private static String getMufflerHatchPath(String tier) {
        String lowerTier = tier.toLowerCase();
        return switch (lowerTier) {
            case "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv" -> lowerTier + "_muffler_hatch";
            default -> null; // if tier does not exist
        };
    }

    private static Block getCoilBlock(int tier) {
        if (tier < 0 || tier >= COIL_PROGRESSION.length) {
            return null;
        }

        ResourceLocation blockId = new ResourceLocation("gtceu", COIL_PROGRESSION[tier]);
        return BuiltInRegistries.BLOCK.get(blockId);
    }

    private static int getCoilTier(String blockName) {
        for (int i = 0; i < COIL_PROGRESSION.length; i++) {
            if (blockName.contains(COIL_PROGRESSION[i]) ||
                    blockName.contains(COIL_PROGRESSION[i].replace("_coil_block", ""))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean canUpgrade(ComponentInfo component, int targetTier) {
        if (!component.getType().isUpgradeable()) {
            return false;
        }

        ComponentType type = component.getType();

        if (type == ComponentType.MAINTENANCE) {
            if (targetTier == GTValues.LV || targetTier == GTValues.MV ||
                targetTier == GTValues.HV || targetTier == GTValues.EV) {
                Block targetBlock = getComponentBlock(type, targetTier);
                if (targetBlock != null) {
                    Block currentBlock = component.getState().getBlock();
                    return targetBlock != currentBlock;
                }
            }
            return false;
        }

        if (type == ComponentType.COIL) {
            if (targetTier >= 0 && targetTier < 8) {
                if (targetTier != component.getTier()) {
                    Block targetBlock = getCoilBlock(targetTier);
                    return targetBlock != null;
                }
            }
            return false;
        }

        if (type == ComponentType.MUFFLER) {
            if (targetTier >= GTValues.LV && targetTier <= GTValues.UV) {
                if (targetTier != component.getTier()) {
                    Block targetBlock = getComponentBlock(type, targetTier);
                    return targetBlock != null;
                }
            }
            return false;
        }
        
        if (type == ComponentType.INPUT_HATCH || type == ComponentType.OUTPUT_HATCH ||
            type == ComponentType.INPUT_BUS || type == ComponentType.OUTPUT_BUS ||
            type == ComponentType.ENERGY_HATCH || type == ComponentType.DUAL_HATCH) {
            
            if (targetTier == component.getTier()) {
                return false;
            }
            
            if (targetTier < 0 || targetTier >= GTValues.VN.length) {
                return false;
            }
            
            Block targetBlock = getComponentBlock(type, targetTier);
            return targetBlock != null;
        }

        if (targetTier <= component.getTier()) {
            return false;
        }

        if (targetTier >= GTValues.VN.length) {
            return false;
        }

        return getComponentBlock(type, targetTier) != null;
    }

    public static String getUpgradeName(ComponentInfo component, int targetTier) {
        if (component.getType() == ComponentType.COIL) {
            return getCoilDisplayName(targetTier);
        }
        
        if (component.getType() == ComponentType.MAINTENANCE) {
            return getMaintenanceDisplayName(targetTier);
        }
        
        if (targetTier < 0 || targetTier >= GTValues.VN.length) {
            return "Invalid Tier";
        }

        return component.getType().getDisplayName() + " → " + GTValues.VN[targetTier];
    }

    private static String getMaintenanceDisplayName(int tier) {
        return switch (tier) {
            case GTValues.LV -> "Maintenance Hatch";
            case GTValues.MV -> "Configurable Maintenance Hatch";
            case GTValues.HV -> "Cleaning Maintenance Hatch";
            case GTValues.EV -> "Auto Maintenance Hatch";
            default -> "Unknown Maintenance Hatch";
        };
    }

    private static String getCoilDisplayName(int tier) {
        if (tier < 0 || tier >= COIL_PROGRESSION.length) {
            return "Unknown Coil";
        }
        
        String[] COIL_DISPLAY_NAMES = {
            "Cupronickel Coil Block",
            "Kanthal Coil Block",
            "Nichrome Coil Block",
            "RTM Alloy Coil Block",
            "HSS-G Coil Block",
            "Naquadah Coil Block",
            "Trinium Coil Block",
            "Tritanium Coil Block"
        };
        
        return COIL_DISPLAY_NAMES[tier];
    }

    public static int[] getPossibleUpgradeTiers(ComponentInfo component) {
        if (!component.getType().isUpgradeable()) {
            return new int[0];
        }

        int currentTier = component.getTier();
        int maxTier = GTValues.VN.length - 1;

        int count = 0;
        for (int tier = currentTier + 1; tier <= maxTier; tier++) {
            if (canUpgrade(component, tier)) {
                count++;
            }
        }

        int[] tiers = new int[count];
        int index = 0;
        for (int tier = currentTier + 1; tier <= maxTier; tier++) {
            if (canUpgrade(component, tier)) {
                tiers[index++] = tier;
            }
        }

        return tiers;
    }
}
