package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.features.gui.BlockESPScreen;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BlockESPModule extends Module {

    public Setting<Color>   color     = color("Color", 255, 0, 255, 180);
    public Setting<Float>   lineWidth = num("LineWidth", 1.5f, 0.1f, 5.0f);
    public Setting<Boolean> fill      = bool("Fill", true);
    public Setting<Boolean> tracers   = bool("Tracers", false);
    public Setting<Integer> radius    = num("Radius", 50, 10, 128);

    private final List<Block>    targetBlocks = new ArrayList<>();
    private final List<BlockPos> found        = new ArrayList<>();

    public BlockESPModule() {
        super("BlockESP", "Highlight saved blocks through walls. Press G to edit list.", Category.RENDER);
    }

    @Override
    public void onKey(int key) {
        if (isEnabled() && key == GLFW.GLFW_KEY_G) {
            mc.execute(() -> mc.setScreen(new BlockESPScreen(this)));
        }
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        found.clear();
        if (targetBlocks.isEmpty()) return;

        BlockPos origin = mc.player.blockPosition();
        int r = radius.getValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -r, -r),
                origin.offset(r, r, r))) {
            BlockState state = mc.level.getBlockState(pos);
            if (targetBlocks.contains(state.getBlock())) {
                found.add(pos.immutable());
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (found.isEmpty()) return;

        for (BlockPos pos : found) {
            AABB box = new AABB(pos);

            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), box,
                        new Color(
                                color.getValue().getRed(),
                                color.getValue().getGreen(),
                                color.getValue().getBlue(),
                                40));
            }

            RenderUtil.drawBox(event.getMatrix(), box,
                    color.getValue(), lineWidth.getValue());

            if (tracers.getValue()) {
                drawTracer(event.getMatrix(), pos);
            }
        }
    }

    private void drawTracer(com.mojang.blaze3d.vertex.PoseStack stack, BlockPos pos) {
        net.minecraft.world.phys.Vec3 cam =
                mc.getEntityRenderDispatcher().camera.position();

        float x = (float) (pos.getX() + 0.5 - cam.x());
        float y = (float) (pos.getY() + 0.5 - cam.y());
        float z = (float) (pos.getZ() + 0.5 - cam.z());

        com.mojang.blaze3d.vertex.BufferBuilder buf =
                com.mojang.blaze3d.vertex.Tesselator.getInstance()
                        .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES,
                                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);

        com.mojang.blaze3d.vertex.PoseStack.Pose pose = stack.last();
        int c = color.getValue().getRGB();

        buf.addVertex(pose, 0, 0, 0).setColor(c).setLineWidth(lineWidth.getValue());
        buf.addVertex(pose, x, y, z).setColor(c).setLineWidth(lineWidth.getValue());

        me.alpha432.oyvey.util.render.Layers.lines().draw(buf.buildOrThrow());
    }

    public boolean addBlock(String id) {
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return false;
        Block block = BuiltInRegistries.BLOCK.get(rl);
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
