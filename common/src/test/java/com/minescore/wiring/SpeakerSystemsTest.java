package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SpeakerSystemsTest {
    private static void ok(WiringRules.Result result) {
        assertInstanceOf(WiringRules.Ok.class, result, () -> "expected Ok but got " + result);
    }

    @Test
    void speakersPlayOnlyWhenTheirBandReachesTheSource() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos manager = world.place("channel_manager", 2);
        BlockPos sub = world.place("subwoofer", 4);
        BlockPos sub2 = world.place("subwoofer", 6);
        BlockPos tweeter = world.place("tweeter_speaker", 8);
        BlockPos mid = world.place("middle_speaker", 10);

        // 12 ga runs through the Channel Manager; 20 ga does not, so its tweeter stays silent.
        ok(world.connect(mixer, "out_cm", manager, "in_source", Gauge.G12, true));
        ok(world.connect(mixer, "out_12", manager, "in_12", Gauge.G12, true));
        ok(world.connect(manager, "out_12", sub, "in", Gauge.G12, false));
        ok(world.connect(sub, "out", sub2, "in", Gauge.G12, false));
        ok(world.connect(manager, "out_20", tweeter, "in", Gauge.G20, false));
        ok(world.connect(mixer, "out_16", mid, "in", Gauge.G16, false));

        Map<BlockPos, SpeakerSystems.Feed> feeds = SpeakerSystems.speakers(world::cablesAt, mixer).stream()
                .collect(Collectors.toMap(SpeakerSystems.Feed::speaker, f -> f));
        assertEquals(3, feeds.size());
        assertEquals(0, feeds.get(sub).chainIndex());
        assertEquals(1, feeds.get(sub2).chainIndex());
        assertEquals(Gauge.G16, feeds.get(mid).band());
        assertTrue(!feeds.containsKey(tweeter), "tweeter has no 20 ga path to the mixer");
    }

    @Test
    void systemBlocksIncludeEverythingDownstream() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos sub = world.place("subwoofer", 40);
        BlockPos stranger = world.place("subwoofer", 80);
        ok(world.connect(mixer, "out_12", sub, "in", Gauge.G12, false));
        assertEquals(List.of(mixer, sub), List.copyOf(SpeakerSystems.blocks(world::cablesAt, mixer)));
        assertTrue(!SpeakerSystems.blocks(world::cablesAt, mixer).contains(stranger));
    }
}
