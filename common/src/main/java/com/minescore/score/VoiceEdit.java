package com.minescore.score;

import com.minescore.score.Notation.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Editing one voice of one measure the way MuseScore's note input does (SCOPE.md §9.4): a new note or rest
 * overwrites the time it covers; a note it lands partway into is shortened, and whatever's left over
 * becomes rests, so a measure always adds up exactly.
 */
public final class VoiceEdit {
    private VoiceEdit() {}

    /** Where each chord starts (a grace note at the same point as the chord after it). */
    public static List<Frac> starts(List<Chord> voice) {
        List<Frac> starts = new ArrayList<>();
        Frac pos = Frac.ZERO;
        for (Chord c : voice) {
            starts.add(pos);
            pos = pos.plus(c.length());
        }
        return starts;
    }

    /** The chord (not a grace note) sounding at {@code pos}, or -1. */
    public static int indexAt(List<Chord> voice, Frac pos) {
        Frac start = Frac.ZERO;
        for (int i = 0; i < voice.size(); i++) {
            Chord c = voice.get(i);
            Frac end = start.plus(c.length());
            if (!c.isGrace() && pos.compareTo(start) >= 0 && pos.compareTo(end) < 0) {
                return i;
            }
            start = end;
        }
        return -1;
    }

    /** The longest written value (up to two dots) that fits in {@code room}. */
    public static Duration fitting(Frac room) {
        Duration best = new Duration(Duration.SIXTY_FOURTH, 0);
        for (int log = Duration.BREVE; log <= Duration.SIXTY_FOURTH; log++) {
            for (int dots = 0; dots <= 2; dots++) {
                Duration d = new Duration(log, dots);
                if (d.length().compareTo(room) <= 0 && d.length().compareTo(best.length()) > 0) {
                    best = d;
                }
            }
        }
        return best;
    }

    private static int nextTupletId(List<Chord> voice) {
        int max = 0;
        for (Chord c : voice) {
            if (c.tuplet != null) {
                max = Math.max(max, c.tuplet.id());
            }
        }
        return max + 1;
    }

    /** A copy of the voice that lasts exactly {@code length}: padded with rests, or cut at the barline. */
    public static List<Chord> normalize(List<Chord> voice, Frac length) {
        List<Chord> out = new ArrayList<>();
        int tuplet = nextTupletId(voice);
        Frac pos = Frac.ZERO;
        for (Chord c : voice) {
            if (pos.compareTo(length) >= 0) {
                break;
            }
            Frac end = pos.plus(c.length());
            if (end.compareTo(length) <= 0) {
                out.add(c.copy());
            } else {
                out.addAll(headOf(c, pos, length.minus(pos), tuplet++));
            }
            pos = Frac.min(end, length);
        }
        if (pos.compareTo(length) < 0) {
            out.addAll(Rests.fillAt(pos, length.minus(pos), tuplet));
        }
        return out;
    }

    /** The first {@code span} of a chord starting at {@code start}: the chord itself shortened, then rests for what its value can't cover. */
    private static List<Chord> headOf(Chord c, Frac start, Frac span, int tupletId) {
        List<Chord> pieces = c.isRest() ? Rests.fillAt(start, span, tupletId) : Rests.fill(span, tupletId);
        if (!c.isRest() && !pieces.isEmpty()) {
            Chord shortened = c.copy();
            shortened.duration = pieces.get(0).duration;
            shortened.tuplet = pieces.get(0).tuplet;
            shortened.notes.replaceAll(n -> n.withTie(false)); // a cut-short note no longer ties on
            pieces.set(0, shortened);
        }
        return pieces;
    }

    /**
     * Puts {@code chord} (a note, chord or rest) at {@code at}, overwriting what's there. If it would run
     * past the barline it's shortened to the longest value that fits.
     */
    public static List<Chord> place(List<Chord> voice, Frac length, Frac at, Chord chord) {
        List<Chord> notes = normalize(voice, length);
        Frac room = length.minus(at);
        if (!room.isPositive() || at.compareTo(Frac.ZERO) < 0) {
            return notes;
        }
        Chord placed = chord.copy();
        if (placed.length().compareTo(room) > 0) {
            placed.tuplet = null;
            placed.duration = fitting(room);
        }
        Frac end = at.plus(placed.length());
        int tuplet = nextTupletId(notes);
        List<Chord> out = new ArrayList<>();
        boolean done = false;
        Frac pos = Frac.ZERO;
        for (Chord c : notes) {
            Frac cs = pos, ce = pos.plus(c.length());
            pos = ce;
            if (c.isGrace()) { // a grace note belongs to the chord after it
                if (cs.compareTo(at) < 0) {
                    out.add(c);
                } else if (cs.compareTo(end) >= 0) {
                    if (!done) {
                        out.add(placed);
                        done = true;
                    }
                    out.add(c);
                }
                continue;
            }
            if (ce.compareTo(at) <= 0) {
                out.add(c);
                continue;
            }
            if (cs.compareTo(end) >= 0) {
                if (!done) {
                    out.add(placed);
                    done = true;
                }
                out.add(c);
                continue;
            }
            // c overlaps [at, end)
            if (cs.compareTo(at) < 0) {
                out.addAll(headOf(c, cs, at.minus(cs), tuplet++));
            }
            if (!done) {
                out.add(placed);
                done = true;
            }
            if (ce.compareTo(end) > 0) {
                out.addAll(Rests.fillAt(end, ce.minus(end), tuplet++));
            }
        }
        if (!done) {
            out.add(placed);
        }
        return tidyRests(out, placed); // a rest just put there stays as it was written
    }

