package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.client.gui.dialog.TierSelectionDialog;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.multiblock.MultiblockScanner;
import com.gtceuterminal.common.multiblock.MultiblockStatus;
import com.gtceuterminal.common.multiblock.MultiblockStatusScanner;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MultiStructureManagerScreen extends Screen {
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int SCAN_RADIUS = 32;

    private static final int LIST_PAD_X = 10;
    private static final int LIST_TOP_PAD = 28;
    private static final int LIST_BOTTOM_PAD = 10;

    private static final int ROW_H = 20;
    private static final int STATUS_SIZE = 8;

    private static final int EDIT_BOX_W = 18;
    private static final int EDIT_BOX_H = 18;
    private static final int EDIT_BOX_GAP_TO_STATUS = 4;

    private static final int REFRESH_W = 28;
    private static final int REFRESH_H = 22;

    private static final String UPGRADE_TEXT = "[Upgrade...]";

    // show multiblocks per page (needs a HEAVILY polish)
    private static final int VISIBLE_MULTIBLOCK_ROWS = 8;

    private final Player player;
    private final ItemStack terminalItem;

    private List<MultiblockInfo> multiblocks = new ArrayList<>();
    private final Map<Integer, Boolean> expandedMultiblocks = new HashMap<>();

    private final Map<String, String> customNames = new HashMap<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private Button refreshButton;
    private int hoveredMultiblockIndex = -1;

    private int selectedIndex = 0;

    private int leftPos;
    private int topPos;

    private boolean needsRefresh = false;

    public static void open(Player player, ItemStack terminalItem) {
        Minecraft.getInstance().setScreen(new MultiStructureManagerScreen(player, terminalItem));
    }

    public MultiStructureManagerScreen(Player player, ItemStack terminalItem) {
        super(Component.literal("Multi-Structure Manager"));
        this.player = player;
        this.terminalItem = terminalItem;
    }

    private ItemStack getCurrentTerminalStack() {
        if (player != null) {
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty() && main.getItem() == terminalItem.getItem()) return main;

            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty() && off.getItem() == terminalItem.getItem()) return off;
        }
        return terminalItem;
    }

    private void loadCustomNames() {
        customNames.clear();

        CompoundTag tag = getCurrentTerminalStack().getTag();
        if (tag != null && tag.contains("CustomMultiblockNames")) {
            CompoundTag namesTag = tag.getCompound("CustomMultiblockNames");
            for (String key : namesTag.getAllKeys()) {
                customNames.put(key, namesTag.getString(key));
            }
        }
    }

    private void saveCustomNames() {
        ItemStack stack = getCurrentTerminalStack();
        CompoundTag tag = stack.getOrCreateTag();

        CompoundTag namesTag = new CompoundTag();
        for (Map.Entry<String, String> entry : customNames.entrySet()) {
            namesTag.putString(entry.getKey(), entry.getValue());
        }

        tag.put("CustomMultiblockNames", namesTag);
        stack.setTag(tag);
    }

    private String getMultiblockKey(MultiblockInfo mb) {
        BlockPos pos = mb.getControllerPos();
        String dim = this.player.level().dimension().location().toString();
        return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String getDisplayName(MultiblockInfo mb) {
        String key = getMultiblockKey(mb);
        String name = customNames.get(key);
        if (name != null) return name;

        BlockPos pos = mb.getControllerPos();
        String oldKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return customNames.getOrDefault(oldKey, mb.getName());
    }

    private static String sanitizeName(String in) {
        if (in == null) return "";
        String s = in.trim();
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        scanMultiblocks();
        loadCustomNames();

        if (multiblocks.isEmpty()) selectedIndex = 0;
        else selectedIndex = Math.max(0, Math.min(selectedIndex, multiblocks.size() - 1));

        this.refreshButton = Button.builder(Component.literal("↻"), btn -> {
            scanMultiblocks();
            loadCustomNames();
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
        }).bounds(this.leftPos + GUI_WIDTH - (REFRESH_W + 5), this.topPos + 5, REFRESH_W, REFRESH_H).build();
        addRenderableWidget(this.refreshButton);

        calculateMaxScroll();
        clampScrollToSelection();
    }

    @Override
    public void tick() {
        super.tick();
        if (needsRefresh) {
            needsRefresh = false;
            scanMultiblocks();
            loadCustomNames();

            if (multiblocks.isEmpty()) selectedIndex = 0;
            else selectedIndex = Math.max(0, Math.min(selectedIndex, multiblocks.size() - 1));
            clampScrollToSelection();
        }
    }

    private void scanMultiblocks() {
        this.multiblocks = MultiblockScanner.scanNearbyMultiblocks(this.player, this.player.level(), SCAN_RADIUS);
        updateMultiblockStatuses();
        calculateMaxScroll();

        this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll);
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, Math.max(0, this.multiblocks.size() - 1)));
    }

    private void updateMultiblockStatuses() {
        for (MultiblockInfo mb : this.multiblocks) {
            MultiblockStatus status = MultiblockStatusScanner.getStatus(mb.getController());
            mb.setStatus(status);
        }
    }

    private void openTierSelectionDialog(ComponentGroup group, MultiblockInfo multiblock) {
        this.minecraft.setScreen(new TierSelectionDialog(this, group, multiblock, this.player, () -> {
            this.needsRefresh = true;
            this.scanMultiblocks();
        }));
    }

    private void calculateMaxScroll() {
        int totalLines = this.multiblocks.size();
        this.maxScroll = Math.max(0, totalLines - VISIBLE_MULTIBLOCK_ROWS);
    }

    private void clampScrollToSelection() {
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + VISIBLE_MULTIBLOCK_ROWS) scrollOffset = selectedIndex - (VISIBLE_MULTIBLOCK_ROWS - 1);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private String liveBlockName(BlockPos pos, String fallback) {
        try {
            if (this.minecraft == null || this.minecraft.level == null) return fallback;
            BlockState state = this.minecraft.level.getBlockState(pos);
            if (state == null || state.isAir()) return fallback;
            String s = state.getBlock().getName().getString();
            return (s == null || s.isEmpty()) ? fallback : s;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private ItemStack liveIconStack(BlockPos pos) {
        try {
            if (this.minecraft == null || this.minecraft.level == null) return ItemStack.EMPTY;
            BlockState state = this.minecraft.level.getBlockState(pos);
            if (state == null || state.isAir()) return ItemStack.EMPTY;
            Block block = state.getBlock();
            return new ItemStack(block);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String stripTierFromName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // Remove trailing "(TIER)" case-insensitive
        for (String vn : GTValues.VN) {
            String up = vn.toUpperCase(Locale.ROOT);
            String lo = vn.toLowerCase(Locale.ROOT);

            if (s.toLowerCase(Locale.ROOT).endsWith((" (" + lo + ")"))) {
                s = s.substring(0, s.length() - (lo.length() + 3)).trim();
                break;
            }
            if (s.endsWith(" (" + up + ")")) {
                s = s.substring(0, s.length() - (up.length() + 3)).trim();
                break;
            }
        }

        // Remove trailing " TIER" (like "Muffler Hatch HV")
        for (String vn : GTValues.VN) {
            String up = vn.toUpperCase(Locale.ROOT);
            if (s.endsWith(" " + up)) {
                s = s.substring(0, s.length() - (up.length() + 1)).trim();
                break;
            }
        }

        // Remove leading "TIER "
        String lower = s.toLowerCase(Locale.ROOT);
        for (String vn : GTValues.VN) {
            String pre = vn.toLowerCase(Locale.ROOT) + " ";
            if (lower.startsWith(pre)) {
                s = s.substring(pre.length()).trim();
                break;
            }
        }

        return s;
    }

    private static String tierNameUpper(int tier) {
        if (tier >= 0 && tier < GTValues.VN.length) return GTValues.VN[tier].toUpperCase(Locale.ROOT);
        return "???";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(this.leftPos, this.topPos, this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0xC0101010);

        graphics.fill(this.leftPos, this.topPos, this.leftPos + GUI_WIDTH, this.topPos + 1, 0xFFC0C0C0);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + GUI_HEIGHT, 0xFFC0C0C0);
        graphics.fill(this.leftPos + GUI_WIDTH - 1, this.topPos, this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0xFF404040);
        graphics.fill(this.leftPos, this.topPos + GUI_HEIGHT - 1, this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0xFF404040);

        graphics.drawString(this.font, "Nearby Multiblocks (" + this.multiblocks.size() + " blocks)",
                this.leftPos + 10, this.topPos + 10, 0xFFFFFFFF);

        renderMultiblockList(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMultiblockList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX1 = this.leftPos + LIST_PAD_X;
        int listY1 = this.topPos + LIST_TOP_PAD;
        int listX2 = this.leftPos + GUI_WIDTH - LIST_PAD_X;
        int listY2 = this.topPos + GUI_HEIGHT - LIST_BOTTOM_PAD;

        graphics.enableScissor(listX1, listY1, listX2, listY2);

        int y = this.topPos + 30;
        int visibleCount = Math.min(VISIBLE_MULTIBLOCK_ROWS, this.multiblocks.size() - this.scrollOffset);

        this.hoveredMultiblockIndex = -1;

        for (int i = this.scrollOffset; i < this.scrollOffset + visibleCount && i < this.multiblocks.size(); i++) {
            MultiblockInfo mb = this.multiblocks.get(i);
            boolean isExpanded = this.expandedMultiblocks.getOrDefault(i, false);

            boolean rowHovered = (mouseX >= this.leftPos + 30 && mouseX <= this.leftPos + GUI_WIDTH - 40
                    && mouseY >= y && mouseY <= y + ROW_H);

            if (rowHovered) this.hoveredMultiblockIndex = i;

            if (rowHovered || i == selectedIndex) {
                graphics.fill(this.leftPos + 10, y, this.leftPos + GUI_WIDTH - 10, y + ROW_H,
                        (i == selectedIndex) ? 0x50FFFFFF : 0x40FFFFFF);
            }

            String arrow = isExpanded ? "▼" : "▶";
            graphics.drawString(this.font, arrow, this.leftPos + 15, y + 6, 0xFFFFFFFF);

            String name = getDisplayName(mb);
            graphics.drawString(this.font, name, this.leftPos + 30, y + 5, 0xFFFFFFFF);

            String distance = mb.getDistanceString();
            graphics.drawString(this.font, distance, this.leftPos + 200, y + 5, 0xFFAABBCC);

            int statusColor = mb.getStatus().getColor();
            int statusX1 = this.leftPos + GUI_WIDTH - 50;
            int statusY1 = y + (ROW_H - STATUS_SIZE) / 2;
            graphics.fill(statusX1, statusY1, statusX1 + STATUS_SIZE, statusY1 + STATUS_SIZE, statusColor | 0xFF000000);

            if (rowHovered || i == selectedIndex) {
                int editX = statusX1 - EDIT_BOX_GAP_TO_STATUS - EDIT_BOX_W;
                int editY = y + (ROW_H - EDIT_BOX_H) / 2;

                graphics.fill(editX, editY, editX + EDIT_BOX_W, editY + EDIT_BOX_H, 0x40FFFF00);
                graphics.fill(editX, editY, editX + EDIT_BOX_W, editY + 1, 0xFFFFFF00);
                graphics.fill(editX, editY, editX + 1, editY + EDIT_BOX_H, 0xFFFFFF00);
                graphics.fill(editX + EDIT_BOX_W - 1, editY, editX + EDIT_BOX_W, editY + EDIT_BOX_H, 0xFFFFFF00);
                graphics.fill(editX, editY + EDIT_BOX_H - 1, editX + EDIT_BOX_W, editY + EDIT_BOX_H, 0xFFFFFF00);

                graphics.drawString(this.font, "✏", editX + 5, editY + 4, 0xFFFFAA00);
            }

            y += 22;

            if (isExpanded) {
                List<ComponentGroup> groups = mb.getGroupedComponents();

                if (groups.isEmpty()) {
                    graphics.drawString(this.font, "  No upgradeable components", this.leftPos + 35, y, 0xFF888888);
                    y += 12;
                } else {
                    for (ComponentGroup group : groups) {
                        ComponentInfo rep = group.getRepresentative();
                        BlockPos repPos = (rep != null) ? rep.getPosition() : null;

                        ItemStack icon = (repPos != null) ? liveIconStack(repPos) : ItemStack.EMPTY;
                        if (!icon.isEmpty()) graphics.renderItem(icon, this.leftPos + 37, y - 1);

                        String rawName = (rep != null)
                                ? liveBlockName(repPos, rep.getDisplayName())
                                : group.getType().getDisplayName();

                        String baseName = stripTierFromName(rawName);

                        ComponentType t = group.getType();
                        int tier = group.getTier();
                        int count = group.getCount();

                        String displayText = baseName;


                        if (count > 1) displayText += " §7x" + count;

                        graphics.drawString(this.font, displayText, this.leftPos + 55, y, 0xFFCCCCCC);

                        boolean looksParallel = baseName != null && baseName.toLowerCase(Locale.ROOT).contains("parallel");
                        boolean canUpgrade = t.isUpgradeable() || looksParallel;

                        if (canUpgrade) {
                            int btnX = this.leftPos + GUI_WIDTH - 90;
                            graphics.drawString(this.font, UPGRADE_TEXT, btnX, y, 0xFFAABBCC);
                        }

                        y += 12;
                    }
                }

                y += 5;
            }
        }

        graphics.disableScissor();

        if (this.maxScroll > 0) {
            String scrollText = "(" + (this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_MULTIBLOCK_ROWS, this.multiblocks.size())
                    + " of " + this.multiblocks.size() + ")";
            graphics.drawString(this.font, scrollText, this.leftPos + GUI_WIDTH - 110, this.topPos + 10, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = this.topPos + 30;
            int visibleCount = Math.min(VISIBLE_MULTIBLOCK_ROWS, this.multiblocks.size() - this.scrollOffset);

            int listX1 = this.leftPos + LIST_PAD_X;
            int listY1 = this.topPos + LIST_TOP_PAD;
            int listX2 = this.leftPos + GUI_WIDTH - LIST_PAD_X;
            int listY2 = this.topPos + GUI_HEIGHT - LIST_BOTTOM_PAD;

            if (mouseX < listX1 || mouseX > listX2 || mouseY < listY1 || mouseY > listY2) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            for (int i = this.scrollOffset; i < this.scrollOffset + visibleCount && i < this.multiblocks.size(); i++) {
                MultiblockInfo mb = this.multiblocks.get(i);
                boolean isExpanded = this.expandedMultiblocks.getOrDefault(i, false);

                boolean rowHit = (mouseX >= this.leftPos + 10 && mouseX <= this.leftPos + GUI_WIDTH - 10
                        && mouseY >= y && mouseY <= y + ROW_H);

                if (rowHit) {
                    selectedIndex = i;
                    clampScrollToSelection();

                    int statusX1 = this.leftPos + GUI_WIDTH - 50;
                    int editX = statusX1 - EDIT_BOX_GAP_TO_STATUS - EDIT_BOX_W;
                    int editY = y + (ROW_H - EDIT_BOX_H) / 2;

                    if (mouseX >= editX && mouseX <= (editX + EDIT_BOX_W)
                            && mouseY >= editY && mouseY <= (editY + EDIT_BOX_H)) {
                        openNameEditDialog(i);
                        return true;
                    }

                    this.expandedMultiblocks.put(i, !isExpanded);
                    this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                    return true;
                }

                y += 22;

                if (isExpanded) {
                    List<ComponentGroup> groups = mb.getGroupedComponents();

                    for (ComponentGroup group : groups) {
                        ComponentInfo rep = group.getRepresentative();
                        BlockPos repPos = (rep != null) ? rep.getPosition() : null;

                        String rawName = (rep != null)
                                ? liveBlockName(repPos, rep.getDisplayName())
                                : group.getType().getDisplayName();

                        String baseName = stripTierFromName(rawName);

                        ComponentType t = group.getType();
                        boolean looksParallel = baseName != null && baseName.toLowerCase(Locale.ROOT).contains("parallel");
                        boolean canUpgrade = t.isUpgradeable() || looksParallel;

                        if (canUpgrade) {
                            int btnX = this.leftPos + GUI_WIDTH - 90;
                            int w = this.font.width(UPGRADE_TEXT);

                            if (mouseX >= btnX && mouseX <= (btnX + w)
                                    && mouseY >= y && mouseY <= (y + 10)) {
                                openTierSelectionDialog(group, mb);
                                return true;
                            }
                        }

                        y += 12;
                    }

                    y += 5;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll > 0) {
            if (delta > 0) this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            else if (delta < 0) this.scrollOffset = Math.min(this.maxScroll, this.scrollOffset + 1);

            clampScrollToSelection();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }

        if (multiblocks.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == 265) { // Up
            selectedIndex = Math.max(0, selectedIndex - 1);
            clampScrollToSelection();
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.2F);
            return true;
        }

        if (keyCode == 264) { // Down
            selectedIndex = Math.min(multiblocks.size() - 1, selectedIndex + 1);
            clampScrollToSelection();
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.2F);
            return true;
        }

        if (keyCode == 266) { // PageUp
            selectedIndex = Math.max(0, selectedIndex - VISIBLE_MULTIBLOCK_ROWS);
            clampScrollToSelection();
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.1F);
            return true;
        }

        if (keyCode == 267) { // PageDown
            selectedIndex = Math.min(multiblocks.size() - 1, selectedIndex + VISIBLE_MULTIBLOCK_ROWS);
            clampScrollToSelection();
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.1F);
            return true;
        }

        if (keyCode == 257 || keyCode == 335) { // Enter
            boolean isExpanded = expandedMultiblocks.getOrDefault(selectedIndex, false);
            expandedMultiblocks.put(selectedIndex, !isExpanded);
            this.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openNameEditDialog(int multiblockIndex) {
        if (multiblockIndex < 0 || multiblockIndex >= this.multiblocks.size()) return;

        MultiblockInfo mb = this.multiblocks.get(multiblockIndex);
        String currentName = getDisplayName(mb);
        String key = getMultiblockKey(mb);

        this.minecraft.setScreen(new Screen(Component.literal("Edit Multiblock Name")) {
            private EditBox nameInput;

            @Override
            protected void init() {
                super.init();

                int dialogWidth = 220;
                int dialogHeight = 120;
                int leftPos = (this.width - dialogWidth) / 2;
                int topPos = (this.height - dialogHeight) / 2;

                nameInput = new EditBox(this.font, leftPos + 10, topPos + 35, dialogWidth - 20, 20,
                        Component.literal("Multiblock Name"));
                nameInput.setMaxLength(32);
                nameInput.setValue(currentName);
                nameInput.setFocused(true);
                addRenderableWidget(nameInput);

                addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
                    String newName = sanitizeName(nameInput.getValue());
                    if (!newName.isEmpty()) {
                        customNames.put(key, newName);
                        saveCustomNames();
                    }
                    needsRefresh = true;
                    minecraft.setScreen(MultiStructureManagerScreen.this);
                }).bounds(leftPos + 10, topPos + 65, (dialogWidth - 30) / 2, 20).build());

                addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
                    needsRefresh = true;
                    minecraft.setScreen(MultiStructureManagerScreen.this);
                }).bounds(leftPos + 10 + (dialogWidth - 30) / 2 + 10, topPos + 65, (dialogWidth - 30) / 2, 20).build());

                addRenderableWidget(Button.builder(Component.literal("Reset to Default"), btn -> {
                    customNames.remove(key);
                    BlockPos pos = mb.getControllerPos();
                    String oldKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                    customNames.remove(oldKey);

                    saveCustomNames();
                    needsRefresh = true;
                    minecraft.setScreen(MultiStructureManagerScreen.this);
                }).bounds(leftPos + 10, topPos + 90, dialogWidth - 20, 20).build());
            }

            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                renderBackground(graphics);

                int dialogWidth = 220;
                int dialogHeight = 120;
                int leftPos = (this.width - dialogWidth) / 2;
                int topPos = (this.height - dialogHeight) / 2;

                graphics.fill(leftPos, topPos, leftPos + dialogWidth, topPos + dialogHeight, 0xC0101010);

                graphics.fill(leftPos, topPos, leftPos + dialogWidth, topPos + 1, 0xFFC0C0C0);
                graphics.fill(leftPos, topPos, leftPos + 1, topPos + dialogHeight, 0xFFC0C0C0);
                graphics.fill(leftPos + dialogWidth - 1, topPos, leftPos + dialogWidth, topPos + dialogHeight, 0xFF404040);
                graphics.fill(leftPos, topPos + dialogHeight - 1, leftPos + dialogWidth, topPos + dialogHeight, 0xFF404040);

                graphics.drawString(this.font, "Edit Multiblock Name", leftPos + 10, topPos + 10, 0xFFFFFFFF);

                super.render(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == 257) { // ENTER
                    String newName = sanitizeName(nameInput.getValue());
                    if (!newName.isEmpty()) {
                        customNames.put(key, newName);
                        saveCustomNames();
                    }
                    needsRefresh = true;
                    minecraft.setScreen(MultiStructureManagerScreen.this);
                    return true;
                }
                if (keyCode == 256) { // ESC
                    needsRefresh = true;
                    minecraft.setScreen(MultiStructureManagerScreen.this);
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        });
    }
}