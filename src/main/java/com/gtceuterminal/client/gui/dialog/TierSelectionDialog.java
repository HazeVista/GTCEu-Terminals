package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.multiblock.MultiStructureManagerScreen;
import com.gtceuterminal.common.config.CoilConfig;
import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.multiblock.MultiblockInfo;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.*;

public class TierSelectionDialog extends Screen {

    private static final int DIALOG_WIDTH = 260;
    private static final int MAX_VISIBLE_ROWS = 10;
    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private final ComponentGroup group;
    private final MultiblockInfo multiblockInfo;
    private final Player player;
    private final Runnable onCloseCallback;

    private final List<TierOption> options = new ArrayList<>();

    private int dialogX;
    private int dialogY;
    private int dialogHeight;
    private int listTop;
    private int listBottom;
    private int scrollOffset;
    private int visibleRows;

    public TierSelectionDialog(
            MultiStructureManagerScreen parent,
            ComponentGroup group,
            MultiblockInfo multiblockInfo,
            Player player,
            Runnable onCloseCallback
    ) {
        super(Component.literal("Select Upgrade Tier"));
        this.parent = parent;
        this.group = group;
        this.multiblockInfo = multiblockInfo;
        this.player = player;
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    protected void init() {
        options.clear();
        buildOptions();

        visibleRows = Math.min(options.size(), MAX_VISIBLE_ROWS);
        dialogHeight = 40 + (visibleRows * ROW_HEIGHT) + 24;

        dialogX = (this.width - DIALOG_WIDTH) / 2;
        dialogY = (this.height - dialogHeight) / 2;

        listTop = dialogY + 30;
        listBottom = listTop + visibleRows * ROW_HEIGHT;

        scrollOffset = 0;
    }

    private void buildOptions() {
        ComponentInfo representative = group.getRepresentative();
        if (representative == null) return;

        ComponentType type = representative.getType();
        int currentTier = group.getTier();

        if (type == ComponentType.MAINTENANCE) {
            buildMaintenanceOptions(representative, currentTier);
            return;
        }
        
        if (type == ComponentType.COIL) {
            buildCoilOptions(representative, currentTier);
            return;
        }
        
        if (type == ComponentType.MUFFLER) {
            buildMufflerOptions(representative, currentTier);
            return;
        }

        for (int targetTier = GTValues.ULV; targetTier <= GTValues.MAX; targetTier++) {

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()) continue;
            if ("air".equalsIgnoreCase(upgradeName)) continue;

            String baseName = cleanComponentName(upgradeName);

            Map<Item, Integer> perComponentCost =
                    MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> entry : perComponentCost.entrySet()) {
                totalCost.put(entry.getKey(), entry.getValue() * count);
            }

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough =
                    player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            MutableComponent label =
                    buildTierLabel(baseName, currentTier, targetTier, hasEnough);

            options.add(new TierOption(targetTier, label, materials, hasEnough));
        }
    }

    private void buildMaintenanceOptions(ComponentInfo representative, int currentTier) {
        int[] tiers = new int[] {
                GTValues.LV,
                GTValues.MV,
                GTValues.HV,
                GTValues.EV
        };
        String[] names = new String[] {
                "Maintenance Hatch",
                "Configurable Maintenance Hatch",
                "Cleaning Maintenance Hatch",
                "Auto Maintenance Hatch"
        };

        for (int i = 0; i < tiers.length; i++) {
            int targetTier = tiers[i];

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()
                    || "air".equalsIgnoreCase(upgradeName)) {
                continue;
            }

            String baseName = names[i];

            Map<Item, Integer> perComponentCost =
                    MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> entry : perComponentCost.entrySet()) {
                totalCost.put(entry.getKey(), entry.getValue() * count);
            }

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough =
                    player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            MutableComponent label =
                    buildMaintenanceLabel(baseName, currentTier, targetTier, hasEnough);

            options.add(new TierOption(targetTier, label, materials, hasEnough));
        }
    }

    private void buildCoilOptions(ComponentInfo representative, int currentTier) {
        List<CoilConfig.CoilEntry> allCoils = CoilConfig.getAllCoils();

        // Log para debug
        GTCEUTerminalMod.LOGGER.info("Building coil options. Current tier: {}, Total coils: {}",
                currentTier, allCoils.size());

        // Build options for ALL coil tiers except current
        for (int targetTier = 0; targetTier < allCoils.size(); targetTier++) {
            // Skip current tier
            if (targetTier == currentTier) {
                GTCEUTerminalMod.LOGGER.info("Skipping current tier: {}", targetTier);
                continue;
            }

            CoilConfig.CoilEntry coilEntry = allCoils.get(targetTier);
            String coilName = CoilConfig.getCoilDisplayName(targetTier);

            GTCEUTerminalMod.LOGGER.info("Adding coil option: tier={}, name={}", targetTier, coilName);

            // Calcular coste para TODO el grupo
            Map<Item, Integer> perComponentCost =
                    MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> entry : perComponentCost.entrySet()) {
                totalCost.put(entry.getKey(), entry.getValue() * count);
            }

            // Check availability
            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough =
                    player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            MutableComponent label = buildCoilLabel(coilName, currentTier, targetTier, hasEnough);

            TierOption option = new TierOption(targetTier, label, materials, hasEnough);
            options.add(option);
        }

        GTCEUTerminalMod.LOGGER.info("Total coil options added: {}", options.size());
    }

    private void buildMufflerOptions(ComponentInfo representative, int currentTier) {
        int[] validTiers = new int[] {
            GTValues.LV,   // 1
            GTValues.MV,   // 2
            GTValues.HV,   // 3
            GTValues.EV,   // 4
            GTValues.IV,   // 5
            GTValues.LuV,  // 6
            GTValues.ZPM,  // 7
            GTValues.UV    // 8
        };
        
        for (int targetTier : validTiers) {
            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()) {
                continue;
            }

            String baseName = cleanComponentName(upgradeName);

            Map<Item, Integer> perComponentCost =
                    MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> entry : perComponentCost.entrySet()) {
                totalCost.put(entry.getKey(), entry.getValue() * count);
            }

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough =
                    player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            MutableComponent label =
                    buildTierLabel(baseName, currentTier, targetTier, hasEnough);

            options.add(new TierOption(targetTier, label, materials, hasEnough));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int right = dialogX + DIALOG_WIDTH;
        int bottom = dialogY + dialogHeight;
        fillGradient(graphics, dialogX, dialogY, right, bottom, 0xC0000000, 0xC0000000);

        Component title = Component.literal("Select Upgrade Tier");
        int titleWidth = this.font.width(title);
        graphics.drawString(
                this.font,
                title,
                dialogX + (DIALOG_WIDTH - titleWidth) / 2,
                dialogY + 6,
                0xFFFFFF,
                false
        );

        ComponentInfo representative = group.getRepresentative();
        if (representative != null) {
            ComponentType type = representative.getType();

            Component currentText;
            if (type == ComponentType.MAINTENANCE) {
                String name = cleanComponentName(representative.getDisplayName());
                currentText = Component.literal(name + " x" + group.getCount());
            } else if (type == ComponentType.COIL) {
                String coilName = ComponentUpgradeHelper.getUpgradeName(representative, group.getTier());
                currentText = Component.literal(coilName + " x" + group.getCount());
            } else {
                String name = cleanComponentName(representative.getDisplayName());
                String tierName = tierName(group.getTier());
                currentText = Component.literal(name + " (" + tierName + ") x" + group.getCount());
            }

            graphics.drawString(
                    this.font,
                    currentText,
                    dialogX + 6,
                    dialogY + 18,
                    0xFFFFFF,
                    false
            );
        }

        int rowLeft = dialogX + 6;
        int rowRight = dialogX + DIALOG_WIDTH - 6;

        visibleRows = Math.min(options.size(), MAX_VISIBLE_ROWS);
        listBottom = listTop + visibleRows * ROW_HEIGHT;

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= options.size()) break;

            TierOption option = options.get(index);
            int rowTop = listTop + i * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT;

            boolean hovered =
                    mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom;

            int bgColor = hovered ? 0x40FFFFFF : 0x20FFFFFF;
            graphics.fill(rowLeft, rowTop, rowRight, rowBottom, bgColor);

            int textColor =
                    option.hasEnough || player.isCreative() ? 0xFFFFFF : 0xFF5555;

            graphics.drawString(
                    this.font,
                    option.label,
                    rowLeft + 2,
                    rowTop + 3,
                    textColor,
                    false
            );
        }

        Component hint =
                Component.literal("Click an option to upgrade, ESC to cancel");
        int hintWidth = this.font.width(hint);
        graphics.drawString(
                this.font,
                hint,
                dialogX + (DIALOG_WIDTH - hintWidth) / 2,
                bottom - 12,
                0xAAAAAA,
                false
        );
    }

    private void fillGradient(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color1,
            int color2
    ) {
        graphics.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowLeft = dialogX + 6;
            int rowRight = dialogX + DIALOG_WIDTH - 6;

            if (mouseX < rowLeft || mouseX >= rowRight
                    || mouseY < listTop || mouseY >= listBottom) {
                this.onClose();
                return true;
            }

            int rowIndex = (int) ((mouseY - listTop) / ROW_HEIGHT);
            int optionIndex = scrollOffset + rowIndex;
            if (optionIndex >= 0 && optionIndex < options.size()) {
                TierOption chosen = options.get(optionIndex);
                openUpgradeDialog(chosen.tier);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (options.size() <= visibleRows) {
            return false;
        }
        if (mouseX < dialogX || mouseX > dialogX + DIALOG_WIDTH
                || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int maxOffset = Math.max(0, options.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxOffset);
        return true;
    }

    private void openUpgradeDialog(int targetTier) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new GroupUpgradeDialog(
                this,
                group,
                targetTier,
                multiblockInfo,
                player,
                () -> {
                    mc.setScreen(parent);
                    if (onCloseCallback != null) {
                        onCloseCallback.run();
                    }
                }
        ));
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parent);
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    private static String cleanComponentName(String raw) {
        if (raw == null) return "";
        String result = raw.trim();

        String lower = result.toLowerCase(Locale.ROOT);
        for (String vn : GTValues.VN) {
            String pre = vn.toLowerCase(Locale.ROOT) + " ";
            if (lower.startsWith(pre)) {
                result = result.substring(pre.length());
                lower = result.toLowerCase(Locale.ROOT);
                break;
            }
        }

        for (String vn : GTValues.VN) {
            String suf1 = " (" + vn + ")";
            String suf2 = " " + vn;
            String suf3 = " + " + vn;

            if (result.endsWith(suf1)) {
                result = result.substring(0, result.length() - suf1.length());
            }
            if (result.endsWith(suf3)) {
                result = result.substring(0, result.length() - suf3.length());
            }
            if (result.endsWith(suf2)) {
                result = result.substring(0, result.length() - suf2.length());
            }
        }

        return result.trim();
    }

    private static String tierName(int tier) {
        if (tier >= 0 && tier < GTValues.VN.length) {
            return GTValues.VN[tier].toUpperCase(Locale.ROOT);
        }
        return "???";
    }

    private static ChatFormatting tierColor(int tier) {
        switch (tier) {
            case GTValues.ULV:
                return ChatFormatting.GRAY;
            case GTValues.LV:
                return ChatFormatting.GREEN;
            case GTValues.MV:
                return ChatFormatting.AQUA;
            case GTValues.HV:
                return ChatFormatting.YELLOW;
            case GTValues.EV:
                return ChatFormatting.LIGHT_PURPLE;
            case GTValues.IV:
                return ChatFormatting.BLUE;
            case GTValues.LuV:
                return ChatFormatting.DARK_AQUA;
            case GTValues.ZPM:
                return ChatFormatting.RED;
            case GTValues.UV:
                return ChatFormatting.DARK_PURPLE;
            case GTValues.UHV:
                return ChatFormatting.DARK_RED;
            case GTValues.UEV:
                return ChatFormatting.GOLD;
            case GTValues.UIV:
                return ChatFormatting.DARK_GREEN;
            case GTValues.UXV:
                return ChatFormatting.DARK_BLUE;
            case GTValues.OpV:
                return ChatFormatting.DARK_GRAY;
            case GTValues.MAX:
                return ChatFormatting.WHITE;
            default:
                return ChatFormatting.WHITE;
        }
    }

    private MutableComponent buildTierLabel(
            String baseName,
            int currentTier,
            int targetTier,
            boolean hasEnough
    ) {
        boolean isCurrent = targetTier == currentTier;
        boolean isUpgrade = targetTier > currentTier;

        String tierName = tierName(targetTier);
        ChatFormatting color = tierColor(targetTier);

        String arrow = isCurrent ? "• " : (isUpgrade ? "↑ " : "↓ ");

        MutableComponent result = Component.literal("")
                .append(Component.literal(arrow).withStyle(color))
                .append(Component.literal(baseName + " (" + tierName + ")"));

        if (!hasEnough && !player.isCreative()) {
            result.append(
                    Component.literal(" (missing materials)")
                            .withStyle(ChatFormatting.RED)
            );
        }

        return result;
    }

    private MutableComponent buildMaintenanceLabel(
            String baseName,
            int currentTier,
            int targetTier,
            boolean hasEnough
    ) {
        boolean isCurrent = targetTier == currentTier;
        boolean isUpgrade = targetTier > currentTier;

        ChatFormatting color = tierColor(targetTier);
        String arrow = isCurrent ? "• " : (isUpgrade ? "↑ " : "↓ ");

        MutableComponent result = Component.literal("")
                .append(Component.literal(arrow).withStyle(color))
                .append(Component.literal(baseName)); // sin "(LV)" etc

        if (!hasEnough && !player.isCreative()) {
            result.append(
                    Component.literal(" (missing materials)")
                            .withStyle(ChatFormatting.RED)
            );
        }

        return result;
    }

    private MutableComponent buildCoilLabel(
            String coilName,
            int currentTier,
            int targetTier,
            boolean hasEnough
    ) {
        boolean isCurrent = targetTier == currentTier;
        boolean isUpgrade = targetTier > currentTier;

        // Color based on coil tier (0-7)
        ChatFormatting color = getCoilColor(targetTier);
        String arrow = isCurrent ? "• " : (isUpgrade ? "↑ " : "↓ ");

        MutableComponent result = Component.literal("")
                .append(Component.literal(arrow).withStyle(color))
                .append(Component.literal(coilName));

        if (!hasEnough && !player.isCreative()) {
            result.append(
                    Component.literal(" (missing materials)")
                            .withStyle(ChatFormatting.RED)
            );
        }

        return result;
    }

    private static ChatFormatting getCoilColor(int coilTier) {
        return switch (coilTier) {
            case 0 -> ChatFormatting.WHITE;         // Cupronickel
            case 1 -> ChatFormatting.YELLOW;        // Kanthal
            case 2 -> ChatFormatting.GOLD;          // Nichrome
            case 3 -> ChatFormatting.RED;           // RTM Alloy
            case 4 -> ChatFormatting.LIGHT_PURPLE;  // HSS-G
            case 5 -> ChatFormatting.DARK_PURPLE;   // Naquadah
            case 6 -> ChatFormatting.BLUE;          // Trinium
            case 7 -> ChatFormatting.DARK_BLUE;     // Tritanium
            default -> ChatFormatting.WHITE;
        };
    }

    private static class TierOption {
        final int tier;
        final Component label;
        final List<MaterialAvailability> materials;
        final boolean hasEnough;

        TierOption(int tier, Component label, List<MaterialAvailability> materials, boolean hasEnough) {
            this.tier = tier;
            this.label = label;
            this.materials = materials;
            this.hasEnough = hasEnough;
        }
    }
}
