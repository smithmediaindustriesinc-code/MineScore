package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The Multi-Effects Unit's place in a system (SCOPE.md §3.10, §4.1). */
class MeuWiringTest {
    private static void ok(WiringRules.Result result) {
        assertInstanceOf(WiringRules.Ok.class, result, () -> "expected Ok but got " + result);
    }

    private static void refused(String key, WiringRules.Result result) {
        WiringRules.Error error = assertInstanceOf(WiringRules.Error.class, result, () -> "expected " + key + " but got " + result);
        assertEquals(key, error.key());
    }

    @Test
    void mixerThenMeuThenChannelManagerThenSpeakers() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos meu = world.place("multi_effects_unit", 2);
        BlockPos manager = world.place("channel_manager", 4);
        BlockPos sub = world.place("subwoofer", 6);
        ok(world.connect(mixer, "out_meu", meu, "in_source", Gauge.G12, true));
        ok(world.connect(mixer, "out_12", meu, "in_12", Gauge.G12, true));
        ok(world.connect(meu, "out_cm", manager, "in_meu", Gauge.G16, true));
        ok(world.connect(meu, "out_12", manager, "in_12", Gauge.G12, true));
        ok(world.connect(manager, "out_12", sub, "in", Gauge.G12, false));

        List<SpeakerSystems.Feed> feeds = SpeakerSystems.speakers(world::cablesAt, mixer);
        assertEquals(1, feeds.size(), "the band runs through the MEU and on to the speaker");
        assertEquals(manager, feeds.get(0).channelManager());
        assertEquals(meu, SpeakerSystems.first(world::cablesAt, mixer, BlockKind.MEU));
        assertEquals(mixer, SpeakerSystems.sourceOf(world::cablesAt, sub));
    }

    @Test
    void meuLinksCountTowardTheBoostCap() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos mixer = world.place("audio_mixer", 0);
        BlockPos meu = world.place("multi_effects_unit", 2);
        BlockPos amp = world.place("amplifier", 4);
        ok(world.connect(mixer, "out_meu", meu, "in_source", Gauge.G20, true));
        ok(world.connect(meu, "out_amp", amp, "in_meu", Gauge.G12, true));
        assertEquals(2, SpeakerSystems.boostCap(world::cablesAt, amp), "the 20 ga Mixer → MEU link is the thinnest");
    }

    @Test
    void onlyASourceFeedsTheMeu() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos vinyl = world.place("vinyl_player", 0);
        BlockPos manager = world.place("channel_manager", 2);
        BlockPos meu = world.place("multi_effects_unit", 4);
        refused("not_allowed", world.connect(vinyl, "out_12", meu, "in_12", Gauge.G12, true));
        refused("link_mismatch", world.connect(manager, "out_amp", meu, "in_source", Gauge.G12, true));
    }

    @Test
    void aLatheTakesTheMeuOrAnAmplifierNotBoth() {
        FakeWireWorld world = new FakeWireWorld();
        BlockPos meu = world.place("multi_effects_unit", 0);
        BlockPos amp = world.place("amplifier", 2);
        BlockPos lathe = world.place("disc_cutting_lathe", 4);
        ok(world.connect(meu, "out_lathe", lathe, "in_meu", Gauge.G12, true));
        refused("lathe_meu_or_amp", world.connect(amp, "out_12", lathe, "in_12", Gauge.G12, true));

        FakeWireWorld other = new FakeWireWorld();
        BlockPos meu2 = other.place("multi_effects_unit", 0);
        BlockPos amp2 = other.place("amplifier", 2);
        BlockPos lathe2 = other.place("disc_cutting_lathe", 4);
        ok(other.connect(amp2, "out_16", lathe2, "in_16", Gauge.G16, true));
        refused("lathe_meu_or_amp", other.connect(meu2, "out_lathe", lathe2, "in_meu", Gauge.G12, true));
    }
}
