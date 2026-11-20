package com.minescore.audio;

/**
 * A built-in test song every client can generate, so speaker systems can be tried before songs can be
 * cut to records. It has something in every band: a bass line for Subwoofers, chords for Middle Speakers,
 * and hi-hats and a bell melody for Tweeters, panned so Left and Right sound different.
 */
public final class DemoTrack {
    public static final String ID = "minescore:demo";

    private static final double BPM = 112;
    private static final int BARS = 8;
    /** A minor, F, C, G (two bars each), as root MIDI notes. */
    private static final int[] ROOTS = {45, 41, 48, 43};
    private static final int[][] CHORDS = {{0, 3, 7}, {0, 4, 7}, {0, 4, 7}, {0, 4, 7}};
    private static final int[] MELODY = {76, 79, 81, 79, 76, 74, 72, 74, 76, 72, 69, 72, 74, 76, 79, 81};

    private static Track cached;

    private DemoTrack() {}

    public static synchronized Track get() {
        if (cached == null) {
            cached = generate();
        }
        return cached;
    }

    private static Track generate() {
        double beat = 60 / BPM;
        int frames = (int) (BARS * 4 * beat * Track.SAMPLE_RATE);
        short[] out = new short[frames * 2];
        long noise = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < frames; i++) {
            double t = (double) i / Track.SAMPLE_RATE;
            double beats = t / beat;
            int bar = (int) (beats / 4);
            double inBeat = beats - Math.floor(beats);
            int root = ROOTS[(bar / 2) % ROOTS.length];

            // Bass: root note on every beat, octave up on the off-beats, decaying.
            double bassFreq = midi(root - 12 + (((int) (beats * 2)) % 2 == 1 ? 12 : 0));
            double inHalf = beats * 2 - Math.floor(beats * 2);
            double bass = Math.sin(2 * Math.PI * bassFreq * t) * Math.exp(-inHalf * 3) * 0.55;

            // Chords: soft saw-ish triad, swelling each bar.
            double chord = 0;
            for (int interval : CHORDS[(bar / 2) % CHORDS.length]) {
                double f = midi(root + 12 + interval);
                chord += (Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(4 * Math.PI * f * t)) / 3;
            }
            double inBar = beats / 4 - bar;
            chord *= 0.22 * (0.6 + 0.4 * Math.sin(Math.PI * inBar));

            // Hi-hat: short noise bursts on every eighth note.
            noise ^= noise << 13;
            noise ^= noise >>> 7;
            noise ^= noise << 17;
            double hat = ((noise >>> 11) / (double) (1L << 53) * 2 - 1) * Math.exp(-inHalf * 40) * 0.18;

            // Bell melody: one note per beat, high and bright.
            double bellFreq = midi(MELODY[((int) beats) % MELODY.length]);
            double bell = (Math.sin(2 * Math.PI * bellFreq * t) + 0.5 * Math.sin(2 * Math.PI * bellFreq * 3 * t))
                    * Math.exp(-inBeat * 5) * 0.16;

            // Chords lean left, bells lean right, so stereo assignment is audible.
            double left = bass + chord * 1.2 + hat + bell * 0.6;
            double right = bass + chord * 0.8 + hat + bell * 1.4;
            double fade = Math.min(1, Math.min(t / 0.05, (frames - i) / (0.5 * Track.SAMPLE_RATE)));
            out[i * 2] = clip(left * fade);
            out[i * 2 + 1] = clip(right * fade);
        }
        return Track.stereo(out);
    }

    private static double midi(int note) {
        return 440 * Math.pow(2, (note - 69) / 12.0);
    }

    private static short clip(double v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767 * 0.9)));
    }
}
