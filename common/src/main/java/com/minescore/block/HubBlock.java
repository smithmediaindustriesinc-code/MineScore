package com.minescore.block;

import com.minescore.client.ClientHooks;
import com.minescore.hub.HubControl;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

/** The Amplifier and the Channel Manager: right-click (with anything but a wire tool) for their screen. */
public class HubBlock extends PortBlock implements EntityBlock {
    public enum Type { AMPLIFIER, CHANNEL_MANAGER }

    private final Type type;

    public HubBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
        registerDefaultState(defaultBlockState().setValue(HubPower.POWERED, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Item held = player.getItemInHand(hand).getItem();
        if (held instanceof WireItem || held instanceof WireCuttersItem || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        switch (type) {
            case AMPLIFIER -> {
                if (level.isClientSide) {
                    ClientHooks.get().openAmplifier(pos);
                }
            }
            case CHANNEL_MANAGER -> {
                if (player instanceof ServerPlayer serverPlayer) {
                    HubControl.sendChannels(serverPlayer, pos, true); // the screen opens when its speaker list arrives
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (type) {
            case AMPLIFIER -> new AmplifierBlockEntity(pos, state);
            case CHANNEL_MANAGER -> new ChannelManagerBlockEntity(pos, state);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HubPower.POWERED);
    }

    /** Once a second: lit or dark, as its system's power says. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> HubPower.tick(l, p, s);
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
