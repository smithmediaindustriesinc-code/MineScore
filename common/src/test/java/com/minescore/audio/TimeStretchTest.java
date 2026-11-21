package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.fx.SongAnalysis;
import com.minescore.audio.fx.TimeStretch;
import com.minescore.mp3.Mp3State;
import com.minescore.song.SongMeta;
import org.junit.jupiter.api.Test;

/** The Speed effect's algorithms (SCOPE.md §5.5), and analysing songs for synced delays and the Tuner. */
class TimeStretchTest {
    private static Track tone(double frequency, int seconds) {
        int frames = seconds * Track.SAMPLE_RATE;
        short[] s = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            s[i * 2] = s[i * 2 + 1] = (short) Math.round(0.5 * Math.sin(2 * Math.PI * frequency * i / Track.SAMPLE_RATE) * 32767);
        }
        return Track.stereo(s);
    }

    /** Frequency from zero crossings, and level, over one second of output after a short settle. */
    private static double[] measure(TimeStretch stretch) {
        int n = Track.SAMPLE_RATE;
        float[] l = new float[n], r = new float[n];
        stretch.seek(Track.SAMPLE_RATE * 0.5);
        stretch.read(l, r, 4410); // settle
        stretch.read(l, r, n);
        int crossings = 0;
        double sum = 0;
        for (int i = 1; i < n; i++) {
            if ((l[i - 1] < 0) != (l[i] < 0)) {
                crossings++;
            }
            sum += l[i] * l[i];
        }
        return new double[] {crossings / 2.0, Math.sqrt(sum / n)};
    }

    @Test
    void wsolaAndThePhaseVocoderKeepThePitch() {
        Track track = tone(440, 6);
        for (TimeStretch.Algorithm algorithm : new TimeStretch.Algorithm[] {TimeStretch.Algorithm.WSOLA, TimeStretch.Algorithm.PHASE_VOCODER}) {
            for (double speed : new double[] {0.5, 0.8, 1.5, 2}) {
                double[] m = measure(TimeStretch.create(algorithm, track, speed, true));
                assertEquals(440, m[0], 12, algorithm + " at " + speed + " moved the pitch");
                assertEquals(0.354, m[1], 0.12, algorithm + " at " + speed + " changed the level");
            }
        }
    }

    @Test
    void tapeLetsThePitchFollow() {
        double[] m = measure(TimeStretch.create(TimeStretch.Algorithm.TAPE, tone(440, 6), 0.5, true));
        assertEquals(220, m[0], 3);
    }

    @Test
    void atFullSpeedItIsAnExactCopy() {
        Track track = tone(300, 2);
        TimeStretch stretch = TimeStretch.create(TimeStretch.Algorithm.WSOLA, track, 1, true);
        float[] l = new float[1000], r = new float[1000];
        stretch.seek(500);
        stretch.read(l, r, 1000);
        for (int i = 0; i < 1000; i++) {
            assertEquals(track.left(500 + i), l[i], 1e-7);
        }
    }

    @Test
    void findsTempoKeyAndTuning() {
        // A kick drum every half second (120 BPM) over an A minor chord (A, C, E) tuned to A = 442 Hz.
        int seconds = 20;
        int frames = seconds * Track.SAMPLE_RATE;
        short[] s = new short[frames * 2];
        double a = 442;
        double[] chord = {a / 2, a / 2 * Math.pow(2, 3 / 12.0), a / 2 * Math.pow(2, 7 / 12.0), a};
        for (int i = 0; i < frames; i++) {
            double t = (double) i / Track.SAMPLE_RATE;
            double v = 0;
            for (double f : chord) {
                v += 0.12 * Math.sin(2 * Math.PI * f * t);
            }
            int k = i % (Track.SAMPLE_RATE / 2);
            if (k < 2000) {
                v += 0.6 * Math.exp(-k / 300.0) * Math.sin(2 * Math.PI * 60 * k / Track.SAMPLE_RATE);
            }
            s[i * 2] = s[i * 2 + 1] = (short) Math.round(Math.max(-1, Math.min(1, v)) * 32767);
        }
        SongAnalysis analysis = SongAnalysis.of(Track.stereo(s));
        assertEquals(120, analysis.bpm(), 3);
        assertEquals(9, analysis.key(), "A");
        assertTrue(analysis.minor());
        assertEquals(442, analysis.tuningHz(), 1.5);
        assertEquals("A minor", analysis.keyName());
    }

    @Test
    void speedChangesDoNotJumpTheSong() {
        Mp3State state = new Mp3State();
        state.addSong(new SongMeta("a".repeat(64), "mp3", "Song", 100));
        state.play(0);
        assertEquals(10, state.position(200), 1e-9);
        state.setSpeed(0.5f, 200);
        assertEquals(10, state.position(200), 1e-9, "carries on from the same place");
        assertEquals(15, state.position(400), 1e-9, "then runs at half speed");
        assertTrue(!state.tick(3799, new java.util.Random(1)));
        assertTrue(state.tick(3800, new java.util.Random(1)), "the last 90 s take 180 s at half speed: it ends at tick 200 + 3600");
    }
}
