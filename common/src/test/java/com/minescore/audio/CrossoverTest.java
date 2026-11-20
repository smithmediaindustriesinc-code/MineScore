package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.wiring.Gauge;
import org.junit.jupiter.api.Test;

class CrossoverTest {
    /** RMS of a unit sine at {@code frequency} after the band's filter (skipping the first half second). */
    private static double rms(Gauge band, double frequency) {
        Crossover filter = Crossover.forBand(band, Track.SAMPLE_RATE, 200, 2000);
        double sum = 0;
        int n = 0;
        for (int i = 0; i < Track.SAMPLE_RATE; i++) {
            float out = filter.process((float) Math.sin(2 * Math.PI * frequency * i / Track.SAMPLE_RATE));
            if (i > Track.SAMPLE_RATE / 2) {
                sum += out * out;
                n++;
            }
        }
        return Math.sqrt(sum / n) * Math.sqrt(2); // 1.0 = passes at full level
    }

    @Test
    void eachBandPassesItsOwnRange() {
        assertTrue(rms(Gauge.G12, 50) > 0.9, "sub passes 50 Hz");
        assertTrue(rms(Gauge.G16, 630) > 0.85, "mid passes 630 Hz");
        assertTrue(rms(Gauge.G20, 8000) > 0.9, "tweeter passes 8 kHz");
    }

    @Test
    void eachBandBlocksTheOthers() {
        assertTrue(rms(Gauge.G12, 2000) < 0.02, "sub blocks 2 kHz");
        assertTrue(rms(Gauge.G16, 40) < 0.05, "mid blocks 40 Hz");
        assertTrue(rms(Gauge.G16, 10000) < 0.05, "mid blocks 10 kHz");
        assertTrue(rms(Gauge.G20, 200) < 0.02, "tweeter blocks 200 Hz");
    }

    @Test
    void crossoverPointsAreHalfPower() {
        // Linkwitz-Riley filters are -6 dB (0.5) at the crossover frequency.
        double sub = rms(Gauge.G12, 200);
        double high = rms(Gauge.G20, 2000);
        assertTrue(Math.abs(sub - 0.5) < 0.05, "sub at 200 Hz was " + sub);
        assertTrue(Math.abs(high - 0.5) < 0.05, "tweeter at 2 kHz was " + high);
    }
}
