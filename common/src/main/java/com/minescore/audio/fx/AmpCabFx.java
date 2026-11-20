package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * Amp & Cabinet modelling: a preamp that saturates by its model's character, a three-band tone stack, then
 * a speaker cabinet played through a short impulse response (one of three built-in cabinets, or none).
 * The models' names are made up; they're types of amp, not copies of real ones.
 */
final class AmpCabFx extends Fx {
    private final Effect model;
    private final int irLength;
    private final Dsp.Biquad[] input = {new Dsp.Biquad(), new Dsp.Biquad()};
    private final Dsp.Biquad[][] stack = new Dsp.Biquad[2][3];
    private final float[][] history;
    private int write;
    private float[] ir;
    private int cabinet = -1;
    private float drive = 1, level = 1, makeup = 1;

    AmpCabFx(Effect model, boolean high) {
        this.model = model;
        irLength = high ? 384 : 160;
        history = new float[2][irLength];
        for (int ch = 0; ch < 2; ch++) {
            input[ch].highPass(model == Effect.MODERN_HIGH_GAIN ? 140 : 70, 0.7);
            for (int b = 0; b < 3; b++) {
                stack[ch][b] = new Dsp.Biquad();
            }
        }
    }

    @Override
    public void set(CardSettings card, Context context) {
        float range = switch (model) {
            case CLEAN_TUBE -> 3;
            case BRITISH_CRUNCH -> 14;
            case BOUTIQUE_LEAD -> 28;
            default -> 70;
        };
        drive = 1 + card.value("gain") * range;
        makeup = (float) (1 / Math.tanh(Math.min(drive, 3)));
        level = Dsp.dbToGain(card.value("level")) * 0.8f;
        double midFreq = model == Effect.BRITISH_CRUNCH ? 900 : 650;
        double scoop = model == Effect.MODERN_HIGH_GAIN ? -4 : 0;
        for (int ch = 0; ch < 2; ch++) {
            stack[ch][0].lowShelf(120, card.value("bass"));
            stack[ch][1].peak(midFreq, 0.8, card.value("mid") + scoop);
            stack[ch][2].highShelf(3200, card.value("treble"));
        }
        int cab = Math.round(card.value("cabinet"));
        if (cab != cabinet) {
            cabinet = cab;
            ir = cab == 0 ? null : cabinetIr(cab, irLength);
        }
    }

    /**
     * A built-in cabinet's impulse response, made from an impulse run through the cabinet's resonances
     * and a fast-decaying tail of reflections, scaled so it plays back at about the same loudness.
     */
    static float[] cabinetIr(int cabinet, int length) {
        double[][] shape = switch (cabinet) { // {frequency, Q, dB} resonances
            case 1 -> new double[][] {{120, 1.2, 4}, {2500, 1.5, 3}};
            case 2 -> new double[][] {{100, 1.2, 5}, {2000, 1.4, 4}};
            default -> new double[][] {{90, 1.4, 6}, {1600, 1.3, 4}, {600, 2, -3}};
        };
        double lowPass = cabinet == 1 ? 5500 : cabinet == 2 ? 5000 : 4200;
        double highPass = cabinet == 1 ? 90 : 75;
        Dsp.Biquad[] chain = new Dsp.Biquad[shape.length + 3];
        for (int i = 0; i < shape.length; i++) {
            chain[i] = new Dsp.Biquad().peak(shape[i][0], shape[i][1], shape[i][2]);
        }
        chain[shape.length] = new Dsp.Biquad().lowPass(lowPass, 0.9);
        chain[shape.length + 1] = new Dsp.Biquad().lowPass(lowPass * 1.3, 0.7);
        chain[shape.length + 2] = new Dsp.Biquad().highPass(highPass, 0.7);
        java.util.Random random = new java.util.Random(cabinet * 7919L);
        float[] ir = new float[length];
        for (int i = 0; i < length; i++) {
            double reflections = (random.nextDouble() * 2 - 1) * 0.08 * Math.exp(-i / (length * 0.18));
            float x = (float) ((i == 0 ? 1 : 0) + reflections);
            for (Dsp.Biquad b : chain) {
                x = b.process(x);
            }
            ir[i] = x;
        }
        // Normalise to unity gain at 1 kHz.
        double re = 0, im = 0;
        for (int i = 0; i < length; i++) {
            double w = 2 * Math.PI * 1000 * i / Dsp.SR;
            re += ir[i] * Math.cos(w);
            im -= ir[i] * Math.sin(w);
        }
        float scale = (float) (1 / Math.max(1e-3, Math.hypot(re, im)));
        for (int i = 0; i < length; i++) {
            ir[i] *= scale;
        }
        return ir;
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 2; ch++) {
                float[] x = ch == 0 ? left : right;
                float v = (float) Math.tanh(input[ch].process(x[i]) * drive) * makeup;
                for (Dsp.Biquad b : stack[ch]) {
                    v = b.process(v);
                }
                history[ch][write] = v;
                if (ir != null) {
                    float sum = 0;
                    int idx = write;
                    for (float tap : ir) {
                        sum += tap * history[ch][idx];
                        idx = idx == 0 ? irLength - 1 : idx - 1;
                    }
                    v = sum;
                }
                x[i] = v * level;
            }
            write = (write + 1) % irLength;
        }
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 2; ch++) {
            input[ch].reset();
            for (Dsp.Biquad b : stack[ch]) {
                b.reset();
            }
            java.util.Arrays.fill(history[ch], 0);
        }
    }
}
