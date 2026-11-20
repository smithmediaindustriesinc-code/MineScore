package com.minescore.block;

import com.minescore.item.WireItem;
import com.minescore.wiring.ModelRotation;
import com.minescore.wiring.PendingCable;
import com.minescore.wiring.WireNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A break point that resets a cable's hang. It sticks out from whichever face it's placed on, and has a
 * dedicated input side (where cables arrive) and output side (where they leave). The input side faces
 * the player who placed it: straight at them on floors and ceilings, and toward their side on walls,
 * where it runs along the wall. Breaking it lets cables run straight past.
 */
public class CableAnchorBlock extends Block {
    /** The direction it sticks out, away from the face it's mounted on. */
    public static final DirectionProperty ATTACH = BlockStateProperties.FACING;
    /** The direction its input side faces. Always horizontal and never along {@link #ATTACH}. */
    public static final DirectionProperty INPUT = DirectionProperty.create("input", Direction.Plane.HORIZONTAL);

    // Model-space pieces and cable ends, in pixels. Floor model: sticks up, input faces north.
    private static final AABB[] FLOOR = {new AABB(6.5, 0, 6.5, 9.5, 4, 9.5), new AABB(7.25, 1.5, 4.5, 8.75, 3, 6.5),
        new AABB(7.25, 1.5, 9.5, 8.75, 3, 11.5)};
    private static final Vec3 FLOOR_IN = new Vec3(8, 2.25, 4.5);
    private static final Vec3 FLOOR_OUT = new Vec3(8, 2.25, 11.5);
    // Wall model: mounted on a wall to the south, sticks out north, input faces west.
    private static final AABB[] WALL = {new AABB(6.5, 6.5, 12, 9.5, 9.5, 16), new AABB(4.5, 7.25, 13, 6.5, 8.75, 14.5),
        new AABB(9.5, 7.25, 13, 11.5, 8.75, 14.5)};
    private static final Vec3 WALL_IN = new Vec3(4.5, 8, 13.75);
    private static final Vec3 WALL_OUT = new Vec3(11.5, 8, 13.75);

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public CableAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ATTACH, Direction.UP).setValue(INPUT, Direction.NORTH));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            Pose pose = pose(state);
            VoxelShape shape = Shapes.empty();
            for (AABB piece : pose.wall ? WALL : FLOOR) {
                shape = Shapes.or(shape, ModelRotation.shape(piece, pose.xRot, pose.yRot));
            }
            shapes.put(state, shape);
        }
    }

    /** How a state's model is rotated, matching the blockstate file. */
    record Pose(boolean wall, boolean mirrored, int xRot, int yRot) {}

    static Pose pose(BlockState state) {
        Direction attach = state.getValue(ATTACH);
        Direction input = state.getValue(INPUT);
        return switch (attach) {
            case UP -> new Pose(false, false, 0, ModelRotation.yRot(input));
            case DOWN -> new Pose(false, false, 180, (ModelRotation.yRot(input) + 180) % 360);
            // Wall model's input faces west, the attach direction's counter-clockwise side; mirror for the other side.
            default -> new Pose(true, input == attach.getClockWise(), 0, ModelRotation.yRot(attach));
        };
    }

    /** The cable input and output ends, in world coordinates. */
    public static Vec3[] ends(BlockState state, BlockPos pos) {
        Pose pose = pose(state);
        Vec3 in = pose.wall ? WALL_IN : FLOOR_IN;
        Vec3 out = pose.wall ? WALL_OUT : FLOOR_OUT;
        if (pose.mirrored) {
            Vec3 t = in;
            in = out;
            out = t;
        }
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        return new Vec3[] {
            origin.add(ModelRotation.apply(in, pose.xRot, pose.yRot).scale(1 / 16.0)),
            origin.add(ModelRotation.apply(out, pose.xRot, pose.yRot).scale(1 / 16.0)),
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACH, INPUT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attach = context.getClickedFace();
        Direction input;
        if (attach.getAxis().isVertical()) {
            input = context.getHorizontalDirection().getOpposite(); // toward the player
        } else {
            // Along the wall, on the player's side of the anchor.
            Direction side = attach.getClockWise();
            Vec3 toPlayer = context.getPlayer() != null
                    ? context.getPlayer().position().subtract(Vec3.atCenterOf(context.getClickedPos()))
                    : Vec3.ZERO;
            double along = toPlayer.x * side.getStepX() + toPlayer.z * side.getStepZ();
            input = along >= 0 ? side : side.getOpposite();
        }
        return defaultBlockState().setValue(ATTACH, attach).setValue(INPUT, input);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WireNetwork.get(serverLevel).removeAnchor(pos);
            // Cables still being laid through this anchor lose it too.
            for (ServerPlayer player : serverLevel.players()) {
                Inventory inventory = player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.getItem() instanceof WireItem) {
                        PendingCable pending = PendingCable.read(stack);
                        if (pending != null && pending.anchors().contains(pos)) {
                            pending.withoutAnchor(pos).write(stack);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
