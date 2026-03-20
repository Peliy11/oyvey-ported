package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.setting.Setting;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;

public class PlayerESPModule extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────
    public final Setting<Boolean> boxes = register(
            new Setting<>("Boxes", true, "Draw ESP box around players"));

    public final Setting<Boolean> tracers = register(
            new Setting<>("Tracers", false, "Draw tracer lines to players"));

    public final Setting<Boolean> showHealth = register(
            new Setting<>("Health", true, "Show player health"));

    public final Setting<Boolean> showArmor = register(
            new Setting<>("Armor", true, "Show armor icons"));

    public final Setting<Boolean> showDistance = register(
            new Setting<>("Distance", false, "Show distance to player"));

    public final Setting<Boolean> teamColor = register(
            new Setting<>("TeamColor", false, "Use the player's team colour"));

    public final Setting<Integer> red = register(
            new Setting<>("Red", 255, 0, 255, "Red channel"));

    public final Setting<Integer> green = register(
            new Setting<>("Green", 0, 0, 255, "Green channel"));

    public final Setting<Integer> blue = register(
            new Setting<>("Blue", 255, 0, 255, "Blue channel"));

    public final Setting<Integer> fillAlpha = register(
            new Setting<>("FillAlpha", 30, 0, 255, "Box fill transparency"));

    public final Setting<Integer> tracerAlpha = register(
            new Setting<>("TracerAlpha", 160, 0, 255, "Tracer transparency"));

    // ── Viewport cache (updated each 3D frame) ─────────────────────────────────
    private int   vpW, vpH;
    private Matrix4f projMatrix    = new Matrix4f();
    private Matrix4f modelViewMat  = new Matrix4f();

    public PlayerESPModule() {
        super("PlayerESP", "Shows nearby players with health, name and armor.", Category.RENDER);
    }

    // ── 3D: boxes + tracers ────────────────────────────────────────────────────
    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        MatrixStack matrices = event.getMatrixStack();
        Camera camera        = mc.gameRenderer.getCamera();
        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        // Cache matrices for 2D projection
        vpW          = mc.getWindow().getFramebufferWidth();
        vpH          = mc.getWindow().getFramebufferHeight();
        projMatrix   = RenderSystem.getProjectionMatrix();
        modelViewMat = new Matrix4f(matrices.peek().getPositionMatrix());

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.5f);

        Tessellator    tess = Tessellator.getInstance();
        BufferBuilder  buf  = tess.getBuffer();

        for (AbstractClientPlayerEntity player : getPlayers()) {
            Color col = getColor(player);
            float r = col.getRed()   / 255f;
            float g = col.getGreen() / 255f;
            float b = col.getBlue()  / 255f;
            float fa = fillAlpha.getValue()   / 255f;
            float ta = tracerAlpha.getValue() / 255f;

            Box box = player.getBoundingBox();
            double x1 = box.minX - cx, y1 = box.minY - cy, z1 = box.minZ - cz;
            double x2 = box.maxX - cx, y2 = box.maxY - cy, z2 = box.maxZ - cz;

            Matrix4f m = matrices.peek().getPositionMatrix();

            // ── Filled box ────────────────────────────────────────────────────
            if (boxes.getValue()) {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                drawFilledBox(buf, m, x1, y1, z1, x2, y2, z2, r, g, b, fa);
                tess.draw();

                // Outline
                buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                drawOutlineBox(buf, m, x1, y1, z1, x2, y2, z2, r, g, b, 1f);
                tess.draw();
            }

            // ── Tracer ────────────────────────────────────────────────────────
            if (tracers.getValue()) {
                double px = player.getX() - cx;
                double py = player.getEyeY() - cy;
                double pz = player.getZ() - cz;

                buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                buf.vertex(m, 0f, 0f, 0f).color(r, g, b, ta).next();
                buf.vertex(m, (float) px, (float) py, (float) pz).color(r, g, b, ta).next();
                tess.draw();
            }
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    // ── 2D: nametag (health)(name)(armor) ─────────────────────────────────────
    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;

        var context  = event.getDrawContext();
        var matrices = context.getMatrices();
        Camera camera = mc.gameRenderer.getCamera();

        for (AbstractClientPlayerEntity player : getPlayers()) {
            // Project the point above the player's head to screen space
            Vec3d worldPos = player.getPos().add(0,
                    player.getBoundingBox().getLengthY() + 0.25, 0);

            float[] screen = worldToScreen(worldPos, camera.getPos());
            if (screen == null) continue; // behind camera

            float sx = screen[0];
            float sy = screen[1];

            Color col = getColor(player);
            int color = col.getRGB();

            // ── Build display string ───────────────────────────────────────────
            // Format:  §c9.5 §fPlayerName  (armor rendered separately below)
            String healthStr = "";
            if (showHealth.getValue()) {
                float hp    = player.getHealth();
                float maxHp = player.getMaxHealth();
                int   r     = (int) MathHelper.lerp(hp / maxHp, 255, 0);
                int   g     = (int) MathHelper.lerp(hp / maxHp, 0, 200);
                healthStr = String.format("§%s%.1f§r ", hpColor(hp, maxHp), hp);
            }

            String distStr = "";
            if (showDistance.getValue()) {
                double dist = mc.player.distanceTo(player);
                distStr = String.format(" §7%.0fm", dist);
            }

            String nameStr  = player.getGameProfile().getName();
            String fullLine = healthStr + "§f" + nameStr + distStr;

            int textW = mc.textRenderer.getWidth(fullLine);
            int textH = mc.textRenderer.fontHeight;

            // ── Draw name tag background ───────────────────────────────────────
            int bgX = (int) sx - textW / 2 - 3;
            int bgY = (int) sy - textH - 2;
            context.fill(bgX, bgY, bgX + textW + 6, bgY + textH + 2, 0x88000000);

            // ── Draw name tag text ─────────────────────────────────────────────
            context.drawTextWithShadow(mc.textRenderer, fullLine,
                    (int) sx - textW / 2, (int) sy - textH, color);

            // ── Draw armor icons below name tag ────────────────────────────────
            if (showArmor.getValue()) {
                EquipmentSlot[] slots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
                };

                // Count non-empty armor pieces
                int armorCount = 0;
                for (EquipmentSlot slot : slots) {
                    if (!player.getEquippedStack(slot).isEmpty()) armorCount++;
                }

                int iconSize  = 9;
                int totalW    = armorCount * (iconSize + 1);
                int startX    = (int) sx - totalW / 2;
                int armorY    = bgY + textH + 4;

                int drawn = 0;
                for (EquipmentSlot slot : slots) {
                    ItemStack stack = player.getEquippedStack(slot);
                    if (stack.isEmpty()) continue;

                    int ix = startX + drawn * (iconSize + 1);
                    context.drawItem(stack, ix, armorY);

                    // Durability bar if damaged
                    if (stack.isDamageable() && stack.getDamage() > 0) {
                        float pct = 1f - (float) stack.getDamage() / stack.getMaxDamage();
                        int barW  = (int) (iconSize * pct);
                        int barCol = Color.HSBtoRGB(pct / 3f, 1f, 1f);
                        context.fill(ix, armorY + iconSize + 1,
                                     ix + barW, armorY + iconSize + 2, barCol | 0xFF000000);
                    }
                    drawn++;
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private List<AbstractClientPlayerEntity> getPlayers() {
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && !p.isDead())
                .toList();
    }

    private Color getColor(AbstractClientPlayerEntity player) {
        if (teamColor.getValue() && player.getScoreboardTeam() != null) {
            int tc = player.getScoreboardTeam().getColor().getColorValue() != null
                   ? player.getScoreboardTeam().getColor().getColorValue()
                   : 0xFFFFFF;
            return new Color(tc);
        }
        return new Color(red.getValue(), green.getValue(), blue.getValue());
    }

    // Returns "c" red / "e" yellow / "a" green based on health
    private String hpColor(float hp, float max) {
        float pct = hp / max;
        if (pct < 0.33f) return "c";
        if (pct < 0.66f) return "e";
        return "a";
    }

    // Projects a world Vec3d to screen [x, y], returns null if behind camera
    private float[] worldToScreen(Vec3d world, Vec3d camPos) {
        double rx = world.x - camPos.x;
        double ry = world.y - camPos.y;
        double rz = world.z - camPos.z;

        Vector4f vec = new Vector4f((float) rx, (float) ry, (float) rz, 1f);
        vec.mul(modelViewMat);
        vec.mul(projMatrix);

        if (vec.w <= 0f) return null; // behind camera

        float ndcX = vec.x / vec.w;
        float ndcY = vec.y / vec.w;

        float sx = (ndcX + 1f) / 2f * mc.getWindow().getScaledWidth();
        float sy = (1f - ndcY) / 2f * mc.getWindow().getScaledHeight();

        return new float[]{sx, sy};
    }

    // ── Box drawing ────────────────────────────────────────────────────────────
    private void drawFilledBox(BufferBuilder buf, Matrix4f m,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               float r, float g, float b, float a) {
        float[][] faces = {
            {(float)x1,(float)y1,(float)z1,(float)x2,(float)y1,(float)z1,(float)x2,(float)y1,(float)z2,(float)x1,(float)y1,(float)z2},
            {(float)x1,(float)y2,(float)z1,(float)x2,(float)y2,(float)z1,(float)x2,(float)y2,(float)z2,(float)x1,(float)y2,(float)z2},
            {(float)x1,(float)y1,(float)z1,(float)x2,(float)y1,(float)z1,(float)x2,(float)y2,(float)z1,(float)x1,(float)y2,(float)z1},
            {(float)x1,(float)y1,(float)z2,(float)x2,(float)y1,(float)z2,(float)x2,(float)y2,(float)z2,(float)x1,(float)y2,(float)z2},
            {(float)x1,(float)y1,(float)z1,(float)x1,(float)y1,(float)z2,(float)x1,(float)y2,(float)z2,(float)x1,(float)y2,(float)z1},
            {(float)x2,(float)y1,(float)z1,(float)x2,(float)y1,(float)z2,(float)x2,(float)y2,(float)z2,(float)x2,(float)y2,(float)z1},
        };
        for (float[] f : faces) {
            buf.vertex(m,f[0],f[1],f[2]).color(r,g,b,a).next();
            buf.vertex(m,f[3],f[4],f[5]).color(r,g,b,a).next();
            buf.vertex(m,f[6],f[7],f[8]).color(r,g,b,a).next();
            buf.vertex(m,f[9],f[10],f[11]).color(r,g,b,a).next();
        }
    }

    private void drawOutlineBox(BufferBuilder buf, Matrix4f m,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b, float a) {
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
            buf.vertex(m,e[0],e[1],e[2]).color(r,g,b,a).next();
            buf.vertex(m,e[3],e[4],e[5]).color(r,g,b,a).next();
        }
    }
}
