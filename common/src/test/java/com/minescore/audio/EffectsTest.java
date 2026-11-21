package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.fx.EffectProcessor;
import com.minescore.audio.fx.Fx;
import com.minescore.meu.CardSettings;
import com.minescore.meu.CardType;
import com.minescore.meu.Effect;
import com.minescore.meu.EffectChain;
import com.minescore.meu.Preset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Multi-Effects Unit's cards, presets and effects (SCOPE.md §5.5). */
class EffectsTest {
    private static final int N = Track.SAMPLE_RATE;

    /** One second of a 220 Hz tone with a little 3 kHz on top, at half volume, through a chain. */
    private static float[][] run(EffectChain chain) {
        float[] left = new float[N], right = new float[N];
        for (int i = 0; i < N; i++) {
            double t = (double) i / Track.SAMPLE_RATE;
            left[i] = right[i] = (float) (0.4 * Math.sin(2 * Math.PI * 220 * t) + 0.1 * Math.sin(2 * Math.PI * 3000 * t));
        }
        EffectProcessor processor = new EffectProcessor(chain, Fx.Context.DEFAULT);
        for (int start = 0; start < N; start += 1024) {
            int n = Math.min(1024, N - start);
            float[] l = java.util.Arrays.copyOfRange(left, start, start + n), r = java.util.Arrays.copyOfRange(right, start, start + n);
            processor.process(l, r, n);
            System.arraycopy(l, 0, left, start, n);
            System.arraycopy(r, 0, right, start, n);
        }
        return new float[][] {left, right};
    }

    private static double rms(float[] x) {
        double sum = 0;
        for (int i = x.length / 2; i < x.length; i++) {
            sum += x[i] * x[i];
        }
        return Math.sqrt(sum / (x.length / 2.0));
    }

    @Test
    void aFreshCardLeavesTheSoundAlone() {
        float[] dry = run(new EffectChain(List.of(), false, 0))[0];
        for (Effect effect : Effect.values()) {
            float[] out = run(new EffectChain(List.of(CardSettings.of(effect)), false, 0.5f))[0];
            for (int i = 0; i < N; i++) {
                assertEquals(dry[i], out[i], 1e-5, effect + " changed the sound before it was turned up");
            }
        }
    }

    @Test
    void everyEffectStaysSaneTurnedUp() {
        double dry = rms(run(new EffectChain(List.of(), false, 0))[0]);
        for (Effect effect : Effect.values()) {
            CardSettings card = CardSettings.of(effect);
            float[][] out = run(new EffectChain(List.of(card.withValue(effect.mixKnob(), 0.5f)), false, 0.5f));
            for (float[] side : out) {
                for (float v : side) {
                    assertTrue(Float.isFinite(v) && Math.abs(v) < 4, effect + " ran away: " + v);
                }
            }
            double level = rms(out[0]);
            if (effect != Effect.GATE) {
                assertTrue(level > dry * 0.1 && level < dry * 4, effect + " changed the level too much: " + level / dry);
            }
        }
    }

    @Test
    void mixAtZeroAndBypassLeaveTheSoundAlone() {
        float[] dry = run(new EffectChain(List.of(), false, 0))[0];
        CardSettings reverb = CardSettings.of(Effect.HALL);
        float[] noMix = run(new EffectChain(List.of(reverb.withValue(Effect.HALL.mixKnob(), 0)), false, 0))[0];
        float[] bypassed = run(new EffectChain(List.of(reverb.withBypass(true)), false, 0))[0];
        float[] master = run(new EffectChain(List.of(reverb), true, 0))[0];
        for (int i = 0; i < N; i++) {
            assertEquals(dry[i], noMix[i], 1e-6);
            assertEquals(dry[i], bypassed[i], 1e-6);
            assertEquals(dry[i], master[i], 1e-6);
        }
    }

    @Test
    void driveAndCompressionCountTowardBlowouts() {
        CardSettings drive = CardSettings.of(Effect.OVERDRIVE).withValue(Effect.OVERDRIVE.knob("level"), 6)
                .withValue(Effect.OVERDRIVE.mixKnob(), 1);
        CardSettings comp = CardSettings.of(Effect.COMPRESSOR).withValue(Effect.COMPRESSOR.knob("makeup"), 4);
        EffectChain chain = new EffectChain(List.of(drive, comp), false, 0);
        assertTrue(chain.loudnessDb() > 6);
        assertEquals(0, new EffectChain(List.of(CardSettings.of(Effect.HALL)), false, 0).loudnessDb());
        assertEquals(2, chain.changedEffects(), "a turned-up drive and a compressor (not the Dynamics card's first effect)");
        assertEquals(0, new EffectChain(List.of(CardSettings.of(Effect.OVERDRIVE)), false, 0).loudnessDb(), "dry drive adds nothing");
        assertEquals(0, new EffectChain(List.of(CardSettings.defaults(CardType.REVERB)), false, 0).changedEffects());
    }

    @Test
    void speedComesFromTheModulationCard() {
        CardSettings slow = CardSettings.of(Effect.SPEED).withValue(0, 80);
        assertEquals(0.8f, new EffectChain(List.of(slow), false, 0).speed(), 1e-6);
        assertEquals(1, new EffectChain(List.of(slow.withBypass(true)), false, 0).speed());
        assertEquals(0.25f, new EffectChain(List.of(slow.withValue(0, 5)), false, 0).speed(), 1e-6, "held to 25%");
    }

    @Test
    void cardsSaveAndLoadAndClampTheirKnobs() {
        CardSettings card = CardSettings.of(Effect.STEREO).withValue(0, 99999).withBypass(true);
        assertEquals(2000, card.value("time"), "held to the knob's range");
        CardSettings back = CardSettings.load(card.save());
        assertNotNull(back);
        assertEquals(card, back);
        assertEquals(CardType.DELAY, back.type());
        assertFalse(CardSettings.defaults(CardType.DRIVE).changed());
    }

    @Test
    void builtInPresetsUseEachCardOnceAndRoundTrip() {
        Set<String> names = new HashSet<>();
        for (Preset preset : Preset.BUILT_IN) {
            assertTrue(names.add(preset.name()), "duplicate preset " + preset.name());
            Set<CardType> types = new HashSet<>();
            for (CardSettings card : preset.cards()) {
                assertTrue(types.add(card.type()), preset.name() + " uses " + card.type() + " twice");
            }
            Preset back = Preset.load(preset.save());
            assertEquals(preset, back);
        }
        assertTrue(names.contains("Slowed + Reverb"));
    }

    @Test
    void theExpressionPedalSweepsTheChosenKnob() {
        // Sweeps the Reverb card's first knob (size) from 0 to 1.
        CardSettings pedal = CardSettings.of(Effect.EXPRESSION).withValue(Effect.EXPRESSION.knob("target_card"), 3);
        CardSettings reverb = CardSettings.of(Effect.HALL);
        float[] at = new float[2];
        for (int i = 0; i < 2; i++) {
            EffectChain chain = new EffectChain(List.of(reverb, pedal), false, i);
            List<CardSettings> cards = com.minescore.audio.fx.EffectProcessorAccess.expressed(chain);
            at[i] = cards.get(0).value("size");
        }
        assertEquals(0, at[0], 1e-6);
        assertEquals(1, at[1], 1e-6);
    }
}
