package com.minescore.playback;

import com.minescore.meu.EffectChain;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Something that is playing. Clients work out where in the track they are from the level's game time,
 * so everyone hears the same moment.
 *
 * @param kind      what is playing it, which decides how its speakers are found
 * @param source    the block it plays from: a Mixer or Desk, a Vinyl Player, or the speaker an MP3 Player is linked to
 * @param track     what is playing: {@code minescore:demo}, {@code file:<name>} or {@code song:<id>}
 * @param startTick game time at which the track's first sample plays (earlier than now when resuming mid-song)
 * @param gain      overall volume (the MP3 Player and Mixer slider)
 * @param speakers  the sound sources it drives right now
 * @param disc      the Vinyl Record being played on a Vinyl Player, otherwise empty
 * @param effects   what its Multi-Effects Unit does to the sound, or null without one
 */
public record Playback(Kind kind, BlockPos source, String track, long startTick, float gain, List<SpeakerInfo> speakers,
                       ItemStack disc, @Nullable EffectChain effects) {
    public enum Kind {
        /** A Mixer or Desk driving a wired speaker system. */
        SYSTEM,
        /** A Vinyl Player, on its own or wired to speakers. */
        VINYL,
        /** An MP3 Player linked straight to one speaker (and its daisy chain). */
        BLUETOOTH
    }

    public Playback {
        speakers = List.copyOf(speakers);
    }

    public boolean hasDisc() {
        return !disc.isEmpty();
    }

    public Playback withSpeakers(List<SpeakerInfo> speakers) {
        return new Playback(kind, source, track, startTick, gain, speakers, disc, effects);
    }

    public Playback withGain(float gain) {
        return new Playback(kind, source, track, startTick, gain, speakers, disc, effects);
    }

    public Playback withEffects(@Nullable EffectChain effects) {
        return new Playback(kind, source, track, startTick, gain, speakers, disc, effects);
    }

    /** How fast the song plays (its MEU's Speed effect): output time × speed = song time. */
    public float speed() {
        return effects == null ? 1 : effects.speed();
    }

    /** Speakers aren't saved; they're worked out again from the cables. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.put("Source", NbtUtils.writeBlockPos(source));
        tag.putString("Track", track);
        tag.putLong("StartTick", startTick);
        tag.putFloat("Gain", gain);
        if (hasDisc()) {
            tag.put("Disc", disc.save(new CompoundTag()));
        }
        return tag;
    }

    public static Playback load(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("Kind").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = Kind.SYSTEM;
        }
        ItemStack disc = tag.contains("Disc") ? ItemStack.of(tag.getCompound("Disc")) : ItemStack.EMPTY;
        return new Playback(kind, NbtUtils.readBlockPos(tag.getCompound("Source")), tag.getString("Track"), tag.getLong("StartTick"),
                tag.contains("Gain") ? tag.getFloat("Gain") : 1, List.of(), disc, null);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBlockPos(source);
        buf.writeUtf(track);
        buf.writeLong(startTick);
        buf.writeFloat(gain);
        buf.writeCollection(speakers, (b, s) -> s.write(b));
        buf.writeItem(disc);
        buf.writeBoolean(effects != null);
        if (effects != null) {
            effects.write(buf);
        }
    }

    public static Playback read(FriendlyByteBuf buf) {
        return new Playback(buf.readEnum(Kind.class), buf.readBlockPos(), buf.readUtf(), buf.readLong(), buf.readFloat(),
                buf.readList(SpeakerInfo::read), buf.readItem(), buf.readBoolean() ? EffectChain.read(buf) : null);
    }
}
