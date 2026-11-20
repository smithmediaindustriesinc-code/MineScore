package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The synthesized sounds: each loud where it starts and quiet by its end, in every style, and the styles differ. */
class SynthRecipesTest {
    @Test
    void theShatterCracksThenSettles() {
        short[] pcm = SynthRecipes.render(SynthSound.RECORD_SHATTER, SynthRecipes.Style.MINECRAFT);
        assertTrue(pcm.length > Track.SAMPLE_RATE);
        int start = peak(pcm, 0, Track.SAMPLE_RATE / 10);
        int end = peak(pcm, pcm.length - Track.SAMPLE_RATE / 10, pcm.length);
        assertTrue(start > 20_000, "a sharp crack: " + start);
        assertTrue(end < start / 4, "shards settling: " + end);
    }

    @Test
    void everySoundInEveryStyleIsShortAndFadesOut() {
        for (SynthSound sound : SynthSound.values()) {
            for (SynthRecipes.Style style : SynthRecipes.Style.values()) {
                short[] pcm = SynthRecipes.render(sound, style);
                assertTrue(pcm.length > 0 && pcm.length < Track.SAMPLE_RATE * 2, sound + " " + style);
                assertTrue(peak(pcm, 0, pcm.length) > 20_000, sound + " " + style + " is heard");
                assertTrue(Math.abs(pcm[pcm.length - 1]) < 200, sound + " " + style + " ends without a click");
            }
            assertFalse(Arrays.equals(SynthRecipes.render(sound, SynthRecipes.Style.RETRO),
                    SynthRecipes.render(sound, SynthRecipes.Style.STUDIO)), sound + ": the packs sound different");
        }
    }

    private static int peak(short[] pcm, int from, int to) {
        int peak = 0;
        for (int i = from; i < to; i++) {
            peak = Math.max(peak, Math.abs(pcm[i]));
        }
        return peak;
    }
}
