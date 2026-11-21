package com.minescore.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minescore.audio.EqCurve;
import org.junit.jupiter.api.Test;

/** The Amplifier's redstone EQ (SCOPE.md §7.4): 15 = 400%, sides set their groups, the bottom moves them all. */
class AmplifierRedstoneTest {
    private static final double DB_400 = 20 * Math.log10(4);

    private static float[] eq(float... gains) {
        float[] all = new float[EqCurve.BANDS];
        System.arraycopy(gains, 0, all, 0, gains.length);
        return all;
    }

    @Test
    void noSignalLeavesTheScreensEq() {
        EqCurve gui = new EqCurve(eq(3, -2, 1));
        assertEquals(gui, AmplifierBlockEntity.redstoneEq(gui, new int[4], 0));
    }

    @Test
    void aSideSetsItsOwnBands() {
        int[] sides = {0, 15, 0, 0}; // left: bands 4–5 to 400%, held to the EQ's range
        float[] gains = AmplifierBlockEntity.redstoneEq(EqCurve.FLAT, sides, 0).gains();
        assertArrayEquals(eq(0, 0, 0, EqCurve.RANGE, EqCurve.RANGE), gains, 1e-4f);
        sides = new int[] {4, 0, 0, 0}; // back: 4/15 × 400% ≈ 107%
        gains = AmplifierBlockEntity.redstoneEq(EqCurve.FLAT, sides, 0).gains();
        assertEquals(20 * Math.log10(4 / 15.0 * 4), gains[0], 1e-4);
        assertEquals(0, gains[3], 1e-6);
    }

    @Test
    void theBottomSetsTheLoudestBandAndKeepsTheRatios() {
        EqCurve gui = new EqCurve(eq(6, 0, -3));
        float[] gains = AmplifierBlockEntity.redstoneEq(gui, new int[4], 4).gains();
        double target = 20 * Math.log10(4 / 15.0 * 4);
        assertEquals(target, gains[0], 1e-4, "the loudest band");
        assertEquals(target - 6, gains[1], 1e-4, "6 dB under it, as on the screen");
        assertEquals(target - 9, gains[2], 1e-4);
    }

    @Test
    void aBandHeldAtTheLimitKeepsItsRatioForLater() {
        EqCurve gui = new EqCurve(eq(12, 0));
        float[] gains = AmplifierBlockEntity.redstoneEq(gui, new int[4], 15).gains();
        assertEquals(EqCurve.RANGE, gains[0], 1e-4);
        assertEquals(DB_400 - 12, gains[1], 1e-4, "still 12 dB under the loudest, even though that one is held");
    }
}
