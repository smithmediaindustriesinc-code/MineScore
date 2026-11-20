package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * Delay: Straight (each side echoes itself), Stereo (the right side's echoes come later) and Ping-pong
 * (echoes bounce from side to side). The time is set in milliseconds, or locked to the song's tempo.
 * Echoes darken a little each time round, like tape.
 */
final class DelayFx extends Fx {
    private static final int MAX = (int) (Dsp.SR * 2.2);
    private final Effect type;
    private final Dsp.DelayLine[] lines = {new Dsp.DelayLine(MAX), new Dsp.DelayLine(MAX)};
    private final Dsp.OnePole[] damping = {new Dsp.OnePole().cutoff(6000), new Dsp.OnePole().cutoff(6000)};
    private final double[] delay = new double[2];
    private final double[] target = new double[2];
    private float feedback;

    DelayFx(Effect type) {
        this.type = type;
    }

    /** The echo time in seconds: the time knob, or a note length at the song's tempo. */
    static double seconds(CardSettings card, Context context) {
        int sync = Math.round(card.value("sync"));
        if (sync > 0) {
            double quarter = 60 / Math.max(40, Math.min(240, context.bpm()));
            return quarter * switch (sync) {
                case 1 -> 1;
                case 2 -> 0.5;
                case 3 -> 0.75;
                default -> 0.25;
            };
        }
        return card.value("time") / 1000;
    }

    @Override
    public void set(CardSettings card, Context context) {
        double t = Math.min(2.0, seconds(card, context)) * Dsp.SR;
        target[0] = t;
        target[1] = type == Effect.STEREO ? Math.min(MAX - 8, t * (1 + 0.5 * card.value("spread"))) : t;
        if (delay[0] == 0) {
            delay[0] = target[0];
            delay[1] = target[1];
        }
        feedback = card.value("feedback");
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            delay[0] += (target[0] - delay[0]) * 0.0005; // glide to a new time instead of clicking
            delay[1] += (target[1] - delay[1]) * 0.0005;
            float outL = lines[0].read(delay[0]);
            float outR = lines[1].read(delay[1]);
            float backL = damping[0].process(outL) * feedback;
            float backR = damping[1].process(outR) * feedback;
            if (type == Effect.PING_PONG) {
                lines[0].push((left[i] + right[i]) * 0.5f + backR);
                lines[1].push(backL);
            } else {
                lines[0].push(left[i] + backL);
                lines[1].push(right[i] + backR);
            }
            left[i] = outL;
            right[i] = outR;
        }
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 2; ch++) {
            lines[ch].reset();
            damping[ch].reset();
        }
    }
}
