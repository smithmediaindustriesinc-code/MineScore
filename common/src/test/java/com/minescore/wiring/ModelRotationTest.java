package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ModelRotationTest {
    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1e-9, "x");
        assertEquals(expected.y, actual.y, 1e-9, "y");
        assertEquals(expected.z, actual.z, 1e-9, "z");
    }

    @Test
    void yRotationTurnsTheFrontLikeBlockstates() {
        Vec3 front = new Vec3(8, 8, 0);
        assertVec(new Vec3(16, 8, 8), ModelRotation.apply(front, 0, 90));
        assertVec(new Vec3(8, 8, 16), ModelRotation.apply(front, 0, 180));
        assertVec(new Vec3(0, 8, 8), ModelRotation.apply(front, 0, 270));
    }

    @Test
    void xRotationTurnsUpToNorth() {
        assertVec(new Vec3(8, 8, 0), ModelRotation.apply(new Vec3(8, 16, 8), 90, 0));
        assertVec(new Vec3(8, 0, 8), ModelRotation.apply(new Vec3(8, 16, 8), 180, 0));
        assertVec(new Vec3(16, 8, 8), ModelRotation.apply(new Vec3(8, 16, 8), 90, 90));
    }

    @Test
    void anyAngleTurnsClockwiseLikeBlockstates() {
        Vec3 p = new Vec3(3, 5, 1);
        for (int y = 0; y < 360; y += 90) {
            assertVec(ModelRotation.apply(p, 0, y), ModelRotation.applyY(p, y));
        }
        Vec3 northEast = ModelRotation.directionY(new Vec3(0, 0, -1), 45);
        assertEquals(Math.sqrt(0.5), northEast.x, 1e-9, "45° turns north toward east");
        assertEquals(-Math.sqrt(0.5), northEast.z, 1e-9);
        assertVec(p, ModelRotation.inverseY(ModelRotation.applyY(p, 45), 45));
    }

    @Test
    void inverseUndoesApply() {
        Vec3 p = new Vec3(3, 4, 13);
        for (int x = 0; x < 360; x += 90) {
            for (int y = 0; y < 360; y += 90) {
                assertVec(p, ModelRotation.inverse(ModelRotation.apply(p, x, y), x, y));
            }
        }
    }
}
