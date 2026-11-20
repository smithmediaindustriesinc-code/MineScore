package com.minescore.block;

import com.minescore.client.ClientHooks;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Audio Mixer. Right-click with an MP3 Player to dock it; right-click otherwise to open the docked
 * player's screen (with the Mixer tab).
 */
public class MixerBlock extends PortBlock implements EntityBlock {
    /** An MP3 Player is in the dock: the front shows it, its screen lit or not with the system's power. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty DOCKED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("docked");

    public MixerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HubPower.POWERED, false).setValue(DOCKED, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        Item item = held.getItem();
        if (item instanceof WireItem || item instanceof WireCuttersItem) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof MixerBlockEntity mixer)) {
            return InteractionResult.PASS;
        }
        if (!mixer.docked()) {
            if (item instanceof Mp3PlayerItem) {
                if (!level.isClientSide) {
                    mixer.dock(held);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("msg.minescore.mixer.dock"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            ClientHooks.get().openMixer(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MixerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.AUDIO_MIXER.get()) {
            return null;
        }
        return (l, p, s, be) -> MixerBlockEntity.serverTick(l, p, s, (MixerBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MixerBlockEntity mixer) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), mixer.takeForDrop());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HubPower.POWERED, DOCKED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        RedstoneHub.neighbourChanged(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparators read each face separately (see ComparatorBlockMixin); this is for anything that doesn't ask. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RedstoneHub hub ? hub.strongestComparatorSignal() : 0;
    }
}
