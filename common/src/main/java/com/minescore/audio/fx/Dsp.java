package com.minescore.audio.fx;

import com.minescore.audio.Track;

/** Small building blocks shared by the effects: filters, delay lines and helpers. */
public final class Dsp {
    public static final double SR = Track.SAMPLE_RATE;

    private Dsp() {}

    public static float dbToGain(double db) {
        return (float) Math.pow(10, db / 20);
    }

    /** A track's sample at any frame; silence outside it. */
    public static float sample(Track track, int channel, long frame) {
        if (frame < 0 || frame >= track.frames()) {
            return 0;
        }
        return channel == 0 ? track.left((int) frame) : track.right((int) frame);
    }

    /** Cubic (Catmull-Rom) interpolation of a track between frames. */
    public static float sampleAt(Track track, int channel, double position) {
        long i = (long) Math.floor(position);
        float t = (float) (position - i);
        float a = sample(track, channel, i - 1), b = sample(track, channel, i), c = sample(track, channel, i + 1),
                d = sample(track, channel, i + 2);
        return b + 0.5f * t * (c - a + t * (2 * a - 5 * b + 4 * c - d + t * (3 * (b - c) + d - a)));
    }

    /** A biquad filter (RBJ cookbook), direct form 2 transposed. */
    public static final class Biquad {
        private double b0 = 1, b1, b2, a1, a2;
        private double z1, z2;

        public float process(float x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return (float) y;
        }

        public void reset() {
            z1 = 0;
            z2 = 0;
        }

        private Biquad set(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
            return this;
        }

        private static double w0(double f) {
            return 2 * Math.PI * Math.min(f, SR * 0.45) / SR;
        }

        public Biquad lowPass(double f, double q) {
            double w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return set((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        public Biquad highPass(double f, double q) {
            double w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return set((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        /** Band-pass with 0 dB peak gain. */
        public Biquad bandPass(double f, double q) {
            double w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return set(alpha, 0, -alpha, 1 + alpha, -2 * cos, 1 - alpha);
        }

        public Biquad peak(double f, double q, double db) {
            double a = Math.pow(10, db / 40), w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return set(1 + alpha * a, -2 * cos, 1 - alpha * a, 1 + alpha / a, -2 * cos, 1 - alpha / a);
        }

        public Biquad lowShelf(double f, double db) {
            double a = Math.pow(10, db / 40), w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / 2 * Math.sqrt(2);
            double s = 2 * Math.sqrt(a) * alpha;
            return set(a * ((a + 1) - (a - 1) * cos + s), 2 * a * ((a - 1) - (a + 1) * cos), a * ((a + 1) - (a - 1) * cos - s),
                    (a + 1) + (a - 1) * cos + s, -2 * ((a - 1) + (a + 1) * cos), (a + 1) + (a - 1) * cos - s);
        }

        public Biquad highShelf(double f, double db) {
            double a = Math.pow(10, db / 40), w = w0(f), cos = Math.cos(w), alpha = Math.sin(w) / 2 * Math.sqrt(2);
            double s = 2 * Math.sqrt(a) * alpha;
            return set(a * ((a + 1) + (a - 1) * cos + s), -2 * a * ((a - 1) + (a + 1) * cos), a * ((a + 1) + (a - 1) * cos - s),
                    (a + 1) - (a - 1) * cos + s, 2 * ((a - 1) - (a + 1) * cos), (a + 1) - (a - 1) * cos - s);
        }
    }

    /** A one-pole low-pass, for tone controls and smoothing. */
    public static final class OnePole {
        private float a = 1;
        private float y;

        public OnePole cutoff(double f) {
            a = (float) (1 - Math.exp(-2 * Math.PI * Math.min(f, SR * 0.45) / SR));
            return this;
        }

        public float process(float x) {
            y += a * (x - y);
            return y;
        }

        public void reset() {
            y = 0;
        }
    }

    /** A delay line that can be read between samples. */
    public static final class DelayLine {
        private final float[] buffer;
        private int write;

        public DelayLine(int maxSamples) {
            buffer = new float[maxSamples + 4];
        }

        public void push(float x) {
            buffer[write] = x;
            write = (write + 1) % buffer.length;
        }

        /** The sample {@code delay} samples ago (1 = the one just pushed), linearly interpolated. */
        public float read(double delay) {
            delay = Math.max(1, Math.min(buffer.length - 3, delay));
            double pos = write - delay;
            while (pos < 0) {
                pos += buffer.length;
            }
            int i = (int) pos;
            float t = (float) (pos - i);
            float a = buffer[i % buffer.length];
            float b = buffer[(i + 1) % buffer.length];
            return a + (b - a) * t;
        }

        public void reset() {
            java.util.Arrays.fill(buffer, 0);
        }

        public int capacity() {
            return buffer.length - 4;
        }
    }

    /** A Schroeder all-pass, for reverbs. */
    public static final class Allpass {
        private final float[] buffer;
        private int index;

        public Allpass(int length) {
            buffer = new float[Math.max(1, length)];
        }

        public float process(float x, float gain) {
            float delayed = buffer[index];
            float y = delayed - gain * x;
            buffer[index] = x + gain * y;
            index = (index + 1) % buffer.length;
            return y;
        }

        public void reset() {
            java.util.Arrays.fill(buffer, 0);
        }
    }
}
