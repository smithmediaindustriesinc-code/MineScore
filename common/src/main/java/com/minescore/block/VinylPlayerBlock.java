package com.minescore.block;

import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.registry.ModBlockEntities;
import com.minescore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * The Vinyl Player. Right-click with a Vinyl Record to play it; right-click with an empty hand to take it
 * off. On its own it plays from itself; wired to speakers it plays through them. A comparator reads 15
 * while it plays.
 */
public class VinylPlayerBlock extends PortBlock implements EntityBlock {
    public VinylPlayerBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        Item item = held.getItem();
        if (item instanceof WireItem || item instanceof WireCuttersItem
                || !(level.getBlockEntity(pos) instanceof VinylPlayerBlockEntity vinyl)) {
            return InteractionResult.PASS;
        }
        if (vinyl.disc().isEmpty() && held.is(ModItems.ENCODED_CD.get())) {
            if (!level.isClientSide) {
                vinyl.insert(held);
                com.minescore.ModAdvancements.award(player, "throwback");
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!vinyl.disc().isEmpty() && held.isEmpty()) {
            if (!level.isClientSide) {
                vinyl.eject(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VinylPlayerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.VINYL_PLAYER.get()) {
            return null;
        }
        return (l, p, s, be) -> VinylPlayerBlockEntity.serverTick(l, p, s, (VinylPlayerBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VinylPlayerBlockEntity vinyl && vinyl.playing() ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VinylPlayerBlockEntity vinyl) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), vinyl.takeDisc());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
