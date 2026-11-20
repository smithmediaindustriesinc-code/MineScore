package com.minescore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Music Cabinet (SCOPE.md §3.11): a walnut cabinet with an open front, 16 Scores on its top shelf and 8 Vinyl
 * Records in sleeves on its bottom one. Right-click the front with a Score or record to file it at the left of
 * its shelf's free places; Shift+right-click one with an empty hand to take it, and the rest close up. Its
 * blockstate counts what's on each shelf, so its model shows how full it is.
 */
public class MusicCabinetBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final int SCORE_SLOTS = 16, VINYL_SLOTS = 8;
    public static final IntegerProperty SCORES = IntegerProperty.create("scores", 0, SCORE_SLOTS);
    public static final IntegerProperty VINYLS = IntegerProperty.create("vinyls", 0, VINYL_SLOTS);

    // The north-facing model in pixels, matching gen_resources.py. The front is at z = 0, so the viewer's
    // left is the high-x side and places count from there.
    public static final double INNER_LEFT = 14.5, INNER_RIGHT = 1.5;
    public static final double SCORE_FLOOR = 8.25, SCORE_CEILING = 14.5, SCORE_WIDTH = (INNER_LEFT - INNER_RIGHT) / SCORE_SLOTS;
    public static final double VINYL_FLOOR = 1.5, VINYL_CEILING = 7.25, VINYL_TOP = 7.0, VINYL_WIDTH = (INNER_LEFT - INNER_RIGHT) / VINYL_SLOTS;

    public MusicCabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SCORES, 0).setValue(VINYLS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCORES, VINYLS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** How tall the Score in place {@code index} stands, in pixels: a little uneven, like real folders. Matches gen_resources.py. */
    public static double spineTop(int index) {
        return 13.0 + (index * 7 % 5) * 0.25;
    }

    /** One place on a shelf: the top shelf's Scores, or the bottom shelf's records, counted from the viewer's left. */
    public record Spot(boolean score, int index) {
        /** Where its Score or sleeve sits in the north-facing model, in pixels. */
        public AABB box() {
            double width = score ? SCORE_WIDTH : VINYL_WIDTH;
            double x2 = INNER_LEFT - index * width;
            return score ? new AABB(x2 - width, SCORE_FLOOR, 2, x2, spineTop(index), 13.5)
                    : new AABB(x2 - width, VINYL_FLOOR, 1.5, x2, VINYL_TOP, 13.5);
        }
    }

    /** The place a click on the cabinet's front points at, or null off the shelves or on another face. */
    public static @Nullable Spot spotAt(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
        Direction facing = state.getValue(FACING);
        if (face != facing) {
            return null;
        }
        Direction right = facing.getCounterClockWise(); // the viewer's right, looking at the front
        double along = right.getAxis() == Direction.Axis.X ? hit.x - pos.getX() : hit.z - pos.getZ();
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            along = 1 - along;
        }
        double fromLeft = along * 16 - (16 - INNER_LEFT);
        double y = (hit.y - pos.getY()) * 16;
        if (fromLeft < 0 || fromLeft >= INNER_LEFT - INNER_RIGHT) {
            return null;
        }
        if (y >= SCORE_FLOOR && y < SCORE_CEILING) {
            return new Spot(true, Math.min(SCORE_SLOTS - 1, (int) (fromLeft / SCORE_WIDTH)));
        }
        if (y >= VINYL_FLOOR && y < VINYL_CEILING) {
            return new Spot(false, Math.min(VINYL_SLOTS - 1, (int) (fromLeft / VINYL_WIDTH)));
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING) || !(level.getBlockEntity(pos) instanceof MusicCabinetBlockEntity cabinet)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            // Only Shift+right-click with an empty hand takes one out; a plain click does nothing.
            Spot spot = spotAt(state, pos, hit.getLocation(), hit.getDirection());
            if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND || spot == null || !cabinet.has(spot)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                ItemStack out = cabinet.take(spot);
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        Boolean score = MusicCabinetBlockEntity.shelfFor(held);
        if (score == null) {
            return InteractionResult.PASS; // nothing blank, and nothing that isn't music
        }
        if (cabinet.isFull(score)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.minescore.music_cabinet.full"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            cabinet.add(player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** How full it is, 0 to 15 across all 24 places. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        int held = state.getValue(SCORES) + state.getValue(VINYLS);
        return (int) Math.ceil(held * 15.0 / (SCORE_SLOTS + VINYL_SLOTS));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MusicCabinetBlockEntity cabinet) {
            for (ItemStack stack : cabinet.empty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MusicCabinetBlockEntity(pos, state);
    }
}
