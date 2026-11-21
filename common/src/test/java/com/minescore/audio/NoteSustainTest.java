package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.notes.NoteSustain;
import org.junit.jupiter.api.Test;

/** Holding a dying note block sound for a long note, and cutting a short one off (SCOPE.md §9.9). */
class NoteSustainTest {
    private static final int RATE = 44_100;

    /** A plucked tone: A4 (MIDI 69) dying away over about a second. */
    private static float[] pluck() {
        float[] s = new float[RATE];
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            s[i] = (float) (Math.sin(2 * Math.PI * 440 * t) * Math.exp(-4 * t) * Math.min(1, t / 0.005));
        }
        return s;
    }

    private static double rms(float[] x, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += x[i] * x[i];
        }
        return Math.sqrt(sum / (to - from));
    }

    @Test
    void aLongNoteHoldsItsLevelWithoutClicks() {
        float[] sample = pluck();
        NoteSustain.Loop loop = NoteSustain.find(sample, RATE, 69);
        assertNotNull(loop);
        float[] note = NoteSustain.render(sample, loop, 1, 3 * RATE, RATE / 20);
        assertEquals(3 * RATE + RATE / 20, note.length);
        double early = rms(note, RATE / 5, RATE / 5 + 4410), late = rms(note, 2 * RATE + RATE / 2, 2 * RATE + RATE / 2 + 4410);
        assertTrue(late > early * 0.7 && late < early * 1.3, "still as loud after 2.5 s: " + early + " vs " + late);
        float jump = 0;
        for (int i = 1; i < 3 * RATE; i++) {
            jump = Math.max(jump, Math.abs(note[i] - note[i - 1]));
        }
        assertTrue(jump < 0.1f, "no clicks at the loop's joins: " + jump); // a 440 Hz sine moves at most ~0.06 a sample
        assertEquals(0, note[note.length - 1], 1e-3, "faded out at the end");
    }

    @Test
    void aShortNoteIsCutOffWithAFade() {
        float[] sample = pluck();
        float[] note = NoteSustain.render(sample, NoteSustain.find(sample, RATE, 69), 1, RATE / 10, RATE / 20);
        assertEquals(RATE / 10 + RATE / 20, note.length, "a tenth of a second, then its fade");
        assertEquals(0, note[note.length - 1], 1e-3);
    }
}
