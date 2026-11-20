package com.minescore.block;

import com.minescore.MineScore;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Disc Cutting Lathe: a wired block with an inventory and a cutting screen. With Create installed the
 * loader module registers a subclass that also takes rotation.
 */
public class LatheBlock extends PortBlock implements EntityBlock {
    public LatheBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Item held = player.getItemInHand(hand).getItem();
        if (held instanceof WireItem || held instanceof WireCuttersItem) {
            return InteractionResult.PASS; // let the wire tool work on the jacks
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LatheHost lathe) {
            MineScore.platform().openMenu(serverPlayer, lathe.lathe(), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LatheBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.DISC_CUTTING_LATHE.get()) {
            return null;
        }
        return (l, p, s, be) -> ((LatheHost) be).lathe().serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LatheHost lathe) {
            lathe.lathe().contents().forEach(stack -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
