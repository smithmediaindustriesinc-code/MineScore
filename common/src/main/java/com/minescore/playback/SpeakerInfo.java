package com.minescore.playback;

import com.minescore.audio.EqCurve;
import com.minescore.audio.OutputChannel;
import com.minescore.wiring.Gauge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a client needs to play one sound source, so it doesn't need the source's chunk.
 *
 * @param front     where the sound comes from (a speaker's front face centre)
 * @param direction unit vector the front faces, or zero for a source that sounds the same all round
 * @param band      the band a speaker plays, or null for the full range (a Vinyl Player on its own)
 * @param gain      volume from the system: the MP3 Player and Mixer, Amplifiers, trims and chains
 * @param range     blocks until it fades to silence (further than the hearing range when boosted past 100%)
 * @param eq        the Amplifier EQ it plays through (flat without one)
 * @param power     its volume from the system's power budget (1 = fully powered; already in {@code gain}), or
 *                  -1 where power doesn't count (None mode, Bluetooth, a Vinyl Player playing from itself)
 * @param cost      FE/t it asks of its system
 */
public record SpeakerInfo(BlockPos pos, Vec3 front, Vec3 direction, @Nullable Gauge band, OutputChannel channel, float gain,
                          float range, EqCurve eq, float power, int cost) {
    public SpeakerInfo withGain(float gain, float range) {
        return new SpeakerInfo(pos, front, direction, band, channel, gain, range, eq, power, cost);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        writeVec(buf, front);
        writeVec(buf, direction);
        buf.writeBoolean(band != null);
        if (band != null) {
            buf.writeEnum(band);
        }
        buf.writeEnum(channel);
        buf.writeFloat(gain);
        buf.writeFloat(range);
        eq.write(buf);
        buf.writeFloat(power);
        buf.writeVarInt(cost);
    }

    public static SpeakerInfo read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Vec3 front = readVec(buf);
        Vec3 direction = readVec(buf);
        Gauge band = buf.readBoolean() ? buf.readEnum(Gauge.class) : null;
        return new SpeakerInfo(pos, front, direction, band, buf.readEnum(OutputChannel.class), buf.readFloat(), buf.readFloat(),
                EqCurve.read(buf), buf.readFloat(), buf.readVarInt());
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
