package com.minescore.score;

import com.minescore.score.Notation.Duration;
import com.minescore.score.Notation.Tuplet;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Filling time with rests, the way a notation editor does when notes are deleted or a measure is added. */
public final class Rests {
    private Rests() {}

    /** Rests adding up to {@code length}: the longest plain values first (breve down to 64th). */
    public static List<Chord> fill(Frac length) {
        return fill(length, 0);
    }

    /**
     * Rests adding up to exactly {@code length}. Plain values where they can (breve down to 64th); a length
     * no plain values add up to (part of a triplet, say) is filled with tuplet rests instead, all sharing
     * tuplet {@code tupletId}: thirds of a beat with 3:2 rests, fifths with 5:4, and so on.
     */
    public static List<Chord> fill(Frac length, int tupletId) {
        List<Chord> rests = new ArrayList<>();
        if (!length.isPositive()) {
            return rests;
        }
        long odd = length.den();
        while (odd % 2 == 0) {
            odd /= 2;
        }
        Tuplet tuplet = null;
        Frac written = length;
        if (odd > 1 && odd <= 32) {
            int actual = (int) odd;
            int normal = Integer.highestOneBit(actual);
            tuplet = new Tuplet(actual, normal, tupletId);
            written = length.times(Frac.of(actual, normal)); // what the rests are written as, before the tuplet shrinks them
        }
        Frac left = written;
        for (int log = Duration.BREVE; log <= Duration.SIXTY_FOURTH && left.isPositive(); log++) {
            Duration d = new Duration(log, 0);
            while (d.length().compareTo(left) <= 0) {
                Chord rest = Chord.rest(d);
                rest.tuplet = tuplet;
                rests.add(rest);
                left = left.minus(d.length());
            }
        }
        return rests;
    }

    /**
     * Rests adding up to {@code length} from {@code start} in a measure, the cleanest way: each one as long as
     * it can be while starting on a multiple of its own length, so after a quarter on beat 1 of 4/4 come a
     * quarter rest and a half rest, as notation writes them. Lengths no plain values add up to fall back to
     * {@link #fill(Frac, int)}.
     */
    public static List<Chord> fillAt(Frac start, Frac length, int tupletId) {
        List<Duration> values = values(start, length);
        if (values == null) {
            return fill(length, tupletId);
        }
        List<Chord> rests = new ArrayList<>();
        values.forEach(d -> rests.add(Chord.rest(d)));
        return rests;
    }

    /**
     * The plain values (no dots, no tuplets) that cover {@code length} from {@code start}, each starting on a
     * multiple of its own length where it can; null if plain values can't add up to it.
     */
    public static @Nullable List<Duration> values(Frac start, Frac length) {
        if (!length.isPositive()) {
            return List.of();
        }
        if (Long.bitCount(start.den()) != 1 || length.divide(new Duration(Duration.SIXTY_FOURTH, 0).length()).den() != 1) {
            return null;
        }
        List<Duration> out = new ArrayList<>();
        Frac pos = start, left = length;
        while (left.isPositive()) {
            Duration pick = null, largest = null;
            for (int log = Duration.BREVE; log <= Duration.SIXTY_FOURTH; log++) {
                Duration d = new Duration(log, 0);
                if (d.length().compareTo(left) > 0) {
                    continue;
                }
                if (largest == null) {
                    largest = d;
                }
                if (pos.divide(d.length()).den() == 1) {
                    pick = d;
                    break;
                }
            }
            Duration d = pick != null ? pick : largest; // off the 64th grid: just the longest that fits
            out.add(d);
            pos = pos.plus(d.length());
            left = left.minus(d.length());
        }
        return out;
    }

    /** The total length of a voice. */
    public static Frac length(List<Chord> voice) {
        Frac total = Frac.ZERO;
        for (Chord c : voice) {
            total = total.plus(c.length());
        }
        return total;
    }
}
