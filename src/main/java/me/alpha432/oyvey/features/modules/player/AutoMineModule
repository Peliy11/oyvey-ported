package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.setting.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoMineModule extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────
    public final Setting<Mode> mode = register(
            new Setting<>("Mode", Mode.TUNNEL_1x2,
                    "Mining pattern to use"));

    public final Setting<Boolean> autoTool = register(
            new Setting<>("AutoTool", true,
                    "Automatically switch to best pickaxe"));

    public final Setting<Boolean> autoWalk = register(
            new Setting<>("AutoWalk", true,
                    "Automatically walk forward while mining"));

    public final Setting<Boolean> collectDrops = register(
            new Setting<>("CollectDrops", true,
                    "Walk into drops to collect them"));

    public final Setting<Integer> delay = register(
            new Setting<>("Delay", 0, 0, 10,
                    "Ticks between each block break"));

    // ── Mining modes ───────────────────────────────────────────────────────────
    public enum Mode {
        TUNNEL_1x2,   // standard 1 wide 2 tall forward tunnel
        TUNNEL_2x2,   // 2 wide 2 tall tunnel
        TUNNEL_3x3,   // full 3x3 tunnel
        STAIRCASE,    // diagonal staircase downward
        FLAT,         // mine only the block directly in front (1x1)
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private int delayTimer  = 0;
    private int savedSlot   = -1;

    public AutoMineModule() {
        super("AutoMine", "Automatically mines forward in the chosen pattern.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        // Restore original hotbar slot when disabled
        if (savedSlot != -1) {
            mc.player.getInventory().selectedSlot = savedSlot;
            savedSlot = -1;
        }
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        ClientPlayerEntity player = mc.player;

        // ── Delay between breaks ───────────────────────────────────────────────
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // ── Get list of blocks to mine this tick ───────────────────────────────
        List<BlockPos> targets = getTargetBlocks();
        if (targets.isEmpty()) {
            // Nothing to mine — walk forward if enabled
            if (autoWalk.getValue()) {
                mc.options.forwardKey.setPressed(true);
            }
            return;
        }

        // ── Pick best tool ─────────────────────────────────────────────────────
        if (autoTool.getValue()) {
            equipBestPickaxe();
        }

        // ── Walk forward while mining ──────────────────────────────────────────
        if (autoWalk.getValue()) {
            mc.options.forwardKey.setPressed(true);
        }

        // ── Mine each target block ─────────────────────────────────────────────
        for (BlockPos pos : targets) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getBlock() == Blocks.BEDROCK) continue;

            // Look at the block face closest to the player
            Direction face = getClosestFace(pos);

            // Send attack packet (start mining)
            mc.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                    net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    pos, face
                )
            );

            // Instantly finish mining (creative-style break)
            mc.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                    net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    pos, face
                )
            );

            // Swing arm visually
            player.swingHand(Hand.MAIN_HAND);
        }

        delayTimer = delay.getValue();
    }

    // ── Build list of blocks to mine based on selected mode ────────────────────
    private List<BlockPos> getTargetBlocks() {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos feet = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();

        switch (mode.getValue()) {

            case FLAT -> {
                // Just the one block directly in front at eye level
                positions.add(feet.offset(facing));
            }

            case TUNNEL_1x2 -> {
                // 1 wide, 2 tall — standard tunnel
                positions.add(feet.offset(facing));               // foot level
                positions.add(feet.offset(facing).up());          // head level
            }

            case TUNNEL_2x2 -> {
                // 2 wide, 2 tall
                Direction left = facing.rotateYCounterclockwise();
                positions.add(feet.offset(facing));
                positions.add(feet.offset(facing).up());
                positions.add(feet.offset(facing).offset(left));
                positions.add(feet.offset(facing).offset(left).up());
            }

            case TUNNEL_3x3 -> {
                // 3 wide, 3 tall — full 3x3 around player
                Direction left  = facing.rotateYCounterclockwise();
                Direction right = facing.rotateYClockwise();
                for (int y = -1; y <= 1; y++) {
                    for (Direction side : new Direction[]{left, facing, right}) {
                        BlockPos base = feet.offset(facing).offset(side != facing ? side : facing, side == facing ? 0 : 1);
                        // Simpler approach: iterate all 9 positions
                    }
                }
                // Build 3x3 manually
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos offset = feet.offset(facing)
                                .offset(facing.rotateYCounterclockwise(), dx < 0 ? -dx : 0)
                                .offset(facing.rotateYClockwise(), dx > 0 ? dx : 0)
                                .up(dy > 0 ? dy - 1 : 0).down(dy == 0 ? 1 : 0);
                        positions.add(offset);
                    }
                }
            }

            case STAIRCASE -> {
                // Mine one block forward and one block down-forward to go down diagonally
                positions.add(feet.offset(facing));               // forward at feet
                positions.add(feet.offset(facing).up());          // forward at head
                positions.add(feet.offset(facing).down());        // floor in front
                // Also clear the stair step
                positions.add(feet.down().offset(facing));
            }
        }

        // Filter out air, bedrock and blocks that are already broken
        positions.removeIf(pos -> {
            BlockState state = mc.world.getBlockState(pos);
            return state.isAir()
                || state.getBlock() == Blocks.BEDROCK
                || state.getHardness(mc.world, pos) < 0; // unbreakable
        });

        // Sort by distance to player so closest block is mined first
        Vec3d playerPos = mc.player.getPos();
        positions.sort(Comparator.comparingDouble(pos ->
                playerPos.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));

        return positions;
    }

    // ── Equip the best pickaxe in hotbar ───────────────────────────────────────
    private void equipBestPickaxe() {
        int bestSlot  = -1;
        int bestScore = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int score = getPickaxeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot  = i;
            }
        }

        if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
            if (savedSlot == -1) savedSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    // Higher = better pickaxe
    private int getPickaxeScore(ItemStack stack) {
        if (stack.getItem() == Items.NETHERITE_PICKAXE) return 6;
        if (stack.getItem() == Items.DIAMOND_PICKAXE)   return 5;
        if (stack.getItem() == Items.IRON_PICKAXE)      return 4;
        if (stack.getItem() == Items.GOLDEN_PICKAXE)    return 3;
        if (stack.getItem() == Items.STONE_PICKAXE)     return 2;
        if (stack.getItem() == Items.WOODEN_PICKAXE)    return 1;
        return 0;
    }

    // Get the face of the block closest to the player
    private Direction getClosestFace(BlockPos pos) {
        Vec3d player = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d diff   = player.subtract(center);

        double ax = Math.abs(diff.x);
        double ay = Math.abs(diff.y);
        double az = Math.abs(diff.z);

        if (ax > ay && ax > az) return diff.x > 0 ? Direction.EAST  : Direction.WEST;
        if (ay > ax && ay > az) return diff.y > 0 ? Direction.UP    : Direction.DOWN;
        return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
