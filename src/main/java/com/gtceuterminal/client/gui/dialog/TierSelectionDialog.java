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
        if (type == ComponentType.PARALLEL_HATCH) {
            buildParallelOptions(representative, currentTier);
            return;
        }

        List<Integer> availableTiers = ComponentUpgradeHelper.getAvailableTiers(representative.getType());
        for (int targetTier : availableTiers) {

            // Skip current tier - no point in "upgrading" to the same tier
            if (targetTier == currentTier) continue;

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()) continue;
            if ("air".equalsIgnoreCase(upgradeName)) continue;

            String baseName = cleanComponentName(upgradeName);

            Map<Item, Integer> perComponentCost = MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> e : perComponentCost.entrySet()) totalCost.put(e.getKey(), e.getValue() * count);

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough = player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            options.add(new TierOption(targetTier, buildTierLabel(baseName, currentTier, targetTier, hasEnough), materials, hasEnough));
        }
    }

    private void buildParallelOptions(ComponentInfo representative, int currentTier) {
        int[] validTiers = new int[]{GTValues.IV, GTValues.LuV, GTValues.ZPM, GTValues.UV};

        for (int targetTier : validTiers) {
            if (targetTier == currentTier) continue;

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()) continue;

            String baseName = "Parallel Hatch";

            Map<Item, Integer> perComponentCost = MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> e : perComponentCost.entrySet()) totalCost.put(e.getKey(), e.getValue() * count);

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough = player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            options.add(new TierOption(targetTier, buildTierLabel(baseName, currentTier, targetTier, hasEnough), materials, hasEnough));
        }
    }

    private void buildMaintenanceOptions(ComponentInfo representative, int currentTier) {
        int[] tiers = new int[]{GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV};
        String[] names = new String[]{
                "Maintenance Hatch",
                "Configurable Maintenance Hatch",
                "Cleaning Maintenance Hatch",
                "Auto Maintenance Hatch"
        };

        for (int i = 0; i < tiers.length; i++) {
            int targetTier = tiers[i];
            if (targetTier == currentTier) continue;

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty() || "air".equalsIgnoreCase(upgradeName)) continue;

            String baseName = names[i];

            Map<Item, Integer> perComponentCost = MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> e : perComponentCost.entrySet()) totalCost.put(e.getKey(), e.getValue() * count);

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough = player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            options.add(new TierOption(targetTier, buildMaintenanceLabel(baseName, currentTier, targetTier, hasEnough), materials, hasEnough));
        }
    }

    private void buildCoilOptions(ComponentInfo representative, int currentTier) {
        List<CoilConfig.CoilEntry> allCoils = CoilConfig.getAllCoils();

        GTCEUTerminalMod.LOGGER.info("Building coil options. Current tier: {}, Total coils: {}", currentTier, allCoils.size());

        for (int targetTier = 0; targetTier < allCoils.size(); targetTier++) {
            if (targetTier == currentTier) continue;

            String coilName = CoilConfig.getCoilDisplayName(targetTier);

            Map<Item, Integer> perComponentCost = MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> e : perComponentCost.entrySet()) totalCost.put(e.getKey(), e.getValue() * count);

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough = player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            options.add(new TierOption(targetTier, buildCoilLabel(coilName, currentTier, targetTier, hasEnough), materials, hasEnough));
        }
    }

    private void buildMufflerOptions(ComponentInfo representative, int currentTier) {
        int[] validTiers = new int[]{
                GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV,
                GTValues.IV, GTValues.LuV, GTValues.ZPM, GTValues.UV
        };

        for (int targetTier : validTiers) {
            if (targetTier == currentTier) continue;

            String upgradeName = ComponentUpgradeHelper.getUpgradeName(representative, targetTier);
            if (upgradeName == null || upgradeName.isEmpty()) continue;

            String baseName = cleanComponentName(upgradeName);

            Map<Item, Integer> perComponentCost = MaterialCalculator.calculateUpgradeCost(representative, targetTier);
            Map<Item, Integer> totalCost = new HashMap<>();
            int count = group.getCount();
            for (Map.Entry<Item, Integer> e : perComponentCost.entrySet()) totalCost.put(e.getKey(), e.getValue() * count);

            List<MaterialAvailability> materials =
                    MaterialCalculator.checkMaterialsAvailability(totalCost, player, player.level());
            boolean hasEnough = player.isCreative() || MaterialCalculator.hasEnoughMaterials(materials);

            options.add(new TierOption(targetTier, buildTierLabel(baseName, currentTier, targetTier, hasEnough), materials, hasEnough));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int right = dialogX + DIALOG_WIDTH;
        int bottom = dialogY + dialogHeight;
        graphics.fillGradient(dialogX, dialogY, right, bottom, 0xC0000000, 0xC0000000);

        Component title = Component.literal("Select Upgrade Tier");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, dialogX + (DIALOG_WIDTH - titleWidth) / 2, dialogY + 6, 0xFFFFFF, false);

        ComponentInfo representative = group.getRepresentative();
        if (representative != null) {
            ComponentType type = representative.getType();

            Component header;
            if (type == ComponentType.COIL) {
                String coilName = cleanComponentName(ComponentUpgradeHelper.getUpgradeName(representative, group.getTier()));
                header = Component.literal(coilName + " x" + group.getCount());
            } else {
                String name = cleanComponentName(representative.getDisplayName());
                header = Component.literal(name + " x" + group.getCount());
            }

            graphics.drawString(this.font, header, dialogX + 6, dialogY + 18, 0xFFFFFF, false);
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

            boolean hovered = mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom;
            graphics.fill(rowLeft, rowTop, rowRight, rowBottom, hovered ? 0x40FFFFFF : 0x20FFFFFF);

            int textColor = option.hasEnough || player.isCreative() ? 0xFFFFFF : 0xFF5555;
            graphics.drawString(this.font, option.label, rowLeft + 2, rowTop + 3, textColor, false);
        }

        Component hint = Component.literal("Click an option to upgrade, ESC to cancel");
        int hintWidth = this.font.width(hint);
        graphics.drawString(this.font, hint, dialogX + (DIALOG_WIDTH - hintWidth) / 2, bottom - 12, 0xAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowLeft = dialogX + 6;
            int rowRight = dialogX + DIALOG_WIDTH - 6;

            if (mouseX < rowLeft || mouseX >= rowRight || mouseY < listTop || mouseY >= listBottom) {
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
        if (options.size() <= visibleRows) return false;
        if (mouseX < dialogX || mouseX > dialogX + DIALOG_WIDTH || mouseY < listTop || mouseY > listBottom) return false;

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
                    if (onCloseCallback != null) onCloseCallback.run();
                }
        ));
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parent);
        if (onCloseCallback != null) onCloseCallback.run();
    }

    private static String cleanComponentName(String raw) {
        if (raw == null) return "";
        String result = raw.trim();

        // strip leading tier "HV "
        String low = result.toLowerCase(Locale.ROOT);
        for (String vn : GTValues.VN) {
            String pre = vn.toLowerCase(Locale.ROOT) + " ";
            if (low.startsWith(pre)) {
                result = result.substring(pre.length()).trim();
                low = result.toLowerCase(Locale.ROOT);
                break;
            }
        }

        for (String vn : GTValues.VN) {
            String up = vn.toUpperCase(Locale.ROOT);
            String lo = vn.toLowerCase(Locale.ROOT);

            // Pattern: " → TIER" (uppercase)
            String arrowTierUp = " → " + up;
            if (result.contains(arrowTierUp)) {
                result = result.replace(arrowTierUp, " →");
            }

            // Pattern: " → tier" (lowercase)
            String arrowTierLo = " → " + lo;
            if (result.toLowerCase(Locale.ROOT).contains(arrowTierLo)) {
                int idx = result.toLowerCase(Locale.ROOT).indexOf(arrowTierLo);
                if (idx >= 0) {
                    result = result.substring(0, idx) + " →" + result.substring(idx + arrowTierLo.length());
                }
            }

            String arrowTierNoSpace = "→ " + up;
            if (result.contains(arrowTierNoSpace)) {
                result = result.replace(arrowTierNoSpace, "→");
            }

            String tierArrowUp = " " + up + " →";
            if (result.contains(tierArrowUp)) {
                result = result.replace(tierArrowUp, " →");
            }

            String tierArrowLo = " " + lo + " →";
            if (result.toLowerCase(Locale.ROOT).contains(tierArrowLo)) {
                int idx = result.toLowerCase(Locale.ROOT).indexOf(tierArrowLo);
                if (idx >= 0) {
                    result = result.substring(0, idx) + " →" + result.substring(idx + tierArrowLo.length());
                }
            }
        }

        // strip trailing "(TIER)" and " TIER"
        for (String vn : GTValues.VN) {
            String up = vn.toUpperCase(Locale.ROOT);
            String loTier = vn.toLowerCase(Locale.ROOT);

            String sufParenLo = " (" + loTier + ")";
            String sufParenUp = " (" + up + ")";
            if (result.toLowerCase(Locale.ROOT).endsWith(sufParenLo)) {
                result = result.substring(0, result.length() - sufParenLo.length()).trim();
            }
            if (result.endsWith(sufParenUp)) {
                result = result.substring(0, result.length() - sufParenUp.length()).trim();
            }

            String sufSpace = " " + up;
            if (result.endsWith(sufSpace)) {
                result = result.substring(0, result.length() - sufSpace.length()).trim();
            }
        }

        return result.trim();
    }

    private static String tierName(int tier) {
        if (tier >= 0 && tier < GTValues.VN.length) return GTValues.VN[tier].toUpperCase(Locale.ROOT);
        return "???";
    }

    private static int getTierColorHex(int tier) {
        switch (tier) {
            case 0:  return 0x545454;  // ULV
            case 1:  return 0xA8A8A8;  // LV
            case 2:  return 0x54FCFC;  // MV
            case 3:  return 0xFCA800;  // HV
            case 4:  return 0xA901A8;  // EV
            case 5:  return 0x5454FC;  // IV
            case 6:  return 0xFD55FC;  // LuV
            case 7:  return 0xFD5554;  // ZPM
            case 8:  return 0x01A9A8;  // UV
            case 9:  return 0XA90001;  // UHV
            case 10: return 0x54FC54;  // UEV
            case 11: return 0x00A800;  // UIV
            case 12: return 0xFCFC54;  // UXV
            case 13: return 0x5454FC;  // OpV
            case 14: return 0xFFFFFF;  // MAX
            default: return 0xFFFFFF;
        }
    }

    private static int getCoilColorHex(int coilTier) {
        switch (coilTier) {
            case 0:  return 0xfa8907;  // Cupronickel
            case 1:  return 0x3c8cb0;  // Kanthal
            case 2:  return 0xe08bd4;  // Nichrome
            case 3:  return 0x1b0d7a;  // RTM Alloy
            case 4:  return 0x536294;  // HSS-G
            case 5:  return 0x292727;  // Naquadah
            case 6:  return 0x5C2E6F;  // Trinium
            case 7:  return 0x520000;  // Tritanium
            default: return 0xFFFFFF;
        }
    }

    private MutableComponent buildTierLabel(String baseName, int currentTier, int targetTier, boolean hasEnough) {
        int colorHex = getTierColorHex(targetTier);
        String arrow = (targetTier > currentTier) ? "↑ " : "↓ ";

        MutableComponent result = Component.literal("")
                .append(Component.literal(arrow).withStyle(style -> style.withColor(colorHex)))
                .append(Component.literal(baseName + " (" + tierName(targetTier) + ")").withStyle(style -> style.withColor(colorHex)));

        if (!hasEnough && !player.isCreative()) {
            result = result.append(Component.literal(" [Missing]").withStyle(ChatFormatting.RED));
        }
        return result;
    }

    private MutableComponent buildMaintenanceLabel(String baseName, int currentTier, int targetTier, boolean hasEnough) {
        int colorHex = getTierColorHex(targetTier);

        MutableComponent result = Component.literal("")
                .append(Component.literal("→ ").withStyle(style -> style.withColor(colorHex)))
                .append(Component.literal(baseName).withStyle(style -> style.withColor(colorHex)));

        if (!hasEnough && !player.isCreative()) {
            result = result.append(Component.literal(" [Missing]").withStyle(ChatFormatting.RED));
        }
        return result;
    }

    private MutableComponent buildCoilLabel(String coilName, int currentTier, int targetTier, boolean hasEnough) {
        int colorHex = getCoilColorHex(targetTier);

        MutableComponent result = Component.literal("")
                .append(Component.literal("→ ").withStyle(style -> style.withColor(colorHex)))
                .append(Component.literal(coilName).withStyle(style -> style.withColor(colorHex)));

        if (!hasEnough && !player.isCreative()) {
            result = result.append(Component.literal(" [Missing]").withStyle(ChatFormatting.RED));
        }
        return result;
    }

    private static class TierOption {
        final int tier;
        final MutableComponent label;
        final List<MaterialAvailability> materials;
        final boolean hasEnough;

        TierOption(int tier, MutableComponent label, List<MaterialAvailability> materials, boolean hasEnough) {
            this.tier = tier;
            this.label = label;
            this.materials = materials;
            this.hasEnough = hasEnough;
        }
    }
}
// I hate this file so much