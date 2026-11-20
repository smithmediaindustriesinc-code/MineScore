package com.minescore.block;

import com.minescore.mp3.Mp3Packets;
import com.minescore.mp3.Mp3Server;
import com.minescore.mp3.Mp3State;
import com.minescore.playback.Playback;
import com.minescore.playback.PlaybackManager;
import com.minescore.power.PowerSource;
import com.minescore.power.SourcePower;
import com.minescore.registry.ModBlockEntities;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The Audio Mixer: plays the MP3 Player docked in it through its speaker system, and takes the system's power. */
public class MixerBlockEntity extends BlockEntity implements PowerSource, RedstoneHub {
    private static final Random RANDOM = new Random();
    private final SourcePower power = new SourcePower();
    private ItemStack mp3 = ItemStack.EMPTY;
    private boolean redstoneControl = RedstoneHub.controlByDefault();
    /** The signals last seen: play (front, back, top, bottom), left and right. Pulses act on their rising edge. */
    private boolean playSignal, leftSignal, rightSignal;
    /** Volume past 100%, which only an Amplifier in the system can deliver (SCOPE.md §3.5). */
    private float boost = 1;

    public MixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUDIO_MIXER.get(), pos, state);
    }

    public ItemStack mp3() {
        return mp3;
    }

    @Override
    public SourcePower power() {
        return power;
    }

    @Override
    public boolean redstoneControl() {
        return redstoneControl;
    }

    @Override
    public void setRedstoneControl(boolean on) {
        redstoneControl = on;
        changed();
    }

    /**
     * SCOPE.md §6.1: a signal on the front, back, top or bottom plays (if anything is queued) and pauses when
     * it goes; a pulse on the left skips ahead; a pulse on the right restarts the song, or goes back one if
     * it's under 2 s in.
     */
    @Override
    public void readRedstone() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = getBlockState();
        Direction front = RedstoneHub.front(state);
        boolean play = false;
        for (Direction side : new Direction[] {front, front.getOpposite(), Direction.UP, Direction.DOWN}) {
            play |= RedstoneHub.signalFrom(level, worldPosition, side) > 0;
        }
        boolean left = RedstoneHub.signalFrom(level, worldPosition, RedstoneHub.left(state)) > 0;
        boolean right = RedstoneHub.signalFrom(level, worldPosition, RedstoneHub.right(state)) > 0;
        boolean playOn = play && !playSignal, playOff = !play && playSignal;
        boolean skip = left && !leftSignal, back = right && !rightSignal;
        playSignal = play;
        leftSignal = left;
        rightSignal = right;
        if (!redstoneControl || mp3.isEmpty() || !(playOn || playOff || back || skip) || !RedstoneHub.feedsSpeakers(level, worldPosition)) {
            return;
        }
        Mp3State mp3State = Mp3State.read(mp3);
        long now = level.getGameTime();
        if (playOn && !mp3State.playing && !mp3State.queue.isEmpty() && PlaybackManager.get(serverLevel).recover(worldPosition)) {
            mp3State.play(now);
        }
        if (playOff && mp3State.playing) {
            mp3State.pause(now);
        }
        if (skip) {
            mp3State.next(now, RANDOM);
        }
        if (back) {
            mp3State.previous(now);
        }
        mp3State.write(mp3);
        changed();
    }

    /** 15 while it plays (every face). */
    @Override
    public int comparatorSignal(Direction face) {
        return level instanceof ServerLevel serverLevel && PlaybackManager.get(serverLevel).isPlaying(worldPosition) ? 15 : 0;
    }

    public boolean docked() {
        return !mp3.isEmpty();
    }

    /** 100–400%. It only counts when the system has a main Amplifier, and never goes past its cap. */
    public float boost() {
        return boost;
    }

    public void setBoost(float boost) {
        this.boost = Mth.clamp(boost, 1, AmplifierBlockEntity.MAX_MASTER);
        changed();
        if (level instanceof ServerLevel serverLevel) {
            PlaybackManager.get(serverLevel).refresh();
        }
    }

    /** The system's Multi-Effects Unit (or its wiring) changed: a new Speed moves the song's timeline. */
    public void onSpeed(float speed) {
        if (mp3.isEmpty() || level == null) {
            return;
        }
        Mp3State state = Mp3State.read(mp3);
        if (state.speed != speed) {
            state.setSpeed(speed, level.getGameTime());
            state.write(mp3);
            changed();
        }
    }

    /** The speed its MEU asks for, or 1 without one. */
    private float systemSpeed() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 1;
        }
        var network = com.minescore.wiring.WireNetwork.get(serverLevel);
        BlockPos meu = com.minescore.wiring.SpeakerSystems.first(network::endsAt, worldPosition, com.minescore.wiring.BlockKind.MEU);
        return meu != null && level.getBlockEntity(meu) instanceof MeuBlockEntity unit ? unit.chain().speed() : 1;
    }

    /** A speaker in its system shorted: the docked player pauses where it was. */
    public void halt() {
        if (mp3.isEmpty() || level == null) {
            return;
        }
        Mp3State state = Mp3State.read(mp3);
        state.pause(level.getGameTime());
        state.write(mp3);
        changed();
    }

    /** Docks an MP3 Player. It keeps playing (or stays paused) where it was. */
    public void dock(ItemStack stack) {
        mp3 = stack.split(1);
        Mp3State state = Mp3State.read(mp3);
        state.holder = null;
        state.link = null;
        if (level != null) {
            state.setSpeed(systemSpeed(), level.getGameTime());
        }
        state.write(mp3);
        changed();
        if (level != null) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.minescore.audio.SynthSound.DOCK.play(serverLevel, worldPosition);
            }
        }
    }

    /** Hands the docked MP3 Player to a player, paused where it was. */
    public void eject(ServerPlayer player) {
        if (mp3.isEmpty()) {
            return;
        }
        Mp3State state = Mp3State.read(mp3);
        state.pause(player.level().getGameTime());
        state.setSpeed(1, player.level().getGameTime()); // carried, it plays at normal speed
        state.write(mp3);
        ItemStack out = mp3;
        mp3 = ItemStack.EMPTY;
        changed();
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
    }

    public ItemStack takeForDrop() {
        ItemStack out = mp3;
        if (!out.isEmpty() && level != null) {
            Mp3State state = Mp3State.read(out);
            state.pause(level.getGameTime());
            state.setSpeed(1, level.getGameTime());
            state.write(out);
        }
        mp3 = ItemStack.EMPTY;
        return out;
    }

    /** A button pressed on the docked MP3 Player's screen. */
    public void command(Mp3Packets.Command command, ServerPlayer player) {
        if (mp3.isEmpty() || !command.target().player().equals(Mp3State.idOf(mp3))) {
            return;
        }
        if (command.action() == Mp3Packets.Action.EJECT) {
            eject(player);
            return;
        }
        Mp3State state = Mp3State.read(mp3);
        boolean wasPlaying = state.playing;
        Mp3Server.apply(state, command, player.level().getGameTime());
        if (state.playing && !wasPlaying && !PlaybackManager.get(player.serverLevel()).recover(worldPosition)) {
            state.pause(player.level().getGameTime()); // a speaker just shorted: reset the system first
            player.displayClientMessage(Component.translatable(PlaybackManager.shortResetMessage()), true);
        }
        state.write(mp3);
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MixerBlockEntity mixer) {
        HubPower.tick(level, pos, state);
        if (level instanceof ServerLevel serverLevel) {
            PlaybackManager playback = PlaybackManager.get(serverLevel);
            if (mixer.power.tick(serverLevel, pos, playback.isPlaying(pos))) {
                playback.refresh();
                level.sendBlockUpdated(pos, state, state, 3); // the Mixer tab shows the power
            }
        }
        if (mixer.mp3.isEmpty() || level.getGameTime() % 10 != 0) {
            return;
        }
        Mp3State mp3State = Mp3State.read(mixer.mp3);
        if (mp3State.tick(level.getGameTime(), RANDOM)) {
            mp3State.write(mixer.mp3);
            mixer.changed();
        }
    }

    /** Saves, tells clients, and makes the system play what the docked player says. */
    private void changed() {
        setChanged();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState block = getBlockState();
        if (block.hasProperty(MixerBlock.DOCKED) && block.getValue(MixerBlock.DOCKED) == mp3.isEmpty()) {
            level.setBlock(worldPosition, block.setValue(MixerBlock.DOCKED, !mp3.isEmpty()), 3); // show the player in the dock, or not
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        PlaybackManager playback = PlaybackManager.get(serverLevel);
        Mp3State state = mp3.isEmpty() ? null : Mp3State.read(mp3);
        Optional<String> track = state == null ? Optional.empty() : state.current().map(s -> s.trackId());
        if (state != null && state.playing && track.isPresent()) {
            playback.start(Playback.Kind.SYSTEM, worldPosition, track.get(), state.startTick, state.volume, ItemStack.EMPTY);
        } else {
            playback.stop(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!mp3.isEmpty()) {
            tag.put("Mp3", mp3.save(new CompoundTag()));
        }
        tag.putFloat("Boost", boost);
        tag.putBoolean("RedstoneControl", redstoneControl);
        power.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mp3 = tag.contains("Mp3") ? ItemStack.of(tag.getCompound("Mp3")) : ItemStack.EMPTY;
        boost = tag.contains("Boost") ? Mth.clamp(tag.getFloat("Boost"), 1, AmplifierBlockEntity.MAX_MASTER) : 1;
        if (tag.contains("RedstoneControl")) {
            redstoneControl = tag.getBoolean("RedstoneControl");
        }
        power.load(tag);
        if (tag.contains("Power")) { // on clients
            power.loadForClient(tag.getCompound("Power"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        tag.put("Power", power.saveForClient());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
