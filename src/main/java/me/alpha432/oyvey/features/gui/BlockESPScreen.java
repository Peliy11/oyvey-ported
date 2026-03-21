package me.alpha432.oyvey.features.gui;

import me.alpha432.oyvey.features.modules.render.BlockESPModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class BlockESPScreen extends Screen {

    private final BlockESPModule module;
    private EditBox inputBox;
    private String  feedback      = "";
    private int     feedbackTimer = 0;
    private int     scrollOffset  = 0;

    private static final int W          = 270;
    private static final int H          = 340;
    private static final int HEADER_H   = 26;
    private static final int PAD        = 10;
    private static final int ROW_H      = 22;
    private static final int VISIBLE    = 9;
    private static final int ACCENT     = 0xFF8B2BE2;
    private static final int BG         = 0xF2101010;
    private static final int ROW_NORMAL = 0x33FFFFFF;
    private static final int ROW_HOVER  = 0x558B2BE2;
    private static final int BTN_NORMAL = 0xFF550000;
    private static final int BTN_HOVER  = 0xFFAA0000;

    public BlockESPScreen(BlockESPModule module) {
        super(Component.literal("BlockESP"));
        this.module = module;
    }

    @Override
    protected void init() {
        int px = px(), py = py();

        inputBox = new EditBox(font,
                px + PAD,
                py + HEADER_H + PAD,
                W - PAD * 2 - 56, 20,
                Component.literal("block id"));
        inputBox.setMaxLength(128);
        inputBox.setSuggestion("e.g. diamond_ore");
        inputBox.setResponder(s ->
                inputBox.setSuggestion(s.isEmpty() ? "e.g. diamond_ore" : ""));
        addRenderableWidget(inputBox);

        addRenderableWidget(Button.builder(
                Component.literal("+ Add"), b -> tryAdd())
                .pos(px + W - PAD - 52, py + HEADER_H + PAD)
                .size(52, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Clear All"), b -> {
                    module.getTargetBlocks().clear();
                    scrollOffset = 0;
                    feedback("§cAll blocks cleared.");
                })
                .pos(px + PAD, py + H - 30)
                .size(80, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Done"), b -> onClose())
                .pos(px + W - PAD - 60, py + H - 30)
                .size(60, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);

        int px    = px();
        int py    = py();
        int listY = listTop();

        // Panel background
        g.fill(px, py, px + W, py + H, BG);

        // Header
        g.fill(px, py, px + W, py + HEADER_H, ACCENT);
        g.drawCenteredString(font, "§lBlockESP — Block List",
                px + W / 2, py + (HEADER_H - 8) / 2, 0xFFFFFF);

        // Hint text
        g.drawCenteredString(font, "§8Press G in-game to reopen",
                px + W / 2, py + HEADER_H + PAD + 24, 0x555555);

        // Separator line
        g.fill(px + PAD, listY - 6, px + W - PAD, listY - 5, 0x33FFFFFF);

        // Block count label
        g.drawString(font,
                "§7Saved Blocks §8(" + module.getTargetBlocks().size() + ")",
                px + PAD, listY - 16, 0xAAAAAA);

        // Block rows
        List<Block> blocks = module.getTargetBlocks();
        int start = clampScroll(blocks.size());
        int end   = Math.min(start + VISIBLE, blocks.size());

        if (blocks.isEmpty()) {
            g.drawCenteredString(font, "§8No blocks added yet.",
                    px + W / 2, listY + 28, 0x555555);
            g.drawCenteredString(font, "§7Type a name above and press Enter.",
                    px + W / 2, listY + 42, 0x444444);
        }

        for (int i = start; i < end; i++) {
            Block  block = blocks.get(i);
            String name  = BuiltInRegistries.BLOCK.getKey(block).getPath();
            int    ry    = listY + (i - start) * ROW_H;

            boolean rowHover = mx >= px + PAD && mx <= px + W - PAD
                            && my >= ry && my < ry + ROW_H - 2;
            boolean xHover   = isXHovered(mx, my, px, ry);

            // Row background
            g.fill(px + PAD, ry, px + W - PAD, ry + ROW_H - 2,
                    rowHover ? ROW_HOVER : ROW_NORMAL);

            // Block name
            g.drawString(font, "§f" + name,
                    px + PAD + 6, ry + (ROW_H - 8) / 2, 0xFFFFFF);

            // Remove button
            int bx = px + W - PAD - 19;
            g.fill(bx, ry + 1, bx + 17, ry + ROW_H - 3,
                    xHover ? BTN_HOVER : BTN_NORMAL);
            g.drawCenteredString(font, "§cX",
                    bx + 8, ry + (ROW_H - 8) / 2, 0xFF5555);
        }

        // Scrollbar
        if (blocks.size() > VISIBLE) {
            int trackH = VISIBLE * ROW_H;
            int thumbH = Math.max(14, trackH * VISIBLE / blocks.size());
            int maxOff = Math.max(1, blocks.size() - VISIBLE);
            int thumbY = listY + (trackH - thumbH) * start / maxOff;
            g.fill(px + W - 5, listY, px + W - 2, listY + trackH, 0x22FFFFFF);
            g.fill(px + W - 5, thumbY, px + W - 2, thumbY + thumbH, ACCENT);
        }

        // Feedback toast
        if (feedbackTimer > 0) {
            feedbackTimer--;
            int a = (int)(Math.min(1f, feedbackTimer / 20f) * 255) << 24;
            g.drawCenteredString(font, feedback,
                    px + W / 2, py + H - 46, a | 0x00FFFFFF);
        }

        // Border
        g.fill(px,             py,         px + W,     py + 1,     ACCENT);
        g.fill(px,             py + H - 1, px + W,     py + H,     ACCENT);
        g.fill(px,             py,         px + 1,     py + H,     ACCENT);
        g.fill(px + W - 1,     py,         px + W,     py + H,     ACCENT);

        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int px    = px();
        int listY = listTop();
        List<Block> blocks = module.getTargetBlocks();
        int start = clampScroll(blocks.size());
        int end   = Math.min(start + VISIBLE, blocks.size());

        for (int i = start; i < end; i++) {
            int ry = listY + (i - start) * ROW_H;
            if (isXHovered((int) mx, (int) my, px, ry)) {
                String name = BuiltInRegistries.BLOCK.getKey(blocks.get(i)).getPath();
                module.removeBlock(blocks.get(i));
                clampScroll(blocks.size());
                feedback("§cRemoved: §f" + name);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, module.getTargetBlocks().size() - VISIBLE);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - dy));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            tryAdd();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void tryAdd() {
        String raw = inputBox.getValue().trim().toLowerCase();
        if (raw.isEmpty()) return;

        boolean added = module.addBlock(raw);
        if (added) {
            feedback("§aAdded: §f" + (raw.contains(":") ? raw.split(":")[1] : raw));
            inputBox.setValue("");
            scrollOffset = Math.max(0, module.getTargetBlocks().size() - VISIBLE);
        } else {
            feedback("§eUnknown block or already in list.");
        }
    }

    private void feedback(String msg) {
        this.feedback      = msg;
        this.feedbackTimer = 80;
    }

    private int px() {
        return (width - W) / 2;
    }

    private int py() {
        return (height - H) / 2;
    }

    private int listTop() {
        return py() + HEADER_H + PAD + 20 + 28;
    }

    private int clampScroll(int size) {
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, size - VISIBLE)));
        return scrollOffset;
    }

    private boolean isXHovered(int mx, int my, int px, int ry) {
        int bx = px + W - PAD - 19;
        return mx >= bx && mx <= bx + 17
            && my >= ry + 1 && my < ry + ROW_H - 3;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
