package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.setting.Setting;
import me.alpha432.oyvey.gui.screens.BlockESPScreen;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class BlockESPModule extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────
    public final Setting<Integer> searchRadius = register(
            new Setting<>("Radius", 50, 10, 128, "Search radius in blocks"));

    public final Setting<Boolean> tracers = register(
            new Setting<>("Tracers", false, "Draw tracer lines to blocks"));

    public final Setting<Boolean> fill = register(
            new Setting<>("Fill", true, "Fill the ESP box"));

    public final Setting<Boolean> outline = register(
            new Setting<>("Outline", true, "Draw outline around blocks"));

    public final Setting<Integer> red = register(
            new Setting<>("Red", 255, 0, 255, "Red colour channel"));

    public final Setting<Integer> green = register(
            new Setting<>("Green", 0, 0, 255, "Green colour channel"));

    public final Setting<Integer> blue = register(
            new Setting<>("Blue", 255, 0, 255, "Blue colour channel"));

    public final Setting<Integer> fillAlpha = register(
            new Setting<>("FillAlpha", 40, 0, 255, "Fill transparency"));

    public final Setting<Integer> tracerAlpha = register(
            new Setting<>("TracerAlpha", 180, 0, 255, "Tracer transparency"));

    // ── Internal state ─────────────────────────────────────────────────────────
    private final List<Block>    targetBlocks = new ArrayList<>();
    private final List<BlockPos> found        = new ArrayList<>();

    public BlockESPModule() {
        super("BlockESP", "Highlight saved blocks through walls. Press G to edit.", Category.RENDER);
    }

    // ── Press G to open block list editor ─────────────────────────────────────
    @Override
    public void onKey(int key) {
        if (isEnabled() && key == GLFW.GLFW_KEY_G) {
            MinecraftClient.getInstance().setScreen(new BlockESPScreen(this));
        }
    }

    // ── Tick: scan world for target blocks ─────────────────────────────────────
    @Override
    public void onTick() {
        if (nullCheck()) return;
        found.clear();
        if (targetBlocks.isEmpty()) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = searchRadius.getValue();

        BlockPos.iterate(
                origin.add(-r, -r, -r),
                origin.add( r,  r,  r)
        ).forEach(pos -> {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (targetBlocks.contains(block)) {
                found.add(pos.toImmutable());
            }
        });
    }

    // ── Render ─────────────────────────────────────────────────────────────────
    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (found.isEmpty()) return;

        MatrixStack matrices = event.getMatrixStack();
        Camera camera = mc.gameRenderer.getCamera();
        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        float r = red.getValue()   / 255f;
        float g = green.getValue() / 255f;
        float b = blue.getValue()  / 255f;
        float fa = fillAlpha.getValue()   / 255f;
        float ta = tracerAlpha.getValue() / 255f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.5f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (BlockPos pos : found) {
            double x = pos.getX() - cx;
            double y = pos.getY() - cy;
            double z = pos.getZ() - cz;

            matrices.push();
            matrices.translate(x, y, z);

            Matrix4f m = matrices.peek().getPositionMatrix();

            // ── Filled box ────────────────────────────────────────────────────
            if (fill.getValue()) {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                drawFilledBox(buf, m, r, g, b, fa);
                tess.draw();
            }

            // ── Outline box ───────────────────────────────────────────────────
            if (outline.getValue()) {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                drawOutlineBox(buf, m, r, g, b, 1f);
                tess.draw();
            }

            // ── Tracer ────────────────────────────────────────────────────────
            if (tracers.getValue()) {
                double px = mc.player.getX() - cx - x;
                double py = mc.player.getEyeY() - cy - y;
                double pz = mc.player.getZ() - cz - z;

                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                buf.vertex(m, (float) px, (float) py, (float) pz)
                   .color(r, g, b, ta).next();
                buf.vertex(m, 0.5f, 0.5f, 0.5f)
                   .color(r, g, b, ta).next();
                tess.draw();
            }

            matrices.pop();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    // ── Box helpers ────────────────────────────────────────────────────────────
    private void drawFilledBox(BufferBuilder buf, Matrix4f m,
                               float r, float g, float b, float a) {
        float[][] faces = {
            // bottom
            {0,0,0, 1,0,0, 1,0,1, 0,0,1},
            // top
            {0,1,0, 1,1,0, 1,1,1, 0,1,1},
            // front
            {0,0,0, 1,0,0, 1,1,0, 0,1,0},
            // back
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},
            // left
            {0,0,0, 0,0,1, 0,1,1, 0,1,0},
            // right
            {1,0,0, 1,0,1, 1,1,1, 1,1,0},
        };
        for (float[] f : faces) {
            buf.vertex(m, f[0],  f[1],  f[2] ).color(r,g,b,a).next();
            buf.vertex(m, f[3],  f[4],  f[5] ).color(r,g,b,a).next();
            buf.vertex(m, f[6],  f[7],  f[8] ).color(r,g,b,a).next();
            buf.vertex(m, f[9],  f[10], f[11]).color(r,g,b,a).next();
        }
    }

    private void drawOutlineBox(BufferBuilder buf, Matrix4f m,
                                float r, float g, float b, float a) {
        float[][] edges = {
            // bottom
            {0,0,0, 1,0,0}, {1,0,0, 1,0,1},
            {1,0,1, 0,0,1}, {0,0,1, 0,0,0},
            // top
            {0,1,0, 1,1,0}, {1,1,0, 1,1,1},
            {1,1,1, 0,1,1}, {0,1,1, 0,1,0},
            // verticals
            {0,0,0, 0,1,0}, {1,0,0, 1,1,0},
            {1,0,1, 1,1,1}, {0,0,1, 0,1,1},
        };
        for (float[] e : edges) {
            buf.vertex(m, e[0], e[1], e[2]).color(r,g,b,a).next();
            buf.vertex(m, e[3], e[4], e[5]).color(r,g,b,a).next();
        }
    }

    // ── Public API for BlockESPScreen ──────────────────────────────────────────
    public boolean addBlock(String id) {
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) return false;
        Block block = Registries.BLOCK.get(identifier);
        if (targetBlocks.contains(block)) return false;
        targetBlocks.add(block);
        return true;
    }

    public boolean removeBlock(Block block) {
        return targetBlocks.remove(block);
    }

    public List<Block> getTargetBlocks() {
        return targetBlocks;
    }
}
