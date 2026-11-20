package com.minescore.block;

import com.minescore.audio.OutputChannel;
import com.minescore.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A speaker's own settings: its name, the channel a Channel Manager assigned it, and its trim. Without a
 * Channel Manager in its system, a speaker plays mono at full trim whatever is stored here.
 */
public class SpeakerBlockEntity extends SettingsBlockEntity {
    public static final int MAX_NAME = 32;

    private String name = "";
    private OutputChannel channel = OutputChannel.MONO;
    /** 0–100%: scales this speaker and everything chained after it. */
    private float trim = 1;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public OutputChannel channel() {
        return channel;
    }

    public float trim() {
        return trim;
    }

    public void setName(String name) {
        String clean = name == null ? "" : name.strip();
        this.name = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
        changed();
    }

    public void setChannel(OutputChannel channel) {
        this.channel = channel;
        changed();
    }

    public void setTrim(float trim) {
        this.trim = Mth.clamp(trim, 0, 1);
        changed();
    }

    @Override
    protected void write(CompoundTag tag) {
        tag.putString("Name", name);
        tag.putString("Channel", channel.name());
        tag.putFloat("Trim", trim);
    }

    @Override
    protected void read(CompoundTag tag) {
        name = tag.getString("Name");
        try {
            channel = OutputChannel.valueOf(tag.getString("Channel").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            channel = OutputChannel.MONO;
        }
        trim = tag.contains("Trim") ? Mth.clamp(tag.getFloat("Trim"), 0, 1) : 1;
    }
}