    /**
     * Joins runs of plain rests into fewer, longer ones, as notation shows them (two quarter rests on beats 3
     * and 4 become a half rest), but only where every new rest starts on a multiple of its own length, so a
     * half rest never starts off the beat.
     */
    public static List<Chord> tidyRests(List<Chord> voice) {
        return tidyRests(voice, null);
    }

    /** As {@link #tidyRests(List)}, leaving {@code keep} (a rest just entered) out of the joining. */
    private static List<Chord> tidyRests(List<Chord> voice, @Nullable Chord keep) {
        List<Chord> out = new ArrayList<>();
        Frac pos = Frac.ZERO;
        int i = 0;
        while (i < voice.size()) {
            Chord c = voice.get(i);
            if (!c.isRest() || c.tuplet != null || c.isGrace() || c == keep) {
                out.add(c);
                pos = pos.plus(c.length());
                i++;
                continue;
            }
            int j = i;
            Frac total = Frac.ZERO;
            while (j < voice.size() && voice.get(j).isRest() && voice.get(j).tuplet == null && !voice.get(j).isGrace() && voice.get(j) != keep) {
                total = total.plus(voice.get(j).length());
                j++;
            }
            List<Chord> joined = Rests.fillAt(pos, total, 0);
            boolean aligned = Rests.length(joined).equals(total);
            Frac at = pos;
            for (Chord r : joined) {
                aligned &= at.divide(r.length()).den() == 1;
                at = at.plus(r.length());
            }
            out.addAll(aligned && joined.size() < j - i ? joined : voice.subList(i, j));
            pos = pos.plus(total);
            i = j;
        }
        return out;
    }

    /**
     * A chord too long for what's left of its measure, cut into pieces for the barline (SCOPE.md §9.4): the
     * values covering {@code span} from {@code start}, each a copy of the chord. Notes are tied on to the
     * next piece; the last ties on if {@code tiedOn} (more of it follows in the next measure), or as the
     * chord's own notes did. Marks stay on the first piece, and only when {@code first}.
     */
    public static List<Chord> tiedPieces(Chord chord, Frac start, Frac span, boolean first, boolean tiedOn) {
        Duration single = fitting(span);
        // one written value if one covers it exactly (a half on beat 2 stays a half), else clean pieces
        List<Duration> values = single.length().equals(span) ? List.of(single) : Rests.values(start, span);
        if (values == null) {
            values = List.of(single);
        }
        List<Chord> pieces = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Chord piece = chord.copy();
            piece.duration = values.get(i);
            piece.tuplet = null;
            if (!first || i > 0) {
                piece.articulations.clear();
                piece.ornament = Notation.Ornament.NONE;
                piece.dynamic = null;
                piece.lyrics.clear();
                piece.chordSymbol = null;
                piece.staffText = null;
            }
            boolean last = i == values.size() - 1;
            List<Note> original = chord.notes;
            for (int n = 0; n < piece.notes.size(); n++) {
                piece.notes.set(n, piece.notes.get(n).withTie(!last || tiedOn || original.get(n).tie()));
            }
            pieces.add(piece);
        }
        return pieces;
    }

    /**
     * Ctrl+number (SCOPE.md §9.7): the chord at {@code index} becomes the first of a tuplet of {@code actual}
     * notes in its time (a quarter becomes a triplet of eighths), the others rests. Null if its length can't be
     * split that way (it's in a tuplet already, or dotted past what the tuplet's notes can be written as).
     */
    public static @Nullable List<Chord> makeTuplet(List<Chord> voice, int index, int actual) {
        if (index < 0 || index >= voice.size() || actual < 2 || actual > 9) {
            return null;
        }
        Chord c = voice.get(index);
        if (c.tuplet != null || c.isGrace()) {
            return null;
        }
        int normal = actual == 2 ? 3 : Integer.highestOneBit(actual);
        Frac each = c.length().divide(Frac.of(normal, 1)); // written value of each note of the tuplet
        Duration written = null;
        for (int log = Duration.BREVE; log <= Duration.SIXTY_FOURTH; log++) {
            for (int dots = 0; dots <= 1; dots++) {
                if (new Duration(log, dots).length().equals(each)) {
                    written = new Duration(log, dots);
                }
            }
        }
        if (written == null) {
            return null;
        }
        Notation.Tuplet tuplet = new Notation.Tuplet(actual, normal, nextTupletId(voice));
        List<Chord> out = new ArrayList<>();
        for (int i = 0; i < voice.size(); i++) {
            if (i != index) {
                out.add(voice.get(i).copy());
                continue;
            }
            Chord head = c.copy();
            head.duration = written;
            head.tuplet = tuplet;
            head.notes.replaceAll(n -> n.withTie(false));
            out.add(head);
            for (int k = 1; k < actual; k++) {
                Chord rest = Chord.rest(written);
                rest.tuplet = tuplet;
                out.add(rest);
            }
        }
        return out;
    }

    /** The chord at {@code index} becomes a rest of the same length. */
    public static List<Chord> toRest(List<Chord> voice, int index) {
        List<Chord> out = new ArrayList<>();
        for (int i = 0; i < voice.size(); i++) {
            Chord c = voice.get(i).copy();
            if (i == index) {
                if (c.isGrace()) {
                    continue; // a deleted grace note just goes
                }
                Chord rest = Chord.rest(c.duration);
                rest.tuplet = c.tuplet;
                out.add(rest);
            } else {
                out.add(c);
            }
        }
        return out;
    }
}
