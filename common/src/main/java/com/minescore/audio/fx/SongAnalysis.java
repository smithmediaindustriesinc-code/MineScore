package com.minescore.audio.fx;

import com.minescore.audio.Track;

/**
 * What the Multi-Effects Unit works out about a song: its tempo (for synced delays), its key and its
 * tuning (for the Tuner). Done once per song on the client, in the background.
 *
 * @param bpm      beats per minute
 * @param key      0 = C … 11 = B, or -1 if unknown
 * @param minor    major or minor key
 * @param tuningHz the pitch the song tunes A to (440 for most music)
 */
public record SongAnalysis(double bpm, int key, boolean minor, double tuningHz) {
    public static final SongAnalysis UNKNOWN = new SongAnalysis(120, -1, false, 440);
    private static final String[] NAMES = {"C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"};
    /** Krumhansl–Kessler key profiles. */
    private static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    private static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
    private static final int FFT = 4096;
    /** Look at up to this much of the song: enough to know it, quick to work out. */
    private static final int MAX_SECONDS = 120;

    /** "A minor", or "?" if unknown. */
    public String keyName() {
        return key < 0 ? "?" : NAMES[key] + (minor ? " minor" : " major");
    }

    public static SongAnalysis of(Track track) {
        if (track.frames() < Track.SAMPLE_RATE) {
            return UNKNOWN;
        }
        int frames = Math.min(track.frames(), MAX_SECONDS * Track.SAMPLE_RATE);
        return new SongAnalysis(tempo(track, frames), 0, false, 440).withPitch(track, frames);
    }

    /** Tempo from how often the music's attacks repeat (autocorrelation of its onset strength). */
    static double tempo(Track track, int frames) {
        int hop = 256;
        int count = frames / hop;
        double[] level = new double[count];
        for (int h = 0; h < count; h++) {
            double energy = 1e-9;
            for (int i = h * hop; i < (h + 1) * hop; i++) {
                float m = (track.left(i) + track.right(i)) * 0.5f;
                energy += m * m;
            }
            level[h] = Math.log(energy);
        }
        // An onset is a jump above the last ~50 ms, not just above the step before: steady notes beating
        // against each other wobble from step to step, but don't climb over their own average.
        double[] onset = new double[count];
        double mean = 0;
        for (int h = 8; h < count; h++) {
            double recent = 0;
            for (int j = h - 8; j < h; j++) {
                recent += level[j];
            }
            onset[h] = Math.max(0, level[h] - recent / 8);
            mean += onset[h];
        }
        mean /= Math.max(1, count);
        for (int h = 0; h < count; h++) {
            onset[h] = Math.max(0, onset[h] - mean);
        }
        // Smear each onset over its neighbours, so a beat that falls between two steps still lines up with itself.
        double[] smooth = new double[count];
        for (int h = 1; h + 1 < count; h++) {
            smooth[h] = 0.25 * onset[h - 1] + 0.5 * onset[h] + 0.25 * onset[h + 1];
        }
        onset = smooth;
        double rate = (double) Track.SAMPLE_RATE / hop;
        int minLag = (int) (rate * 60 / 200), maxLag = (int) (rate * 60 / 60);
        double best = -1;
        int bestLag = (int) (rate * 60 / 120);
        double[] score = new double[maxLag + 2];
        for (int lag = minLag; lag <= maxLag && lag < count; lag++) {
            double sum = 0;
            for (int i = lag; i < count; i++) {
                sum += onset[i] * onset[i - lag];
            }
            sum /= count - lag; // per overlapping step, so long lags aren't penalised for overlapping less
            double bpm = rate * 60 / lag;
            double weight = Math.exp(-0.5 * Math.pow(Math.log(bpm / 120) / Math.log(2) / 0.9, 2)); // prefer tempos near 120
            score[lag] = sum * weight;
            if (score[lag] > best) {
                best = score[lag];
                bestLag = lag;
            }
        }
        double lag = bestLag;
        if (bestLag > minLag && bestLag < maxLag) { // between two lags: parabolic peak
            double a = score[bestLag - 1], b = score[bestLag], c = score[bestLag + 1];
            double denominator = a - 2 * b + c;
            if (denominator != 0) {
                lag += 0.5 * (a - c) / denominator;
            }
        }
        return Math.round(rate * 60 / lag * 10) / 10.0;
    }

    /** Key from the song's pitch-class profile, and tuning from how far its notes sit off the 440 Hz grid. */
    private SongAnalysis withPitch(Track track, int frames) {
        double[] chroma = new double[12];
        double sin = 0, cos = 0;
        double[] re = new double[FFT], im = new double[FFT];
        double[] window = new double[FFT];
        for (int i = 0; i < FFT; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT);
        }
        int step = FFT * 2;
        for (int start = 0; start + FFT < frames; start += step) {
            for (int i = 0; i < FFT; i++) {
                re[i] = (track.left(start + i) + track.right(start + i)) * 0.5 * window[i];
                im[i] = 0;
            }
            Fft.transform(re, im, false);
            int lo = (int) (55.0 * FFT / Track.SAMPLE_RATE), hi = (int) (2000.0 * FFT / Track.SAMPLE_RATE);
            double[] mag = new double[hi + 2];
            for (int k = lo - 1; k <= hi + 1; k++) {
                mag[k] = Math.hypot(re[k], im[k]);
            }
            for (int k = lo; k <= hi; k++) {
                if (mag[k] <= mag[k - 1] || mag[k] < mag[k + 1]) {
                    continue; // only spectral peaks
                }
                double a = mag[k - 1], b = mag[k], c = mag[k + 1];
                double offset = 0.5 * (a - c) / (a - 2 * b + c + 1e-12);
                double frequency = (k + offset) * Track.SAMPLE_RATE / FFT;
                double midi = 69 + 12 * Math.log(frequency / 440) / Math.log(2);
                int note = (int) Math.round(midi);
                double weight = Math.log1p(b);
                chroma[Math.floorMod(note, 12)] += weight;
                double cents = (midi - note) * 100;
                sin += weight * Math.sin(2 * Math.PI * cents / 100);
                cos += weight * Math.cos(2 * Math.PI * cents / 100);
            }
        }
        double meanCents = Math.atan2(sin, cos) / (2 * Math.PI) * 100;
        double tuning = 440 * Math.pow(2, meanCents / 1200);
        int bestKey = -1;
        boolean bestMinor = false;
        double best = Double.NEGATIVE_INFINITY;
        for (int key = 0; key < 12; key++) {
            for (boolean minor : new boolean[] {false, true}) {
                double r = correlation(chroma, minor ? MINOR : MAJOR, key);
                if (r > best) {
                    best = r;
                    bestKey = key;
                    bestMinor = minor;
                }
            }
        }
        boolean heard = false;
        for (double v : chroma) {
            heard |= v > 0;
        }
        return new SongAnalysis(bpm, heard ? bestKey : -1, bestMinor, Math.round(tuning * 10) / 10.0);
    }

    /** Pearson correlation of the song's pitch classes with a key profile rooted on {@code key}. */
    static double correlation(double[] chroma, double[] profile, int key) {
        double mc = 0, mp = 0;
        for (int i = 0; i < 12; i++) {
            mc += chroma[i];
            mp += profile[i];
        }
        mc /= 12;
        mp /= 12;
        double num = 0, dc = 0, dp = 0;
        for (int i = 0; i < 12; i++) {
            double c = chroma[(i + key) % 12] - mc;
            double p = profile[i] - mp;
            num += c * p;
            dc += c * c;
            dp += p * p;
        }
        return num / Math.sqrt(dc * dp + 1e-12);
    }
}
