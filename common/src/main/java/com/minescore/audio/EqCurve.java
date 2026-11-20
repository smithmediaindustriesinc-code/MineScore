package com.minescore.audio;

import com.minescore.wiring.Gauge;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;

/** A 10-band EQ setting (one gain per {@link Equalizer#FREQUENCIES} band, in dB), compared by value. */
public record EqCurve(float[] gains) {
    public static final int BANDS = Equalizer.FREQUENCIES.length;
    /** How far each band can be boosted or cut. */
    public static final float RANGE = 12;
    public static final EqCurve FLAT = new EqCurve(new float[BANDS]);

    public EqCurve {
        float[] copy = new float[BANDS];
        for (int i = 0; i < BANDS && i < gains.length; i++) {
            copy[i] = Math.max(-RANGE, Math.min(RANGE, gains[i]));
        }
        gains = copy;
    }

    @Override
    public float[] gains() {
        return gains.clone();
    }

    public boolean isFlat() {
        for (float gain : gains) {
            if (gain != 0) {
                return false;
            }
        }
        return true;
    }

    /** Two settings in a row (an Amplifier, then a Line Amplifier): the gains add up, still within ±12 dB. */
    public EqCurve then(EqCurve next) {
        float[] sum = new float[BANDS];
        for (int i = 0; i < BANDS; i++) {
            sum[i] = gains[i] + next.gains[i];
        }
        return new EqCurve(sum);
    }

    /** The EQ bands each speaker band covers: 31–125 Hz, 250 Hz–1 kHz, 2–16 kHz. */
    public static int[] bandsOf(Gauge band) {
        return switch (band) {
            case G12 -> new int[] {0, 1, 2};
            case G16 -> new int[] {3, 4, 5};
            case G20 -> new int[] {6, 7, 8, 9};
        };
    }

    /** The biggest boost this EQ gives a speaker band, in dB (what pushes it hardest). */
    public float boostDb(Gauge band) {
        float most = -RANGE;
        for (int i : bandsOf(band)) {
            most = Math.max(most, gains[i]);
        }
        return most;
    }

    /** The biggest boost anywhere, at least 0 dB: the headroom a speaker needs to play this without clipping. */
    public float peakDb() {
        float most = 0;
        for (float gain : gains) {
            most = Math.max(most, gain);
        }
        return most;
    }

    public void write(FriendlyByteBuf buf) {
        for (float gain : gains) {
            buf.writeFloat(gain);
        }
    }

    public static EqCurve read(FriendlyByteBuf buf) {
        float[] gains = new float[BANDS];
        for (int i = 0; i < BANDS; i++) {
            gains[i] = buf.readFloat();
        }
        return new EqCurve(gains);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EqCurve other && Arrays.equals(gains, other.gains);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(gains);
    }

    @Override
    public String toString() {
        return "EqCurve" + Arrays.toString(gains);
    }
}
