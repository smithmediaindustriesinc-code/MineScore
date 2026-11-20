package com.minescore.audio;

import com.minescore.wiring.Gauge;

/**
 * Splits audio into the three speaker bands with 4th-order Linkwitz-Riley filters (two cascaded
 * Butterworth biquads per cut): Subwoofer below the low split, Middle Speaker between, Tweeter above.
 */
public final class Crossover {
    private static final double BUTTERWORTH_Q = Math.sqrt(0.5);

    private final Biquad[] stages;

    private Crossover(Biquad... stages) {
        this.stages = stages;
    }

    /**
     * @param band      the band whose speakers this feeds (12 ga = sub, 16 ga = mid, 20 ga = tweeter)
     * @param lowSplit  sub/mid crossover in Hz (200 by default)
     * @param highSplit mid/tweeter crossover in Hz (2,000 by default)
     */
    public static Crossover forBand(Gauge band, double sampleRate, double lowSplit, double highSplit) {
        return switch (band) {
            case G12 -> new Crossover(lowPass(lowSplit, sampleRate), lowPass(lowSplit, sampleRate));
            case G16 -> new Crossover(highPass(lowSplit, sampleRate), highPass(lowSplit, sampleRate),
                    lowPass(highSplit, sampleRate), lowPass(highSplit, sampleRate));
            case G20 -> new Crossover(highPass(highSplit, sampleRate), highPass(highSplit, sampleRate));
        };
    }

    public float process(float sample) {
        double x = sample;
        for (Biquad stage : stages) {
            x = stage.process(x);
        }
        return (float) x;
    }

    /** Forgets past samples, e.g. after a seek. */
    public void reset() {
        for (Biquad stage : stages) {
            stage.reset();
        }
    }

    private static Biquad lowPass(double frequency, double sampleRate) {
        double w0 = 2 * Math.PI * frequency / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * BUTTERWORTH_Q);
        return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
    }

    private static Biquad highPass(double frequency, double sampleRate) {
        double w0 = 2 * Math.PI * frequency / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * BUTTERWORTH_Q);
        return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
    }

    /** One second-order section (RBJ cookbook), transposed direct form II. */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double z1, z2;

        Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        double process(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        void reset() {
            z1 = 0;
            z2 = 0;
        }
    }
}
