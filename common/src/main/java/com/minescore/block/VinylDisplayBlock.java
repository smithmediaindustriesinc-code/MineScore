package com.minescore.block;

import com.minescore.item.CdData;
import com.minescore.registry.ModItems;
import com.minescore.wiring.ModelRotation;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Vinyl Display: like an item frame, but for Vinyl Records only. A half-record plaque that holds a record
 * halfway in a slot along its top, so the top half of the disc shows. It hangs on a wall, or stands on
 * the floor on a little foot. Right-click with a Vinyl Record to put it up, with an empty hand to take it.
 */
public class VinylDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Standing on the floor rather than hanging on a wall. */
    public static final BooleanProperty FLOOR = BooleanProperty.create("floor");

    /** Where the displayed record's centre sits in the north-facing model, in pixels, and how far it's in from the front. */
    public static final double CD_Y = 9;
    public static final double WALL_CD_Z = 14.5, FLOOR_CD_Z = 8;

    // The north-facing models in pixels, matching gen_resources.py.
    private static final AABB[] WALL = plaque(13, 16);
    private static final AABB[] FLOOR_STAND = append(plaque(6.5, 9.5), new AABB(4, 0, 5, 12, 2, 11));

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public VinylDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FLOOR, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            VoxelShape shape = Shapes.empty();
            for (AABB box : state.getValue(FLOOR) ? FLOOR_STAND : WALL) {
                shape = Shapes.or(shape, ModelRotation.shape(box, 0, ModelRotation.yRot(state.getValue(FACING))));
            }
            shapes.put(state, shape);
        }
    }

    private static AABB[] plaque(double z1, double z2) {
        return new AABB[] {new AABB(1, 6, z1, 15, 9, z2), new AABB(2, 4, z1, 14, 6, z2), new AABB(4, 2, z1, 12, 4, z2)};
    }

    private static AABB[] append(AABB[] boxes, AABB extra) {
        AABB[] all = java.util.Arrays.copyOf(boxes, boxes.length + 1);
        all[boxes.length] = extra;
        return all;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLOOR);
    }

    /** On a wall's side it hangs facing out from it; on top of a block it stands facing the player. Not on ceilings. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = face.getAxis().isHorizontal()
                ? defaultBlockState().setValue(FACING, face).setValue(FLOOR, false)
                : face == Direction.UP ? defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(FLOOR, true)
                : null;
        return state != null && state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = state.getValue(FLOOR) ? Direction.DOWN : state.getValue(FACING).getOpposite();
        BlockPos support = pos.relative(toSupport);
        return level.getBlockState(support).isFaceSturdy(level, support, toSupport.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                  BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof VinylDisplayBlockEntity display)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (display.disc().isEmpty()) {
            if (!held.is(ModItems.ENCODED_CD.get()) || CdData.read(held) == null) {
                return InteractionResult.PASS; // only written records go up
            }
            if (!level.isClientSide) {
                display.put(player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = display.take();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 15 while it shows a record, like an item frame with something in it. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VinylDisplayBlockEntity display && !display.disc().isEmpty() ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VinylDisplayBlockEntity display) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, display.take());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VinylDisplayBlockEntity(pos, state);
    }
}
