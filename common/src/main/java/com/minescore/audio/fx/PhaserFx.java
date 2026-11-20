package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;

/** Phaser: six first-order all-pass stages swept between 200 Hz and 2 kHz; mixed with the dry sound it carves moving notches. */
final class PhaserFx extends Fx {
    private static final int STAGES = 6;
    private final float[][] state = new float[2][STAGES];
    private final float[] last = new float[2];
    private double phase, rate, depth;
    private float feedback;

    @Override
    public void set(CardSettings card, Context context) {
        rate = card.value("rate");
        depth = card.value("depth");
        feedback = card.value("feedback");
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        double step = 2 * Math.PI * rate / Dsp.SR;
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 2; ch++) {
                float[] x = ch == 0 ? left : right;
                double lfo = (1 + Math.sin(phase + ch * Math.PI / 2)) * 0.5 * depth;
                double f = 200 * Math.pow(10, lfo); // 200 Hz – 2 kHz
                double t = Math.tan(Math.PI * f / Dsp.SR);
                float a = (float) ((t - 1) / (t + 1));
                float v = x[i] + last[ch] * feedback;
                for (int s = 0; s < STAGES; s++) {
                    float y = a * v + state[ch][s];
                    state[ch][s] = v - a * y;
                    v = y;
                }
                last[ch] = v;
                x[i] = v;
            }
            phase += step;
        }
        phase %= 2 * Math.PI;
    }

    @Override
    public void reset() {
        for (float[] s : state) {
            java.util.Arrays.fill(s, 0);
        }
        last[0] = last[1] = 0;
    }
}
