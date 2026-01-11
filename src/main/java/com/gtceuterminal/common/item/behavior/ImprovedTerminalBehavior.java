package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

public class ImprovedTerminalBehavior {

    private final int cooldownTicks;
    private final boolean enableSounds;

    public ImprovedTerminalBehavior(int cooldownTicks, boolean enableSounds) {
        this.cooldownTicks = cooldownTicks;
        this.enableSounds = enableSounds;
    }

    public ImprovedTerminalBehavior() {
        this(20, true);
    }

    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();
        InteractionHand usedHand = context.getHand();

        // Requested behavior: allow opening the Multi-Structure Manager no matter what block is clicked.
     
        if (level.isClientSide) {
            openMultiStructureManager(player, itemStack, usedHand);
        }

        if (cooldownTicks > 0 && player.getCooldowns().isOnCooldown(itemStack.getItem())) {
            if (!level.isClientSide) {
                sendMessage(player, "terminal.cooldown", false);
            }
            // Cooldown only affects server-side actions (like auto-build). The GUI is still allowed.
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        MetaMachine machine = MetaMachine.getMachine(level, blockPos);

        if (!level.isClientSide && machine instanceof IMultiController controller && !controller.isFormed()) {
            try {
                controller.getPattern().autoBuild(player, controller.getMultiblockState());

                if (cooldownTicks > 0) {
                    player.getCooldowns().addCooldown(itemStack.getItem(), cooldownTicks);
                }

                sendMessage(player, "terminal.success", true);
                playSound(level, blockPos, SoundEvents.ANVIL_USE, 1.0f, 1.2f);

            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error during auto-build", e);
                sendMessage(player, "terminal.failed", false);
                playSound(level, blockPos, SoundEvents.VILLAGER_NO, 0.5f, 0.8f);
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Item item, @NotNull Level level,
                                                           @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                openMultiStructureManager(player, itemStack, usedHand);
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private void openMultiStructureManager(Player player, ItemStack itemStack, InteractionHand hand) {
        com.gtceuterminal.client.gui.multiblock.MultiStructureManagerScreen.open(player, itemStack);
    }

    private void sendMessage(@NotNull Player player, @NotNull String message, boolean isSuccess) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private void playSound(@NotNull Level level, @NotNull BlockPos pos,
                           @NotNull net.minecraft.sounds.SoundEvent sound,
                           float volume, float pitch) {
        if (enableSounds) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}