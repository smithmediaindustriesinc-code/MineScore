package com.minescore.block;

import com.minescore.audio.OutputChannel;
import com.minescore.playback.PlaybackManager;
import com.minescore.registry.ModBlockEntities;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Channel Manager's switches (SCOPE.md §5.6): whether its Left and Right channels play, and whether it
 * forces everything to mono. Each speaker's own channel and trim live on the speaker.
 */
public class ChannelManagerBlockEntity extends SettingsBlockEntity implements RedstoneHub {
    private boolean leftOn = true;
    private boolean rightOn = true;
    private boolean forceMono;
    private boolean redstoneControl = RedstoneHub.controlByDefault();
    /** What redstone does right now (§7.3): weak power on a side turns that channel off; strong power forces mono. */
    private boolean redstoneLeftOff, redstoneRightOff, redstoneMono;

    public ChannelManagerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHANNEL_MANAGER.get(), pos, state);
    }

    public boolean leftOn() {
        return leftOn;
    }

    public boolean rightOn() {
        return rightOn;
    }

    public boolean forceMono() {
        return forceMono;
    }

    public void setLeftOn(boolean on) {
        leftOn = on;
        changed();
    }

    public void setRightOn(boolean on) {
        rightOn = on;
        changed();
    }

    public void setForceMono(boolean on) {
        forceMono = on;
        changed();
    }

    /** What a speaker set to {@code assigned} plays through this Channel Manager, or null if its channel is off. */
    public @Nullable OutputChannel play(OutputChannel assigned) {
        if (mono()) {
            return OutputChannel.MONO;
        }
        return switch (assigned) {
            case LEFT -> leftPlays() ? OutputChannel.LEFT : null;
            case RIGHT -> rightPlays() ? OutputChannel.RIGHT : null;
            case MONO -> OutputChannel.MONO;
        };
    }

    /** Forced to mono, by its switch or by strong redstone power. */
    public boolean mono() {
        return forceMono || redstoneMono;
    }

    private boolean leftPlays() {
        return leftOn && !redstoneLeftOff;
    }

    private boolean rightPlays() {
        return rightOn && !redstoneRightOff;
    }

    @Override
    public boolean redstoneControl() {
        return redstoneControl;
    }

    @Override
    public void setRedstoneControl(boolean on) {
        redstoneControl = on;
        readRedstone();
        changed();
    }

    @Override
    public void readRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        boolean strong = false;
        for (Direction side : Direction.values()) {
            strong |= RedstoneHub.strongFrom(level, worldPosition, side);
        }
        boolean active = redstoneControl && RedstoneHub.feedsSpeakers(level, worldPosition);
        boolean mono = active && strong;
        boolean leftOff = active && !strong && RedstoneHub.signalFrom(level, worldPosition, RedstoneHub.left(state)) > 0;
        boolean rightOff = active && !strong && RedstoneHub.signalFrom(level, worldPosition, RedstoneHub.right(state)) > 0;
        if (mono != redstoneMono || leftOff != redstoneLeftOff || rightOff != redstoneRightOff) {
            redstoneMono = mono;
            redstoneLeftOff = leftOff;
            redstoneRightOff = rightOff;
            changed();
        }
    }

    /** 15 from its left while Left plays, from its right while Right plays, and from every face while it plays mono. */
    @Override
    public int comparatorSignal(Direction face) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        BlockPos source = SpeakerSystems.sourceOf(WireNetwork.get(serverLevel)::endsAt, worldPosition);
        if (source == null || !PlaybackManager.get(serverLevel).isPlaying(source)) {
            return 0;
        }
        BlockState state = getBlockState();
        if (mono()) {
            return 15;
        }
        if (face == RedstoneHub.left(state)) {
            return leftPlays() ? 15 : 0;
        }
        return face == RedstoneHub.right(state) && rightPlays() ? 15 : 0;
    }

    @Override
    protected void write(CompoundTag tag) {
        tag.putBoolean("LeftOn", leftOn);
        tag.putBoolean("RightOn", rightOn);
        tag.putBoolean("ForceMono", forceMono);
        tag.putBoolean("RedstoneControl", redstoneControl);
    }

    @Override
    protected void read(CompoundTag tag) {
        leftOn = !tag.contains("LeftOn") || tag.getBoolean("LeftOn");
        rightOn = !tag.contains("RightOn") || tag.getBoolean("RightOn");
        forceMono = tag.getBoolean("ForceMono");
        if (tag.contains("RedstoneControl")) {
            redstoneControl = tag.getBoolean("RedstoneControl");
        }
    }
}
