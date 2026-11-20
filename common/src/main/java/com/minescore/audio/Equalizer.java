package com.minescore.audio;

/**
 * A 10-band graphic equaliser: one peaking filter (RBJ cookbook) per octave band, 31 Hz to 16 kHz,
 * each boosting or cutting by up to ±12 dB. Bands left at 0 dB cost nothing.
 */
public final class Equalizer {
    public static final double[] FREQUENCIES = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    /** Octave-wide bands. */
    private static final double Q = 1.41;

    private final double[][] coefficients = new double[FREQUENCIES.length][];
    private final double[][] state = new double[FREQUENCIES.length][2];
    private final float[] gains = new float[FREQUENCIES.length];

    /** @param gainsDb one gain per band, in decibels */
    public void setGains(float[] gainsDb, double sampleRate) {
        for (int i = 0; i < FREQUENCIES.length; i++) {
            float gain = i < gainsDb.length ? gainsDb[i] : 0;
            if (coefficients[i] != null && gains[i] == gain) {
                continue;
            }
            gains[i] = gain;
            coefficients[i] = Math.abs(gain) < 0.05 ? null : peaking(FREQUENCIES[i], gain, sampleRate);
        }
    }

    public float process(float sample) {
        double x = sample;
        for (int i = 0; i < FREQUENCIES.length; i++) {
            double[] c = coefficients[i];
            if (c == null) {
                continue;
            }
            double[] z = state[i];
            double y = c[0] * x + z[0];
            z[0] = c[1] * x - c[3] * y + z[1];
            z[1] = c[2] * x - c[4] * y;
            x = y;
        }
        return (float) x;
    }

    public void reset() {
        for (double[] z : state) {
            z[0] = 0;
            z[1] = 0;
        }
    }

    /** {b0, b1, b2, a1, a2}, normalised by a0. */
    private static double[] peaking(double frequency, double gainDb, double sampleRate) {
        double a = Math.pow(10, gainDb / 40);
        double w0 = 2 * Math.PI * Math.min(frequency, sampleRate * 0.45) / sampleRate;
        double alpha = Math.sin(w0) / (2 * Q);
        double cos = Math.cos(w0);
        double a0 = 1 + alpha / a;
        return new double[] {
            (1 + alpha * a) / a0, -2 * cos / a0, (1 - alpha * a) / a0, -2 * cos / a0, (1 - alpha / a) / a0,
        };
    }
}
