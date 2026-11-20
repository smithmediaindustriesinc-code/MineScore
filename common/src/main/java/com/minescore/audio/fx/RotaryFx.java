package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;

/**
 * Rotary speaker: a spinning horn for the highs and a slower drum for the lows. Each one swells and fades
 * as it turns and its pitch wobbles (Doppler); the two sides hear it at opposite points of the turn.
 */
final class RotaryFx extends Fx {
    private final Dsp.Biquad[] lows = {new Dsp.Biquad(), new Dsp.Biquad()};
    private final Dsp.Biquad[] highs = {new Dsp.Biquad(), new Dsp.Biquad()};
    private final Dsp.DelayLine[] horn = {new Dsp.DelayLine(512), new Dsp.DelayLine(512)};
    private double hornPhase, drumPhase, rate, depth;

    RotaryFx() {
        for (int ch = 0; ch < 2; ch++) {
            lows[ch].lowPass(800, 0.7);
            highs[ch].highPass(800, 0.7);
        }
    }

    @Override
    public void set(CardSettings card, Context context) {
        rate = card.value("rate");
        depth = card.value("depth");
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        double hornStep = 2 * Math.PI * rate / Dsp.SR;
        double drumStep = hornStep * 0.85; // the drum lags the horn a little
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 2; ch++) {
                float[] x = ch == 0 ? left : right;
                double side = ch * Math.PI;
                double h = Math.sin(hornPhase + side);
                double d = Math.sin(drumPhase + side);
                horn[ch].push(highs[ch].process(x[i]));
                float high = horn[ch].read(40 + 25 * depth * (1 + h)) * (float) (1 - 0.45 * depth * (1 - h) * 0.5);
                float low = lows[ch].process(x[i]) * (float) (1 - 0.25 * depth * (1 - d) * 0.5);
                x[i] = high + low;
            }
            hornPhase += hornStep;
            drumPhase += drumStep;
        }
        hornPhase %= 2 * Math.PI;
        drumPhase %= 2 * Math.PI;
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 2; ch++) {
            lows[ch].reset();
            highs[ch].reset();
            horn[ch].reset();
        }
    }
}
