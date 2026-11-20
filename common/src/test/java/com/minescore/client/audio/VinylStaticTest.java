package com.minescore.client.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Vinyl static: quiet enough to sit under the music, with some crackles, and the same on every speaker. */
class VinylStaticTest {
    @Test
    void itSitsFarBelowFullScaleButCrackles() {
        VinylStatic noise = new VinylStatic();
        double energy = 0;
        int frames = 44_100 * 10, pops = 0;
        float last = 0;
        for (int f = 0; f < frames; f++) {
            float s = noise.next(f, 42);
            energy += s * s;
            if (Math.abs(s) > 0.01f && Math.abs(last) <= 0.01f) {
                pops++;
            }
            last = s;
        }
        double db = 20 * Math.log10(Math.sqrt(energy / frames));
        assertTrue(db < -40 && db > -65, "about 50 dB down: " + db);
        assertTrue(pops > 10 && pops < 150, "a few crackles a second: " + pops);
    }

    @Test
    void everySpeakerHearsTheSameStatic() {
        VinylStatic a = new VinylStatic(), b = new VinylStatic();
        for (int f = 0; f < 10_000; f++) {
            assertEquals(a.next(f, 7), b.next(f, 7));
        }
    }
}
