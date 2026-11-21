package com.minescore.platform;

import com.minescore.block.LatheBlock;
import com.minescore.block.LatheBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import java.util.function.BiFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Loader-specific bits that have no vanilla equivalent. */
public interface PlatformHelper {
    /** A menu type whose client side is built from extra data the server sends (the block's position). */
    <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory);

    /** Opens a block's menu, sending its position along so the client can find the block. */
    void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);

    /** Registers an advancement criterion (vanilla keeps CriteriaTriggers.register private; loaders open it). */
    void registerTrigger(net.minecraft.advancements.CriterionTrigger<?> trigger);

    /** A block entity type (vanilla's builder isn't public outside the loaders). */
    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    /** The Disc Cutting Lathe: plain, or one that takes rotation when Create is installed (SCOPE.md §2.4). */
    default LatheBlock latheBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        return new LatheBlock(properties, shape);
    }

    /** The block entity to match {@link #latheBlock}. */
    default BiFunction<BlockPos, BlockState, BlockEntity> latheBlockEntity() {
        return LatheBlockEntity::new;
    }

    @FunctionalInterface
    interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int id, Inventory inventory, FriendlyByteBuf extra);
    }
}
