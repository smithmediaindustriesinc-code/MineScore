package com.minescore.audio;

import com.minescore.wiring.Gauge;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * How loud each speaker band of a song is over time. Measured once, on the client that cuts the song,
 * and kept by the server so it can tell when music played too loud blows a speaker (SCOPE.md §5.5).
 *
 * <p>Each window stores the loudest 0.4 s of each band, as whole decibels below full scale.
 */
public final class Loudness {
    /** Keeps the data small enough to ride along with the Cut packet (6 KB at most). */
    public static final int MAX_WINDOWS = 2000;
    /** A band this loud counts as normal; louder music pushes speakers harder. */
    public static final double REFERENCE_DB = -15;
    /** Levels are stored from 0 down to -120 dB. */
    private static final int FLOOR = 120;
    private static final double BLOCK_SECONDS = 0.4;

    private final float windowSeconds;
    /** [band][window], as positive decibels below full scale. */
    private final byte[][] levels;

    private Loudness(float windowSeconds, byte[][] levels) {
        this.windowSeconds = windowSeconds;
        this.levels = levels;
    }

    /** Splits the song into bands (as the speakers do) and measures each. Slow: call off-thread. */
    public static Loudness measure(Track track, double lowSplit, double highSplit) {
        double seconds = Math.max(track.seconds(), 0.001);
        float windowSeconds = (float) Math.max(1, seconds / MAX_WINDOWS);
        int windows = Math.max(1, (int) Math.ceil(seconds / windowSeconds));
        int windowFrames = Math.max(1, Math.round(windowSeconds * Track.SAMPLE_RATE));
        int blockFrames = Math.min(windowFrames, (int) (BLOCK_SECONDS * Track.SAMPLE_RATE));
        byte[][] levels = new byte[Gauge.values().length][windows];
        for (Gauge band : Gauge.values()) {
            Crossover filter = Crossover.forBand(band, Track.SAMPLE_RATE, lowSplit, highSplit);
            double loudest = 0;
            double sum = 0;
            int inBlock = 0;
            for (int frame = 0; frame < track.frames(); frame++) {
                float sample = filter.process((track.left(frame) + track.right(frame)) * 0.5f);
                sum += sample * sample;
                if (++inBlock == blockFrames || frame == track.frames() - 1) {
                    loudest = Math.max(loudest, sum / inBlock);
                    sum = 0;
                    inBlock = 0;
                }
                if ((frame + 1) % windowFrames == 0 || frame == track.frames() - 1) {
                    int window = Math.min(windows - 1, frame / windowFrames);
                    levels[band.ordinal()][window] = toByte(10 * Math.log10(Math.max(loudest, 1e-12)));
                    loudest = 0;
                }
            }
        }
        return new Loudness(windowSeconds, levels);
    }

    private static byte toByte(double db) {
        return (byte) Math.max(0, Math.min(FLOOR, Math.round(-db)));
    }

    /** A band's level at a moment in the song, in dB below full scale. */
    public double levelDb(Gauge band, double seconds) {
        byte[] values = levels[band.ordinal()];
        int window = (int) Math.max(0, Math.min(values.length - 1, Math.floor(seconds / windowSeconds)));
        return -(values[window] & 0xFF);
    }

    /** How far a band is above normal level at a moment in the song, in dB (negative when quieter). */
    public double excessDb(Gauge band, double seconds) {
        return levelDb(band, seconds) - REFERENCE_DB;
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeFloat(windowSeconds);
            out.writeShort(levels[0].length);
            for (byte[] band : levels) {
                out.write(band);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // can't happen writing to memory
        }
        return bytes.toByteArray();
    }

    /** @throws IOException if the data is malformed */
    public static Loudness decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            float windowSeconds = in.readFloat();
            int windows = in.readUnsignedShort();
            if (!(windowSeconds >= 1) || windows < 1 || windows > MAX_WINDOWS || data.length != 6 + windows * Gauge.values().length) {
                throw new IOException("Bad loudness data");
            }
            byte[][] levels = new byte[Gauge.values().length][windows];
            for (byte[] band : levels) {
                in.readFully(band);
            }
            return new Loudness(windowSeconds, levels);
        }
    }
}
