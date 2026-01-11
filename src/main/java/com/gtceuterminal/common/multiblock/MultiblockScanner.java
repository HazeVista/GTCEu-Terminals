package com.gtceuterminal.common.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.config.CoilConfig;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiblockScanner {

    private static final int MAX_SCAN_SIZE_XZ = 48;
    private static final int MAX_SCAN_SIZE_Y  = 48;
    private static final int BOUNDS_PADDING = 2;

private static boolean isCandidate(BlockState state) {
    if (state == null || state.isAir()) return false;

    try {
        String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
        if ("gtceu".equals(namespace)) return true;
    } catch (Exception ignored) {}

    // Allow configured coil blocks even if they aren't in the gtceu namespace.
    return CoilConfig.getCoilTier(state) >= 0;
}

    public static List<MultiblockInfo> scanNearbyMultiblocks(Player player, Level level, int radius) {
        List<MultiblockInfo> multiblocks = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();
        Vec3 playerVec = player.position();

        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-radius, -radius, -radius),
                playerPos.offset(radius, radius, radius)
        )) {
            MetaMachine machine = MetaMachine.getMachine(level, pos);

            if (machine instanceof IMultiController controller) {
                BlockPos controllerPos = pos.immutable();
                double distance = playerVec.distanceTo(Vec3.atCenterOf(controllerPos));

                String name = getMultiblockName(controller);
                int tier = getMultiblockTier(controller);
                boolean isFormed = controller.isFormed();

                MultiblockInfo info = new MultiblockInfo(
                        controller,
                        name,
                        controllerPos,
                        tier,
                        distance,
                        isFormed
                );

                if (isFormed) {
                    scanComponents(controller, info, level);
                }

                multiblocks.add(info);
            }
        }

        multiblocks.sort((a, b) -> Double.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
        return multiblocks;
    }

    private static void scanComponents(IMultiController controller, MultiblockInfo info, Level level) {
        Set<BlockPos> scannedPositions = new HashSet<>();
        BlockPos controllerPos = controller.self().getPos();

        try {
            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                for (var part : parts) {
                    if (part == null || part.self() == null) continue;

                    BlockPos partPos = part.self().getPos().immutable();
                    if (!scannedPositions.add(partPos)) continue;

                    BlockState state = level.getBlockState(partPos);
                    ComponentInfo component = identifyComponent(state, partPos);
                    if (component != null) info.addComponent(component);
                }
            }

            Set<BlockPos> structurePositions = getMultiblockBlocks(controller);

            for (BlockPos pos : structurePositions) {
                BlockPos ipos = pos.immutable();
                if (!scannedPositions.add(ipos)) continue;

                BlockState state = level.getBlockState(ipos);
                if (state.isAir()) continue;

                ComponentInfo component = identifyComponent(state, ipos);

                if (component != null &&
                        (component.getType().isUpgradeable() || component.getType() == ComponentType.COIL)) {
                    info.addComponent(component);
                }
            }

            GTCEUTerminalMod.LOGGER.debug("Scanned {} total blocks for multiblock at {}",
                    scannedPositions.size(), controllerPos);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error scanning components", e);
        }
    }

    private static ComponentInfo identifyComponent(BlockState state, BlockPos pos) {
        try {
            String blockName = state.getBlock().builtInRegistryHolder().key().location().getPath();

            ComponentType type = identifyComponentType(blockName);
            int tier = identifyTier(blockName, state);

            return new ComponentInfo(type, tier, pos.immutable(), state);
        } catch (Exception e) {
            return null;
        }
    }

    private static ComponentType identifyComponentType(String blockName) {
        if (blockName == null) return ComponentType.OTHER;

        String lower = blockName.toLowerCase();

        // Parallel Hatch
        if (lower.contains("parallel_hatch")) {
            return ComponentType.PARALLEL_HATCH;
        }

        if (lower.contains("input_hatch") || lower.contains("import_hatch")) {
            return ComponentType.INPUT_HATCH;
        } else if (lower.contains("output_hatch") || lower.contains("export_hatch")) {
            return ComponentType.OUTPUT_HATCH;
        } else if (lower.contains("input_bus") || lower.contains("import_bus")) {
            return ComponentType.INPUT_BUS;
        } else if (lower.contains("output_bus") || lower.contains("export_bus")) {
            return ComponentType.OUTPUT_BUS;
        } else if (lower.contains("energy_hatch") || lower.contains("dynamo_hatch")) {
            return ComponentType.ENERGY_HATCH;
        } else if (lower.contains("muffler")) {
            return ComponentType.MUFFLER;
        } else if (lower.contains("maintenance")) {
            return ComponentType.MAINTENANCE;
        } else if (lower.contains("coil")) {
            return ComponentType.COIL;
        } else if (lower.contains("pipe")) {
            return ComponentType.PIPE;
        }

        return ComponentType.OTHER;
    }

    private static int identifyTier(String blockName, BlockState state) {
        if (blockName == null) return -1;

        String lower = blockName.toLowerCase();

        if (lower.contains("coil")) {
            return CoilConfig.getCoilTier(state);
        }

        for (int i = 0; i < GTValues.VN.length; i++) {
            String tierName = GTValues.VN[i].toLowerCase();
            if (lower.contains(tierName) || lower.contains("." + tierName + ".")) {
                return i;
            }
        }

        return -1;
    }

    private static String getMultiblockName(IMultiController controller) {
        try {
            BlockState state = controller.self().getBlockState();
            String displayName = state.getBlock().getName().getString();

            if (!displayName.isEmpty() && !displayName.contains("block.gtceu")) {
                return displayName;
            }

            String className = controller.getClass().getSimpleName();
            String name = className
                    .replace("MetaTileEntity", "")
                    .replace("Machine", "")
                    .replace("Controller", "");

            return name.isEmpty() ? "Unknown Multiblock" : name;
        } catch (Exception e) {
            return "Unknown Multiblock";
        }
    }

    private static int getMultiblockTier(IMultiController controller) {
        try {
            if (controller instanceof MetaMachine meta) {
                return meta.getDefinition().getTier();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static Set<BlockPos> getMultiblockBlocks(IMultiController controller) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos controllerPos = controller.self().getPos();
        Level level = controller.self().getLevel();

        try {
            List<BlockPos> anchors = new ArrayList<>();
            anchors.add(controllerPos.immutable());

            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                for (var part : parts) {
                    if (part != null && part.self() != null) {
                        BlockPos p = part.self().getPos().immutable();
                        anchors.add(p);
                        positions.add(p);
                    }
                }
            }

            Bounds b = Bounds.fromAnchors(anchors, BOUNDS_PADDING);
b = b.clampToMaxSize(controllerPos, MAX_SCAN_SIZE_XZ, MAX_SCAN_SIZE_Y);

// Add controller itself if it's a candidate.
if (isCandidate(level.getBlockState(controllerPos))) {
    positions.add(controllerPos.immutable());
}

// Flood-fill only connected candidate blocks. This avoids "merging" nearby but separated multiblocks.
ArrayDeque<BlockPos> queue = new ArrayDeque<>();
Set<BlockPos> visited = new HashSet<>();

for (BlockPos a : anchors) {
    if (a == null) continue;
    if (visited.add(a)) {
        queue.add(a);
    }
}

while (!queue.isEmpty()) {
    BlockPos cur = queue.poll();

    for (Direction dir : Direction.values()) {
        BlockPos next = cur.relative(dir);
        if (!b.contains(next)) continue;
        if (!visited.add(next)) continue;

        BlockState s = level.getBlockState(next);
        if (!isCandidate(s)) continue;

        positions.add(next.immutable());
        queue.add(next);
    }
}
GTCEUTerminalMod.LOGGER.debug(
                    "getMultiblockBlocks: {} blocks in bounds [{},{} , {},{} , {},{}] controller={}",
                    positions.size(),
                    b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ,
                    controllerPos
            );

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error scanning multiblock blocks at " + controllerPos, e);
        }

        return positions;
    }

    public static com.gtceuterminal.common.data.BlockReplacementData scanMultiblock(IMultiController controller) {
        com.gtceuterminal.common.data.BlockReplacementData data =
                new com.gtceuterminal.common.data.BlockReplacementData();

        Set<BlockPos> positions = getMultiblockBlocks(controller);

        for (BlockPos pos : positions) {
            try {
                BlockState state = controller.self().getLevel().getBlockState(pos);
                if (!state.isAir()) {
                    data.addBlock(state);
                }
            } catch (Exception ignored) {}
        }

        return data;
    }

    private static final class Bounds {
        int minX, maxX, minY, maxY, minZ, maxZ;


boolean contains(BlockPos pos) {
    if (pos == null) return false;
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();
    return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
}

        static Bounds fromAnchors(List<BlockPos> anchors, int padding) {
            Bounds b = new Bounds();
            b.minX = Integer.MAX_VALUE;
            b.minY = Integer.MAX_VALUE;
            b.minZ = Integer.MAX_VALUE;
            b.maxX = Integer.MIN_VALUE;
            b.maxY = Integer.MIN_VALUE;
            b.maxZ = Integer.MIN_VALUE;

            for (BlockPos p : anchors) {
                b.minX = Math.min(b.minX, p.getX());
                b.minY = Math.min(b.minY, p.getY());
                b.minZ = Math.min(b.minZ, p.getZ());
                b.maxX = Math.max(b.maxX, p.getX());
                b.maxY = Math.max(b.maxY, p.getY());
                b.maxZ = Math.max(b.maxZ, p.getZ());
            }

            b.minX -= padding; b.maxX += padding;
            b.minY -= padding; b.maxY += padding;
            b.minZ -= padding; b.maxZ += padding;

            return b;
        }

        Bounds clampToMaxSize(BlockPos center, int maxXZ, int maxY) {
            int sizeX = (maxX - minX) + 1;
            int sizeY = (maxY - minY) + 1;
            int sizeZ = (maxZ - minZ) + 1;

            if (sizeX > maxXZ) {
                int half = maxXZ / 2;
                minX = center.getX() - half;
                maxX = center.getX() + half;
            }
            if (sizeZ > maxXZ) {
                int half = maxXZ / 2;
                minZ = center.getZ() - half;
                maxZ = center.getZ() + half;
            }
            if (sizeY > maxY) {
                int half = maxY / 2;
                minY = center.getY() - half;
                maxY = center.getY() + half;
            }
            return this;
        }
    }
}