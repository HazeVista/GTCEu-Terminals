package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupUpgradeDialog extends Screen {

    private final Screen parent;
    private final ComponentGroup group;
    private final int targetTier;
    private final MultiblockInfo multiblock;
    private final Player player;
    private final Runnable onSuccess;

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 200;

    private int dialogX;
    private int dialogY;

    private Button confirmButton;
    private Button cancelButton;

    private List<MaterialAvailability> materials;
    private boolean hasEnough;

    public GroupUpgradeDialog(
            Screen parent,
            ComponentGroup group,
            int targetTier,
            MultiblockInfo multiblock,
            Player player,
            Runnable onSuccess
    ) {
        super(Component.literal("Group Upgrade Confirmation"));
        this.parent = parent;
        this.group = group;
        this.targetTier = targetTier;
        this.multiblock = multiblock;
        this.player = player;
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        dialogX = (width - DIALOG_WIDTH) / 2;
        dialogY = (height - DIALOG_HEIGHT) / 2;

        boolean isCreative = player.isCreative();

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            Map<Item, Integer> singleRequired = ComponentUpgradeHelper.getUpgradeItems(rep, targetTier);
            Map<Item, Integer> totalRequired = new HashMap<>();

            for (var entry : singleRequired.entrySet()) {
                totalRequired.put(entry.getKey(), entry.getValue() * group.getCount());
            }

            if (isCreative) {
                // Creative mode
                materials = new ArrayList<>();
                hasEnough = true;
            } else {
                // Survival mode
                materials = MaterialCalculator.checkMaterialsAvailability(totalRequired, player, minecraft.level);
                hasEnough = MaterialCalculator.hasEnoughMaterials(materials);
            }
        } else {
            materials = new ArrayList<>();
            hasEnough = false;
        }

        confirmButton = Button.builder(
                        Component.literal(hasEnough ? "Upgrade All (" + group.getCount() + ")" : "Missing Materials"),
                        btn -> {
                            if (hasEnough) {
                                performGroupUpgrade();
                            }
                        })
                .bounds(dialogX + 20, dialogY + DIALOG_HEIGHT - 40, 120, 20)
                .build();

        confirmButton.active = hasEnough;
        addRenderableWidget(confirmButton);

        cancelButton = Button.builder(
                        Component.literal("Cancel"),
                        btn -> minecraft.setScreen(parent))
                .bounds(dialogX + DIALOG_WIDTH - 140, dialogY + DIALOG_HEIGHT - 40, 120, 20)
                .build();

        addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xE0000000);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 20, 0xFF444444);

        graphics.drawString(font, "Upgrade Group Confirmation",
                dialogX + 10, dialogY + 6, 0xFFFFFF);

        int y = dialogY + 30;

        // Get proper display names
        String componentName = group.getType().getDisplayName();
        String fromName = getDisplayName(group.getType(), group.getTier());
        String toName = getDisplayName(group.getType(), targetTier);

        graphics.drawString(font, "§eUpgrade " + group.getCount() + "x " + componentName,
                dialogX + 10, y, 0xFFFFFF);

        y += 15;
        graphics.drawString(font, "§7From: §f" + fromName,
                dialogX + 10, y, 0xFFFFFF);

        y += 12;
        graphics.drawString(font, "§7To: §f" + toName,
                dialogX + 10, y, 0xFFFFFF);

        y += 20;

        // Show materials only in survival mode
        if (!player.isCreative()) {
            graphics.drawString(font, "§7Required materials:",
                    dialogX + 10, y, 0xFFFFFF);

            y += 12;
            for (MaterialAvailability mat : materials) {
                String itemName = mat.getItemName();
                int required = mat.getRequired();
                long available = mat.getAvailable();

                int color = available >= required ? 0x88FF88 : 0xFF8888;
                graphics.drawString(font, "  " + itemName + ": " + available + "/" + required,
                        dialogX + 10, y, color);
                y += 10;
            }
        } else {
            // Creative mode message
            graphics.drawString(font, "§aCreative Mode - No materials required",
                    dialogX + 10, y, 0x88FF88);
            y += 15;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void performGroupUpgrade() {
        java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        for (ComponentInfo comp : group.getComponents()) {
            positions.add(comp.getPosition());
        }

        com.gtceuterminal.common.network.TerminalNetwork.CHANNEL.sendToServer(
                new com.gtceuterminal.common.network.CPacketComponentUpgrade(positions, targetTier)
        );

        minecraft.setScreen(parent);

        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String getDisplayName(com.gtceuterminal.common.multiblock.ComponentType type, int tier) {
        if (type == com.gtceuterminal.common.multiblock.ComponentType.COIL) {
            // Coils: just material name
            return getCoilName(tier);
        } else if (type == com.gtceuterminal.common.multiblock.ComponentType.MAINTENANCE) {
            // Maintenance: no tier
            return type.getDisplayName();
        } else {
            String tierName = com.gregtechceu.gtceu.api.GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
            return type.getDisplayName() + " (" + tierName + ")";
        }
    }

    private String getCoilName(int tier) {
        return switch (tier) {
            case 0 -> "Cupronickel";
            case 1 -> "Kanthal";
            case 2 -> "Nichrome";
            case 3 -> "RTM Alloy";
            case 4 -> "HSS-G";
            case 5 -> "Naquadah";
            case 6 -> "Trinium";
            case 7 -> "Tritanium";
            default -> "Unknown Coil";
        };
    }
}