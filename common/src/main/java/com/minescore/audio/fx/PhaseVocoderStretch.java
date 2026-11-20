package com.minescore.audio.fx;

import com.minescore.audio.Track;

/**
 * Phase-vocoder time-stretching with identity phase locking: the song is taken apart into 2048-point
 * spectra, each frequency's phase is advanced as if the song played at the new speed, and the spectra are
 * put back together. Bins near a peak keep their phase relation to it (phase locking), which avoids most
 * of the "phasey" sound plain vocoders have. Pitch stays put.
 */
final class PhaseVocoderStretch implements TimeStretch {
    private static final int N = 2048;
    private static final int HOP = N / 4;
    private static final int BINS = N / 2 + 1;
    private static final double[] WINDOW = new double[N];
    /** Hann analysis × Hann synthesis at 75% overlap sums to 1.5. */
    private static final double SCALE = 1 / 1.5;

    static {
        for (int i = 0; i < N; i++) {
            WINDOW[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N);
        }
    }

    private final Track track;
    private final double speed;
    private final double[][] lastPhase = new double[2][BINS];
    private final double[][] outPhase = new double[2][BINS];
    private final double[] re = new double[N], im = new double[N];
    private final double[] magnitude = new double[BINS], phase = new double[BINS];
    private final float[][] acc = new float[2][N];
    private final float[][] out = new float[2][HOP];
    private int outPos = HOP;
    private double position;
    private long lastStart;
    private boolean first = true;

    PhaseVocoderStretch(Track track, double speed) {
        this.track = track;
        this.speed = speed;
    }

    @Override
    public void seek(double sourceFrame) {
        position = sourceFrame;
        first = true;
        for (float[] a : acc) {
            java.util.Arrays.fill(a, 0);
        }
        outPos = HOP;
    }

    @Override
    public void read(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            if (outPos == HOP) {
                step();
            }
            left[i] = out[0][outPos];
            right[i] = out[1][outPos];
            outPos++;
        }
    }

    private void step() {
        long start = Math.round(position);
        double hop = start - lastStart;
        for (int ch = 0; ch < 2; ch++) {
            for (int i = 0; i < N; i++) {
                re[i] = Dsp.sample(track, ch, start + i) * WINDOW[i];
                im[i] = 0;
            }
            Fft.transform(re, im, false);
            for (int k = 0; k < BINS; k++) {
                magnitude[k] = Math.hypot(re[k], im[k]);
                phase[k] = Math.atan2(im[k], re[k]);
            }
            double[] last = lastPhase[ch];
            double[] synth = outPhase[ch];
            if (first) {
                System.arraycopy(phase, 0, synth, 0, BINS);
            } else {
                lockPhases(last, synth, hop);
            }
            System.arraycopy(phase, 0, last, 0, BINS);
            for (int k = 0; k < BINS; k++) {
                re[k] = magnitude[k] * Math.cos(synth[k]);
                im[k] = magnitude[k] * Math.sin(synth[k]);
            }
            for (int k = BINS; k < N; k++) { // mirror for a real signal
                re[k] = re[N - k];
                im[k] = -im[N - k];
            }
            Fft.transform(re, im, true);
            float[] a = acc[ch];
            for (int i = 0; i < N; i++) {
                a[i] += (float) (re[i] * WINDOW[i] * SCALE);
            }
            System.arraycopy(a, 0, out[ch], 0, HOP);
            System.arraycopy(a, HOP, a, 0, N - HOP);
            java.util.Arrays.fill(a, N - HOP, N, 0);
        }
        first = false;
        lastStart = start;
        outPos = 0;
        position += HOP * speed;
    }

    /**
     * Advances each spectral peak by its true frequency, then keeps every other bin's phase relation to
     * its nearest peak (identity phase locking).
     */
    private void lockPhases(double[] last, double[] synth, double hop) {
        int[] peaks = new int[BINS];
        int count = 0;
        for (int k = 2; k < BINS - 2; k++) {
            double m = magnitude[k];
            if (m > magnitude[k - 1] && m >= magnitude[k + 1] && m > magnitude[k - 2] && m >= magnitude[k + 2]) {
                peaks[count++] = k;
            }
        }
        if (count == 0) { // silence: just advance everything
            for (int k = 0; k < BINS; k++) {
                synth[k] += 2 * Math.PI * k / N * HOP;
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            int k = peaks[i];
            double expected = 2 * Math.PI * k * hop / N;
            double deviation = wrap(phase[k] - last[k] - expected);
            double trueFrequency = 2 * Math.PI * k / N + deviation / Math.max(1, hop);
            synth[k] += trueFrequency * HOP;
        }
        int nearest = 0;
        for (int k = 0; k < BINS; k++) {
            while (nearest + 1 < count && Math.abs(peaks[nearest + 1] - k) <= Math.abs(peaks[nearest] - k)) {
                nearest++;
            }
            int p = peaks[nearest];
            if (p != k) {
                synth[k] = synth[p] + phase[k] - phase[p];
            }
        }
    }

    private static double wrap(double angle) {
        return angle - 2 * Math.PI * Math.floor((angle + Math.PI) / (2 * Math.PI));
    }
}
