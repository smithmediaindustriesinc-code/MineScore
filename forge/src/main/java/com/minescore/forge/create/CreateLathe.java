package com.minescore.forge.create;

import com.minescore.block.LatheBlock;
import com.minescore.power.KineticSettings;
import com.minescore.registry.ModBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * MineScore's Create integration (SCOPE.md §2.4, §15). Only called when Create is installed, so none of
 * Create's classes load without it: callers see plain MineScore and Minecraft types only.
 */
public final class CreateLathe {
    private CreateLathe() {}

    public static LatheBlock block(BlockBehaviour.Properties properties, VoxelShape shape) {
        return new CreateLatheBlock(properties, shape);
    }

    public static BiFunction<BlockPos, BlockState, BlockEntity> blockEntity() {
        return CreateLatheBlockEntity::new;
    }

    /** The Lathe puts 1 SU per RPM on its network by default ("stressPerRpm" in the common config). */
    public static void registerStress() {
        BlockStressValues.IMPACTS.register(ModBlocks.DISC_CUTTING_LATHE.get(), () -> KineticSettings.stressPerRpm.getAsDouble());
    }
}
