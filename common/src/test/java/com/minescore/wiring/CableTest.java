package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CableTest {
    private static Cable cableThroughAnchor() {
        return new Cable(UUID.randomUUID(), new PortRef(BlockPos.ZERO, "out_12"), BlockKind.SOURCE,
                new PortRef(new BlockPos(10, 0, 0), "in"), BlockKind.SPEAKER, Signal.BAND_12, Gauge.G12,
                List.of(new BlockPos(5, 2, 0)),
                List.of(new Vec3(0.5, 0.5, 1), new Vec3(5.2, 2.3, 0.5), new Vec3(5.8, 2.3, 0.5), new Vec3(10.5, 0.5, 1)));
    }

    @Test
    void savesAndLoads() {
        Cable cable = cableThroughAnchor();
        assertEquals(cable, Cable.load(cable.save()));
    }

    @Test
    void removingAnAnchorRunsTheCableStraightThrough() {
        Cable cable = cableThroughAnchor().withoutAnchor(new BlockPos(5, 2, 0));
        assertTrue(cable.anchors().isEmpty());
        assertEquals(List.of(new Vec3(0.5, 0.5, 1), new Vec3(10.5, 0.5, 1)), cable.path());
    }

    @Test
    void cablesSag() {
        List<Vec3> points = CableGeometry.sample(new Vec3(0, 10, 0), new Vec3(10, 10, 0));
        Vec3 middle = points.get(points.size() / 2);
        assertTrue(middle.y < 9.9, "middle should hang below the ends, was " + middle.y);
        assertEquals(new Vec3(0, 10, 0), points.get(0));
        assertEquals(new Vec3(10, 10, 0), points.get(points.size() - 1));
    }

    @Test
    void cuttersPickTheCableBeingLookedAt() {
        Cable cable = cableThroughAnchor();
        Vec3 target = CableGeometry.sample(cable.path().get(0), cable.path().get(1)).get(5);
        Vec3 eye = target.add(0, 0, -3);
        assertSame(cable, CableGeometry.pick(List.of(cable), eye, new Vec3(0, 0, 1), 6, 0.3));
        assertNull(CableGeometry.pick(List.of(cable), eye, new Vec3(0, 0, -1), 6, 0.3));
    }
}
