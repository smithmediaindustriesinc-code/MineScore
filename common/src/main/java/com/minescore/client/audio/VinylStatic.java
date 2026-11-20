package com.minescore.client.audio;

/**
 * A record's surface noise (SCOPE.md §3.8): a soft hiss, a gentle low rumble, and now and then a crackle or
 * a pop. It's worked out from the track frame (and the track), not from a running random generator, so every
 * speaker playing the same record gets the same crackles at the same moment, as if they were in the groove.
 * At 100% it sits about 50 dB under full scale, so you hear it in the quiet passages and lose it under the music.
 */
final class VinylStatic {
    private static final float HISS = 0.0032f, RUMBLE = 0.0025f, CRACKLE = 0.045f;
    /** Chance a crackle starts on any one frame: about four a second. */
    private static final double CRACKLE_CHANCE = 4.0 / 44_100;

    private float hissLow, rumble, pop;

    void reset() {
        hissLow = rumble = pop = 0;
    }

    /** The static at {@code frame}, about -1..1 times the levels above. */
    float next(long frame, int seed) {
        long h = mix(frame * 0x9E3779B97F4A7C15L + seed);
        float white = ((h & 0xFFFF) / 32768f) - 1f;
        hissLow += (white - hissLow) * 0.35f;           // soften the hiss a touch, like worn vinyl
        rumble += (white - rumble) * 0.002f;            // a slow, low wander
        double roll = ((h >>> 16) & 0xFFFFFF) / (double) 0x1000000;
        if (roll < CRACKLE_CHANCE) {
            float size = 0.25f + ((h >>> 40) & 0xFF) / 255f * 0.75f;
            pop = ((h >>> 48) & 1) == 0 ? size : -size;
        }
        pop *= 0.92f;                                   // each crackle dies away in a couple of milliseconds
        // the softened hiss and the slow rumble come out quieter than white noise, so they're scaled back up
        return hissLow * HISS * 2 + rumble * RUMBLE * 20 + pop * CRACKLE * (white * 0.5f + 0.5f);
    }

    /** SplitMix64's finaliser: a good spread of bits from a counter. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
