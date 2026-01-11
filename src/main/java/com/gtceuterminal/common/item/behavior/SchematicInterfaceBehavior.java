package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.SchematicInterfaceScreen;
import com.gtceuterminal.common.data.SchematicData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class SchematicInterfaceBehavior {

    @NotNull
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();

        boolean shiftDown = player.isShiftKeyDown();

        if (shiftDown) {
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);
            if (machine instanceof IMultiController) {
                IMultiController controller = (IMultiController) machine;
                if (controller.isFormed()) {
                    if (!level.isClientSide) {
                        copyMultiblock(controller, itemStack, player, level, blockPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            if (level.isClientSide) {
                openSchematicGUI(itemStack, player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            pasteMultiblockAtLookPosition(itemStack, player, level);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Item item, @NotNull Level level,
                                                  @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        // Use the actual Shift key state instead of "crouching" so it works while flying/in-air.
        boolean shiftDown = player.isShiftKeyDown();

        // Shift+Right Click in the air = open GUI (client) and consume the action (both sides)
        if (shiftDown) {
            if (level.isClientSide) {
                openSchematicGUI(itemStack, player);
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
        }

        // Right click in the air (no shift) = paste (server)
        if (!level.isClientSide) {
            pasteMultiblockAtLookPosition(itemStack, player, level);
        }
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }

    private void copyMultiblock(IMultiController controller, ItemStack stack, Player player,
                                Level level, BlockPos controllerPos) {
        try {
            Set<BlockPos> positions = scanMultiblockArea(controller, level);

            if (positions.isEmpty()) {
                player.displayClientMessage(Component.literal("Failed to scan multiblock!"), true);
                return;
            }

            Map<BlockPos, BlockState> blocks = new HashMap<>();
            Map<String, Integer> namespaceStats = new HashMap<>();

            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);

                if (state.isAir()) {
                    continue;
                }

                String namespace = state.getBlock().builtInRegistryHolder()
                        .key().location().getNamespace();
                namespaceStats.put(namespace, namespaceStats.getOrDefault(namespace, 0) + 1);

                BlockPos relativePos = pos.subtract(controllerPos);
                blocks.put(relativePos, state);
            }

            Direction originalFacing = Direction.NORTH;
            BlockState controllerState = level.getBlockState(controllerPos);

            if (controllerState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                originalFacing = controllerState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            } else if (controllerState.hasProperty(BlockStateProperties.FACING)) {
                Direction facing = controllerState.getValue(BlockStateProperties.FACING);
                if (facing.getAxis() != Direction.Axis.Y) {
                    originalFacing = facing;
                }
            }

            GTCEUTerminalMod.LOGGER.info("Copied multiblock with original facing: {}", originalFacing);
            GTCEUTerminalMod.LOGGER.info("Copied {} blocks from {} different mods",
                    blocks.size(), namespaceStats.size());

            String multiblockType = controller.getClass().getSimpleName();
            SchematicData clipboard = new SchematicData("Clipboard", multiblockType, blocks,
                    originalFacing.getName());

            CompoundTag stackTag = stack.getOrCreateTag();
            CompoundTag clipboardTag = clipboard.toNBT();
            stackTag.put("Clipboard", clipboardTag);

            player.displayClientMessage(
                    Component.literal("§aCopied " + blocks.size() + " blocks (will face you when pasted)"),
                    true
            );

            level.playSound(null, controllerPos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error copying multiblock", e);
            player.displayClientMessage(Component.literal("§cError copying multiblock!"), true);
        }
    }

    private void pasteMultiblockAtLookPosition(ItemStack stack, Player player, Level level) {
        try {
            CompoundTag stackTag = stack.getTag();
            if (stackTag == null || !stackTag.contains("Clipboard")) {
                player.displayClientMessage(
                        Component.literal("No schematic in clipboard! Copy a multiblock first."),
                        true
                );
                return;
            }

            CompoundTag clipboardTag = stackTag.getCompound("Clipboard");
            SchematicData clipboard = SchematicData.fromNBT(clipboardTag, level.registryAccess());

            Direction originalFacing = Direction.NORTH;
            try {
                String facingStr = clipboard.getOriginalFacing();
                if (facingStr != null && !facingStr.isEmpty()) {
                    originalFacing = Direction.byName(facingStr);
                }
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.warn("Could not parse original facing, using NORTH");
            }

            double distance = calculateOptimalDistance(clipboard);
            BlockPos pasteOrigin = getTargetPlacementPosition(player, level, distance);

            if (pasteOrigin == null) {
                player.displayClientMessage(
                        Component.literal("§eCouldn't find placement position!"),
                        true
                );
                return;
            }

            Direction playerFacing = getPlayerHorizontalFacing(player);

            Direction targetFacing = playerFacing.getOpposite();

            Rotation rotation = getRotationBetweenFacings(originalFacing, targetFacing);

            GTCEUTerminalMod.LOGGER.info("Player facing: {}", playerFacing);
            GTCEUTerminalMod.LOGGER.info("Original multiblock facing: {}", originalFacing);
            GTCEUTerminalMod.LOGGER.info("Target facing (toward player): {}", targetFacing);
            GTCEUTerminalMod.LOGGER.info("Rotation applied: {}", rotation);

            int placedCount = 0;
            int failedCount = 0;
            List<String> missingBlocks = new ArrayList<>();

            for (Map.Entry<BlockPos, BlockState> entry : clipboard.getBlocks().entrySet()) {
                BlockPos relativePos = entry.getKey();
                BlockState state = entry.getValue();

                BlockPos rotatedPos = rotatePosition(relativePos, rotation);
                BlockPos targetPos = pasteOrigin.offset(rotatedPos);

                BlockState rotatedState = state.rotate(rotation);

                if (tryPlaceBlock(player, level, targetPos, rotatedState)) {
                    placedCount++;
                } else {
                    failedCount++;
                    String blockName = rotatedState.getBlock().getName().getString();
                    if (!missingBlocks.contains(blockName)) {
                        missingBlocks.add(blockName);
                    }
                }
            }

            if (placedCount > 0) {
                player.displayClientMessage(
                        Component.literal("§aPlaced " + placedCount + " blocks (facing you)" +
                                (failedCount > 0 ? " §c(" + failedCount + " failed)" : "")),
                        true
                );

                if (!missingBlocks.isEmpty() && missingBlocks.size() <= 5) {
                    player.displayClientMessage(
                            Component.literal("§eMissing: " + String.join(", ", missingBlocks)),
                            false
                    );
                }

                level.playSound(null, pasteOrigin, SoundEvents.ITEM_PICKUP,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                player.displayClientMessage(
                        Component.literal("§cFailed to place any blocks! Check inventory/ME."),
                        true
                );
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error pasting multiblock", e);
            player.displayClientMessage(
                    Component.literal("§cError pasting multiblock!"),
                    true
            );
        }
    }

    private Set<BlockPos> scanMultiblockArea(IMultiController controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();

        try {
            Collection<BlockPos> cachePos = controller.getMultiblockState().getCache();
            if (cachePos != null && !cachePos.isEmpty()) {
                positions.addAll(cachePos);
                return positions;
            }
        } catch (Exception e) {
        }

        BlockPos controllerPos = controller.self().getPos();
        List<IMultiPart> parts = controller.getParts();

        if (parts.isEmpty()) {
            return positions;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (IMultiPart part : parts) {
            BlockPos pos = part.self().getPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        minX -= 3; minY -= 3; minZ -= 3;
        maxX += 3; maxY += 3; maxZ += 3;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir()) {
                        positions.add(pos);
                    }
                }
            }
        }

        return positions;
    }

    private void openSchematicGUI(ItemStack stack, Player player) {
        try {
            Minecraft mc = Minecraft.getInstance();
            List<SchematicData> schematics = loadSchematics(stack, mc.level);

            SchematicInterfaceScreen screen = new SchematicInterfaceScreen(
                    stack,
                    schematics,
                    action -> {}
            );

            mc.setScreen(screen);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error opening schematic GUI", e);
        }
    }

    private List<SchematicData> loadSchematics(ItemStack stack, Level level) {
        List<SchematicData> schematics = new ArrayList<>();

        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("SavedSchematics")) {
            return schematics;
        }

        ListTag savedList = stackTag.getList("SavedSchematics", 10);
        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                schematics.add(SchematicData.fromNBT(schematicTag, level.registryAccess()));
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error loading schematic {}: {}", i, e.getMessage());
            }
        }

        return schematics;
    }

    private double calculateOptimalDistance(SchematicData schematic) {
        BlockPos size = schematic.getSize();
        int maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        double distance = 4.0 + maxDimension / 2.0;
        return Math.min(15.0, Math.max(4.0, distance));
    }

    private BlockPos getTargetPlacementPosition(Player player, Level level, double distance) {

        double raycastDistance = Math.max(10.0, distance + 5.0);

        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(lookVec.scale(raycastDistance));

        BlockHitResult hitResult = level.clip(new net.minecraft.world.level.ClipContext(
                eyePos,
                endVec,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return hitResult.getBlockPos().relative(hitResult.getDirection());
        }

        Vec3 targetVec = eyePos.add(lookVec.scale(distance));

        return new BlockPos(
                (int)Math.floor(targetVec.x),
                (int)Math.floor(targetVec.y),
                (int)Math.floor(targetVec.z)
        );
    }

    private Direction getPlayerHorizontalFacing(Player player) {
        return Direction.fromYRot(player.getYRot());
    }

    private Rotation getRotationBetweenFacings(Direction from, Direction to) {
        if (from == to) return Rotation.NONE;

        int fromIndex = getHorizontalIndex(from);
        int toIndex = getHorizontalIndex(to);

        int diff = (toIndex - fromIndex + 4) % 4;

        return switch (diff) {
            case 1 -> Rotation.CLOCKWISE_90;        // 90°
            case 2 -> Rotation.CLOCKWISE_180;       // 180°
            case 3 -> Rotation.COUNTERCLOCKWISE_90; // 90°
            default -> Rotation.NONE;               // 0°
        };
    }

    private int getHorizontalIndex(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private BlockPos rotatePosition(BlockPos pos, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }

    private boolean hasBlockInInventory(Player player, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return false;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }

        return false;
    }

    private boolean removeBlockFromInventory(Player player, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return false;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return true;
            }
        }

        return false;
    }

    private boolean tryPlaceBlock(Player player, Level level, BlockPos pos, BlockState state) {
        if (player.isCreative()) {
            return level.setBlock(pos, state, 3);
        }

        if (hasBlockInInventory(player, state) && level.setBlock(pos, state, 3)) {
            removeBlockFromInventory(player, state);
            return true;
        }

        return false;
    }
}