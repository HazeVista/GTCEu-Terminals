package com.gtceuterminal.common.material;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtceuterminal.common.config.*;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.GTCEUTerminalMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

 // Helper class for component upgrades.

public class ComponentUpgradeHelper {

    public static Map<Item, Integer> getUpgradeItems(ComponentInfo component, int targetTier) {
        Map<Item, Integer> items = new HashMap<>();

        Block targetBlock = getComponentBlock(component.getType(), targetTier);
        if (targetBlock != null && targetBlock.asItem() != null) {
            items.put(targetBlock.asItem(), 1);
        }

        return items;
    }

    private static Block getComponentBlock(ComponentType type, int tier) {
        String blockId = getBlockIdFromConfig(type, tier);
        if (blockId == null) return null;

        try {
            ResourceLocation id = new ResourceLocation(blockId);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != net.minecraft.world.level.block.Blocks.AIR) {
                return block;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Invalid block ID: {}", blockId, e);
        }

        return null;
    }


     // Get block ID from config based on component type and tier.

    private static String getBlockIdFromConfig(ComponentType type, int tier) {
        return switch (type) {
            case INPUT_HATCH -> {
                for (HatchConfig.HatchEntry hatch : HatchConfig.getInputHatches()) {
                    if (hatch.tier == tier) yield hatch.blockId;
                }
                yield null;
            }
            case OUTPUT_HATCH -> {
                for (HatchConfig.HatchEntry hatch : HatchConfig.getOutputHatches()) {
                    if (hatch.tier == tier) yield hatch.blockId;
                }
                yield null;
            }
            case INPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getInputBuses()) {
                    if (bus.tier == tier) yield bus.blockId;
                }
                yield null;
            }
            case OUTPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getOutputBuses()) {
                    if (bus.tier == tier) yield bus.blockId;
                }
                yield null;
            }
            case ENERGY_HATCH -> {
                // Get ANY energy hatch for this tier
                List<EnergyHatchConfig.EnergyHatchEntry> energyHatches = EnergyHatchConfig.getAllEnergyHatches();

                // First try basic energy hatch
                for (EnergyHatchConfig.EnergyHatchEntry energy : energyHatches) {
                    if (energy.tier == tier &&
                            (energy.blockId.endsWith("_energy_input_hatch") || energy.blockId.endsWith("_energy_output_hatch"))) {
                        yield energy.blockId;
                    }
                }
                // Then try 4A energy hatch (needs polish lmao)
                for (EnergyHatchConfig.EnergyHatchEntry energy : energyHatches) {
                    if (energy.tier == tier) yield energy.blockId;
                }

                yield null;
            }
            case PARALLEL_HATCH -> {
                for (ParallelHatchConfig.ParallelHatchEntry parallel : ParallelHatchConfig.getAllParallelHatches()) {
                    if (parallel.tier == tier) yield parallel.blockId;
                }
                yield null;
            }
            case MUFFLER -> {
                for (MufflerHatchConfig.MufflerHatchEntry muffler : MufflerHatchConfig.getAllMufflerHatches()) {
                    if (muffler.tier == tier) yield muffler.blockId;
                }
                yield null;
            }
            case COIL -> {
                List<CoilConfig.CoilEntry> coils = CoilConfig.getAllCoils();
                if (tier >= 0 && tier < coils.size()) {
                    yield coils.get(tier).blockId;
                }
                yield null;
            }
            case MAINTENANCE -> {
                // Maintenance hatches are special: LV/MV/HV/EV with specific names
                yield getMaintenanceHatchPath(tier);
            }
            default -> null;
        };
    }

     // Check if component can be upgraded to target tier.

    public static boolean canUpgrade(ComponentInfo component, int targetTier) {
        if (!component.getType().isUpgradeable()) {
            return false;
        }

        ComponentType type = component.getType();

        // Check if component exists in config
        String blockId = getBlockIdFromConfig(type, targetTier);
        if (blockId == null) {
            return false;
        }

        // Don't allow "upgrading" to same tier
        if (targetTier == component.getTier()) {
            return false;
        }

        // Check if block actually exists in game
        return getComponentBlock(type, targetTier) != null;
    }

    public static String getUpgradeName(ComponentInfo component, int targetTier) {
        ComponentType type = component.getType();

        return switch (type) {
            case INPUT_HATCH -> {
                for (HatchConfig.HatchEntry hatch : HatchConfig.getInputHatches()) {
                    if (hatch.tier == targetTier) yield hatch.displayName + " →";
                }
                yield null;
            }
            case OUTPUT_HATCH -> {
                for (HatchConfig.HatchEntry hatch : HatchConfig.getOutputHatches()) {
                    if (hatch.tier == targetTier) yield hatch.displayName + " →";
                }
                yield null;
            }
            case INPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getInputBuses()) {
                    if (bus.tier == targetTier) yield bus.displayName + " →";
                }
                yield null;
            }
            case OUTPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getOutputBuses()) {
                    if (bus.tier == targetTier) yield bus.displayName + " →";
                }
                yield null;
            }
            case ENERGY_HATCH -> {

                for (EnergyHatchConfig.EnergyHatchEntry energy : EnergyHatchConfig.getAllEnergyHatches()) {
                    if (energy.tier == targetTier &&
                            (energy.blockId.endsWith("_energy_input_hatch") || energy.blockId.endsWith("_energy_output_hatch"))) {
                        yield energy.displayName + " →";
                    }
                }
                yield null;
            }
            case PARALLEL_HATCH -> {
                for (ParallelHatchConfig.ParallelHatchEntry parallel : ParallelHatchConfig.getAllParallelHatches()) {
                    if (parallel.tier == targetTier) yield parallel.displayName + " →";
                }
                yield null;
            }
            case MUFFLER -> {
                for (MufflerHatchConfig.MufflerHatchEntry muffler : MufflerHatchConfig.getAllMufflerHatches()) {
                    if (muffler.tier == targetTier) yield muffler.displayName + " →";
                }
                yield null;
            }
            case COIL -> {
                List<CoilConfig.CoilEntry> coils = CoilConfig.getAllCoils();
                if (targetTier >= 0 && targetTier < coils.size()) {
                    yield coils.get(targetTier).displayName;
                }
                yield null;
            }
            case MAINTENANCE -> {
                // Maintenance hatches have special names
                yield getMaintenanceDisplayName(targetTier);
            }
            default -> null;
        };
    }

    public static List<Integer> getAvailableTiers(ComponentType type) {
        return switch (type) {
            case INPUT_HATCH -> HatchConfig.getInputHatches().stream().map(h -> h.tier).sorted().toList();
            case OUTPUT_HATCH -> HatchConfig.getOutputHatches().stream().map(h -> h.tier).sorted().toList();
            case INPUT_BUS -> BusConfig.getInputBuses().stream().map(b -> b.tier).sorted().toList();
            case OUTPUT_BUS -> BusConfig.getOutputBuses().stream().map(b -> b.tier).sorted().toList();
            case ENERGY_HATCH -> EnergyHatchConfig.getAllEnergyHatches().stream().map(e -> e.tier).distinct().sorted().toList();
            case PARALLEL_HATCH -> ParallelHatchConfig.getAllParallelHatches().stream().map(p -> p.tier).sorted().toList();
            case MUFFLER -> MufflerHatchConfig.getAllMufflerHatches().stream().map(m -> m.tier).sorted().toList();
            case COIL -> java.util.stream.IntStream.range(0, CoilConfig.getAllCoils().size()).boxed().toList();
            case MAINTENANCE -> List.of(GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV); // Special tiers
            default -> List.of();
        };
    }

    private static String getMaintenanceHatchPath(int tier) {
        return switch (tier) {
            case GTValues.LV -> "gtceu:maintenance_hatch";
            case GTValues.MV -> "gtceu:configurable_maintenance_hatch";
            case GTValues.HV -> "gtceu:cleaning_maintenance_hatch";
            case GTValues.EV -> "gtceu:auto_maintenance_hatch";
            default -> null;
        };
    }

    private static String getMaintenanceDisplayName(int tier) {
        return switch (tier) {
            case GTValues.LV -> "Maintenance Hatch";
            case GTValues.MV -> "Configurable Maintenance Hatch";
            case GTValues.HV -> "Cleaning Maintenance Hatch";
            case GTValues.EV -> "Auto Maintenance Hatch";
            default -> null;
        };
    }
}