package com.gtceuterminal.common.network;

import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CPacketComponentUpgrade {

    private final List<BlockPos> positions;
    private final int targetTier;

    public CPacketComponentUpgrade(List<BlockPos> positions, int targetTier) {
        this.positions = positions;
        this.targetTier = targetTier;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
        buf.writeInt(targetTier);
    }

    public static CPacketComponentUpgrade decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        int targetTier = buf.readInt();
        return new CPacketComponentUpgrade(positions, targetTier);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            int upgraded = 0;
            int failed = 0;

            for (BlockPos pos : positions) {
                var state = player.level().getBlockState(pos);
                var block = state.getBlock();

                ComponentType type = detectComponentType(block);
                int currentTier = detectTier(block);

                if (type == null) {
                    failed++;
                    continue;
                }

                ComponentInfo component = new ComponentInfo(type, currentTier, pos, state);

                ComponentUpgrader.UpgradeResult result = ComponentUpgrader.upgradeComponent(
                        component,
                        targetTier,
                        player,
                        player.level(),
                        true
                );

                if (result.success) upgraded++;
                else failed++;
            }

            if (upgraded > 0) {
                player.displayClientMessage(
                        Component.literal("§aUpgraded " + upgraded + " components to " +
                                com.gregtechceu.gtceu.api.GTValues.VN[targetTier] +
                                (failed > 0 ? " §e(" + failed + " failed)" : "!")),
                        false
                );
                player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
            } else {
                player.displayClientMessage(
                        Component.literal("§cFailed to upgrade components"),
                        true
                );
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            }
        });

        ctx.get().setPacketHandled(true);
    }

    private static ComponentType detectComponentType(net.minecraft.world.level.block.Block block) {
        String blockName = block.getDescriptionId().toLowerCase();

        // Parallel Hatch
        if (blockName.contains("parallel_hatch")) {
            return ComponentType.PARALLEL_HATCH;
        }

        // Check energy FIRST
        if (blockName.contains("energy_hatch") || blockName.contains("energy_input") ||
                blockName.contains("energy.input") || blockName.contains("energy_output")) {
            return ComponentType.ENERGY_HATCH;
        }

        if (blockName.contains("input_hatch") || blockName.contains("item_import_bus") ||
                blockName.contains("item.input")) {
            return ComponentType.INPUT_HATCH;
        } else if (blockName.contains("output_hatch") || blockName.contains("item_export_bus") ||
                blockName.contains("item.output")) {
            return ComponentType.OUTPUT_HATCH;
        }

        if (blockName.contains("input_bus") || blockName.contains("fluid_import_hatch") ||
                blockName.contains("fluid.input")) {
            return ComponentType.INPUT_BUS;
        } else if (blockName.contains("output_bus") || blockName.contains("fluid_export_hatch") ||
                blockName.contains("fluid.output")) {
            return ComponentType.OUTPUT_BUS;
        }

        if (blockName.contains("muffler")) return ComponentType.MUFFLER;
        if (blockName.contains("maintenance")) return ComponentType.MAINTENANCE;
        if (blockName.contains("coil")) return ComponentType.COIL;

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.warn("Could not detect component type for block: {}", blockName);
        return null;
    }

    private static int detectTier(net.minecraft.world.level.block.Block block) {
        String blockId = block.getDescriptionId().toLowerCase();
        String blockName = block.getName().getString().toLowerCase();

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Detecting tier for block: id='{}', name='{}'", blockId, blockName);

        // Coil tiers
        if (blockId.contains("cupronickel") || blockName.contains("cupronickel")) return 0;
        if (blockId.contains("kanthal") || blockName.contains("kanthal")) return 1;
        if (blockId.contains("nichrome") || blockName.contains("nichrome")) return 2;
        if (blockId.contains("rtm") || blockName.contains("rtm")) return 3;
        if (blockId.contains("hss") || blockName.contains("hss")) return 4;
        if ((blockId.contains("naquadah") || blockName.contains("naquadah")) &&
                (blockId.contains("coil") || blockName.contains("coil"))) return 5;
        if (blockId.contains("trinium") || blockName.contains("trinium")) return 6;
        if (blockId.contains("tritanium") || blockName.contains("tritanium")) return 7;

        // Standard tiers
        if (blockId.contains("ulv") || blockName.contains("ulv")) return 0;
        if (blockId.contains("lv") && !blockId.contains("ulv") && !blockId.contains("luv")) return 1;
        if (blockId.contains("mv")) return 2;
        if (blockId.contains("hv")) return 3;
        if (blockId.contains("ev")) return 4;
        if (blockId.contains("iv") && !blockId.contains("div")) return 5;
        if (blockId.contains("luv")) return 6;
        if (blockId.contains("zpm")) return 7;
        if (blockId.contains("uv") && !blockId.contains("luv")) return 8;

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.warn("Could not detect tier for block '{}', defaulting to 0", blockId);
        return 0;
    }
}