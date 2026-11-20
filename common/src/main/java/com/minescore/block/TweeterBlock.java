package com.minescore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Tweeter Speaker can face any of eight directions, 45° apart: {@code facing} plus, when
 * {@code diagonal} is set, another 45° clockwise (so north + diagonal faces north-east).
 */
public class TweeterBlock extends SpeakerBlock {
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    private static final AABB BODY = new AABB(4, 0, 4, 12, 8, 12);
    private static final Direction[] STRAIGHT = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final VoxelShape straight = Shapes.create(scaled(BODY));
    private final VoxelShape diagonal = Shapes.create(scaled(rotatedBounds(BODY, 45)));

    public TweeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DIAGONAL, false));
    }

    private static AABB scaled(AABB px) {
        return new AABB(px.minX / 16, px.minY / 16, px.minZ / 16, px.maxX / 16, px.maxY / 16, px.maxZ / 16);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIAGONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face back at the player, snapped to 45°. A yaw of y degrees looks y degrees clockwise from south,
        // so the front's angle clockwise from north equals the yaw.
        int step = Mth.floor(context.getRotation() / 45 + 0.5) & 7; // 0 = north, 1 = north-east, ...
        Direction facing = STRAIGHT[step / 2];
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(DIAGONAL, (step & 1) == 1);
        return state.setValue(DROP, dropFor(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    public double yAngle(BlockState state) {
        return super.yAngle(state) + (state.getValue(DIAGONAL) ? 45 : 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(DIAGONAL) ? diagonal : straight;
        return state.getValue(DROP) == 0 ? shape : shape.move(0, yOffset(state) / 16, 0);
    }
}
