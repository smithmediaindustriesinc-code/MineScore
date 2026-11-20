package com.minescore.score;

import com.minescore.score.Notation.SpannerType;
import net.minecraft.nbt.CompoundTag;

/**
 * A line in one part from one point to another: a slur, hairpin, 8va, glissando, ritardando or
 * accelerando (SCOPE.md §8.2). It covers chords starting from {@code start} up to and including {@code end}.
 */
public record Spanner(SpannerType type, Pos start, Pos end, int voice) {
    /** A point in a part: a measure and how far into it, in whole notes. */
    public record Pos(int measure, Frac at) implements Comparable<Pos> {
        @Override
        public int compareTo(Pos o) {
            return measure != o.measure ? Integer.compare(measure, o.measure) : at.compareTo(o.at);
        }
    }

    public Spanner {
        if (end.compareTo(start) < 0) {
            Pos swap = start;
            start = end;
            end = swap;
        }
        voice = Math.max(0, Math.min(3, voice));
    }

    public boolean covers(Pos pos) {
        return pos.compareTo(start) >= 0 && pos.compareTo(end) <= 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("t", (byte) type.ordinal());
        tag.putInt("sm", start.measure());
        tag.putString("sa", start.at().toString());
        tag.putInt("em", end.measure());
        tag.putString("ea", end.at().toString());
        tag.putByte("v", (byte) voice);
        return tag;
    }

    public static Spanner load(CompoundTag tag) {
        return new Spanner(Chord.pick(SpannerType.values(), tag.getByte("t"), SpannerType.SLUR),
                new Pos(tag.getInt("sm"), Frac.parse(tag.getString("sa"))), new Pos(tag.getInt("em"), Frac.parse(tag.getString("ea"))),
                tag.getByte("v"));
    }
}
