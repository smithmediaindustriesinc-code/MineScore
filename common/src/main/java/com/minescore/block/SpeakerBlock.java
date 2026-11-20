package com.minescore.block;

import com.minescore.client.ClientHooks;
import com.minescore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A speaker. Right-click with an empty hand for its screen; anything held (wire, an MP3 Player) does its own thing.
 *
 * <p>Stacking (2026-11-20): a Middle Speaker or Tweeter standing on a Middle Speaker, or a Tweeter on a Tweeter,
 * sinks down to sit flush on it, with no gap. {@link #DROP} is how far, in 4-pixel steps; its model, shape, jacks
 * and sound all move with it. A column builds up the drop speaker by speaker, up to a whole block.
 */
public class SpeakerBlock extends PortBlock implements EntityBlock {
    public static final int DROP_STEP = 4, MAX_DROP = 4;
    public static final IntegerProperty DROP = IntegerProperty.create("drop", 0, MAX_DROP);

    public SpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DROP, 0));
    }

    public SpeakerBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(DROP, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DROP);
    }

    /** How tall the speaker's body is, in pixels. */
    public static int height(BlockState state) {
        return state.is(ModBlocks.MIDDLE_SPEAKER.get()) ? 12 : state.is(ModBlocks.TWEETER_SPEAKER.get()) ? 8 : 16;
    }

    /** How far {@code self} sinks to sit flush on what's under it, in steps (0 when it doesn't stack). */
    public static int dropFor(BlockGetter level, BlockPos pos, BlockState self) {
        BlockState below = level.getBlockState(pos.below());
        boolean onMiddle = below.is(ModBlocks.MIDDLE_SPEAKER.get())
                && (self.is(ModBlocks.MIDDLE_SPEAKER.get()) || self.is(ModBlocks.TWEETER_SPEAKER.get()));
        boolean tweeterOnTweeter = below.is(ModBlocks.TWEETER_SPEAKER.get()) && self.is(ModBlocks.TWEETER_SPEAKER.get());
        if (!onMiddle && !tweeterOnTweeter) {
            return 0;
        }
        int gap = 16 - (height(below) - DROP_STEP * below.getValue(DROP)); // from the top of the one below to this cell's floor
        return gap / DROP_STEP <= MAX_DROP ? gap / DROP_STEP : 0; // past a whole block, start a fresh column
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(DROP, dropFor(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                  BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return direction == Direction.DOWN && updated.is(this) ? updated.setValue(DROP, dropFor(level, pos, updated)) : updated;
    }

    @Override
    public double yOffset(BlockState state) {
        return -DROP_STEP * state.getValue(DROP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = super.getShape(state, level, pos, context);
        return state.getValue(DROP) == 0 ? shape : shape.move(0, yOffset(state) / 16, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            ClientHooks.get().openSpeaker(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }
}
