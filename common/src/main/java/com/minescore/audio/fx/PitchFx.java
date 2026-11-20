package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * Pitch shifting without changing speed: two read heads slide through a short delay line at the new rate,
 * each faded in and out so one is always mid-grain while the other jumps back. Used by the Pitch shifter
 * (semitones and cents) and the Tuner (a key shift, plus retuning from the song's own reference pitch).
 */
final class PitchFx extends Fx {
    private static final int WINDOW = 2048; // about 46 ms grains
    private final Dsp.DelayLine[] lines = {new Dsp.DelayLine(WINDOW * 2), new Dsp.DelayLine(WINDOW * 2)};
    private double phase;
    private double ratio = 1;

    PitchFx(Effect type) {}

    /** The pitch ratio a card asks for. */
    static double ratio(CardSettings card, Context context) {
        if (card.effect() == Effect.TUNER) {
            double reference = card.value("retune") > 0 ? card.value("reference") : Math.max(400, context.tuningHz());
            return Math.pow(2, card.value("shift") / 12.0) * reference / Math.max(400, context.tuningHz());
        }
        return Math.pow(2, (card.value("semitones") + card.value("fine") / 100.0) / 12.0);
    }

    @Override
    public void set(CardSettings card, Context context) {
        ratio = ratio(card, context);
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        if (Math.abs(ratio - 1) < 1e-4) {
            for (int i = 0; i < n; i++) { // in tune already: keep the lines filled so a change has no gap
                lines[0].push(left[i]);
                lines[1].push(right[i]);
            }
            return;
        }
        double step = (1 - ratio) / WINDOW;
        for (int i = 0; i < n; i++) {
            lines[0].push(left[i]);
            lines[1].push(right[i]);
            double p1 = phase;
            double p2 = (phase + 0.5) % 1;
            float g1 = (float) Math.sin(Math.PI * p1);
            float g2 = (float) Math.sin(Math.PI * p2);
            double d1 = 1 + p1 * WINDOW;
            double d2 = 1 + p2 * WINDOW;
            left[i] = (lines[0].read(d1) * g1 * g1 + lines[0].read(d2) * g2 * g2);
            right[i] = (lines[1].read(d1) * g1 * g1 + lines[1].read(d2) * g2 * g2);
            phase += step;
            phase -= Math.floor(phase);
        }
    }

    @Override
    public void reset() {
        lines[0].reset();
        lines[1].reset();
        phase = 0;
    }
}
