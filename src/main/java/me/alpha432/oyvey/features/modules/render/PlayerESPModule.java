package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.*;
import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.Layers;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.List;

public class PlayerESPModule extends Module {

    public Setting<Color>   color      = color("Color", 255, 0, 255, 200);
    public Setting<Float>   lineWidth  = num("LineWidth", 1.5f, 0.1f, 5.0f);
    public Setting<Boolean> boxes      = bool("Boxes", true);
    public Setting<Boolean> tracers    = bool("Tracers", false);
    public Setting<Boolean> showHealth = bool("Health", true);
    public Setting<Boolean> showArmor  = bool("Armor", true);
    public Setting<Boolean> showDist   = bool("Distance", false);

    private Matrix4f projMat = new Matrix4f();
    private Matrix4f viewMat = new Matrix4f();

    public PlayerESPModule() {
        super("PlayerESP", "Shows players through walls with health and armor.", Category.RENDER);
    }

    private List<AbstractClientPlayer> getPlayers() {
        return mc.level.players().stream()
                .filter(p -> p != mc.player && p.isAlive())
                .toList();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

viewMat = new Matrix4f(event.getMatrix().last().pose());

        for (AbstractClientPlayer player : getPlayers()) {
            AABB box = player.getBoundingBox();

            if (boxes.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), box,
                        new Color(
                                color.getValue().getRed(),
                                color.getValue().getGreen(),
                                color.getValue().getBlue(),
                                30));
                RenderUtil.drawBox(event.getMatrix(), box,
                        color.getValue(), lineWidth.getValue());
            }

            if (tracers.getValue()) {
                Vec3 cam = mc.getEntityRenderDispatcher().camera.position();
                float tx = (float)(player.getX() - cam.x());
                float ty = (float)(player.getEyeY() - cam.y());
                float tz = (float)(player.getZ() - cam.z());

                BufferBuilder buf = Tesselator.getInstance()
                        .begin(VertexFormat.Mode.LINES,
                                DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
                PoseStack.Pose pose = event.getMatrix().last();
                int c = color.getValue().getRGB();

                buf.addVertex(pose, 0, 0, 0).setColor(c).setLineWidth(lineWidth.getValue());
                buf.addVertex(pose, tx, ty, tz).setColor(c).setLineWidth(lineWidth.getValue());
                Layers.lines().draw(buf.buildOrThrow());
            }
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;
        GuiGraphics g = event.getContext();

        for (AbstractClientPlayer player : getPlayers()) {
            Vec3 worldPos = new Vec3(
                    player.getX(),
                    player.getBoundingBox().maxY + 0.3,
                    player.getZ());

            float[] screen = worldToScreen(worldPos);
            if (screen == null) continue;

            int sx = (int) screen[0];
            int sy = (int) screen[1];

            String healthStr = "";
            if (showHealth.getValue()) {
                float hp  = player.getHealth();
                float max = player.getMaxHealth();
                String col = hp / max < 0.33f ? "§c"
                           : hp / max < 0.66f ? "§e"
                           : "§a";
                healthStr = col + String.format("%.1f", hp) + " §f";
            }

            String distStr = showDist.getValue()
                    ? " §7" + (int) mc.player.distanceTo(player) + "m"
                    : "";

            String line  = healthStr + player.getName().getString() + distStr;
            int    textW = mc.font.width(line);

            g.fill(sx - textW / 2 - 3, sy - 11,
                   sx + textW / 2 + 3, sy + 1,
                   0x88000000);
            g.drawString(mc.font, line, sx - textW / 2, sy - 9, 0xFFFFFF);

            if (showArmor.getValue()) {
                EquipmentSlot[] slots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
                };

                int count = 0;
                for (EquipmentSlot slot : slots) {
                    if (!player.getItemBySlot(slot).isEmpty()) count++;
                }

                int iconSize = 9;
                int startX   = sx - (count * (iconSize + 1)) / 2;
                int armorY   = sy + 3;
                int drawn    = 0;

                for (EquipmentSlot slot : slots) {
                    ItemStack stack = player.getItemBySlot(slot);
                    if (stack.isEmpty()) continue;
                    g.renderItem(stack, startX + drawn * (iconSize + 1), armorY);
                    drawn++;
                }
            }
        }
    }

    private float[] worldToScreen(Vec3 world) {
        Vec3 cam = mc.getEntityRenderDispatcher().camera.position();
        double rx = world.x - cam.x();
        double ry = world.y - cam.y();
        double rz = world.z - cam.z();

        Vector4f vec = new Vector4f((float) rx, (float) ry, (float) rz, 1f);
        viewMat.transform(vec);

        if (vec.w <= 0) return null;

        float ndcX =  vec.x / vec.w;
        float ndcY = -vec.y / vec.w;

        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();

        return new float[]{
            (ndcX + 1f) / 2f * sw,
            (ndcY + 1f) / 2f * sh
        };
    }
}
