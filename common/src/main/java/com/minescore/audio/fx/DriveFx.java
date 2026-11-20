package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/** Drive & Distortion: Overdrive (warm, uneven soft clipping), Fuzz (hard clipping with an octave edge), High-gain (two stages). */
final class DriveFx extends Fx {
    private final Effect type;
    private final Dsp.OnePole[] tone = {new Dsp.OnePole(), new Dsp.OnePole()};
    private final Dsp.Biquad[] tighten = {new Dsp.Biquad(), new Dsp.Biquad()};
    private float gain = 1, makeup = 1, level = 1;

    DriveFx(Effect type) {
        this.type = type;
        for (Dsp.Biquad b : tighten) {
            b.highPass(type == Effect.HIGH_GAIN ? 250 : 40, 0.7);
        }
    }

    @Override
    public void set(CardSettings card, Context context) {
        float drive = card.value("drive");
        float range = switch (type) {
            case FUZZ -> 60;
            case HIGH_GAIN -> 150;
            default -> 20;
        };
        gain = 1 + drive * range;
        makeup = (float) (1 / Math.tanh(Math.min(gain, 3))); // keep quiet passages about as loud as clean
        level = Dsp.dbToGain(card.value("level")) * 0.8f;
        double cutoff = 800 * Math.pow(15, card.value("tone")); // 800 Hz – 12 kHz
        for (Dsp.OnePole p : tone) {
            p.cutoff(cutoff);
        }
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int ch = 0; ch < 2; ch++) {
            float[] x = ch == 0 ? left : right;
            for (int i = 0; i < n; i++) {
                float v = tighten[ch].process(x[i]) * gain;
                float y = switch (type) {
                    case FUZZ -> Math.max(-1, Math.min(1, v)) * 0.75f + Math.abs((float) Math.tanh(v)) * 0.25f - 0.12f;
                    case HIGH_GAIN -> (float) Math.tanh(Math.tanh(v) * 3) * 0.9f;
                    default -> (float) (Math.tanh(v + 0.15) - Math.tanh(0.15)); // a little uneven, like a tube
                };
                x[i] = tone[ch].process(y) * makeup * level;
            }
        }
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 2; ch++) {
            tone[ch].reset();
            tighten[ch].reset();
        }
    }
}
