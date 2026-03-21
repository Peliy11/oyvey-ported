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
                px + PAD, py + HEADER_H + PAD,
                W - PAD * 2 - 56, 20,
                Component.literal("block id"));
        inputBox.setMaxLength(128);
        inputBox.setSuggestion("e.g. diamond_ore");
        inputBox.setResponder(s -> inputBox.setSuggestion(s.isEmpty() ? "e.g. diamond_ore" : ""));
        addRenderableWidget(inputBox);

        addRenderableWidget(Button.builder(
                Component.literal("+ Add"), b -> tryAdd())
                .pos(px + W - PAD - 52, py + HEADER_H + PAD)
                .size(52, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Clear All"), b -> {
                    module.getTargetBlocks().clear();
                    scrollOffset = 0;
                    feedback("§cAll blocks cleared.");
                })
                .pos(px + PAD, py + H - 30)
                .size(80, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Done"), b -> onClose())
                .pos(px + W - PAD - 60, py + H - 30)
                .size(60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);

        int px = px(), py = py();
        int listY = listTop();

        // Panel
        g.fill(px, py, px + W, py + H, BG);

        // Header
        g.fill(px, py, px + W, py + HEADER_H, ACCENT);
        g.drawCenteredString(font, "§lBlockESP — Block List",
                px + W / 2, py + (HEADER_H - 8) / 2, 0xFFFFFF);

        // Hint
        g.drawCenteredString(font, "§8Press G in-game to reopen",
                px + W / 2, py + HEADER_H + PAD + 24, 0x555555);

        // Separator
        g.fill(px + PAD, listY - 6, px + W - PAD, listY - 5, 0x33FFFFFF);

        // Count label
        g.drawString(font, "§7Saved Blocks §8(" + module.getTargetBlocks().size() + ")",
                px + PAD, listY - 16, 0xAAAAAA);
