package com.gtceuterminal;

import com.gtceuterminal.common.config.CoilConfig;
import com.gtceuterminal.common.config.ComponentConfig;
import com.gtceuterminal.common.data.GTCEUTerminalItems;
import com.gtceuterminal.common.network.TerminalNetwork;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GTCEUTerminalMod.MOD_ID)
public class GTCEUTerminalMod {

    public static final String MOD_ID = "gtceuterminal";
    public static final String NAME = "GTCEu Terminal";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public GTCEUTerminalMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register items
        GTCEUTerminalItems.ITEMS.register(modEventBus);

        // Register network packets
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("GTCEu Terminal initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TerminalNetwork::registerPackets);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventHandler {

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                // Initialize coil configuration
                CoilConfig.initialize();
                LOGGER.info("Coil configuration loaded with {} coil types",
                        CoilConfig.getAllCoils().size());

                // Initialize component configuration
                ComponentConfig.initialize();
                LOGGER.info("Component configuration loaded");
            });
        }
    }
}