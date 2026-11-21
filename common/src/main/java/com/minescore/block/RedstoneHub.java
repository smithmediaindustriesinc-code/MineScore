package com.minescore.block;

import com.minescore.MineScoreSettings;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hub's redstone (SCOPE.md §7): inputs, which only count while its Redstone Control toggle is on and it
 * feeds speakers, and comparator outputs, which always work and can differ by face.
 *
 * <p>Left and right are the block's own, as if you were the block looking out of its front. "Strong" power
 * is what a repeater, comparator, torch, lever or button gives; redstone dust only gives weak power.
 */
public interface RedstoneHub {
    boolean redstoneControl();

    void setRedstoneControl(boolean on);

    /** Its neighbours' signals may have changed: read them again (server only). */
    void readRedstone();

    /** What a comparator reading {@code face} of it gets, 0–15. */
    int comparatorSignal(Direction face);

    /** Whether newly placed hubs start with Redstone Control on (S8: off, configurable). */
    static boolean controlByDefault() {
        return MineScoreSettings.redstoneControlByDefault.getAsBoolean();
    }

    static Direction front(BlockState state) {
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }

    static Direction left(BlockState state) {
        return front(state).getCounterClockWise();
    }

    static Direction right(BlockState state) {
        return front(state).getClockWise();
    }

    /** The signal coming into {@code pos} from its {@code side}, weak or strong. */
    static int signalFrom(Level level, BlockPos pos, Direction side) {
        return level.getSignal(pos.relative(side), side);
    }

    /** Whether strong power comes into {@code pos} from its {@code side}. */
    static boolean strongFrom(Level level, BlockPos pos, Direction side) {
        BlockPos from = pos.relative(side);
        BlockState state = level.getBlockState(from);
        return !(state.getBlock() instanceof RedStoneWireBlock) && state.getDirectSignal(level, from, side) > 0;
    }

    /** Redstone only does anything on hubs that feed speakers. */
    static boolean feedsSpeakers(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel
                && !SpeakerSystems.speakers(WireNetwork.get(serverLevel)::endsAt, pos).isEmpty();
    }

    /** The strongest comparator signal on any face, for anything that asks without saying which. */
    default int strongestComparatorSignal() {
        int best = 0;
        for (Direction face : Direction.values()) {
            best = Math.max(best, comparatorSignal(face));
        }
        return best;
    }

    /** Called by the hub blocks when a neighbour changes. */
    static void neighbourChanged(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RedstoneHub hub) {
            hub.readRedstone();
            if (hub.redstoneControl() && level.hasNeighborSignal(pos)) {
                com.minescore.ModAdvancements.awardNear(level, pos, "redstone_dj");
            }
        }
    }
}
