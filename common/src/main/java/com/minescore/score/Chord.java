package com.minescore.score;

import com.minescore.score.Notation.Articulation;
import com.minescore.score.Notation.Duration;
import com.minescore.score.Notation.Dynamic;
import com.minescore.score.Notation.Grace;
import com.minescore.score.Notation.Ornament;
import com.minescore.score.Notation.Tuplet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * A chord, a single note (a chord of one) or a rest (no notes), with everything written on it. Grace notes
 * are chords too: they take no time of their own and steal it from the chord after them.
 */
public final class Chord {
    public Duration duration;
    public @Nullable Tuplet tuplet;
    public final List<Note> notes = new ArrayList<>();
    public final Set<Articulation> articulations = EnumSet.noneOf(Articulation.class);
    public Ornament ornament = Ornament.NONE;
    public Grace grace = Grace.NONE;
    public Notation.Arpeggio arpeggio = Notation.Arpeggio.NONE;
    public @Nullable Dynamic dynamic;
    /** One syllable per verse; "" where a verse has none. A trailing "-" joins it to the next syllable. */
    public final List<String> lyrics = new ArrayList<>();
    public @Nullable String chordSymbol;
    public @Nullable String staffText;

    public Chord(Duration duration) {
        this.duration = duration;
    }

    public static Chord rest(Duration duration) {
        return new Chord(duration);
    }

    public static Chord of(Duration duration, Note... notes) {
        Chord chord = new Chord(duration);
        chord.notes.addAll(List.of(notes));
        return chord;
    }

    public boolean isRest() {
        return notes.isEmpty();
    }

    public boolean isGrace() {
        return grace != Grace.NONE;
    }

    /** How long it lasts in whole notes: its duration, scaled by its tuplet; grace notes take none. */
    public Frac length() {
        if (isGrace()) {
            return Frac.ZERO;
        }
        Frac length = duration.length();
        return tuplet == null ? length : length.times(tuplet.scale());
    }

    public Chord copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("l", (byte) duration.log());
        if (duration.dots() > 0) {
            tag.putByte("d", (byte) duration.dots());
        }
        if (tuplet != null) {
            tag.putIntArray("tu", new int[] {tuplet.actual(), tuplet.normal(), tuplet.id()});
        }
        if (!notes.isEmpty()) {
            ListTag list = new ListTag();
            notes.forEach(n -> list.add(n.save()));
            tag.put("n", list);
        }
        if (!articulations.isEmpty()) {
            int bits = 0;
            for (Articulation a : articulations) {
                bits |= 1 << a.ordinal();
            }
            tag.putInt("ar", bits);
        }
        if (ornament != Ornament.NONE) {
            tag.putByte("or", (byte) ornament.ordinal());
        }
        if (grace != Grace.NONE) {
            tag.putByte("g", (byte) grace.ordinal());
        }
        if (arpeggio != Notation.Arpeggio.NONE) {
            tag.putByte("ap", (byte) arpeggio.ordinal());
        }
        if (dynamic != null) {
            tag.putByte("dy", (byte) dynamic.ordinal());
        }
        if (!lyrics.isEmpty()) {
            ListTag list = new ListTag();
            lyrics.forEach(l -> list.add(StringTag.valueOf(l)));
            tag.put("ly", list);
        }
        if (chordSymbol != null) {
            tag.putString("cs", chordSymbol);
        }
        if (staffText != null) {
            tag.putString("tx", staffText);
        }
        return tag;
    }

    public static Chord load(CompoundTag tag) {
        Chord chord = new Chord(new Duration(tag.getByte("l"), tag.getByte("d")));
        if (tag.contains("tu")) {
            int[] t = tag.getIntArray("tu");
            if (t.length == 3) {
                chord.tuplet = new Tuplet(t[0], t[1], t[2]);
            }
        }
        for (Tag t : tag.getList("n", Tag.TAG_COMPOUND)) {
            chord.notes.add(Note.load((CompoundTag) t));
        }
        int bits = tag.getInt("ar");
        for (Articulation a : Articulation.values()) {
            if ((bits & (1 << a.ordinal())) != 0) {
                chord.articulations.add(a);
            }
        }
        chord.ornament = pick(Ornament.values(), tag.getByte("or"), Ornament.NONE);
        chord.grace = pick(Grace.values(), tag.getByte("g"), Grace.NONE);
        chord.arpeggio = pick(Notation.Arpeggio.values(), tag.getByte("ap"), Notation.Arpeggio.NONE);
        chord.dynamic = tag.contains("dy") ? pick(Dynamic.values(), tag.getByte("dy"), null) : null;
        for (Tag t : tag.getList("ly", Tag.TAG_STRING)) {
            chord.lyrics.add(t.getAsString());
        }
        chord.chordSymbol = tag.contains("cs") ? tag.getString("cs") : null;
        chord.staffText = tag.contains("tx") ? tag.getString("tx") : null;
        return chord;
    }

    static <E> E pick(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
