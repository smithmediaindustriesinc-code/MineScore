package com.minescore.meu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A saved Multi-Effects Unit setup: its cards in order, each with its effect, knobs and bypass. The unit's
 * built-in presets live in {@link #BUILT_IN}; players' own are written onto Preset Chips.
 */
public record Preset(String name, List<CardSettings> cards) {
    public static final int MAX_NAME = 24;
    private static final String TAG = "MineScorePreset";

    public Preset {
        name = name == null || name.isBlank() ? "Untitled" : name.strip();
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        cards = List.copyOf(cards);
    }

    /**
     * The presets every MEU comes with. Loading one and turning a knob never changes it here: the unit just
     * holds a modified copy, which can be saved onto a chip under a new name.
     */
    public static final List<Preset> BUILT_IN = List.of(
            new Preset("Clean", List.of()),
            new Preset("Stadium", List.of(
                    set(Effect.COMPRESSOR, "threshold", -20, "ratio", 3, "makeup", 3),
                    set(Effect.STEREO, "time", 380, "feedback", 0.3f, "mix", 0.2f),
                    set(Effect.HALL, "size", 0.95f, "decay", 0.85f, "mix", 0.35f))),
            new Preset("Lo-fi Radio", List.of(
                    set(Effect.COMPRESSOR, "threshold", -30, "ratio", 8, "makeup", 8),
                    set(Effect.OVERDRIVE, "drive", 0.3f, "tone", 0.2f, "level", -4, "mix", 1),
                    set(Effect.CLEAN_TUBE, "bass", -10, "treble", -6, "cabinet", 1, "mix", 1),
                    set(Effect.CHORUS, "rate", 0.3f, "depth", 0.25f, "mix", 0.3f))),
            new Preset("Cathedral", List.of(
                    set(Effect.HALL, "size", 1, "decay", 0.95f, "damping", 0.35f, "predelay", 60, "mix", 0.55f))),
            new Preset("Slowed + Reverb", List.of(
                    set(Effect.SPEED, "speed", 80),
                    set(Effect.HALL, "size", 0.9f, "decay", 0.85f, "mix", 0.4f))),
            new Preset("Nightcore", List.of(
                    set(Effect.SPEED, "speed", 125),
                    set(Effect.TUNER, "shift", 3))),
            new Preset("Arena Rock", List.of(
                    set(Effect.OVERDRIVE, "drive", 0.55f, "level", -3, "mix", 1),
                    set(Effect.BRITISH_CRUNCH, "gain", 0.5f, "mid", 3, "cabinet", 3, "mix", 1),
                    set(Effect.STRAIGHT, "sync", 2, "feedback", 0.25f, "mix", 0.2f),
                    set(Effect.ROOM, "mix", 0.2f))),
            new Preset("Underwater", List.of(
                    set(Effect.PHASER, "rate", 0.15f, "depth", 0.9f, "feedback", 0.6f, "mix", 0.7f),
                    set(Effect.CLEAN_TUBE, "gain", 0.1f, "treble", -12, "cabinet", 0, "mix", 1),
                    set(Effect.PLATE, "mix", 0.45f))));

    /** A card set to an effect with some knobs changed: knob id, value, knob id, value... */
    static CardSettings set(Effect effect, Object... knobs) {
        CardSettings card = CardSettings.of(effect);
        for (int i = 0; i + 1 < knobs.length; i += 2) {
            card = card.withValue(effect.knob((String) knobs[i]), ((Number) knobs[i + 1]).floatValue());
        }
        return card;
    }

    public @Nullable CardSettings card(CardType type) {
        for (CardSettings card : cards) {
            if (card.type() == type) {
                return card;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        ListTag list = new ListTag();
        cards.forEach(card -> list.add(card.save()));
        tag.put("Cards", list);
        return tag;
    }

    public static Preset load(CompoundTag tag) {
        List<CardSettings> cards = new ArrayList<>();
        for (Tag t : tag.getList("Cards", Tag.TAG_COMPOUND)) {
            CardSettings card = CardSettings.load((CompoundTag) t);
            if (card != null && cards.stream().noneMatch(c -> c.type() == card.type())) {
                cards.add(card);
            }
        }
        return new Preset(tag.getString("Name"), cards);
    }

    /** The preset on a Preset Chip, or null if it's blank. */
    public static @Nullable Preset read(ItemStack chip) {
        CompoundTag tag = chip.getTagElement(TAG);
        return tag == null ? null : load(tag);
    }

    public void write(ItemStack chip) {
        chip.addTagElement(TAG, save());
    }
}
