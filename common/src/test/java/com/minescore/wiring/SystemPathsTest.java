package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** What each speaker's sound passes through on its way from the source (SCOPE.md §6.1). */
class SystemPathsTest {
    private static void ok(WiringRules.Result result) {
        assertInstanceOf(WiringRules.Ok.class, result, () -> "expected Ok but got " + result);
    }

    @Test
    void feedsKnowTheirHubsLineAmpsAndBranches() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos manager = world.place("channel_manager", 2);
        BlockPos amp = world.place("amplifier", 4);
        BlockPos sub = world.place("subwoofer", 6);
        BlockPos sub2 = world.place("subwoofer", 8);
        BlockPos line = world.place("amplifier", 10);
        BlockPos sub3 = world.place("subwoofer", 12);

        ok(world.connect(mixer, "out_cm", manager, "in_source", Gauge.G12, true));
        ok(world.connect(mixer, "out_12", manager, "in_12", Gauge.G12, true));
        ok(world.connect(manager, "out_amp", amp, "in_cm", Gauge.G16, true)); // a 16 ga link: 300% cap
        ok(world.connect(manager, "out_12", amp, "in_12", Gauge.G20, true)); // band wires don't count
        ok(world.connect(amp, "out_12", sub, "in", Gauge.G12, false));
        ok(world.connect(sub, "out", sub2, "in", Gauge.G12, false));
        ok(world.connect(sub2, "out", line, "in_12", Gauge.G12, false));
        ok(world.connect(line, "out_12", sub3, "in", Gauge.G12, false));

        Map<BlockPos, SpeakerSystems.Feed> feeds = SpeakerSystems.speakers(world::cablesAt, mixer).stream()
                .collect(Collectors.toMap(SpeakerSystems.Feed::speaker, f -> f));
        assertEquals(3, feeds.size());

        SpeakerSystems.Feed first = feeds.get(sub);
        assertEquals(manager, first.channelManager());
        assertEquals(amp, first.mainAmp());
        assertTrue(first.direct(), "wired straight to the amp: has its own trim");
        assertEquals(sub, first.branch());

        SpeakerSystems.Feed chained = feeds.get(sub2);
        assertEquals(1, chained.chainIndex());
        assertFalse(chained.direct());
        assertEquals(sub, chained.branch(), "the first speaker's trim scales its chain");

        SpeakerSystems.Feed afterLine = feeds.get(sub3);
        assertEquals(List.of(line), afterLine.lineAmps());
        assertEquals(0, afterLine.chainIndex(), "a Line Amplifier starts the chain again");
        assertEquals(sub, afterLine.branch());
        assertEquals(amp, afterLine.mainAmp());

        assertEquals(3, SpeakerSystems.boostCap(world::cablesAt, amp));
        assertTrue(SpeakerSystems.isLineAmp(world::cablesAt, line));
        assertFalse(SpeakerSystems.isLineAmp(world::cablesAt, amp));
        assertEquals(mixer, SpeakerSystems.sourceOf(world::cablesAt, sub3));
        assertEquals(manager, SpeakerSystems.first(world::cablesAt, mixer, BlockKind.CHANNEL_MANAGER));
        assertEquals(List.of(amp, line), SpeakerSystems.downstream(world::cablesAt, mixer, BlockKind.AMPLIFIER));
    }

    @Test
    void boostCapFollowsTheThinnestHubLink() {
        assertEquals(4, SpeakerSystems.capFor(12));
        assertEquals(3, SpeakerSystems.capFor(16));
        assertEquals(2, SpeakerSystems.capFor(20));
        assertEquals(1, SpeakerSystems.capFor(0), "not linked: no boost");

        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos amp = world.place("amplifier", 2);
        ok(world.connect(mixer, "out_12", amp, "in_12", Gauge.G12, true));
        assertEquals(1, SpeakerSystems.boostCap(world::cablesAt, amp), "band wires alone don't set the cap");
        ok(world.connect(mixer, "out_amp", amp, "in_source", Gauge.G12, true));
        assertEquals(4, SpeakerSystems.boostCap(world::cablesAt, amp), "a 12 ga orange link: 400%");

        // Through a Channel Manager, the purple and teal links both count: the thinner one wins.
        FakeWireWorld chained = new FakeWireWorld();
        BlockPos mixer2 = chained.place("audio_mixer", 0);
        BlockPos manager = chained.place("channel_manager", 2);
        BlockPos amp2 = chained.place("amplifier", 4);
        ok(chained.connect(mixer2, "out_cm", manager, "in_source", Gauge.G20, true));
        ok(chained.connect(manager, "out_amp", amp2, "in_cm", Gauge.G12, true));
        assertEquals(2, SpeakerSystems.boostCap(chained::cablesAt, amp2), "the 20 ga purple link holds it to 200%");
    }

    @Test
    void aLoneSpeakerHasNoSource() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos sub = world.place("subwoofer", 0);
        assertNull(SpeakerSystems.sourceOf(world::cablesAt, sub));
    }
}
