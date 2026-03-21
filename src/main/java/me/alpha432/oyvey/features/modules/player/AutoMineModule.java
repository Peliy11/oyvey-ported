package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoMineModule extends Module {

    public Setting<Boolean> autoTool = bool("AutoTool", true);
    public Setting<Boolean> autoWalk = bool("AutoWalk", true);

    public enum MineMode { FLAT, TUNNEL_1x2, TUNNEL_2x2, STAIRCASE }
    public Setting<MineMode> mode = new Setting<>("Mode", MineMode.TUNNEL_1x2);

    private int savedSlot  = -1;
    private int delayTimer = 0;

    public AutoMineModule() {
        super("AutoMine", "Automatically mines forward in the chosen pattern.", Category.PLAYER);
        register(mode);
    }

    @Override
    public void onDisable() {
        if (savedSlot != -1) {
            mc.player.getInventory().setSelectedHotbarSlot(savedSlot);
            savedSlot = -1;
        }
        mc.options.keyUp.setDown(false);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (delayTimer-- > 0) return;

        if (autoTool.getValue()) equipBestPickaxe();
        if (autoWalk.getValue()) mc.options.keyUp.setDown(true);

        List<BlockPos> targets = getTargets();
        if (targets.isEmpty()) return;

        for (BlockPos pos : targets) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;

            Direction face = getClosestFace(pos);
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, 0));
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face, 1));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        delayTimer = 2;
    }

    private List<BlockPos> getTargets() {
        List<BlockPos> list = new ArrayList<>();
        BlockPos  feet   = mc.player.blockPosition();
        Direction facing = mc.player.getDirection();
        Direction left   = facing.getCounterClockWise();

        switch (mode.getValue()) {
            case FLAT -> list.add(feet.relative(facing));
            case TUNNEL_1x2 -> {
                list.add(feet.relative(facing));
                list.add(feet.relative(facing).above());
            }
            case TUNNEL_2x2 -> {
                list.add(feet.relative(facing));
                list.add(feet.relative(facing).above());
                list.add(feet.relative(facing).relative(left));
                list.add(feet.relative(facing).relative(left).above());
            }
            case STAIRCASE -> {
                list.add(feet.relative(facing));
                list.add(feet.relative(facing).above());
                list.add(feet.below().relative(facing));
            }
        }

        list.removeIf(pos -> {
            BlockState s = mc.level.getBlockState(pos);
            return s.isAir() || s.getBlock() == Blocks.BEDROCK;
        });

        Vec3 pp = mc.player.position();
        list.sort(Comparator.comparingDouble(p ->
                pp.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5)));

        return list;
    }

    private void equipBestPickaxe() {
        int best = -1, score = 0;
        for (int i = 0; i < 9; i++) {
            int s = pickScore(mc.player.getInventory().getItem(i));
            if (s > score) { score = s; best = i; }
        }
        if (best != -1 && best != mc.player.getInventory().getSelectedHotbarSlot()) {
            if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedHotbarSlot();
            mc.player.getInventory().setSelectedHotbarSlot(best);
        }
    }

    private int pickScore(ItemStack s) {
        if (s.is(Items.NETHERITE_PICKAXE)) return 6;
        if (s.is(Items.DIAMOND_PICKAXE))   return 5;
        if (s.is(Items.IRON_PICKAXE))      return 4;
        if (s.is(Items.GOLDEN_PICKAXE))    return 3;
        if (s.is(Items.STONE_PICKAXE))     return 2;
        if (s.is(Items.WOODEN_PICKAXE))    return 1;
        return 0;
    }

    private Direction getClosestFace(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 c   = Vec3.atCenterOf(pos);
        Vec3 d   = eye.subtract(c);
        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        if (ax > ay && ax > az) return d.x > 0 ? Direction.EAST  : Direction.WEST;
        if (ay > az)            return d.y > 0 ? Direction.UP    : Direction.DOWN;
        return                         d.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
