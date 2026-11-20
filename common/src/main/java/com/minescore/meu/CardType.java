package com.minescore.meu;

import java.util.Locale;

/** The six Multi-Effects Unit cards. A unit holds at most one of each. */
public enum CardType {
    DRIVE, MODULATION, DELAY, REVERB, AMP_CAB, DYNAMICS;

    public Effect defaultEffect() {
        return Effect.of(this).get(0);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The card item's registry name. */
    public String itemName() {
        return "card_" + id();
    }
}
