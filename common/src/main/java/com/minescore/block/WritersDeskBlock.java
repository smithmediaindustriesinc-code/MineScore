package com.minescore.block;

import com.minescore.MineScore;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.wiring.Port;
import com.minescore.wiring.WireNetwork;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import com.minescore.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Writer's Desk (SCOPE.md §9.2): two desk blocks facing the same way, side by side, make one desk, the
 * halves mirror images: each a drawer pedestal at its outer end under the top, with knee space between. The
 * left half (as you face it) holds the slots and the jacks. A row of desks pairs off from the left; a desk
 * with no partner is a lone, unfinished half.
 */
public class WritersDeskBlock extends PortBlock implements EntityBlock {
    public enum Half implements StringRepresentable {
        SINGLE, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
    /** Furthest along a row of desks it looks to work out which half it is. */
    private static final int MAX_ROW = 32;
    private static final Map<Direction, VoxelShape> LEFT_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> RIGHT_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // North-facing: the top, a back panel, and a drawer pedestal at the outer end (the viewer's left is east).
            VoxelShape top = Shapes.or(Block.box(0, 13, 0, 16, 16, 16), turned(0, 1, 14, 16, 13, 16, facing));
            LEFT_SHAPES.put(facing, Shapes.or(top, turned(9, 0, 1, 16, 13, 15, facing)));
            RIGHT_SHAPES.put(facing, Shapes.or(top, turned(0, 0, 1, 7, 13, 15, facing)));
        }
    }

    public WritersDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HALF, Half.SINGLE));
    }

    /** A north-facing box in pixels, turned to face {@code facing}. */
    private static VoxelShape turned(double x1, double y1, double z1, double x2, double y2, double z2, Direction facing) {
        int turns = (facing.get2DDataValue() + 2) & 3; // quarter turns clockwise from north (2D data: south 0, west 1, north 2, east 3)
        for (int i = 0; i < turns; i++) {
            double nx1 = 16 - z2, nx2 = 16 - z1, nz1 = x1, nz2 = x2;
            x1 = nx1;
            x2 = nx2;
            z1 = nz1;
            z2 = nz2;
        }
        return Block.box(x1, y1, z1, x2, y2, z2);
    }

    /** As you face the desk: the side its partner is on when it's the left half. */
    static Direction toViewersRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    private boolean isDesk(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing;
    }

    /** Which half a desk is: count the desks to its left in its row, then pair off. */
    private Half halfAt(BlockGetter level, BlockPos pos, Direction facing) {
        Direction left = toViewersRight(facing).getOpposite();
        int before = 0;
        while (before < MAX_ROW && isDesk(level, pos.relative(left, before + 1), facing)) {
            before++;
        }
        if (before % 2 == 1) {
            return Half.RIGHT;
        }
        return isDesk(level, pos.relative(toViewersRight(facing)), facing) ? Half.LEFT : Half.SINGLE;
    }

    /** The pedestal half a desk block belongs to: itself, or for a right half the block to its left. */
    public static BlockPos pedestal(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == Half.RIGHT ? pos.relative(toViewersRight(state.getValue(FACING)).getOpposite()) : pos;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(HALF, halfAt(context.getLevel(), context.getClickedPos(), state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
                                  BlockPos neighbourPos) {
        return state.setValue(HALF, halfAt(level, pos, state.getValue(FACING)));
    }

    /** Becoming a right half: it has no slots or jacks of its own any more, so what it held comes out. */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel serverLevel && old.is(this) && old.getValue(HALF) != Half.RIGHT && state.getValue(HALF) == Half.RIGHT) {
            dropContents(level, pos);
            WireNetwork.get(serverLevel).removeBlock(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == Half.RIGHT ? RIGHT_SHAPES : LEFT_SHAPES).get(state.getValue(FACING));
    }

    /** The right half has no jacks. */
    @Override
    public Optional<Port> pick(BlockState state, BlockPos pos, Vec3 eye, Vec3 look) {
        return state.getValue(HALF) == Half.RIGHT ? Optional.empty() : super.pick(state, pos, eye, look);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Item held = player.getItemInHand(hand).getItem();
        if (held instanceof WireItem || held instanceof WireCuttersItem) {
            return InteractionResult.PASS; // let the wire tool work on the jacks
        }
        BlockPos pedestal = pedestal(state, pos);
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pedestal) instanceof WritersDeskBlockEntity desk) {
            MineScore.platform().openMenu(serverPlayer, desk, pedestal);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WritersDeskBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.WRITERS_DESK.get() || state.getValue(HALF) == Half.RIGHT) {
            return null; // only the half with the slots and jacks plays
        }
        return (l, p, s, be) -> WritersDeskBlockEntity.serverTick(l, p, s, (WritersDeskBlockEntity) be);
    }

    private static void dropContents(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WritersDeskBlockEntity desk) {
            for (int i = 0; i < desk.items().getContainerSize(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), desk.items().removeItemNoUpdate(i));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.minescore.playback.PlaybackManager.get(serverLevel).stop(pos);
            }
            dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
