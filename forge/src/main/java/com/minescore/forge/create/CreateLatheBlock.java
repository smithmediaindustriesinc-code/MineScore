package com.minescore.forge.create;

import com.minescore.block.LatheBlock;
import com.minescore.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Disc Cutting Lathe when Create is installed: everything the plain one does, and it takes rotation
 * from a shaft or cog underneath it, the way its platter turns (SCOPE.md §3.1). The hooks below are the
 * ones Create's own kinetic blocks use to join and leave a rotation network.
 */
public class CreateLatheBlock extends LatheBlock implements IRotate {
    public CreateLatheBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreateLatheBlockEntity(pos, state);
    }

    /** Kinetic block entities tick on both sides (the client shows rotation effects). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type != ModBlockEntities.DISC_CUTTING_LATHE.get() ? null : (l, p, s, be) -> ((CreateLatheBlockEntity) be).tick();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // As Create's KineticBlock: a change that keeps the same connections needn't re-propagate rotation.
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) {
            kinetic.preventSpeedUpdate = 0;
            if (oldState.getBlock() == state.getBlock() && state.hasBlockEntity() == oldState.hasBlockEntity()) {
                kinetic.preventSpeedUpdate = 2;
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int count) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) || kinetic.preventSpeedUpdate > 0) {
            return;
        }
        kinetic.warnOfMovement();
        kinetic.clearKineticInformation();
        kinetic.updateSpeed = true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CreateLatheBlockEntity lathe) {
            lathe.queueRotationIndicators();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SmartBlockEntity smart) {
            smart.destroy(); // leaves its rotation network
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
