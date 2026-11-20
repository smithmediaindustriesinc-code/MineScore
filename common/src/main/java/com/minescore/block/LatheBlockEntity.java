package com.minescore.block;

import com.minescore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The Disc Cutting Lathe without Create: FE powered. Its workings are in {@link LatheCore}. */
public class LatheBlockEntity extends BlockEntity implements LatheHost {
    private final LatheCore lathe = new LatheCore(this);

    public LatheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISC_CUTTING_LATHE.get(), pos, state);
    }

    @Override
    public LatheCore lathe() {
        return lathe;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        lathe.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lathe.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        lathe.saveForClient(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
