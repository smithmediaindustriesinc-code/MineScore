package com.minescore.audio;

/** Decoded audio, always 16-bit stereo at {@link #SAMPLE_RATE}. */
public final class Track {
    public static final int SAMPLE_RATE = 44100;

    /** Interleaved left/right samples. */
    private final short[] samples;

    private Track(short[] samples) {
        this.samples = samples;
    }

    /** Wraps interleaved stereo samples already at {@link #SAMPLE_RATE}. */
    public static Track stereo(short[] interleaved) {
        return new Track(interleaved);
    }

    /**
     * Converts decoded PCM of any channel count and rate. Mono is copied to both sides; extra channels
     * beyond two are dropped; other rates are linearly resampled.
     */
    public static Track convert(short[] interleaved, int channels, int sampleRate) {
        int inFrames = interleaved.length / channels;
        if (inFrames == 0) {
            return new Track(new short[0]);
        }
        long outFrames = (long) inFrames * SAMPLE_RATE / sampleRate;
        short[] out = new short[(int) outFrames * 2];
        double step = (double) sampleRate / SAMPLE_RATE;
        for (int i = 0; i < outFrames; i++) {
            double src = i * step;
            int a = Math.min((int) src, inFrames - 1);
            int b = Math.min(a + 1, inFrames - 1);
            double t = src - a;
            for (int ch = 0; ch < 2; ch++) {
                int c = Math.min(ch, channels - 1);
                double v = interleaved[a * channels + c] * (1 - t) + interleaved[b * channels + c] * t;
                out[i * 2 + ch] = (short) Math.round(v);
            }
        }
        return new Track(out);
    }

    public int frames() {
        return samples.length / 2;
    }

    public double seconds() {
        return (double) frames() / SAMPLE_RATE;
    }

    /** Left sample of a frame, from -1 to 1. */
    public float left(int frame) {
        return samples[frame * 2] / 32768f;
    }

    public float right(int frame) {
        return samples[frame * 2 + 1] / 32768f;
    }
}
