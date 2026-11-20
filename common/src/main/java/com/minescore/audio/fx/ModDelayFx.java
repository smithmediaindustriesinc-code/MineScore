package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/** Chorus and Flanger: a short delay swept by a slow wave, the two sides a quarter-turn apart for width. */
final class ModDelayFx extends Fx {
    private final boolean flanger;
    private final Dsp.DelayLine[] lines = {new Dsp.DelayLine(4096), new Dsp.DelayLine(4096)};
    private double phase;
    private double rate, base, depth;
    private float feedback;
    private final float[] last = new float[2];

    ModDelayFx(Effect type) {
        flanger = type == Effect.FLANGER;
    }

    @Override
    public void set(CardSettings card, Context context) {
        rate = card.value("rate");
        double d = card.value("depth");
        base = (flanger ? 0.001 : 0.018) * Dsp.SR;
        depth = (flanger ? 0.004 : 0.008) * d * Dsp.SR;
        feedback = flanger ? card.value("feedback") : 0;
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        double step = 2 * Math.PI * rate / Dsp.SR;
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 2; ch++) {
                float[] x = ch == 0 ? left : right;
                double lfo = Math.sin(phase + ch * Math.PI / 2);
                double delay = base + depth * (1 + lfo) * 0.5;
                lines[ch].push(x[i] + last[ch] * feedback);
                last[ch] = lines[ch].read(delay);
                x[i] = last[ch];
            }
            phase += step;
        }
        phase %= 2 * Math.PI;
    }

    @Override
    public void reset() {
        lines[0].reset();
        lines[1].reset();
        last[0] = last[1] = 0;
    }
}
