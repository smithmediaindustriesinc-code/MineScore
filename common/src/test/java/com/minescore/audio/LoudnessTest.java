package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.playback.Blowouts;
import com.minescore.wiring.Gauge;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class LoudnessTest {
    /** A bass-heavy track: a loud 60 Hz tone with a faint 6 kHz one on top, quiet for its second half. */
    private static Track bassTrack(int seconds) {
        int frames = seconds * Track.SAMPLE_RATE;
        short[] samples = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = (double) i / Track.SAMPLE_RATE;
            double level = i < frames / 2 ? 1 : 0.05;
            double v = level * (0.7 * Math.sin(2 * Math.PI * 60 * t) + 0.01 * Math.sin(2 * Math.PI * 6000 * t));
            samples[i * 2] = samples[i * 2 + 1] = (short) Math.round(v * 32767);
        }
        return Track.stereo(samples);
    }

    @Test
    void measuresEachBandOverTime() throws IOException {
        Loudness loudness = Loudness.measure(bassTrack(4), 200, 2000);
        double sub = loudness.levelDb(Gauge.G12, 0.5);
        double tweeter = loudness.levelDb(Gauge.G20, 0.5);
        assertEquals(-6, sub, 1.5, "a 0.7 sine is about 6 dB under full scale");
        assertTrue(tweeter < sub - 30, "the faint high tone is far quieter");
        assertTrue(loudness.levelDb(Gauge.G12, 3.5) < sub - 20, "the quiet half is quieter");

        Loudness back = Loudness.decode(loudness.encode());
        assertEquals(sub, back.levelDb(Gauge.G12, 0.5));
        assertEquals(sub - Loudness.REFERENCE_DB, back.excessDb(Gauge.G12, 0.5));
        assertThrows(IOException.class, () -> Loudness.decode(new byte[] {1, 2, 3}));
    }

    @Test
    void longSongsStaySmall() {
        Loudness loudness = Loudness.measure(Track.stereo(new short[2 * Track.SAMPLE_RATE * 10]), 200, 2000);
        assertTrue(loudness.encode().length < 8192);
    }

    @Test
    void bassBlowsFirst() {
        // At 100%, music at normal level is safe everywhere.
        for (Gauge band : Gauge.values()) {
            assertFalse(Blowouts.overLimit(1, EqCurve.FLAT, band, 0));
        }
        // At 300% (+9.5 dB) the Subwoofer goes, but not the others.
        assertTrue(Blowouts.overLimit(3, EqCurve.FLAT, Gauge.G12, 0));
        assertFalse(Blowouts.overLimit(3, EqCurve.FLAT, Gauge.G16, 0));
        assertFalse(Blowouts.overLimit(3, EqCurve.FLAT, Gauge.G20, 0));
        // Quiet music lets it go louder; an EQ boost in its band pushes it harder.
        assertFalse(Blowouts.overLimit(3, EqCurve.FLAT, Gauge.G12, -6));
        float[] bass = new float[EqCurve.BANDS];
        bass[1] = 6;
        assertTrue(Blowouts.overLimit(1.5f, new EqCurve(bass), Gauge.G12, 0));
        assertFalse(Blowouts.overLimit(1.5f, new EqCurve(bass), Gauge.G20, 0));
    }

    @Test
    void eqCurvesClampAndAddUp() {
        float[] gains = new float[EqCurve.BANDS];
        gains[0] = 20;
        EqCurve curve = new EqCurve(gains);
        assertEquals(12, curve.gains()[0], "held to ±12 dB");
        assertEquals(12, curve.then(curve).gains()[0]);
        assertEquals(new EqCurve(gains), curve, "compared by value");
        assertTrue(EqCurve.FLAT.isFlat());
        assertEquals(12, curve.boostDb(Gauge.G12));
        assertEquals(0, curve.boostDb(Gauge.G20));
    }
}
