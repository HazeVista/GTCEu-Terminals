package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpgradeConfirmationDialog extends Screen {
    
    private final Screen parent;
    private final ComponentInfo component;
    private final int targetTier;
    private final MultiblockInfo multiblock;
    private final Player player;
    private final Runnable onSuccess;
    
    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 180;
    
    private int dialogX;
    private int dialogY;
    
    private Button confirmButton;
    private Button cancelButton;
    
    private List<MaterialAvailability> materials;
    private boolean hasEnough;
    
    public UpgradeConfirmationDialog(
        Screen parent,
        ComponentInfo component,
        int targetTier,
        MultiblockInfo multiblock,
        Player player,
        Runnable onSuccess
    ) {
        super(Component.literal("Upgrade Confirmation"));
        this.parent = parent;
        this.component = component;
        this.targetTier = targetTier;
        this.multiblock = multiblock;
        this.player = player;
        this.onSuccess = onSuccess;
    }
    
    @Override
    protected void init() {
        dialogX = (width - DIALOG_WIDTH) / 2;
        dialogY = (height - DIALOG_HEIGHT) / 2;
        
        // Calculate required materials
        Map<Item, Integer> required = ComponentUpgradeHelper.getUpgradeItems(component, targetTier);
        materials = MaterialCalculator.checkMaterialsAvailability(required, player, minecraft.level);
        hasEnough = MaterialCalculator.hasEnoughMaterials(materials);
        
        // Confirm button
        confirmButton = Button.builder(
            Component.literal(hasEnough ? "Confirm Upgrade" : "Missing Materials"),
            btn -> {
                if (hasEnough) {
                    performUpgrade();
                }
            })
            .bounds(dialogX + 20, dialogY + DIALOG_HEIGHT - 35, 110, 20)
            .build();
        confirmButton.active = hasEnough;
        addRenderableWidget(confirmButton);
        
        // Cancel button
        cancelButton = Button.builder(
            Component.literal("Cancel"),
            btn -> minecraft.setScreen(parent))
            .bounds(dialogX + 150, dialogY + DIALOG_HEIGHT - 35, 110, 20)
            .build();
        addRenderableWidget(cancelButton);
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x80000000);
        
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF2B2B2B);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 2, 0xFF3F3F3F);
        
        String title = "Upgrade Confirmation";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, dialogX + (DIALOG_WIDTH - titleWidth) / 2, dialogY + 10, 0xFFFFFF);
        
        // Draw component info
        int y = dialogY + 30;
        
        String compName = component.getType().getDisplayName();
        String currentTier = component.getTierName();
        String newTier = com.gregtechceu.gtceu.api.GTValues.VN[targetTier].toUpperCase(java.util.Locale.ROOT);
        
        graphics.drawString(font, "§7Upgrading:", dialogX + 20, y, 0xFFFFFF);
        y += 12;
        graphics.drawString(font, "§b" + compName, dialogX + 20, y, 0xFFFFFF);
        y += 12;
        graphics.drawString(font, "§7" + currentTier + " §f→ §e" + newTier, dialogX + 20, y, 0xFFFFFF);
        
        y += 18;
        graphics.drawString(font, "§7Required Materials:", dialogX + 20, y, 0xFFFFFF);
        y += 12;
        
        // Draw materials list
        for (MaterialAvailability mat : materials) {
            String icon = mat.hasEnough() ? "§a✓" : "§c✗";
            String matText = icon + " §7" + mat.getItemName();
            graphics.drawString(font, matText, dialogX + 25, y, 0xFFFFFF);
            
            // Show counts
            String counts = "§7(" + mat.getAvailable() + "/" + mat.getRequired() + ")";
            graphics.drawString(font, counts, dialogX + 200, y, 0xFFFFFF);
            
            y += 10;
        }
        
        // Draw warning if missing materials
        if (!hasEnough) {
            y += 5;
            graphics.drawString(font, "§c⚠ Missing materials!", dialogX + 20, y, 0xFFFFFF);
        }
        
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    private void performUpgrade() {
        ComponentUpgrader.UpgradeResult result = ComponentUpgrader.upgradeComponent(
            component,
            targetTier,
            player,
            minecraft.level,
            true
        );
        
        if (result.success) {
            player.displayClientMessage(
                Component.literal("§a✓ " + result.message),
                true
            );
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            if (onSuccess != null) {
                onSuccess.run();
            }
        } else {
            player.displayClientMessage(
                Component.literal("§c✗ " + result.message),
                true
            );
            player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }
        
        minecraft.setScreen(parent);
    }
    
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}