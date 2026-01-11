package com.gtceuterminal.common.item;

import com.gtceuterminal.common.item.behavior.ImprovedTerminalBehavior;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MultiStructureManagerItem extends Item {

    private final ImprovedTerminalBehavior behavior;

    public MultiStructureManagerItem(Properties properties, int cooldownTicks, boolean enableSounds) {
        super(properties);
        this.behavior = new ImprovedTerminalBehavior(cooldownTicks, enableSounds);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        return behavior.useOn(context);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        return behavior.use(this, level, player, usedHand);
    }
}