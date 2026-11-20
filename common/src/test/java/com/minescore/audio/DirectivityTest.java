package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DirectivityTest {
    private static final Vec3 FRONT = new Vec3(0.5, 0.5, 0);
    private static final Vec3 NORTH = new Vec3(0, 0, -1);

    private static float gainAt(double x, double z) {
        return Directivity.gain(FRONT, NORTH, new Vec3(x, 0.5, z), 0.3f);
    }

    @Test
    void fullStraightAheadAndSideOnIsSofter() {
        assertEquals(1f, gainAt(0.5, -10), 1e-6);
        assertEquals(Directivity.SIDE, gainAt(10.5, 0), 1e-4);
    }

    @Test
    void throughTheCabinetDropsToTheBehindVolume() {
        assertEquals(0.3f, gainAt(0.5, 10), 1e-6, "straight behind");
        assertEquals(0.3f, gainAt(4.5, 10), 1e-4, "behind and to one side still passes through the cabinet");
        float justBehind = gainAt(10.5, 0.5);
        assertTrue(justBehind < Directivity.SIDE && justBehind > 0.3f, "eases in just past side-on: " + justBehind);
    }

    @Test
    void dependsOnlyOnThePathNotDistance() {
        assertEquals(gainAt(3.5, -3), gainAt(30.5, -30), 1e-6);
    }
}
