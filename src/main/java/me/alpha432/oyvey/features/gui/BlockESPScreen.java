package me.alpha432.oyvey.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.features.modules.render.BlockESP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BlockESPScreen extends Screen {

    private final BlockESP module;
    private EditBox searchBox;
    private String feedback = "";
    private int feedbackTimer = 0;

    // ── Layout ─────────────────────────────────────────────────────────────────
    private static final int PANEL_W    = 260;
    private static final int PANEL_H    = 320;
    private static final int HEADER_H   = 24;
    private static final int ROW_H      = 20;
    private static final int PADDING    = 8;

    // ── Scroll ─────────────────────────────────────────────────────────────────
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 8;

    public BlockESPScreen(BlockESP module) {
        super(Component.literal("BlockESP – Block List"));
        this.module = module;
    }

    @Override
    protected void init() {
        int panelX = (width  - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // Search / add box
        searchBox = new EditBox(font,
                panelX + PADDING,
                panelY + HEADER_H + PADDING,
                PANEL_W - PADDING * 2 - 54, 18,
                Component.literal("block id"));
        searchBox.setMaxLength(128);
        searchBox.setSuggestion("e.g. diamond_ore");
        searchBox.setResponder(s -> searchBox.setSuggestion(s.isEmpty() ? "e.g. diamond_ore" : ""));
        addRenderableWidget(searchBox);

        // ADD button
        addRenderableWidget(Button.builder(Component.literal("Add"),
                btn -> addBlockFromInput())
                .pos(panelX + PANEL_W - PADDING - 50, panelY + HEADER_H + PADDING)
                .size(50, 18)
                .build());

        // CLEAR ALL button at bottom
        addRenderableWidget(Button.builder(Component.literal("Clear All"),
                btn -> {
                    module.getTargetBlocks().clear();
                    scrollOffset = 0;
                    setFeedback("§cCleared all blocks.", false);
                })
                .pos(panelX + PADDING, panelY + PANEL_H - 28)
                .size(80, 18)
                .build());

        // CLOSE button at bottom
        addRenderableWidget(Button.builder(Component.literal("Close"),
                btn -> onClose())
                .pos(panelX + PANEL_W - PADDING - 60, panelY + PANEL_H - 28)
                .size(60, 18)
                .build());
    }

    @Override
    public void render(PoseStack ps, int mouseX, int mouseY, float delta) {
        // ── Dim background ─────────────────────────────────────────────────────
        renderBackground(ps);

        int panelX = (width  - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // ── Panel background ───────────────────────────────────────────────────
        fill(ps, panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE8101010);

        // ── Header ────────────────────────────────────────────────────────────
        fill(ps, panelX, panelY, panelX + PANEL_W, panelY + HEADER_H, 0xFF8B2BE2);
        drawCenteredString(ps, font, "§fBlockESP – Block List",
                panelX + PANEL_W / 2, panelY + (HEADER_H - 8) / 2, 0xFFFFFF);

        // ── Column label ──────────────────────────────────────────────────────
        int listTop = panelY + HEADER_H + PADDING + 22 + 4;
        drawString(ps, font, "§7Saved Blocks (" + module.getTargetBlocks().size() + ")",
                panelX + PADDING, listTop, 0xAAAAAA);
        listTop += 12;

        // ── Block rows ────────────────────────────────────────────────────────
        List<Block> blocks = module.getTargetBlocks();
        int start  = Math.min(scrollOffset, Math.max(0, blocks.size() - VISIBLE_ROWS));
        int end    = Math.min(start + VISIBLE_ROWS, blocks.size());
        List<Block> removeQueue = new ArrayList<>();

        for (int i = start; i < end; i++) {
            Block block  = blocks.get(i);
            String name  = BuiltInRegistries.BLOCK.getKey(block).getPath(); // e.g. "diamond_ore"
            int rowY     = listTop + (i - start) * ROW_H;
            boolean hovered = mouseX >= panelX + PADDING && mouseX <= panelX + PANEL_W - PADDING
                           && mouseY >= rowY && mouseY < rowY + ROW_H;

            // Row bg
            fill(ps, panelX + PADDING, rowY,
                     panelX + PANEL_W - PADDING, rowY + ROW_H - 2,
                     hovered ? 0x551E8FFF : 0x33FFFFFF);

            // Block name
            drawString(ps, font, "§f" + name,
                    panelX + PADDING + 4, rowY + (ROW_H - 8) / 2, 0xFFFFFF);

            // Remove [X] button area
            int btnX = panelX + PANEL_W - PADDING - 18;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + 16
                            && mouseY >= rowY + 1 && mouseY < rowY + ROW_H - 2;
            fill(ps, btnX, rowY + 1, btnX + 16, rowY + ROW_H - 3,
                    btnHover ? 0xFF8B0000 : 0xFF550000);
            drawCenteredString(ps, font, "§c✕", btnX + 8, rowY + (ROW_H - 8) / 2, 0xFF4444);
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (blocks.isEmpty()) {
            drawCenteredString(ps, font, "§7No blocks saved. Type an ID above and click Add.",
                    panelX + PANEL_W / 2, listTop + 20, 0x888888);
        }

        // ── Scrollbar ─────────────────────────────────────────────────────────
        if (blocks.size() > VISIBLE_ROWS) {
            int trackH  = VISIBLE_ROWS * ROW_H;
            int thumbH  = Math.max(10, trackH * VISIBLE_ROWS / blocks.size());
            int thumbY  = listTop + (trackH - thumbH) * scrollOffset
                          / Math.max(1, blocks.size() - VISIBLE_ROWS);
            fill(ps, panelX + PANEL_W - 5, listTop,
                     panelX + PANEL_W - 2, listTop + trackH, 0x33FFFFFF);
            fill(ps, panelX + PANEL_W - 5, thumbY,
                     panelX + PANEL_W - 2, thumbY + thumbH, 0xFF8B2BE2);
        }

        // ── Feedback message ──────────────────────────────────────────────────
        if (feedbackTimer > 0) {
            feedbackTimer--;
            float alpha = Math.min(1f, feedbackTimer / 20f);
            int a = (int)(alpha * 255) << 24;
            drawCenteredString(ps, font, feedback,
                    panelX + PANEL_W / 2, panelY + PANEL_H - 44, a | 0x00FFFFFF);
        }

        // ── Border ────────────────────────────────────────────────────────────
        hLine(ps, panelX, panelX + PANEL_W - 1, panelY,              0xFF8B2BE2);
        hLine(ps, panelX, panelX + PANEL_W - 1, panelY + PANEL_H,    0xFF8B2BE2);
        vLine(ps, panelX,              panelY, panelY + PANEL_H,      0xFF8B2BE2);
        vLine(ps, panelX + PANEL_W - 1, panelY, panelY + PANEL_H,    0xFF8B2BE2);

        super.render(ps, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX  = (width  - PANEL_W) / 2;
        int panelY  = (height - PANEL_H) / 2;
        int listTop = panelY + HEADER_H + PADDING + 22 + 4 + 12;

        List<Block> blocks = module.getTargetBlocks();
        int start = Math.min(scrollOffset, Math.max(0, blocks.size() - VISIBLE_ROWS));
        int end   = Math.min(start + VISIBLE_ROWS, blocks.size());

        for (int i = start; i < end; i++) {
            int rowY = listTop + (i - start) * ROW_H;
            int btnX = panelX + PANEL_W - PADDING - 18;

            // Click the [X] remove button
            if (mouseX >= btnX && mouseX <= btnX + 16
             && mouseY >= rowY + 1 && mouseY < rowY + ROW_H - 2) {
                Block removed = blocks.get(i);
                String name = BuiltInRegistries.BLOCK.getKey(removed).getPath();
                module.removeBlock(removed);
                scrollOffset = Math.max(0, Math.min(scrollOffset, blocks.size() - VISIBLE_ROWS));
                setFeedback("§cRemoved: §f" + name, false);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, module.getTargetBlocks().size() - VISIBLE_ROWS);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - delta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter to add block
        if (keyCode == 257 || keyCode == 335) { // ENTER / NUMPAD_ENTER
            addBlockFromInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void addBlockFromInput() {
        String input = searchBox.getValue().trim().toLowerCase();
        if (input.isEmpty()) return;

        if (!input.contains(":")) input = "minecraft:" + input;
        ResourceLocation rl = ResourceLocation.tryParse(input);

        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            setFeedback("§cUnknown block: " + input, true);
            return;
        }

        Block block = BuiltInRegistries.BLOCK.get(rl);
        boolean added = module.addBlock(block);
        if (added) {
            setFeedback("§aAdded: §f" + rl.getPath(), false);
            searchBox.setValue("");
            // Scroll to bottom to show new entry
            scrollOffset = Math.max(0, module.getTargetBlocks().size() - VISIBLE_ROWS);
        } else {
            setFeedback("§eAlready in list: §f" + rl.getPath(), true);
        }
    }

    private void setFeedback(String msg, boolean isError) {
        this.feedback     = msg;
        this.feedbackTimer = 80; // ~4 seconds
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
