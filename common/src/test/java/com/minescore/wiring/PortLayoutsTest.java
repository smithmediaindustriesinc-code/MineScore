package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortLayoutsTest {
    @Test
    void everyWiredBlockHasALayout() {
        Map<String, PortLayouts.Layout> all = PortLayouts.all();
        assertEquals(Set.of("audio_mixer", "writers_desk", "channel_manager", "amplifier", "tweeter_speaker",
                "middle_speaker", "subwoofer", "disc_cutting_lathe", "vinyl_player", "multi_effects_unit"), all.keySet());
    }

    @Test
    void jackCountsMatchTheScope() {
        // The Mixer's 5 outputs plus the olive one to a Multi-Effects Unit (2026-11-19).
        assertEquals(6, PortLayouts.get("audio_mixer").ports().stream().filter(Port::output).count());
        PortLayouts.Layout cm = PortLayouts.get("channel_manager");
        assertEquals(5, cm.ports().stream().filter(p -> !p.output()).count());
        assertEquals(4, cm.ports().stream().filter(Port::output).count());
        PortLayouts.Layout amp = PortLayouts.get("amplifier");
        assertEquals(6, amp.ports().stream().filter(p -> !p.output()).count());
        assertEquals(3, amp.ports().stream().filter(Port::output).count());
        PortLayouts.Layout meu = PortLayouts.get("multi_effects_unit");
        assertEquals(4, meu.ports().stream().filter(p -> !p.output()).count());
        assertEquals(6, meu.ports().stream().filter(Port::output).count());
    }

    @Test
    void speakersCarryTheirOwnBand() {
        assertEquals(Gauge.G12, PortLayouts.get("subwoofer").port("in").orElseThrow().signal().band());
        assertEquals(Gauge.G16, PortLayouts.get("middle_speaker").port("in").orElseThrow().signal().band());
        assertEquals(Gauge.G20, PortLayouts.get("tweeter_speaker").port("in").orElseThrow().signal().band());
    }

    @Test
    void clicksPickTheNearestJack() {
        PortLayouts.Layout amp = PortLayouts.get("amplifier");
        assertEquals("in_16", amp.nearest(new Vec3(10, 7, 16), 1).orElseThrow().id());
        assertEquals("out_20", amp.nearest(new Vec3(13.5, 3, 16), 1).orElseThrow().id());
        assertEquals("in_cm", amp.nearest(new Vec3(2.5, 7, 16), 1).orElseThrow().id());
        assertTrue(amp.nearest(new Vec3(8, 14.5, 16), 1).isEmpty(), "top of the back panel has no jack");
    }

    @Test
    void hubLinksAreOnTheLeftHalfAndBandsOnTheRight() {
        // Seen from behind, left is low x.
        for (String hub : new String[] {"audio_mixer", "writers_desk", "channel_manager", "amplifier"}) {
            for (Port port : PortLayouts.get(hub).ports()) {
                if (port.signal().isLink()) {
                    assertTrue(port.box().maxX <= 8, hub + " " + port.id() + " should be on the left half");
                } else {
                    assertTrue(port.box().minX >= 8, hub + " " + port.id() + " should be on the right half");
                }
            }
        }
    }

    @Test
    void jacksStandHalfAPixelOutAndAllTheSameDepth() {
        for (PortLayouts.Layout layout : PortLayouts.all().values()) {
            for (Port port : layout.ports()) {
                double depth = port.box().maxZ - port.box().minZ;
                assertEquals(0.6, depth, 1e-9, port.id() + " depth"); // 0.1 into the block, 0.5 out of it
            }
        }
    }
}
