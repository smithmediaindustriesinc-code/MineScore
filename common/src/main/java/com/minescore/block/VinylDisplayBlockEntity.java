package com.minescore.block;

import com.minescore.item.CdItem;
import com.minescore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Vinyl Record a Vinyl Display shows, sent to clients so they can draw it. */
public class VinylDisplayBlockEntity extends BlockEntity {
    /** The empty display's label colour; matches tools/textures.py DISPLAY_LABEL. */
    public static final int DEFAULT_LABEL = 0xBE323C;

    private ItemStack disc = ItemStack.EMPTY;

    public VinylDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VINYL_DISPLAY.get(), pos, state);
    }

    public ItemStack disc() {
        return disc;
    }

    public void put(ItemStack stack) {
        disc = stack;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1, 1.1f);
        }
        changed();
    }

    public ItemStack take() {
        ItemStack out = disc;
        disc = ItemStack.EMPTY;
        if (!out.isEmpty() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1, 1.1f);
        }
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!disc.isEmpty()) {
            tag.put("Disc", disc.save(new CompoundTag()));
        }
        // Always write something: Minecraft drops an empty update, so clients would never see the record come out.
        tag.putBoolean("Shown", !disc.isEmpty());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        disc = tag.contains("Disc") ? ItemStack.of(tag.getCompound("Disc")) : ItemStack.EMPTY;
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8); // redraw its label in the record's colour
        }
    }

    /** The colour its label shows: the dye of the record in it, or its own red when empty. */
    public int labelColor() {
        return disc.getItem() instanceof CdItem record ? record.getColor(disc) : DEFAULT_LABEL;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
