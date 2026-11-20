package com.minescore.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiringRulesTest {
    private FakeWireWorld world;
    private BlockPos mixer;
    private BlockPos manager;
    private BlockPos amp;
    private BlockPos sub;
    private BlockPos sub2;
    private BlockPos mid;
    private BlockPos tweeter;

    @BeforeEach
    void setUp() {
        world = new FakeWireWorld();
        mixer = world.place("audio_mixer", 0);
        manager = world.place("channel_manager", 2);
        amp = world.place("amplifier", 4);
        sub = world.place("subwoofer", 6);
        sub2 = world.place("subwoofer", 8);
        mid = world.place("middle_speaker", 10);
        tweeter = world.place("tweeter_speaker", 12);
    }

    private static void assertOk(WiringRules.Result result) {
        assertInstanceOf(WiringRules.Ok.class, result, () -> "expected Ok but got " + result);
    }

    private static void assertError(String key, WiringRules.Result result) {
        WiringRules.Error error = assertInstanceOf(WiringRules.Error.class, result, () -> "expected " + key);
        assertEquals(key, error.key());
    }

    @Test
    void speakerNeedsMatchingGaugeAndNoShift() {
        assertError("no_shift_for_speakers", world.connect(mixer, "out_12", sub, "in", Gauge.G12, true));
        assertError("wrong_gauge", world.connect(mixer, "out_12", sub, "in", Gauge.G16, false));
        assertError("band_mismatch", world.connect(mixer, "out_16", sub, "in", Gauge.G12, false));
        assertOk(world.connect(mixer, "out_12", sub, "in", Gauge.G12, false));
        assertOk(world.connect(mixer, "out_20", tweeter, "in", Gauge.G20, false));
    }

    @Test
    void hubsNeedShiftButAnyGauge() {
        assertError("shift_for_hubs", world.connect(mixer, "out_cm", manager, "in_source", Gauge.G20, false));
        assertOk(world.connect(mixer, "out_cm", manager, "in_source", Gauge.G20, true));
        assertOk(world.connect(mixer, "out_12", manager, "in_12", Gauge.G16, true));
        assertError("link_mismatch", world.connect(mixer, "out_amp", manager, "in_16", Gauge.G16, true));
    }

    @Test
    void clickingTheInputFirstStillRunsOutputToInput() {
        WiringRules.Result result = world.connect(sub, "in", mixer, "out_12", Gauge.G12, false);
        WiringRules.Ok ok = assertInstanceOf(WiringRules.Ok.class, result);
        assertTrue(ok.swapped());
        Cable cable = world.cables().get(0);
        assertEquals(mixer, cable.from().pos());
        assertEquals(sub, cable.to().pos());
        assertEquals(cable.path().get(0), world.endpoint(cable.from()).point());
    }

    @Test
    void amplifierNeverFeedsChannelManager() {
        assertError("link_mismatch", world.connect(amp, "out_12", manager, "in_source", Gauge.G12, true));
        assertError("not_allowed", world.connect(amp, "out_12", manager, "in_12", Gauge.G12, true));
    }

    @Test
    void inputsTakeOneCable() {
        assertOk(world.connect(mixer, "out_12", sub, "in", Gauge.G12, false));
        assertError("jack_in_use", world.connect(sub2, "out", sub, "in", Gauge.G12, false));
    }

    @Test
    void outputFeedsEitherOneHubOrManySpeakers() {
        assertOk(world.connect(mixer, "out_12", sub, "in", Gauge.G12, false));
        assertOk(world.connect(mixer, "out_12", sub2, "in", Gauge.G12, false));
        assertError("jack_feeds_speakers", world.connect(mixer, "out_12", manager, "in_12", Gauge.G12, true));

        FakeWireWorld other = new FakeWireWorld();
        BlockPos m = other.place("audio_mixer", 0);
        BlockPos cm = other.place("channel_manager", 2);
        BlockPos s = other.place("subwoofer", 4);
        assertOk(other.connect(m, "out_12", cm, "in_12", Gauge.G12, true));
        assertError("jack_feeds_hub", other.connect(m, "out_12", s, "in", Gauge.G12, false));
    }

    @Test
    void daisyChainsCannotLoop() {
        assertOk(world.connect(sub, "out", sub2, "in", Gauge.G12, false));
        assertError("loop", world.connect(sub2, "out", sub, "in", Gauge.G12, false));
    }

    @Test
    void blocksBelongToOneSystem() {
        BlockPos otherMixer = world.place("audio_mixer", 20);
        assertOk(world.connect(mixer, "out_cm", manager, "in_source", Gauge.G12, true));
        assertError("other_system", world.connect(otherMixer, "out_12", manager, "in_12", Gauge.G12, true));
    }

    @Test
    void lineAmplifierHandlesOneSpeakerType() {
        assertOk(world.connect(sub, "out", amp, "in_12", Gauge.G12, false));
        assertError("line_amp_band", world.connect(amp, "out_16", mid, "in", Gauge.G16, false));
        assertOk(world.connect(amp, "out_12", sub2, "in", Gauge.G12, false));
        assertError("amp_role", world.connect(mixer, "out_amp", amp, "in_source", Gauge.G12, true));
    }

    @Test
    void oneMainAmplifierPerSystem() {
        BlockPos amp2 = world.place("amplifier", 30);
        assertOk(world.connect(mixer, "out_amp", amp, "in_source", Gauge.G12, true));
        assertError("one_main_amp", world.connect(mixer, "out_12", amp2, "in_12", Gauge.G12, true));
    }

    @Test
    void latheConnectsToAnAmplifierOutput() {
        BlockPos lathe = world.place("disc_cutting_lathe", 40);
        assertError("lathe_needs_amp", world.connect(mixer, "out_12", lathe, "in_12", Gauge.G12, true));
        assertError("band_mismatch", world.connect(amp, "out_20", lathe, "in_12", Gauge.G16, true));
        assertOk(world.connect(amp, "out_20", lathe, "in_20", Gauge.G16, true));
    }

    @Test
    void linkJacksOnlyJoinTheirOwnPair() {
        // Mixer↔Channel Manager, Mixer↔Amplifier and Channel Manager↔Amplifier each have their own jacks.
        assertError("link_mismatch", world.connect(mixer, "out_cm", amp, "in_source", Gauge.G12, true));
        assertError("link_mismatch", world.connect(manager, "out_amp", amp, "in_source", Gauge.G12, true));
        assertOk(world.connect(manager, "out_amp", amp, "in_cm", Gauge.G12, true));
    }

    @Test
    void sameBlockAndSameDirectionAreRejected() {
        assertError("same_block", world.connect(manager, "out_12", manager, "in_12", Gauge.G12, true));
        assertError("need_in_and_out", world.connect(mixer, "out_12", manager, "out_12", Gauge.G12, true));
    }
}
