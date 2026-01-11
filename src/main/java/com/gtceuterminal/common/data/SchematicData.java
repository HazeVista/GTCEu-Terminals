package com.gtceuterminal.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SchematicData {

    private final String name;
    private final String multiblockType;
    private final Map<BlockPos, BlockState> blocks;
    private final BlockPos size;
    private final String originalFacing;

    public SchematicData(String name, String multiblockType, Map<BlockPos, BlockState> blocks) {
        this(name, multiblockType, blocks, "south");
    }

    public SchematicData(String name, String multiblockType, Map<BlockPos, BlockState> blocks, String originalFacing) {
        this.name = name;
        this.multiblockType = multiblockType;
        this.blocks = new HashMap<>(blocks);
        this.originalFacing = originalFacing;
        this.size = calculateSize();
    }

    private BlockPos calculateSize() {
        if (blocks.isEmpty()) return BlockPos.ZERO;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public String getName() {
        return name;
    }

    public String getMultiblockType() {
        return multiblockType;
    }

    public Map<BlockPos, BlockState> getBlocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public BlockPos getSize() {
        return size;
    }

    public String getOriginalFacing() {
        return originalFacing;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Type", multiblockType);
        tag.putString("OriginalFacing", originalFacing);

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Saving schematic '{}' - originalFacing: {}", name, originalFacing);

        // Able to copy and paste blocks from different mods, include liquids like water and lava.

        ListTag blocksList = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.put("Pos", NbtUtils.writeBlockPos(entry.getKey()));
            blockTag.put("State", NbtUtils.writeBlockState(entry.getValue()));
            blocksList.add(blockTag);
        }
        tag.put("Blocks", blocksList);

        return tag;
    }

    public static SchematicData fromNBT(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        String name = tag.getString("Name");
        String type = tag.getString("Type");
        String originalFacing = tag.contains("OriginalFacing") ? tag.getString("OriginalFacing") : "south";

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Loading schematic '{}' - originalFacing: {}", name, originalFacing);

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        ListTag blocksList = tag.getList("Blocks", 10);

        var blockLookup = provider.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);

        for (int i = 0; i < blocksList.size(); i++) {
            CompoundTag blockTag = blocksList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(blockTag.getCompound("Pos"));
            BlockState state = NbtUtils.readBlockState(blockLookup, blockTag.getCompound("State"));
            blocks.put(pos, state);
        }

        return new SchematicData(name, type, blocks, originalFacing);
    }

    public SchematicData rotate(net.minecraft.core.Direction.Axis axis) {
        Map<BlockPos, BlockState> rotatedBlocks = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            BlockPos newPos = rotatePosition(pos, axis);
            BlockState newState = rotateBlockState(state, axis);

            rotatedBlocks.put(newPos, newState);
        }

        return new SchematicData(name, multiblockType, rotatedBlocks, originalFacing);
    }

    private BlockPos rotatePosition(BlockPos pos, net.minecraft.core.Direction.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(pos.getX(), -pos.getZ(), pos.getY());
            case Y -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case Z -> new BlockPos(pos.getY(), -pos.getX(), pos.getZ());
        };
    }

    private BlockState rotateBlockState(BlockState state, net.minecraft.core.Direction.Axis axis) {
        try {
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                var rotated = switch (axis) {
                    case Y -> facing.getClockWise();
                    case X, Z -> facing;
                };
                return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, rotated);
            }

            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                var rotated = switch (axis) {
                    case Y -> facing.getClockWise();
                    case X -> rotateFacingX(facing);
                    case Z -> rotateFacingZ(facing);
                };
                return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, rotated);
            }
        }catch (Exception e) {
        }

        return state;
    }

    private net.minecraft.core.Direction rotateFacingX(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case UP -> net.minecraft.core.Direction.NORTH;
            case NORTH -> net.minecraft.core.Direction.DOWN;
            case DOWN -> net.minecraft.core.Direction.SOUTH;
            case SOUTH -> net.minecraft.core.Direction.UP;
            default -> facing;
        };
    }

    private net.minecraft.core.Direction rotateFacingZ(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case UP -> net.minecraft.core.Direction.EAST;
            case EAST -> net.minecraft.core.Direction.DOWN;
            case DOWN -> net.minecraft.core.Direction.WEST;
            case WEST -> net.minecraft.core.Direction.UP;
            default -> facing;
        };
    }
}
