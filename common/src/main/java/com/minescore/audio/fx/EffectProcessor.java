package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.CardType;
import com.minescore.meu.Effect;
import com.minescore.meu.EffectChain;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a Multi-Effects Unit's cards over a stereo signal, in slot order, each blended with the dry sound by
 * its mix knob. Settings can change while it runs: effects that stay the same keep their state (a reverb
 * tail carries on), and only new or changed effects start fresh. Speed is not done here (see
 * {@link TimeStretch}); Expression moves another card's knob.
 */
public final class EffectProcessor {
    private record Stage(CardSettings card, Fx fx) {}

    private final List<Stage> stages = new ArrayList<>();
    private float[] dryLeft = new float[0], dryRight = new float[0];

    public EffectProcessor(EffectChain chain, Fx.Context context) {
        update(chain, context);
    }

    public void update(EffectChain chain, Fx.Context context) {
        List<CardSettings> cards = expressed(chain);
        List<Stage> next = new ArrayList<>();
        for (CardSettings card : cards) {
            Stage reuse = null;
            for (Stage stage : stages) {
                if (stage.card().effect() == card.effect() && next.stream().noneMatch(s -> s.fx() == stage.fx())) {
                    reuse = stage;
                    break;
                }
            }
            Fx fx = reuse != null ? reuse.fx() : Fx.create(card.effect(), context);
            if (fx == null) {
                continue;
            }
            fx.set(card, context);
            next.add(new Stage(card, fx));
        }
        stages.clear();
        stages.addAll(next);
    }

    /**
     * The active cards with the expression pedal applied: an Expression effect sweeps one knob of another
     * card from its "from" to its "to" point (both as a share of that knob's travel).
     */
    static List<CardSettings> expressed(EffectChain chain) {
        List<CardSettings> cards = new ArrayList<>(chain.active());
        CardSettings pedal = chain.active(Effect.EXPRESSION);
        if (pedal != null) {
            int targetCard = Math.round(pedal.value("target_card"));
            CardType target = targetCard < 0 ? null : CardType.values()[targetCard];
            int knob = Math.round(pedal.value("target_knob")) - 1;
            for (int i = 0; i < cards.size(); i++) {
                CardSettings card = cards.get(i);
                if (card.type() == target && knob < card.effect().knobs().size()) {
                    float from = pedal.value("from");
                    float to = pedal.value("to");
                    float fraction = from + (to - from) * chain.expression();
                    cards.set(i, card.withValue(knob, card.effect().knobs().get(knob).at(fraction)));
                }
            }
        }
        return cards;
    }

    /** True if there's nothing to do, so the sound can skip the unit entirely. */
    public boolean isEmpty() {
        return stages.isEmpty();
    }

    public void process(float[] left, float[] right, int n) {
        if (dryLeft.length < n) {
            dryLeft = new float[n];
            dryRight = new float[n];
        }
        for (Stage stage : stages) {
            int mixKnob = stage.card().effect().mixKnob();
            float mix = mixKnob < 0 ? 1 : stage.card().value(mixKnob);
            if (mix <= 0) {
                continue; // fully dry: nothing to hear, so nothing to work out
            }
            if (mix < 1) {
                System.arraycopy(left, 0, dryLeft, 0, n);
                System.arraycopy(right, 0, dryRight, 0, n);
            }
            stage.fx().process(left, right, n);
            if (mix < 1) {
                for (int i = 0; i < n; i++) {
                    left[i] = dryLeft[i] + (left[i] - dryLeft[i]) * mix;
                    right[i] = dryRight[i] + (right[i] - dryRight[i]) * mix;
                }
            }
            for (int i = 0; i < n; i++) { // an effect gone wild must never reach the speakers as noise
                if (!Float.isFinite(left[i]) || !Float.isFinite(right[i])) {
                    stage.fx().reset();
                    java.util.Arrays.fill(left, 0, n, 0);
                    java.util.Arrays.fill(right, 0, n, 0);
                    break;
                }
            }
        }
    }

    public void reset() {
        stages.forEach(stage -> stage.fx().reset());
    }
}
