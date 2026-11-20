package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.wiring.Gauge;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The numbers in SCOPE.md §7.3. */
class OcclusionTest {
    @Test
    void harderBlocksMuffleMore() {
        assertEquals(3.8, Occlusion.blockDb(0.2f, 1, false), 1e-6, "leaves");
        assertEquals(5, Occlusion.blockDb(0.5f, 1, false), 1e-6, "dirt");
        assertEquals(9, Occlusion.blockDb(1.5f, 1, false), 1e-6, "stone");
        assertEquals(23, Occlusion.blockDb(5, 1, false), 1e-6, "iron block");
        assertEquals(24, Occlusion.blockDb(50, 1, false), 1e-6, "obsidian hits the cap");
        assertEquals(24, Occlusion.blockDb(-1, 1, false), 1e-6, "bedrock");
        assertEquals(4.5, Occlusion.blockDb(1.5f, 0.5, false), 1e-6, "a stone slab");
        assertEquals(0, Occlusion.blockDb(0, 0, false), "flowers, torches: nothing to stop it");
        assertEquals(12.4, Occlusion.blockDb(0.8f, 1, true), 1e-6, "wool loses twice as much");
    }

    @Test
    void bassGetsThroughBest() {
        assertTrue(Occlusion.bandScale(Gauge.G12) < Occlusion.bandScale(Gauge.G16));
        assertTrue(Occlusion.bandScale(Gauge.G16) < Occlusion.bandScale(Gauge.G20));
        assertEquals(1, Occlusion.bandScale(null));
        assertEquals(0.5, Occlusion.gain(6.0206), 1e-3);
    }

    @Test
    void walksEveryBlockOnThePathOnce() {
        List<BlockPos> straight = Occlusion.blocksBetween(new Vec3(0.5, 64.5, 0.5), new Vec3(5.5, 64.5, 0.5));
        assertEquals(6, straight.size());
        assertEquals(new BlockPos(0, 64, 0), straight.get(0));
        assertEquals(new BlockPos(5, 64, 0), straight.get(5));

        List<BlockPos> diagonal = Occlusion.blocksBetween(new Vec3(0.2, 64.7, 0.4), new Vec3(-7.3, 60.1, 9.9));
        assertEquals(new HashSet<>(diagonal).size(), diagonal.size(), "no block twice");
        assertEquals(new BlockPos(-8, 60, 9), diagonal.get(diagonal.size() - 1));
        for (int i = 1; i < diagonal.size(); i++) {
            assertEquals(1, diagonal.get(i).distManhattan(diagonal.get(i - 1)), "each step crosses one face");
        }
    }
}
