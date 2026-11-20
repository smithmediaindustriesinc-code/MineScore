package com.minescore.meu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a Multi-Effects Unit does to the sound, in processing order (its slot order, empty slots
 * left out). Sent to clients with the playback, so they can run the effects without the unit's chunk.
 *
 * @param bypass     master bypass (or disabled by redstone): the sound passes through untouched
 * @param expression the expression pedal, 0–1
 */
public record EffectChain(List<CardSettings> cards, boolean bypass, float expression) {
    public EffectChain {
        cards = List.copyOf(cards);
        expression = Math.max(0, Math.min(1, expression));
    }

    /** The cards actually doing something. */
    public List<CardSettings> active() {
        return bypass ? List.of() : cards.stream().filter(c -> !c.bypass()).toList();
    }

    public int activeCount() {
        return active().size();
    }

    public @Nullable CardSettings active(Effect effect) {
        for (CardSettings card : active()) {
            if (card.effect() == effect) {
                return card;
            }
        }
        return null;
    }

    /** How fast the song plays: the Modulation card's Speed effect, or 1. */
    public float speed() {
        CardSettings speed = active(Effect.SPEED);
        return speed == null ? 1 : speed.value("speed") / 100f;
    }

    /** Effects changed from their defaults: each adds 2 s when a Lathe prints them onto a record (SCOPE.md §3.10). */
    public int changedEffects() {
        return (int) active().stream().filter(CardSettings::changed).count();
    }

    /**
     * Roughly how much louder the unit makes the music, in dB, for blowouts (SCOPE.md §5.5): drive and amp
     * gain saturate it hotter, and level and makeup knobs raise it.
     */
    public double loudnessDb() {
        double db = 0;
        for (CardSettings card : active()) {
            switch (card.type()) {
                case DRIVE -> db += (card.value("level") + 6 * card.value("drive")) * card.value("mix");
                case AMP_CAB -> db += (card.value("level") + 4 * card.value("gain")) * card.value("mix");
                case DYNAMICS -> {
                    if (card.effect() == Effect.COMPRESSOR) {
                        db += card.value("makeup");
                    }
                }
                default -> { }
            }
        }
        return db;
    }

    /** True if the unit changes nothing at all. */
    public boolean passesThrough() {
        return active().isEmpty();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(cards, (b, c) -> c.writeBuf(b));
        buf.writeBoolean(bypass);
        buf.writeFloat(expression);
    }

    public static EffectChain read(FriendlyByteBuf buf) {
        List<CardSettings> cards = new ArrayList<>(buf.readList(CardSettings::readBuf));
        while (cards.size() > CardType.values().length) {
            cards.remove(cards.size() - 1);
        }
        return new EffectChain(cards, buf.readBoolean(), buf.readFloat());
    }
}
