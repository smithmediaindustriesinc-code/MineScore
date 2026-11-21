package com.minescore.block;

import com.minescore.item.CdData;
import com.minescore.playback.Playback;
import com.minescore.playback.PlaybackManager;
import com.minescore.power.PowerSource;
import com.minescore.power.SourcePower;
import com.minescore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Vinyl Player: holds one Vinyl Record and plays it once through, then stops (the record stays on). It needs
 * no power itself, but its speakers draw through it (SCOPE.md §3.1).
 */
public class VinylPlayerBlockEntity extends BlockEntity implements PowerSource {
    private final SourcePower power = new SourcePower();
    private ItemStack disc = ItemStack.EMPTY;
    private boolean playing;
    private long startTick;

    public VinylPlayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VINYL_PLAYER.get(), pos, state);
    }

    public ItemStack disc() {
        return disc;
    }

    @Override
    public SourcePower power() {
        return power;
    }

    public boolean playing() {
        return playing;
    }

    /** Puts a record on and starts playing it. */
    public void insert(ItemStack stack) {
        CdData data = CdData.read(stack);
        if (data == null || !disc.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!PlaybackManager.get(serverLevel).recover(worldPosition)) {
            for (var player : serverLevel.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < 64) {
                    player.displayClientMessage(Component.translatable(PlaybackManager.shortResetMessage()), true);
                }
            }
            return; // still recovering from a short
        }
        disc = stack.split(1);
        playing = true;
        startTick = level.getGameTime() + PlaybackManager.START_DELAY;
        PlaybackManager.get(serverLevel).start(Playback.Kind.VINYL, worldPosition, data.song().trackId(), startTick, 1, disc);
        com.minescore.audio.SynthSound.NEEDLE_DROP.play(serverLevel, worldPosition);
        changed();
    }

    /** Takes the record off and hands it to the player. */
    public void eject(Player player) {
        if (disc.isEmpty()) {
            return;
        }
        ItemStack out = takeDisc();
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
    }

    public ItemStack takeDisc() {
        ItemStack out = disc;
        disc = ItemStack.EMPTY;
        stop();
        return out;
    }

    /** A speaker it fed shorted: the record stops, and stays on the platter until taken off. */
    public void halt() {
        stop();
    }

    private void stop() {
        playing = false;
        if (level instanceof ServerLevel serverLevel) {
            PlaybackManager.get(serverLevel).stop(worldPosition);
        }
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VinylPlayerBlockEntity player) {
        if (level instanceof ServerLevel serverLevel && player.power.tick(serverLevel, pos, player.playing)) {
            PlaybackManager.get(serverLevel).refresh();
        }
        if (!player.playing || level.getGameTime() % 10 != 0) {
            return;
        }
        CdData data = CdData.read(player.disc);
        if (data == null || level.getGameTime() > player.startTick + Math.round(data.song().seconds() * 20) + 10) {
            player.stop(); // the record has finished
        }
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
        tag.putBoolean("Playing", playing);
        tag.putLong("StartTick", startTick);
        power.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        disc = tag.contains("Disc") ? ItemStack.of(tag.getCompound("Disc")) : ItemStack.EMPTY;
        playing = tag.getBoolean("Playing");
        startTick = tag.getLong("StartTick");
        power.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
