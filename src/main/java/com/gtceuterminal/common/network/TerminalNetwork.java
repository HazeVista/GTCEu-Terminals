package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

 // Handles network communication between client and server

public class TerminalNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTCEUTerminalMod.MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.messageBuilder(CPacketBlockReplacement.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketBlockReplacement::encode)
                .decoder(CPacketBlockReplacement::new)
                .consumerMainThread(CPacketBlockReplacement::handle)
                .add();

        CHANNEL.messageBuilder(CPacketSchematicAction.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketSchematicAction::encode)
                .decoder(CPacketSchematicAction::new)
                .consumerMainThread(CPacketSchematicAction::handle)
                .add();

        CHANNEL.messageBuilder(CPacketComponentUpgrade.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketComponentUpgrade::encode)
                .decoder(CPacketComponentUpgrade::decode)
                .consumerMainThread(CPacketComponentUpgrade::handle)
                .add();

        CHANNEL.messageBuilder(CPacketSetCustomMultiblockName.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketSetCustomMultiblockName::encode)
                .decoder(CPacketSetCustomMultiblockName::new)
                .consumerMainThread(CPacketSetCustomMultiblockName::handle)
                .add();

        GTCEUTerminalMod.LOGGER.info("Network packets registered");
    }
}