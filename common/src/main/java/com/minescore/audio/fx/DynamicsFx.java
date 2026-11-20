package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * Noise gate (silences everything quieter than its threshold) and compressor (evens out loud and quiet
 * parts, then makes the whole lot louder). Both follow the louder of the two sides, so the stereo image
 * doesn't shift.
 */
final class DynamicsFx extends Fx {
    private final boolean gate;
    private float threshold, ratio = 1, attack, release, makeup = 1;
    private float envelope;
    private float gain = 1;

    DynamicsFx(Effect type) {
        gate = type == Effect.GATE;
    }

    /** A gate turned all the way down stays open, so a fresh card changes nothing. */
    private static final float OPEN = -80;

    private static float coefficient(double ms) {
        return (float) (1 - Math.exp(-1 / (Math.max(0.05, ms) / 1000 * Dsp.SR)));
    }

    @Override
    public void set(CardSettings card, Context context) {
        threshold = card.value("threshold");
        release = coefficient(card.value("release"));
        if (gate) {
            attack = coefficient(1);
        } else {
            ratio = card.value("ratio");
            attack = coefficient(card.value("attack"));
            makeup = Dsp.dbToGain(card.value("makeup"));
        }
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            float level = Math.max(Math.abs(left[i]), Math.abs(right[i]));
            envelope += (level - envelope) * (level > envelope ? attack : release);
            double db = 20 * Math.log10(Math.max(envelope, 1e-6));
            float want;
            if (gate) {
                want = threshold <= OPEN || db > threshold ? 1 : 0;
            } else {
                double over = db - threshold;
                want = over > 0 ? Dsp.dbToGain(-over * (1 - 1 / ratio)) : 1;
            }
            // A compressor clamps down fast and lets go slowly; a gate opens fast and closes slowly.
            boolean fast = gate ? want > gain : want < gain;
            gain += (want - gain) * (fast ? attack : release);
            float g = gain * makeup;
            left[i] *= g;
            right[i] *= g;
        }
    }

    @Override
    public void reset() {
        envelope = 0;
        gain = 1;
    }
}
