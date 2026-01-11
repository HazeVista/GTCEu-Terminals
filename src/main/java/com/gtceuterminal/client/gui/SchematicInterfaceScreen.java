package com.gtceuterminal.client.gui;

import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.network.CPacketSchematicAction;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class SchematicInterfaceScreen extends Screen {
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private ItemStack terminalItem;
    private List<SchematicData> schematics;
    private final Consumer<SchematicAction> onAction;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Preview rotation
    private float previewRotationX = 30.0F;
    private float previewRotationY = -45.0F;

    // Preview zoom
    private float previewZoom = 1.0F;
    private static final float MIN_ZOOM = 0.3F;
    private static final float MAX_ZOOM = 3.0F;
    private static final float ZOOM_STEP = 0.1F;

    private boolean isDraggingPreview = false;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    private EditBox nameInput;

    private Button saveButton;
    private Button loadButton;
    private Button deleteButton;
    private Button pasteButton;

    public SchematicInterfaceScreen(ItemStack terminalItem, List<SchematicData> schematics, Consumer<SchematicAction> onAction) {
        super(Component.literal("Schematic Terminal"));
        this.terminalItem = terminalItem;
        this.schematics = schematics;
        this.onAction = onAction;

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int totalLines = this.schematics.size();
        int visibleLines = 8;
        this.maxScroll = Math.max(0, totalLines - visibleLines);
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = (this.width - 320) / 2;
        int topPos = (this.height - 240) / 2;

        // Name input field
        this.nameInput = new EditBox(this.font, leftPos + 10, topPos + 10, 120, 20, Component.literal("Schematic Name"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setValue("");
        this.nameInput.setHint(Component.literal("Enter name..."));
        addRenderableWidget(this.nameInput);

        // Save button
        this.saveButton = Button.builder(Component.literal("Save Clipboard"), btn -> {
                    CompoundTag tag = this.terminalItem.getTag();
                    if (tag == null || !tag.contains("Clipboard")) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("§cNo clipboard! Copy a multiblock first."), true);
                        return;
                    }

                    CompoundTag clipboardTag = tag.getCompound("Clipboard");
                    if (!clipboardTag.contains("Blocks") || clipboardTag.getList("Blocks", 10).isEmpty()) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("§cClipboard is empty!"), true);
                        return;
                    }

                    String name = this.nameInput.getValue().trim();
                    if (name.isEmpty()) name = "Unnamed Schematic";

                    String finalName = name;
                    boolean isDuplicate = this.schematics.stream().anyMatch(s -> s.getName().equals(finalName));
                    if (isDuplicate) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("§cSchematic name already exists!"), true);
                        return;
                    }

                    TerminalNetwork.CHANNEL.sendToServer(new CPacketSchematicAction(CPacketSchematicAction.ActionType.SAVE, finalName, -1));
                    reloadSchematics();
                    this.nameInput.setValue("");

                    new Timer().schedule(new TimerTask() {
                        public void run() {
                            Minecraft.getInstance().execute(() -> SchematicInterfaceScreen.this.reloadSchematics());
                        }
                    }, 50L);
                })
                .bounds(leftPos + 135, topPos + 10, 85, 20)
                .build();
        addRenderableWidget(this.saveButton);

        // Load button
        this.loadButton = Button.builder(Component.literal("Load"), btn -> {
                    if (this.selectedIndex >= 0 && this.selectedIndex < this.schematics.size()) {
                        TerminalNetwork.CHANNEL.sendToServer(new CPacketSchematicAction(CPacketSchematicAction.ActionType.LOAD, "", this.selectedIndex));
                    }
                })
                .bounds(leftPos + 225, topPos + 10, 40, 20)
                .build();
        addRenderableWidget(this.loadButton);

        // Delete button
        this.deleteButton = Button.builder(Component.literal("Delete"), btn -> {
                    if (this.selectedIndex >= 0 && this.selectedIndex < this.schematics.size()) {
                        TerminalNetwork.CHANNEL.sendToServer(new CPacketSchematicAction(CPacketSchematicAction.ActionType.DELETE, "", this.selectedIndex));
                        this.selectedIndex = -1;
                        reloadSchematics();

                        new Timer().schedule(new TimerTask() {
                            public void run() {
                                Minecraft.getInstance().execute(() -> SchematicInterfaceScreen.this.reloadSchematics());
                            }
                        }, 50L);
                    }
                })
                .bounds(leftPos + 270, topPos + 10, 40, 20)
                .build();
        addRenderableWidget(this.deleteButton);

        // Close button
        this.pasteButton = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(leftPos + 10, topPos + 210, 300, 20)
                .build();
        addRenderableWidget(this.pasteButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int leftPos = (this.width - 320) / 2;
        int topPos = (this.height - 240) / 2;

        // Main background (gris oscuro con transparencia)
        graphics.fill(leftPos, topPos, leftPos + 320, topPos + 240, 0xC0101010);

        // Bordes externos delgados (1 pixel) estilo Multi-Structure Manager
        graphics.fill(leftPos, topPos, leftPos + 320, topPos + 1, 0xFFC0C0C0);  // Superior (gris claro)
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + 240, 0xFFC0C0C0);  // Izquierdo (gris claro)
        graphics.fill(leftPos + 319, topPos, leftPos + 320, topPos + 240, 0xFF404040);  // Derecho (gris medio-oscuro)
        graphics.fill(leftPos, topPos + 239, leftPos + 320, topPos + 240, 0xFF404040);  // Inferior (gris medio-oscuro)

        // "Saved Schematics" label
        graphics.drawString(this.font, "Saved Schematics", leftPos + 10, topPos + 43, 0xFFCCCCCC);

        graphics.fill(leftPos + 10, topPos + 55, leftPos + 160, topPos + 200, 0xFF2A2A2A);

        graphics.fill(leftPos + 10, topPos + 55, leftPos + 160, topPos + 56, 0xFF505050);  // Superior
        graphics.fill(leftPos + 10, topPos + 55, leftPos + 11, topPos + 200, 0xFF505050);  // Izquierdo
        graphics.fill(leftPos + 159, topPos + 55, leftPos + 160, topPos + 200, 0xFF151515);  // Derecho
        graphics.fill(leftPos + 10, topPos + 199, leftPos + 160, topPos + 200, 0xFF151515);  // Inferior

        // Render schematic list
        graphics.enableScissor(leftPos + 11, topPos + 56, leftPos + 159, topPos + 199);
        renderSchematicList(graphics, leftPos, topPos, mouseX, mouseY);
        graphics.disableScissor();

        // Scrollbar
        if (this.maxScroll > 0) {
            int scrollbarHeight = Math.max(20, 1160 / this.schematics.size());
            int scrollbarY = topPos + 56 + (int)((143 - scrollbarHeight) * this.scrollOffset / this.maxScroll);
            graphics.fill(leftPos + 160, topPos + 55, leftPos + 165, topPos + 200, 0xFF1A1A1A);  // Fondo
            graphics.fill(leftPos + 161, scrollbarY, leftPos + 164, scrollbarY + scrollbarHeight, 0xFF808080);  // Thumb
        }

        graphics.fill(leftPos + 170, topPos + 55, leftPos + 310, topPos + 200, 0xFF2A2A2A);

        graphics.fill(leftPos + 170, topPos + 55, leftPos + 310, topPos + 56, 0xFF505050);  // Superior
        graphics.fill(leftPos + 170, topPos + 55, leftPos + 171, topPos + 200, 0xFF505050);  // Izquierdo
        graphics.fill(leftPos + 309, topPos + 55, leftPos + 310, topPos + 200, 0xFF151515);  // Derecho
        graphics.fill(leftPos + 170, topPos + 199, leftPos + 310, topPos + 200, 0xFF151515);  // Inferior

        graphics.drawString(this.font, "Preview (Drag to Rotate)", leftPos + 175, topPos + 62, 0xFFFFAA00);

        if (this.selectedIndex >= 0 && this.selectedIndex < this.schematics.size()) {
            SchematicData schematic = this.schematics.get(this.selectedIndex);

            graphics.enableScissor(leftPos + 171, topPos + 73, leftPos + 309, topPos + 173);
            renderPreview(graphics, leftPos + 170, topPos + 73, 139, 100, schematic);
            graphics.disableScissor();

            graphics.drawString(this.font, "Size: " + schematic.getSize().toShortString(), leftPos + 175, topPos + 177, 0xFFCCCCCC);
            graphics.drawString(this.font, "Blocks: " + schematic.getBlockCount(), leftPos + 175, topPos + 186, 0xFFCCCCCC);
        } else {
            String msg = "Select a Schematic";
            int msgWidth = this.font.width(msg);
            graphics.drawString(this.font, msg, leftPos + 240 - msgWidth / 2, topPos + 125, 0xFF888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSchematicList(GuiGraphics graphics, int leftPos, int topPos, int mouseX, int mouseY) {
        int yPos = topPos + 58;
        int index = 0;

        for (int i = this.scrollOffset; i < this.schematics.size() && index < 8; i++, index++) {
            SchematicData schematic = this.schematics.get(i);

            boolean isSelected = (i == this.selectedIndex);
            boolean isHovered = (mouseX >= leftPos + 10 && mouseX <= leftPos + 155 &&
                    mouseY >= yPos && mouseY <= yPos + 18);

            // Selection/hover highlight
            if (isSelected) {
                graphics.fill(leftPos + 12, yPos, leftPos + 153, yPos + 17, 0xFF404080);
            } else if (isHovered) {
                graphics.fill(leftPos + 12, yPos, leftPos + 153, yPos + 17, 0xFF404040);
            }

            String displayName = schematic.getName();
            if (displayName.length() > 18) {
                displayName = displayName.substring(0, 18) + "...";
            }

            int color = isSelected ? 0xFFFFFF00 : 0xFFCCCCCC;
            graphics.drawString(this.font, displayName, leftPos + 15, yPos + 4, color);

            yPos += 18;
        }
    }

    private void renderPreview(GuiGraphics graphics, int x, int y, int width, int height, SchematicData schematic) {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            return;
        }

        BlockPos size = schematic.getSize();
        if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
            return;
        }

        float maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        float scale = Math.min(width, height) / maxDimension * 0.7F * this.previewZoom;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x + width / 2.0F, y + height / 2.0F, 200.0F);
        poseStack.scale(scale, -scale, scale);

        poseStack.mulPose(Axis.XP.rotationDegrees(this.previewRotationX));
        poseStack.mulPose(Axis.YP.rotationDegrees(this.previewRotationY));

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : schematic.getBlocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }

        poseStack.translate(
                -(minX + size.getX() / 2.0F),
                -(minY + size.getY() / 2.0F),
                -(minZ + size.getZ() / 2.0F)
        );

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (Map.Entry<BlockPos, BlockState> entry : schematic.getBlocks().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockState state = entry.getValue();

            poseStack.pushPose();
            poseStack.translate(relPos.getX(), relPos.getY(), relPos.getZ());

            try {
                blockRenderer.renderSingleBlock(
                        state,
                        poseStack,
                        buffer,
                        15728880,
                        OverlayTexture.NO_OVERLAY
                );
            } catch (Exception e) {
                // Ignore rendering errors
            }

            poseStack.popPose();
        }

        buffer.endBatch();
        poseStack.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftPos = (this.width - 320) / 2;
        int topPos = (this.height - 240) / 2;

        // Click in schematic list
        if (mouseX >= (leftPos + 10) && mouseX <= (leftPos + 155) &&
                mouseY >= (topPos + 55) && mouseY <= (topPos + 200)) {

            int relativeY = (int)(mouseY - topPos - 58);
            int clickedIndex = this.scrollOffset + relativeY / 18;

            if (clickedIndex >= 0 && clickedIndex < this.schematics.size()) {
                this.selectedIndex = clickedIndex;
                return true;
            }
        }

        // Click in preview area to start dragging
        if (mouseX >= (leftPos + 165) && mouseX <= (leftPos + 310) &&
                mouseY >= (topPos + 55) && mouseY <= (topPos + 200)) {

            this.isDraggingPreview = true;
            this.lastMouseX = (int)mouseX;
            this.lastMouseY = (int)mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingPreview) {
            int deltaX = (int)mouseX - this.lastMouseX;
            int deltaY = (int)mouseY - this.lastMouseY;

            this.previewRotationY += deltaX * 0.5F;
            this.previewRotationX += deltaY * 0.5F;

            this.previewRotationX = Math.max(-89.0F, Math.min(89.0F, this.previewRotationX));

            this.lastMouseX = (int)mouseX;
            this.lastMouseY = (int)mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int leftPos = (this.width - 320) / 2;
        int topPos = (this.height - 240) / 2;

        if (mouseX >= (leftPos + 165) && mouseX <= (leftPos + 310) &&
                mouseY >= (topPos + 55) && mouseY <= (topPos + 200)) {

            this.previewZoom += (float)delta * ZOOM_STEP;
            this.previewZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.previewZoom));
            return true;
        }

        this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset - (int)delta));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadSchematics() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player != null) {
            ItemStack updatedStack = null;

            if (player.getMainHandItem().getItem() == this.terminalItem.getItem()) {
                updatedStack = player.getMainHandItem();
            } else if (player.getOffhandItem().getItem() == this.terminalItem.getItem()) {
                updatedStack = player.getOffhandItem();
            }

            if (updatedStack != null) {
                this.terminalItem = updatedStack;
            }
        }

        CompoundTag tag = this.terminalItem.getTag();
        if (tag == null || !tag.contains("SavedSchematics")) {
            this.schematics.clear();
            calculateMaxScroll();
            return;
        }

        this.schematics.clear();
        ListTag savedList = tag.getList("SavedSchematics", 10);

        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                this.schematics.add(SchematicData.fromNBT(schematicTag, minecraft.level.registryAccess()));
            } catch (Exception e) {
                // Ignore errors
            }
        }

        calculateMaxScroll();
    }

    public static class SchematicAction {
        public final ActionType type;
        public final String name;
        public final int index;

        public SchematicAction(ActionType type, String name, int index) {
            this.type = type;
            this.name = name;
            this.index = index;
        }
    }

    public enum ActionType {
        SAVE,
        LOAD,
        DELETE;
    }
}