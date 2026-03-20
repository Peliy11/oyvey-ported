package me.alpha432.oyvey.gui.screens;

import me.alpha432.oyvey.features.modules.render.BlockESPModule;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;

public class BlockESPScreen extends Screen {

    private final BlockESPModule module;
    private TextFieldWidget inputBox;
    private String  feedback      = "";
    private int     feedbackTimer = 0;
    private int     scrollOffset  = 0;

    // ── Layout ─────────────────────────────────────────────────────────────────
    private static final int W        = 270;
    private static final int H        = 340;
    private static final int HEADER_H = 26;
    private static final int PAD      = 10;
    private static final int ROW_H    = 22;
    private static final int VISIBLE  = 9;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final int ACCENT     = 0xFF8B2BE2;
    private static final int BG         = 0xF2101010;
    private static final int ROW_NORMAL = 0x33FFFFFF;
    private static final int ROW_HOVER  = 0x558B2BE2;
    private static final int BTN_NORMAL = 0xFF550000;
    private static final int BTN_HOVER  = 0xFFAA0000;

    public BlockESPScreen(BlockESPModule module) {
        super(Text.literal("BlockESP"));
        this.module = module;
    }

    @Override
    protected void init() {
        int px = px(), py = py();

        // ── Input box ──────────────────────────────────────────────────────────
        inputBox = new TextFieldWidget(textRenderer,
                px + PAD,
                py + HEADER_H + PAD,
                W - PAD * 2 - 56, 20,
                Text.literal("block id"));
        inputBox.setMaxLength(128);
        inputBox.setSuggestion("e.g. diamond_ore");
        inputBox.setChangedListener(s ->
                inputBox.setSuggestion(s.isEmpty() ? "e.g. diamond_ore" : ""));
        addDrawableChild(inputBox);

        // ── Add button ─────────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add"), b -> tryAdd())
                .position(px + W - PAD - 52, py + HEADER_H + PAD)
                .size(52, 20)
                .build());

        // ── Clear all button ───────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear All"), b -> {
                    module.getTargetBlocks().clear();
                    scrollOffset = 0;
                    feedback("§cAll blocks cleared.");
                })
                .position(px + PAD, py + H - 30)
                .size(80, 20)
                .build());

        // ── Done button ────────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"), b -> close())
                .position(px + W - PAD - 60, py + H - 30)
                .size(60, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mx, int my, float delta) {
        renderBackground(context);

        int px = px(), py = py();
        int listY = listTop();

        // ── Panel background ───────────────────────────────────────────────────
        context.fill(px, py, px + W, py + H, BG);

        // ── Header ────────────────────────────────────────────────────────────
        context.fill(px, py, px + W, py + HEADER_H, ACCENT);
        context.drawCenteredTextWithShado
