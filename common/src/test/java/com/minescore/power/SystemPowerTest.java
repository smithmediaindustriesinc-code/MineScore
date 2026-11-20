package com.minescore.power;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.wiring.Gauge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Power budgets, minimums and the daisy-chain curve (SCOPE.md §5.3, §5.4, S1). */
class SystemPowerTest {
    private static SystemPower.Load load(Gauge band, int chainIndex) {
        return new SystemPower.Load(SystemPower.speakerCost(band, chainIndex), null);
    }

    /** SCOPE.md §5.3's example: Mixer + Amplifier, 2 Subwoofers, 4 Middle Speakers and 4 Tweeters, each its own chain. */
    private static SystemPower.Demand example() {
        List<SystemPower.Load> speakers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            speakers.add(load(Gauge.G12, i));
        }
        for (int i = 0; i < 4; i++) {
            speakers.add(load(Gauge.G16, i));
        }
        for (int i = 0; i < 4; i++) {
            speakers.add(load(Gauge.G20, i));
        }
        return new SystemPower.Demand(100, speakers);
    }

    @Test
    void theScopesExampleDraws275AndPlaysAtFullVolume() {
        SystemPower.Demand demand = example();
        assertEquals(275, demand.total());
        SystemPower.Allocation power = SystemPower.allocate(demand, 500, true);
        assertTrue(power.running());
        for (float f : power.factors()) {
            assertEquals(1, f);
        }
    }

    @Test
    void lessThanTheHubsNeedAndNothingPlays() {
        assertFalse(SystemPower.allocate(example(), 99, true).running());
        assertTrue(SystemPower.allocate(example(), 100, true).running());
    }

    @Test
    void pastTheBudgetSpeakersFollowTheCurveInChainOrder() {
        // 100 for the hubs, 55 for both Subwoofers, 10 for the first Middle Speaker: then it runs out.
        SystemPower.Allocation power = SystemPower.allocate(example(), 165, true);
        float[] f = power.factors();
        assertEquals(1, f[0]);
        assertEquals(1, f[1]);
        assertEquals(1, f[2]);
        assertEquals(0.95f, f[3], 1e-6);
        assertEquals(0.9025f, f[4], 1e-6);
        assertEquals((float) Math.pow(0.95, 7), f[9], 1e-6, "every speaker after the first past the budget continues the curve");
    }

    @Test
    void aLineAmplifierStartsTheCurveAgain() {
        BlockPos lineAmp = new BlockPos(5, 64, 5);
        List<SystemPower.Load> speakers = List.of(
                new SystemPower.Load(10, null), new SystemPower.Load(15, null),
                new SystemPower.Load(10, lineAmp), new SystemPower.Load(15, lineAmp));
        float[] f = SystemPower.allocate(new SystemPower.Demand(50, speakers), 50, true).factors();
        assertArrayEquals(new float[] {0.95f, 0.9025f, 0.95f, 0.9025f}, f, 1e-6f);
    }

    @Test
    void noneModeNeedsNothingAndLosesNothing() {
        SystemPower.Allocation power = SystemPower.allocate(example(), 0, false);
        assertTrue(power.running());
        for (float f : power.factors()) {
            assertEquals(1, f);
        }
    }

    @Test
    void theCurveFloorsAtTenPercentThenShares() {
        assertEquals(0.1f, SystemPower.curve(45, 45), 1e-6, "the 45th speaker reaches the floor");
        assertEquals(0.9f, SystemPower.curve(1, 46), 1e-6, "one more lowers the first by 5 points");
        assertEquals(0.1f, SystemPower.curve(46, 46), 1e-6);
        assertEquals(0.5f, SystemPower.curve(1, 54), 1e-6, "nine more: the first at 50%");
        assertEquals(0.5f - 0.4f * 26 / 53, SystemPower.curve(27, 54), 1e-5, "the ones between evenly spaced");
        assertEquals(0.1f, SystemPower.curve(1, 62), 1e-6, "at N₀ = 62 they're all at 10%");
        assertEquals(0.05f, SystemPower.curve(7, 124), 1e-6, "then share 10% × N₀ ÷ n");
    }

    @Test
    void createCutsTakeThreeTimesSixtyFourOverTheRpm() {
        assertEquals(60, KineticSettings.cutTicks(64), 1e-9, "64 RPM: 3 s");
        assertEquals(3840, KineticSettings.cutTicks(1), 1e-9, "1 RPM: 192 s");
    }

    @Test
    void speakersCostMoreFurtherDownTheirChain() {
        assertEquals(25, SystemPower.speakerCost(Gauge.G12, 0));
        assertEquals(35, SystemPower.speakerCost(Gauge.G12, 2));
        assertEquals(5, SystemPower.speakerCost(Gauge.G20, 0));
    }
}
