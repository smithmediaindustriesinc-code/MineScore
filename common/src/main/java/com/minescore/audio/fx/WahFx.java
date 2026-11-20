package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;

/**
 * Wah: a resonant band-pass swept between 400 Hz and 2.2 kHz, either by the expression pedal (the MEU's
 * slider) or automatically by how loud the music is (auto-wah).
 */
final class WahFx extends Fx {
    private final float[][] state = new float[2][2]; // state-variable filter: low, band
    private boolean auto;
    private float sensitivity, damping, pedal;
    private float envelope;

    @Override
    public void set(CardSettings card, Context context) {
        auto = Math.round(card.value("mode")) == 1;
        sensitivity = card.value("sensitivity");
        damping = (float) (1 / (1 + 7 * card.value("resonance"))); // Q from 1 to 8
        pedal = context.expression();
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            float level = Math.abs(left[i]) + Math.abs(right[i]);
            envelope += (level - envelope) * (level > envelope ? 0.01f : 0.0005f);
            float position = auto ? Math.min(1, envelope * (1 + sensitivity * 8)) : pedal;
            double f = 400 * Math.pow(5.5, position);
            float k = (float) (2 * Math.sin(Math.PI * f / Dsp.SR));
            for (int ch = 0; ch < 2; ch++) {
                float[] x = ch == 0 ? left : right;
                float[] s = state[ch];
                float high = x[i] - s[0] - damping * 2 * s[1];
                s[1] += k * high;
                s[0] += k * s[1];
                x[i] = s[1] * 2;
            }
        }
    }

    @Override
    public void reset() {
        for (float[] s : state) {
            s[0] = s[1] = 0;
        }
        envelope = 0;
    }
}
