package com.gtceuterminal;

import com.gtceuterminal.common.config.*;
import com.gtceuterminal.common.data.GTCEUTerminalItems;
import com.gtceuterminal.common.data.GTCEUTerminalTabs;
import com.gtceuterminal.common.network.TerminalNetwork;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("gtceuterminal")
public class GTCEUTerminalMod {
    public static final String MOD_ID = "gtceuterminal";
    public static final String NAME = "GTCEu Terminal";
    public static final Logger LOGGER = LoggerFactory.getLogger("GTCEu Terminal");

    public GTCEUTerminalMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GTCEUTerminalItems.ITEMS.register(modEventBus);
        GTCEUTerminalTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        LOGGER.info("GTCEu Terminal initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TerminalNetwork::registerPackets);
    }

    @EventBusSubscriber(modid = "gtceuterminal", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventHandler {
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                LOGGER.info("Initializing GTCEu Terminal configuration system...");

                // Coil
                CoilConfig.initialize();
                LOGGER.info("  ✓ Coil configuration: {} types", CoilConfig.getAllCoils().size());

                // Hatches
                HatchConfig.initialize();
                LOGGER.info("  ✓ Hatch configuration: {} input, {} output",
                        HatchConfig.getInputHatches().size(),
                        HatchConfig.getOutputHatches().size());
                // Buses
                BusConfig.initialize();
                LOGGER.info("  ✓ Bus configuration: {} input, {} output",
                        BusConfig.getInputBuses().size(),
                        BusConfig.getOutputBuses().size());
                // Energy Hatches
                EnergyHatchConfig.initialize();
                LOGGER.info("  ✓ Energy hatch configuration: {} input, {} output",
                        EnergyHatchConfig.getInputHatches().size(),
                        EnergyHatchConfig.getOutputHatches().size());
                // Parallel Hatches
                ParallelHatchConfig.initialize();
                LOGGER.info("  ✓ Parallel hatch configuration: {} hatches",
                        ParallelHatchConfig.getAllParallelHatches().size());
                // Muffler Hatches
                MufflerHatchConfig.initialize();
                LOGGER.info("  ✓ Muffler hatch configuration: {} hatches",
                        MufflerHatchConfig.getAllMufflerHatches().size());

                // Calculate total components
                int totalComponents =
                        HatchConfig.getInputHatches().size() +
                                HatchConfig.getOutputHatches().size() +
                                BusConfig.getInputBuses().size() +
                                BusConfig.getOutputBuses().size() +
                                EnergyHatchConfig.getAllEnergyHatches().size() +
                                ParallelHatchConfig.getAllParallelHatches().size() +
                                MufflerHatchConfig.getAllMufflerHatches().size();

                LOGGER.info("GTCEu Terminal configuration loaded: {} total components", totalComponents);
            });
        }
    }
}