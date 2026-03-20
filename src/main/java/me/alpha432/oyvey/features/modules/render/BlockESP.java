package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.setting.Setting;
import me.alpha432.oyvey.gui.screens.BlockESPScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.rendering.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BlockESP extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────
    public final Setting<Integer> searchRadius = register(
            new Setting<>("Radius", 50, 10, 256, "Search radius in blocks"));

    public final Setting<Boolean> tracers = register(
            new Setting<>("Tracers", false, "Draw tracer lines to blocks"));

    public final Setting<Boolean> fill = register(
            new Setting<>("Fill", true, "Fill the ESP highlight box"));

    public final Setting<Boolean> outline = register(
            new Setting<>("Outline", true, "Draw outline around blocks"));

    public final Setting<Integer> red = register(
            new Setting<>("Red", 255, 0, 255, "Red colour channel"));

    public final Setting<Integer> green = register(
            new Setting<>("Green", 0, 0, 255, "Green colour channel"));

    public final Setting<Integer> blue = register(
            new Setting<>("Blue", 255, 0, 255, "Blue colour channel"));

    public final Setting<Integer> fillAlpha = register(
            new Setting<>("FillAlpha", 40, 0, 255, "Fill box transparency"));

    public final Setting<Integer> tracerAlpha = register(
            new Setting<>("TracerAlpha", 180, 0, 255, "Tracer line transparency"));

    // ── State ──────────────────────────────────────────────────────────────────
    private final List<Block>    targetBlocks = new ArrayList<>();
    private final List<BlockPos> found        = new ArrayList<>();

    public BlockESP() {
        super("BlockESP", "Highlight saved blocks through walls", Category.RENDER);
    }

    // ── Open custom config screen when right-clicked in ClickGUI ──────────────
    @Override
    public void onConfigScreen() {
        Minecraft.getInstance().setScreen(new BlockESPScreen(this));
    }

    // ── Tick: scan world for target blocks ─────────────────────────────────────
    @Override
    public void onTick() {
        if (nullCheck()) return;
        found.clear();
        if (targetBlocks.isEmpty()) return;

        BlockPos origin = mc.player.blockPosition();
        int r = searchRadius.getValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -r, -r),
                origin.offset( r,  r,  r))) {
            BlockState state = mc.level.getBlockState(pos);
            if (targetBlocks.contains(state.getBlock())) {
                found.add(pos.immutable());
            }
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onRender(RenderLevelStageEvent event) {
        if (!isEnabled() || nullCheck()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (found.isEmpty()) return;

        PoseStack ps  = event.getPoseStack();
        Vec3      cam = mc.gameRenderer.getMainCamera().getPosition();
        Color     col = new Color(red.getValue(), green.getValue(), blue.getValue(), 255);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        GL11.glLineWidth(1.5f);

        Tesselator    tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.getBuilder();

        for (BlockPos pos : found) {
            double x = pos.getX() - cam.x;
            double y = pos.getY() - cam.y;
            double z = pos.getZ() - cam.z;

            ps.pushPose();
            ps.translate(x, y, z);

            if (fill.getValue()) {
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                drawFilledBox(buf, ps, 0, 0, 0, 1, 1, 1, col, fillAlpha.getValue());
                tess.end();
            }

            if (outline.getValue()) {
                buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                drawOutlineBox(buf, ps, 0, 0, 0, 1, 1, 1, col, 255);
                tess.end();
            }

            if (tracers.getValue()) {
                double cx = mc.player.getX() - cam.x - x;
                double cy = mc.player.getEyeY() - cam.y - y;
                double cz = mc.player.getZ() - cam.z - z;

                buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                buf.vertex(ps.last().pose(), (float) cx, (float) cy, (float) cz)
                   .color(col.getRed(), col.getGreen(), col.getBlue(), tracerAlpha.getValue())
                   .endVertex();
                buf.vertex(ps.last().pose(), 0.5f, 0.5f, 0.5f)
                   .color(col.getRed(), col.getGreen(), col.getBlue(), tracerAlpha.getValue())
                   .endVertex();
                tess.end();
            }

            ps.popPose();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ── Public API for BlockESPScreen ──────────────────────────────────────────
    public boolean addBlock(Block block) {
        if (targetBlocks.contains(block)) return false;
        targetBlocks.add(block);
        return true;
    }

    public boolean addBlock(String id) {
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return false;
        return addBlock(BuiltInRegistries.BLOCK.get(rl));
    }

    public boolean removeBlock(Block block) {
        return targetBlocks.remove(block);
    }

    public List<Block> getTargetBlocks() {
        return targetBlocks;
    }

    // ── Box drawing helpers ────────────────────────────────────────────────────
    private void drawFilledBox(BufferBuilder buf, PoseStack ps,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               Color c, int alpha) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        var m = ps.last().pose();
        float[][] faces = {
            {(float)x1,(float)y1,(float)z1, (float)x2,(float)y1,(float)z1, (float)x2,(float)y1,(float)z2, (float)x1,(float)y1,(float)z2},
            {(float)x1,(float)y2,(float)z1, (float)x2,(float)y2,(float)z1, (float)x2,(float)y2,(float)z2, (float)x1,(float)y2,(float)z2},
            {(float)x1,(float)y1,(float)z1, (float)x2,(float)y1,(float)z1, (float)x2,(float)y2,(float)z1, (float)x1,(float)y2,(float)z1},
            {(float)x1,(float)y1,(float)z2, (float)x2,(float)y1,(float)z2, (float)x2,(float)y2,(float)z2, (float)x1,(float)y2,(float)z2},
            {(float)x1,(float)y1,(float)z1, (float)x1,(float)y1,(float)z2, (float)x1,(float)y2,(float)z2, (float)x1,(float)y2,(float)z1},
            {(float)x2,(float)y1,(float)z1, (float)x2,(float)y1,(float)z2, (float)x2,(float)y2,(float)z2, (float)x2,(float)y2,(float)z1},
        };
        for (float[] f : faces) {
            buf.vertex(m,f[0],f[1],f[2]).color(r,g,b,alpha).endVertex();
            buf.vertex(m,f[3],f[4],f[5]).color(r,g,b,alpha).endVertex();
            buf.vertex(m,f[6],f[7],f[8]).color(r,g,b,alpha).endVertex();
            buf.vertex(m,f[9],f[10],f[11]).color(r,g,b,alpha).endVertex();
        }
    }

    private void drawOutlineBox(BufferBuilder buf, PoseStack ps,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                Color c, int alpha) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        var m = ps.last().pose();
        float[][] edges = {
            {(float)x1,(float)y1,(float)z1,(float)x2,(float)y1,(float)z1},
            {(float)x2,(float)y1,(float)z1,(float)x2,(float)y1,(float)z2},
            {(float)x2,(float)y1,(float)z2,(float)x1,(float)y1,(float)z2},
            {(float)x1,(float)y1,(float)z2,(float)x1,(float)y1,(float)z1},
            {(float)x1,(float)y2,(float)z1,(float)x2,(float)y2,(float)z1},
            {(float)x2,(float)y2,(float)z1,(float)x2,(float)y2,(float)z2},
            {(float)x2,(float)y2,(float)z2,(float)x1,(float)y2,(float)z2},
            {(float)x1,(float)y2,(float)z2,(float)x1,(float)y2,(float)z1},
            {(float)x1,(float)y1,(float)z1,(float)x1,(float)y2,(float)z1},
            {(float)x2,(float)y1,(float)z1,(float)x2,(float)y2,(float)z1},
            {(float)x2,(float)y1,(float)z2,(float)x2,(float)y2,(float)z2},
            {(float)x1,(float)y1,(float)z2,(float)x1,(float)y2,(float)z2},
        };
        for (float[] e : edges) {
            buf.vertex(m,e[0],e[1],e[2]).color(r,g,b,alpha).endVertex();
            buf.vertex(m,e[3],e[4],e[5]).color(r,g,b,alpha).endVertex();
        }
    }
}
