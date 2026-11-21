package com.minescore.client.score;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.network.MineScoreNetwork;
import com.minescore.score.Bar;
import com.minescore.score.Chord;
import com.minescore.score.Frac;
import com.minescore.score.Instruments;
import com.minescore.score.Notation;
import com.minescore.score.Notation.Clef;
import com.minescore.score.Notation.Duration;
import com.minescore.score.Note;
import com.minescore.score.Part;
import com.minescore.score.Rests;
import com.minescore.score.Score;
import com.minescore.score.ScoreChange;
import com.minescore.score.ScorePackets;
import com.minescore.score.Spanner;
import com.minescore.score.VoiceEdit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * One open score editor, on the client (SCOPE.md §9.3): its copy of the score, what's selected, the note
 * input settings, undo and redo, and the clipboard. Every action becomes {@link ScoreChange}s, applied here
 * at once and sent to the server, which passes them on to anyone else editing the score.
 */
public final class ScoreEditor {
    private static final int UNDO_LIMIT = 100;

    /** A chord (note = -1) or one note of it. */
    public record ChordRef(int part, int measure, int voice, int chord, int note) {}

    /**
     * A range across parts, from one point in the music up to (not including) another, as MuseScore selects:
     * the end is (measure2, at2), where at2 may be measure2's full length.
     */
    public record Range(int part1, int part2, int measure1, Frac at1, int measure2, Frac at2) {
        /** Whether a chord starting at (measure, at) in this part is inside. */
        public boolean contains(int part, int measure, Frac at) {
            if (part < part1 || part > part2) {
                return false;
            }
            boolean afterStart = measure > measure1 || (measure == measure1 && at.compareTo(at1) >= 0);
            boolean beforeEnd = measure < measure2 || (measure == measure2 && at.compareTo(at2) < 0);
            return afterStart && beforeEnd;
        }
    }

    /** A point in the music: a measure, and how far into it. */
    public record Point(int measure, Frac at) {}

    /** Copied music: each chord with its part (counted from the first copied) and how far after the start it was. */
    private record Clip(int part, int voice, Frac offset, CompoundTag chord) {}

    private record Step(List<ScoreChange> forward, List<ScoreChange> back) {}

    /** Someone else editing this score: their name, where their cursor is, and the colour it's drawn in. */
    public record Peer(String name, ScorePackets.CursorState state, int color) {}

    private static final int[] PEER_COLORS = {0xFFE0563B, 0xFF2EA043, 0xFFB45CD6, 0xFFE0A21E, 0xFF1EA7B8, 0xFFD6487E, 0xFF6E8B2E, 0xFF8A6A3E};

    /** Each player's own colour, the same for them every time. */
    public static int peerColor(java.util.UUID player) {
        return PEER_COLORS[Math.floorMod(player.hashCode(), PEER_COLORS.length)];
    }

    public final BlockPos desk;
    public final String scoreId;
    public final Score score;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private final Consumer<ScoreEditor> onChange;

    public @Nullable ChordRef selected;
    public @Nullable Range range;
    /** A palette's Text item asks the screen to start typing this kind of text. */
    public @Nullable ScoreElement.Kind wantsText;
    /** A single mark selected on its own (a dynamic, a line…), instead of notes. */
    public @Nullable ScoreElement element;
    /** Where the last of this player's own edits (or undos) happened, for the view to follow; taken by the screen. */
    private @Nullable Point lastEdit;
    private int lastEditPart = -1;
    // note input
    public boolean inputMode;
    public int voice;
    public int durationLog = 2;
    public int dots;
    /** -2…2 to put on the next note entered, or {@link #NO_ACCIDENTAL} to follow the key. */
    public int accidental = NO_ACCIDENTAL;
    public static final int NO_ACCIDENTAL = 99;
    public int cursorPart, cursorMeasure;
    public Frac cursorAt = Frac.ZERO;
    private int lastPitch = 71;
    /** More chords picked with Ctrl+click, besides {@link #selected}. */
    public final List<ChordRef> also = new ArrayList<>();
    /** Everyone else with this score open, by player (SCOPE.md §9.10, stage 4). */
    public final java.util.Map<java.util.UUID, Peer> peers = new java.util.LinkedHashMap<>();
    private ScorePackets.CursorState sentCursor = ScorePackets.CursorState.NONE;
    /** Extending a selection with Shift+arrows: the fixed end and the moving end, from the score's start. */
    private @Nullable Frac anchor, focus;
    // clipboard
    private List<Clip> clip = List.of();
    private Frac clipLength = Frac.ZERO;
    private int clipParts = 1;

    public ScoreEditor(BlockPos desk, String scoreId, Score score, Consumer<ScoreEditor> onChange) {
        this.desk = desk;
        this.scoreId = scoreId;
        this.score = score;
        this.onChange = onChange;
    }

    // --- changes ----------------------------------------------------------------------------

    /** Makes an edit: applies it, remembers how to undo it, and sends it to the server. */
    public void edit(List<ScoreChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        List<ScoreChange> back = ScoreChange.applyAll(score, changes);
        if (back == null) {
            return;
        }
        undo.push(new Step(changes, back));
        while (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
        send(changes);
        follow(changes);
        changed();
    }

    /** Remembers where some changes happened, so the view can scroll there if it's out of sight. */
    private void follow(List<ScoreChange> changes) {
        for (ScoreChange c : changes) {
            if (c instanceof ScoreChange.MeasureContent mc) {
                lastEdit = new Point(mc.measure(), Frac.ZERO);
                lastEditPart = mc.part();
                return;
            } else if (c instanceof ScoreChange.BarContent bc) {
                lastEdit = new Point(bc.measure(), Frac.ZERO);
                lastEditPart = -1;
                return;
            } else if (c instanceof ScoreChange.InsertMeasure im) {
                lastEdit = new Point(im.index(), Frac.ZERO);
                lastEditPart = -1;
                return;
            }
        }
    }

    /** Where the view should go to show the last edit (a measure, and the part or -1), once; null if nowhere new. */
    public int @Nullable [] takeFocus() {
        Point f = lastEdit;
        lastEdit = null;
        return f == null ? null : new int[] {Math.min(f.measure(), score.measureCount() - 1), Math.min(lastEditPart, score.parts.size() - 1)};
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        Step step = undo.poll();
        if (step != null && ScoreChange.applyAll(score, step.back()) != null) {
            redo.push(step);
            send(step.back());
            follow(step.back());
            changed();
        }
    }

    public void redo() {
        Step step = redo.poll();
        if (step != null && ScoreChange.applyAll(score, step.forward()) != null) {
            undo.push(step);
            send(step.forward());
            follow(step.forward());
            changed();
        }
    }

    /**
     * Someone else changed the score. Their changes win, and undo keeps working (undoing puts our own version
     * back: the last change wins). Only when they moved measures or parts around, so our history no longer
     * lines up, is it dropped.
     */
    public void remoteEdit(List<ScoreChange> changes) {
        ScoreChange.applyAll(score, changes);
        boolean moved = changes.stream().anyMatch(c -> c instanceof ScoreChange.InsertMeasure || c instanceof ScoreChange.DeleteMeasure
                || c instanceof ScoreChange.AddPart || c instanceof ScoreChange.RemovePart || c instanceof ScoreChange.SwapParts);
        if (moved) {
            undo.clear();
            redo.clear();
        }
        clampSelection();
        changed();
    }

    /** Where this player's cursor is, as the others are shown it. */
    public ScorePackets.CursorState cursorState() {
        if (inputMode) {
            return new ScorePackets.CursorState(cursorPart, cursorPart, cursorMeasure, cursorAt, cursorMeasure, cursorAt, true);
        }
        if (range != null) {
            return new ScorePackets.CursorState(range.part1(), range.part2(), range.measure1(), range.at1(), range.measure2(), range.at2(), false);
        }
        if (selected != null && selectedChord() != null) {
            Frac at = startOf(selected);
            Frac end = Frac.min(at.plus(selectedChord().length()), score.lengthOf(selected.measure()));
            return new ScorePackets.CursorState(selected.part(), selected.part(), selected.measure(), at, selected.measure(), end, false);
        }
        if (element != null && element.measure() < score.measureCount()) {
            int p = Math.max(0, element.part());
            return new ScorePackets.CursorState(p, p, element.measure(), Frac.ZERO, element.measure(), score.lengthOf(element.measure()), false);
        }
        return ScorePackets.CursorState.NONE;
    }

    /** Tells the server where the cursor is, when it's moved since last time. */
    public void sendCursor() {
        ScorePackets.CursorState now = cursorState();
        if (!now.equals(sentCursor)) {
            sentCursor = now;
            MineScoreNetwork.sendToServer(new ScorePackets.Cursor(desk, now));
        }
    }

    private void send(List<ScoreChange> changes) {
        MineScoreNetwork.sendToServer(new ScorePackets.Edit(desk, changes));
    }

    private void changed() {
        clampSelection();
        onChange.accept(this);
    }

    private void clampSelection() {
        also.removeIf(r -> r.part() >= score.parts.size() || r.measure() >= score.measureCount() || r.chord() >= voiceOf(r).size());
        if (selected != null) {
            if (selected.part() >= score.parts.size() || selected.measure() >= score.measureCount()
                    || selected.chord() >= voiceOf(selected).size()) {
                selected = null;
            }
        }
        if (range != null && (range.part2() >= score.parts.size() || range.measure2() >= score.measureCount())) {
            range = null;
        }
        if (element != null && !exists(element)) {
            element = null;
        }
        cursorPart = Math.min(cursorPart, score.parts.size() - 1);
        cursorMeasure = Math.min(cursorMeasure, score.measureCount() - 1);
    }

    // --- reading ----------------------------------------------------------------------------

    public List<Chord> voiceOf(ChordRef ref) {
        return score.parts.get(ref.part()).measures.get(ref.measure()).voices[ref.voice()];
    }

    public @Nullable Chord selectedChord() {
        if (selected == null) {
            return null;
        }
        List<Chord> voice = voiceOf(selected);
        return selected.chord() < voice.size() ? voice.get(selected.chord()) : null;
    }

    /** The measure something's selected in (or the cursor's, in note input). */
    public int currentMeasure() {
        if (selected != null) {
            return selected.measure();
        }
        if (range != null) {
            return range.measure1();
        }
        return cursorMeasure;
    }

    public int currentPart() {
        if (selected != null) {
            return selected.part();
        }
        if (range != null) {
            return range.part1();
        }
        return cursorPart;
    }

    public Duration duration() {
        return new Duration(durationLog, dots);
    }

    private Part.Measure measureCopy(int p, int m) {
        return Part.Measure.load(score.parts.get(p).measures.get(m).save());
    }

    private ScoreChange measureChange(int p, int m, Part.Measure measure) {
        return new ScoreChange.MeasureContent(p, m, measure.save());
    }

    public Frac startOf(ChordRef ref) {
        return VoiceEdit.starts(voiceOf(ref)).get(ref.chord());
    }

    /** How far a point is from the score's start, in whole notes. */
    public Frac abs(int measure, Frac at) {
        Frac t = Frac.ZERO;
        for (int i = 0; i < measure && i < score.measureCount(); i++) {
            t = t.plus(score.lengthOf(i));
        }
        return t.plus(at);
    }

    /** The point at a distance from the score's start, or null past its end. An exact barline is the next measure's start. */
    public @Nullable Point locate(Frac abs) {
        Frac start = Frac.ZERO;
        for (int m = 0; m < score.measureCount(); m++) {
            Frac end = start.plus(score.lengthOf(m));
            if (abs.compareTo(end) < 0) {
                return new Point(m, abs.minus(start));
            }
            start = end;
        }
        return null;
    }

    /** As {@link #locate}, but a point on a barline is the end of the measure before (for the end of a range). */
    private Point locateEnd(Frac abs) {
        Frac start = Frac.ZERO;
        for (int m = 0; m < score.measureCount(); m++) {
            Frac end = start.plus(score.lengthOf(m));
            if (abs.compareTo(end) <= 0) {
                return new Point(m, abs.minus(start));
            }
            start = end;
        }
        int last = score.measureCount() - 1;
        return new Point(last, score.lengthOf(last));
    }

    /** A range of whole measures. */
    public Range measures(int part1, int part2, int measure1, int measure2) {
        return new Range(part1, part2, measure1, Frac.ZERO, measure2, score.lengthOf(measure2));
    }

    private boolean wholeMeasures(Range r) {
        return r.at1().isZero() && r.at2().equals(score.lengthOf(r.measure2()));
    }

    /** Whether the range covers more than one point in time (so playing it plays just that stretch). */
    public boolean rangeHasSeveral() {
        if (range == null) {
            return false;
        }
        java.util.Set<Frac> times = new java.util.HashSet<>();
        for (int p = range.part1(); p <= range.part2(); p++) {
            for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                for (List<Chord> v : score.parts.get(p).measures.get(mi).voices) {
                    List<Frac> starts = VoiceEdit.starts(v);
                    for (int i = 0; i < v.size(); i++) {
                        if (range.contains(p, mi, starts.get(i)) && times.add(abs(mi, starts.get(i))) && times.size() > 1) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Whether a chord (or one of its notes) is selected, for drawing it blue. */
    public boolean isSelected(int part, int measure, int voice, int chord, int note) {
        for (ChordRef ref : refs()) {
            if (ref.part() == part && ref.measure() == measure && ref.voice() == voice && ref.chord() == chord) {
                return ref.note() < 0 || ref.note() == note;
            }
        }
        if (range != null) {
            List<Chord> v = score.parts.get(part).measures.get(measure).voices[voice];
            return chord < v.size() && range.contains(part, measure, VoiceEdit.starts(v).get(chord));
        }
        return false;
    }

    private List<ChordRef> refs() {
        List<ChordRef> refs = new ArrayList<>();
        if (selected != null) {
            refs.add(selected);
        }
        refs.addAll(also);
        return refs;
    }

    /** Lets the player hear a chord (or the one note) they've selected or entered. */
    private void hear(@Nullable ChordRef ref) {
        if (ref == null || ref.chord() >= voiceOf(ref).size()) {
            return;
        }
        Part part = score.parts.get(ref.part());
        Chord c = voiceOf(ref).get(ref.chord());
        int shift = part.clefAt(ref.measure()).octaveShift();
        if (ref.note() >= 0 && ref.note() < c.notes.size()) {
            Audition.play(part, c.notes.get(ref.note()), shift);
        } else {
            Audition.play(part, c, shift);
        }
    }

    // --- note input -------------------------------------------------------------------------

    /** The note for a letter (0 = C … 6 = B) nearest the last note entered, in the key unless an accidental is set. */
    private Note noteFor(int letter) {
        int key = score.keyAt(cursorMeasure);
        int alter = accidental != NO_ACCIDENTAL ? accidental : ScoreView.keyAlter(key, letter);
        Note best = null;
        for (int octave = 0; octave <= 8; octave++) {
            Note n = Note.of(letter, alter, octave);
            if (best == null || Math.abs(n.pitch() - lastPitch) < Math.abs(best.pitch() - lastPitch)) {
                best = n;
            }
        }
        return best;
    }

    /** On a percussion staff, which drum a staff position plays: low for the bass drum, middle snare, high hi-hat. */
    public static NoteInstrument drumFor(int staffStep) {
        return staffStep <= 31 ? NoteInstrument.BASEDRUM : staffStep >= 38 ? NoteInstrument.HAT : NoteInstrument.SNARE;
    }

    private Note drumNote(Note n) {
        return new Note(n.step(), 0, n.octave(), false, drumFor(n.staffStep()));
    }

    /** Note input: a note (or, with {@code chord}, a note added to the one just entered) at the cursor. */
    public void enterLetter(int letter, boolean chord) {
        Note note = noteFor(letter);
        if (chord && selected != null && selectedChord() != null && !selectedChord().isRest()) {
            addNote(selected, note);
            return;
        }
        enterAt(cursorPart, cursorMeasure, cursorAt, note);
    }

    /** Note input at a clicked staff position. */
    public void enterStep(int part, int measure, Frac at, int staffStep) {
        int letter = Math.floorMod(staffStep, 7);
        int octave = Math.floorDiv(staffStep, 7);
        int alter = accidental != NO_ACCIDENTAL ? accidental : ScoreView.keyAlter(score.keyAt(measure), letter);
        enterAt(part, measure, at, Note.of(letter, alter, octave));
    }

    /** Note input: a rest at the cursor. */
    public void enterRest() {
        enterAt(cursorPart, cursorMeasure, cursorAt, null);
    }

    private void enterAt(int part, int measure, Frac at, @Nullable Note note) {
        Part p = score.parts.get(part);
        if (note != null && p.clefAt(measure) == Clef.PERCUSSION) {
            note = drumNote(note);
        }
        Chord chord = note == null ? Chord.rest(duration()) : Chord.of(duration(), note);
        List<Chord> current = score.parts.get(part).measures.get(measure).voices[voice];
        int there = VoiceEdit.indexAt(current, at);
        if (there >= 0 && current.get(there).tuplet != null) {
            chord.tuplet = current.get(there).tuplet; // inside a tuplet, notes go in at its ratio
        }
        Frac length = score.lengthOf(measure);
        Frac total = chord.length();
        java.util.Map<Long, Part.Measure> touched = new java.util.LinkedHashMap<>();
        placeAcross(touched, part, measure, voice, at, chord);
        edit(changesFor(touched));
        int index = VoiceEdit.indexAt(score.parts.get(part).measures.get(measure).voices[voice], at);
        selected = index < 0 ? null : new ChordRef(part, measure, voice, index, -1);
        range = null;
        element = null;
        also.clear();
        anchor = null;
        if (note != null) {
            hear(selected);
        }
        if (note != null) {
            lastPitch = note.pitch();
        }
        // the cursor moves on past what was entered (into the next measure if it was carried over)
        Chord placed = index < 0 ? chord : score.parts.get(part).measures.get(measure).voices[voice].get(index);
        Frac next = at.plus(placed.length().compareTo(length.minus(at)) >= 0 ? total : placed.length());
        cursorPart = part;
        Point after = locate(abs(measure, Frac.ZERO).plus(next));
        if (next.compareTo(length) >= 0 && after != null) {
            cursorMeasure = after.measure();
            cursorAt = after.at();
        } else {
            cursorMeasure = measure;
            cursorAt = Frac.min(next, length);
        }
        accidental = NO_ACCIDENTAL;
    }

    /**
     * Puts a chord at a point in a voice. One longer than what's left of its measure carries on over the
     * barline, tied, as MuseScore does (a dotted note at the end of a measure ties its dot into the next).
     */
    private void placeAcross(java.util.Map<Long, Part.Measure> touched, int part, int measure, int v, Frac at, Chord chord) {
        Frac length = score.lengthOf(measure);
        Part.Measure first = touched.computeIfAbsent(key(part, measure), k -> measureCopy(part, measure));
        if (chord.length().compareTo(length.minus(at)) <= 0 || measure + 1 >= score.measureCount() || chord.tuplet != null || chord.isGrace()) {
            first.voices[v] = new ArrayList<>(VoiceEdit.place(first.voices[v], length, at, chord));
            return;
        }
        Frac left = chord.length(), pos = at;
        for (int mi = measure; left.isPositive() && mi < score.measureCount(); mi++) {
            Frac len = score.lengthOf(mi);
            Frac take = Frac.min(left, len.minus(pos));
            boolean more = left.compareTo(take) > 0 && mi + 1 < score.measureCount();
            int target = mi;
            Part.Measure m = touched.computeIfAbsent(key(part, mi), k -> measureCopy(part, target));
            Frac p = pos;
            for (Chord piece : VoiceEdit.tiedPieces(chord, pos, take, mi == measure, more)) {
                m.voices[v] = new ArrayList<>(VoiceEdit.place(m.voices[v], len, p, piece));
                p = p.plus(piece.length());
            }
            left = left.minus(take);
            pos = Frac.ZERO;
        }
    }

    private void addNote(ChordRef ref, Note note) {
        Part.Measure m = measureCopy(ref.part(), ref.measure());
        Chord c = m.voices[ref.voice()].get(ref.chord());
        if (c.notes.stream().noneMatch(n -> n.staffStep() == note.staffStep()) && c.notes.size() < 16) {
            c.notes.add(note);
            edit(List.of(measureChange(ref.part(), ref.measure(), m)));
            lastPitch = note.pitch();
            Audition.play(score.parts.get(ref.part()), note, score.parts.get(ref.part()).clefAt(ref.measure()).octaveShift());
        }
    }

    /** Shift+click in note input: a note added to the chord there, or a new one if there's none. */
    public void addNoteAt(int part, int measure, int voiceIndex, Frac at, int staffStep) {
        List<Chord> v = score.parts.get(part).measures.get(measure).voices[voiceIndex];
        int i = VoiceEdit.indexAt(v, at);
        if (i >= 0 && !v.get(i).isRest() && VoiceEdit.starts(v).get(i).equals(at)) {
            int letter = Math.floorMod(staffStep, 7);
            Note note = Note.of(letter, accidental != NO_ACCIDENTAL ? accidental : ScoreView.keyAlter(score.keyAt(measure), letter),
                    Math.floorDiv(staffStep, 7));
            if (score.parts.get(part).clefAt(measure) == Clef.PERCUSSION) {
                note = drumNote(note);
            }
            addNote(new ChordRef(part, measure, voiceIndex, i, -1), note);
            selected = new ChordRef(part, measure, voiceIndex, i, -1);
        } else {
            enterStep(part, measure, at, staffStep);
        }
    }

    /** Shift+click: the range from what's selected to (and including) what was clicked. */
    public void extendTo(int part, int measure, Frac at, Frac length) {
        Frac s1, e1;
        int p1, p2;
        if (selected != null && selectedChord() != null) {
            s1 = abs(selected.measure(), startOf(selected));
            e1 = s1.plus(selectedChord().length());
            p1 = p2 = selected.part();
        } else if (range != null) {
            s1 = abs(range.measure1(), range.at1());
            e1 = abs(range.measure2(), range.at2());
            p1 = range.part1();
            p2 = range.part2();
        } else {
            return;
        }
        Frac s2 = abs(measure, at), e2 = s2.plus(length);
        Point a = locate(Frac.min(s1, s2)), b = locateEnd(Frac.max(e1, e2));
        if (a != null) {
            selectRange(new Range(Math.min(p1, part), Math.max(p2, part), a.measure(), a.at(), b.measure(), b.at()));
        }
    }

    /** Alt+number: a note an interval above the selected chord's top note (Alt+3 a third), in the key. */
    public void addInterval(int interval) {
        Chord c = selectedChord();
        if (c == null || c.isRest() || interval < 2) {
            return;
        }
        Note top = c.notes.stream().max((a, b) -> Integer.compare(a.staffStep(), b.staffStep())).orElseThrow();
        int step = top.staffStep() + interval - 1;
        int letter = Math.floorMod(step, 7);
        addNote(selected, Note.of(letter, ScoreView.keyAlter(score.keyAt(selected.measure()), letter), Math.floorDiv(step, 7)));
    }

    // --- changing what's selected -------------------------------------------------------------

    /** Selects a chord, or one of its notes, and plays it. */
    public void select(@Nullable ChordRef ref) {
        selected = ref;
        range = null;
        element = null;
        also.clear();
        anchor = null;
        if (ref != null) {
            cursorPart = ref.part();
            cursorMeasure = ref.measure();
            cursorAt = startOf(ref);
            voice = ref.voice();
            Chord c = selectedChord();
            if (c != null && !c.isRest()) {
                lastPitch = c.notes.get(ref.note() >= 0 && ref.note() < c.notes.size() ? ref.note() : 0).pitch();
            }
            hear(ref);
        }
    }

    public void selectRange(Range r) {
        range = r;
        selected = null;
        element = null;
        also.clear();
        anchor = null;
        cursorPart = r.part1();
        cursorMeasure = r.measure1();
        cursorAt = r.at1();
    }

    public void selectMeasures(int part1, int part2, int measure1, int measure2) {
        selectRange(measures(part1, part2, measure1, measure2));
    }

    /** Clicking a part's name: its whole staff. */
    public void selectStaff(int part) {
        selectMeasures(part, part, 0, score.measureCount() - 1);
    }

    /** Ctrl+click: adds a chord (or note) to the selection, or takes it out. */
    public void toggleAlso(ChordRef ref) {
        if (range != null || selected == null) {
            select(ref);
            return;
        }
        if (ref.equals(selected)) {
            selected = also.isEmpty() ? null : also.remove(0);
        } else if (!also.remove(ref)) {
            also.add(ref);
            hear(ref);
        }
    }

    /**
     * Shift+arrows: grows (or shrinks) the selection to the next or previous chord, or to the barline, from a
     * fixed end, as MuseScore does.
     */
    public void extend(int direction, boolean byMeasure) {
        int part1, part2;
        if (anchor == null) {
            if (selected != null && selectedChord() != null) {
                Frac s = abs(selected.measure(), startOf(selected));
                Frac e = s.plus(selectedChord().length());
                anchor = direction > 0 ? s : e;
                focus = direction > 0 ? e : s;
                part1 = part2 = selected.part();
            } else if (range != null) {
                anchor = direction > 0 ? abs(range.measure1(), range.at1()) : abs(range.measure2(), range.at2());
                focus = direction > 0 ? abs(range.measure2(), range.at2()) : abs(range.measure1(), range.at1());
                part1 = range.part1();
                part2 = range.part2();
            } else {
                return;
            }
        } else {
            part1 = range != null ? range.part1() : cursorPart;
            part2 = range != null ? range.part2() : cursorPart;
        }
        focus = boundary(part1, focus, direction, byMeasure);
        Frac from = Frac.min(anchor, focus), to = Frac.max(anchor, focus);
        if (from.equals(to)) {
            return;
        }
        Point a = locate(from), b = locateEnd(to);
        if (a == null) {
            return;
        }
        Frac keepAnchor = anchor, keepFocus = focus;
        selectRange(new Range(part1, part2, a.measure(), a.at(), b.measure(), b.at()));
        anchor = keepAnchor;
        focus = keepFocus;
    }

    /** The next (or previous) chord start or barline in a part after (before) a point. */
    private Frac boundary(int part, Frac from, int direction, boolean byMeasure) {
        List<Frac> marks = new ArrayList<>();
        Frac start = Frac.ZERO;
        for (int m = 0; m < score.measureCount(); m++) {
            marks.add(start);
            if (!byMeasure) {
                for (List<Chord> v : score.parts.get(part).measures.get(m).voices) {
                    for (Frac at : VoiceEdit.starts(v)) {
                        marks.add(start.plus(at));
                    }
                }
            }
            start = start.plus(score.lengthOf(m));
        }
        marks.add(start);
        Frac best = from;
        for (Frac mark : marks) {
            if (direction > 0 && mark.compareTo(from) > 0 && (best.equals(from) || mark.compareTo(best) < 0)) {
                best = mark;
            } else if (direction < 0 && mark.compareTo(from) < 0 && (best.equals(from) || mark.compareTo(best) > 0)) {
                best = mark;
            }
        }
        return best;
    }

    /** Applies {@code change} to every selected chord: the selected ones, or every chord starting inside the range. */
    private void eachSelectedChord(java.util.function.BiConsumer<Chord, Integer> change) {
        java.util.Map<Long, Part.Measure> touched = new java.util.LinkedHashMap<>();
        for (ChordRef ref : refs()) {
            Part.Measure m = touched.computeIfAbsent(key(ref.part(), ref.measure()), k -> measureCopy(ref.part(), ref.measure()));
            List<Chord> v = m.voices[ref.voice()];
            if (ref.chord() < v.size()) {
                change.accept(v.get(ref.chord()), ref.note());
            }
        }
        if (range != null) {
            for (int p = range.part1(); p <= range.part2(); p++) {
                for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                    int part = p, measure = mi;
                    Part.Measure m = touched.computeIfAbsent(key(p, mi), k -> measureCopy(part, measure));
                    for (List<Chord> v : m.voices) {
                        List<Frac> starts = VoiceEdit.starts(v);
                        for (int i = 0; i < v.size(); i++) {
                            if (range.contains(p, mi, starts.get(i))) {
                                change.accept(v.get(i), -1);
                            }
                        }
                    }
                }
            }
        }
        edit(changesFor(touched));
    }

    private static long key(int part, int measure) {
        return ((long) part << 32) | measure;
    }

    private List<ScoreChange> changesFor(java.util.Map<Long, Part.Measure> touched) {
        List<ScoreChange> changes = new ArrayList<>();
        touched.forEach((k, m) -> changes.add(measureChange((int) (k >> 32), (int) (long) k, m)));
        return changes;
    }

    /** Moves the selected notes by semitones (an octave with Ctrl), respelled the plain way, and plays them. */
    public void transpose(int semitones) {
        eachSelectedChord((c, note) -> {
            for (int i = 0; i < c.notes.size(); i++) {
                if ((note < 0 || note == i) && c.notes.get(i).drum() == null) {
                    c.notes.set(i, c.notes.get(i).transposed(semitones));
                }
            }
        });
        Chord c = selectedChord();
        if (c != null && !c.isRest()) {
            lastPitch = c.notes.get(0).pitch();
        }
        hear(selected);
    }

    /** Sets the selected chord's written value (and dots), overwriting what follows as note input would. */
    public void setDuration(int log, int dotCount) {
        Chord c = selectedChord();
        if (c == null) {
            return;
        }
        Chord changed = c.copy();
        changed.duration = new Duration(log, dotCount);
        if (c.tuplet != null && !c.isGrace()) {
            changed.tuplet = c.tuplet; // a note in a tuplet keeps its ratio
        }
        Frac at = startOf(selected);
        java.util.Map<Long, Part.Measure> touched = new java.util.LinkedHashMap<>();
        placeAcross(touched, selected.part(), selected.measure(), selected.voice(), at, changed);
        edit(changesFor(touched));
        int index = VoiceEdit.indexAt(score.parts.get(selected.part()).measures.get(selected.measure()).voices[selected.voice()], at);
        selected = index < 0 ? null : new ChordRef(selected.part(), selected.measure(), selected.voice(), index, -1);
    }

    public void toggleTie() {
        eachSelectedChord((c, note) -> {
            boolean on = c.notes.stream().noneMatch(Note::tie);
            c.notes.replaceAll(n -> n.withTie(on));
        });
    }

    /** Sets the accidental of the selected notes, or the next note entered when none are selected. */
    public void applyAccidental(int alter) {
        if (selected == null && range == null) {
            accidental = accidental == alter ? NO_ACCIDENTAL : alter;
            return;
        }
        eachSelectedChord((c, note) -> {
            for (int i = 0; i < c.notes.size(); i++) {
                Note n = c.notes.get(i);
                if ((note < 0 || note == i) && n.drum() == null) {
                    c.notes.set(i, new Note(n.step(), alter, n.octave(), n.tie(), null));
                }
            }
        });
    }

    public void toggleArticulation(Notation.Articulation articulation) {
        boolean on = selectedChord() == null || !selectedChord().articulations.contains(articulation);
        eachSelectedChord((c, note) -> {
            if (!c.isRest()) {
                if (on) {
                    c.articulations.add(articulation);
                } else {
                    c.articulations.remove(articulation);
                }
            }
        });
    }

    public void setOrnament(Notation.Ornament ornament) {
        Notation.Ornament next = selectedChord() != null && selectedChord().ornament == ornament ? Notation.Ornament.NONE : ornament;
        eachSelectedChord((c, note) -> {
            if (!c.isRest()) {
                c.ornament = next;
            }
        });
    }

    /** Rolls the selected chords as an arpeggio (the same one again takes it off). */
    public void setArpeggio(Notation.Arpeggio arpeggio) {
        Notation.Arpeggio next = selectedChord() != null && selectedChord().arpeggio == arpeggio ? Notation.Arpeggio.NONE : arpeggio;
        eachSelectedChord((c, note) -> {
            if (!c.isRest()) {
                c.arpeggio = next;
            }
        });
    }

    /** A dynamic on the selected chord, or on the first chord of each selected part's range (again to remove it). */
    public void setDynamic(Notation.Dynamic dynamic) {
        if (selected != null) {
            Notation.Dynamic next = selectedChord() != null && selectedChord().dynamic == dynamic ? null : dynamic;
            eachSelectedChord((c, note) -> c.dynamic = next);
        } else if (range != null) {
            List<ScoreChange> changes = new ArrayList<>();
            for (int p = range.part1(); p <= range.part2(); p++) {
                Part.Measure m = measureCopy(p, range.measure1());
                if (!m.voices[0].isEmpty()) {
                    m.voices[0].get(0).dynamic = dynamic;
                    changes.add(measureChange(p, range.measure1(), m));
                }
            }
            edit(changes);
        }
    }

    /** Moves the selected notes up or down by staff steps (lines and spaces), spelled in the key. */
    public void moveSteps(int steps) {
        if (steps == 0) {
            return;
        }
        int key = score.keyAt(currentMeasure());
        eachSelectedChord((c, note) -> {
            for (int i = 0; i < c.notes.size(); i++) {
                Note n = c.notes.get(i);
                if (note < 0 || note == i) {
                    int step = n.staffStep() + steps;
                    int letter = Math.floorMod(step, 7);
                    Note moved = Note.of(letter, ScoreView.keyAlter(key, letter), Math.floorDiv(step, 7)).withTie(n.tie());
                    c.notes.set(i, n.drum() != null ? new Note(letter, 0, Math.floorDiv(step, 7), n.tie(), drumFor(step)) : moved);
                }
            }
        });
        hear(selected);
    }

    /** Delete: notes become rests (a note in a chord just goes); a range of whole measures empties out; a selected mark goes. */
    public void delete() {
        if (element != null) {
            deleteElement(element);
            element = null;
            return;
        }
        java.util.Map<Long, Part.Measure> touched = new java.util.LinkedHashMap<>();
        if (range != null && wholeMeasures(range)) {
            for (int p = range.part1(); p <= range.part2(); p++) {
                for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                    Part.Measure empty = Part.Measure.rest(score.lengthOf(mi));
                    empty.clefChange = score.parts.get(p).measures.get(mi).clefChange;
                    touched.put(key(p, mi), empty);
                }
            }
            edit(changesFor(touched));
            return;
        }
        if (range != null) {
            for (int p = range.part1(); p <= range.part2(); p++) {
                for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                    Part.Measure m = measureCopy(p, mi);
                    for (int v = 0; v < Part.VOICES; v++) {
                        List<Frac> starts = VoiceEdit.starts(m.voices[v]);
                        for (int i = m.voices[v].size() - 1; i >= 0; i--) {
                            if (range.contains(p, mi, starts.get(i))) {
                                m.voices[v] = new ArrayList<>(VoiceEdit.toRest(m.voices[v], i));
                            }
                        }
                        m.voices[v] = new ArrayList<>(VoiceEdit.tidyRests(m.voices[v]));
                    }
                    touched.put(key(p, mi), m);
                }
            }
            edit(changesFor(touched));
            return;
        }
        List<ChordRef> refs = refs();
        refs.sort((a, b) -> b.chord() != a.chord() ? Integer.compare(b.chord(), a.chord()) : Integer.compare(b.note(), a.note()));
        for (ChordRef ref : refs) {
            Part.Measure m = touched.computeIfAbsent(key(ref.part(), ref.measure()), k -> measureCopy(ref.part(), ref.measure()));
            List<Chord> voice = m.voices[ref.voice()];
            if (ref.chord() >= voice.size()) {
                continue;
            }
            Chord c = voice.get(ref.chord());
            if (ref.note() >= 0 && c.notes.size() > 1) {
                c.notes.remove(Math.min(ref.note(), c.notes.size() - 1));
            } else {
                m.voices[ref.voice()] = new ArrayList<>(VoiceEdit.toRest(voice, ref.chord()));
            }
        }
        edit(changesFor(touched));
        if (selected != null) {
            selected = new ChordRef(selected.part(), selected.measure(), selected.voice(),
                    Math.max(0, Math.min(selected.chord(), voiceOf(selected).size() - 1)), -1);
        }
        also.clear();
    }

    // --- clipboard --------------------------------------------------------------------------

    /** Copies the selection: every chord in it, with where it falls from the selection's start. */
    public void copy() {
        List<Clip> copied = new ArrayList<>();
        if (range != null) {
            Frac start = abs(range.measure1(), range.at1());
            for (int p = range.part1(); p <= range.part2(); p++) {
                for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                    Part.Measure m = score.parts.get(p).measures.get(mi);
                    for (int v = 0; v < Part.VOICES; v++) {
                        List<Frac> starts = VoiceEdit.starts(m.voices[v]);
                        for (int i = 0; i < m.voices[v].size(); i++) {
                            if (range.contains(p, mi, starts.get(i))) {
                                copied.add(new Clip(p - range.part1(), v, abs(mi, starts.get(i)).minus(start), m.voices[v].get(i).save()));
                            }
                        }
                    }
                }
            }
            clipLength = abs(range.measure2(), range.at2()).minus(start);
            clipParts = range.part2() - range.part1() + 1;
        } else if (!refs().isEmpty()) {
            List<ChordRef> refs = refs();
            Frac first = null;
            int firstPart = Integer.MAX_VALUE, lastPart = 0;
            for (ChordRef ref : refs) {
                Frac at = abs(ref.measure(), startOf(ref));
                first = first == null ? at : Frac.min(first, at);
                firstPart = Math.min(firstPart, ref.part());
                lastPart = Math.max(lastPart, ref.part());
            }
            Frac end = first;
            for (ChordRef ref : refs) {
                Chord c = voiceOf(ref).get(ref.chord());
                Frac offset = abs(ref.measure(), startOf(ref)).minus(first);
                copied.add(new Clip(ref.part() - firstPart, ref.voice(), offset, c.save()));
                end = Frac.max(end, first.plus(offset).plus(c.length()));
            }
            clipLength = end.minus(first);
            clipParts = lastPart - firstPart + 1;
        }
        if (!copied.isEmpty()) {
            clip = copied;
        }
    }

    public void cut() {
        copy();
        delete();
    }

    /** Pastes at the selection (or the cursor), overwriting the time it covers, as MuseScore does. */
    public void paste() {
        int part = currentPart();
        Frac start = selected != null ? abs(selected.measure(), startOf(selected))
                : range != null ? abs(range.measure1(), range.at1()) : abs(cursorMeasure, cursorAt);
        pasteAt(part, start);
    }

    private void pasteAt(int part, Frac start) {
        if (clip.isEmpty()) {
            return;
        }
        java.util.Map<Long, Part.Measure> touched = new java.util.LinkedHashMap<>();
        Frac end = start.plus(clipLength);
        for (int r = 0; r < clipParts && part + r < score.parts.size(); r++) {
            clearSpan(touched, part + r, start, end);
        }
        for (Clip c : clip) {
            int p = part + c.part();
            Point at = locate(start.plus(c.offset()));
            if (p >= score.parts.size() || at == null) {
                continue;
            }
            Part.Measure m = touched.computeIfAbsent(key(p, at.measure()), k -> measureCopy(p, at.measure()));
            m.voices[c.voice()] = new ArrayList<>(VoiceEdit.place(m.voices[c.voice()], score.lengthOf(at.measure()), at.at(), Chord.load(c.chord())));
        }
        edit(changesFor(touched));
        Point a = locate(start);
        if (a != null) {
            Point b = locateEnd(Frac.min(end, abs(score.measureCount(), Frac.ZERO)));
            selectRange(new Range(part, Math.min(score.parts.size() - 1, part + clipParts - 1), a.measure(), a.at(), b.measure(), b.at()));
        }
    }

    /** Rests over a stretch of a part (voice 1), and its other voices emptied where they lie wholly inside it. */
    private void clearSpan(java.util.Map<Long, Part.Measure> touched, int part, Frac from, Frac to) {
        Frac measureStart = Frac.ZERO;
        for (int mi = 0; mi < score.measureCount() && measureStart.compareTo(to) < 0; mi++) {
            Frac length = score.lengthOf(mi);
            Frac measureEnd = measureStart.plus(length);
            if (measureEnd.compareTo(from) > 0) {
                int measure = mi;
                Part.Measure m = touched.computeIfAbsent(key(part, mi), k -> measureCopy(part, measure));
                Frac a = Frac.max(from, measureStart).minus(measureStart), b = Frac.min(to, measureEnd).minus(measureStart);
                Frac at = a;
                for (Chord rest : Rests.fill(b.minus(a))) {
                    m.voices[0] = new ArrayList<>(VoiceEdit.place(m.voices[0], length, at, rest));
                    at = at.plus(rest.length());
                }
                if (a.isZero() && b.equals(length)) {
                    for (int v = 1; v < Part.VOICES; v++) {
                        m.voices[v].clear();
                    }
                }
            }
            measureStart = measureEnd;
        }
    }

    /** R: the selection again, straight after itself. */
    public void repeat() {
        Frac end;
        int part = currentPart();
        if (range != null) {
            end = abs(range.measure2(), range.at2());
        } else if (selected != null && selectedChord() != null) {
            end = abs(selected.measure(), startOf(selected)).plus(selectedChord().length());
        } else {
            return;
        }
        copy();
        pasteAt(part, end);
    }

    // --- moving around ----------------------------------------------------------------------

    /** The next (or previous) chord in the selected voice, on across barlines. */
    public void step(int direction) {
        if (selected == null) {
            if (range != null) {
                Point at = direction > 0 ? locateEnd(abs(range.measure2(), range.at2())) : new Point(range.measure1(), range.at1());
                select(chordAt(range.part1(), at.measure(), direction > 0 ? Frac.max(Frac.ZERO, at.at().minus(Frac.of(1, 1024))) : at.at()));
            }
            return;
        }
        int p = selected.part(), m = selected.measure(), v = selected.voice(), i = selected.chord() + direction;
        while (m >= 0 && m < score.measureCount()) {
            List<Chord> voice = score.parts.get(p).measures.get(m).voices[v];
            if (i >= 0 && i < voice.size()) {
                select(navigated(new ChordRef(p, m, v, i, -1)));
                return;
            }
            m += direction;
            if (m >= 0 && m < score.measureCount()) {
                i = direction > 0 ? 0 : score.parts.get(p).measures.get(m).voices[v].size() - 1;
            }
        }
    }

    /** The voice-1 chord sounding at a point in a part. */
    public @Nullable ChordRef chordAt(int part, int measure, Frac at) {
        List<Chord> voice = score.parts.get(part).measures.get(measure).voices[0];
        int i = VoiceEdit.indexAt(voice, at);
        return i < 0 ? null : navigated(new ChordRef(part, measure, 0, i, -1));
    }

    public @Nullable ChordRef firstIn(int part, int measure) {
        List<Chord> voice = score.parts.get(part).measures.get(measure).voices[0];
        return voice.isEmpty() ? null : navigated(new ChordRef(part, measure, 0, 0, -1));
    }

    /** A chord reached with the arrow keys: its top note, bottom note or the whole chord, as the preferences say. */
    private ChordRef navigated(ChordRef ref) {
        Chord c = ref.chord() < voiceOf(ref).size() ? voiceOf(ref).get(ref.chord()) : null;
        EditorPrefs.ArrowSelect pick = EditorPrefs.arrowSelect();
        if (c == null || c.isRest() || pick == EditorPrefs.ArrowSelect.ALL) {
            return new ChordRef(ref.part(), ref.measure(), ref.voice(), ref.chord(), -1);
        }
        int best = 0;
        for (int n = 1; n < c.notes.size(); n++) {
            int step = c.notes.get(n).staffStep(), bestStep = c.notes.get(best).staffStep();
            if (pick == EditorPrefs.ArrowSelect.TOP ? step > bestStep : step < bestStep) {
                best = n;
            }
        }
        return new ChordRef(ref.part(), ref.measure(), ref.voice(), ref.chord(), best);
    }

    /** Alt+Page Up/Down: the chord at the same point on the staff above or below. */
    public void moveStaff(int direction) {
        int p = currentPart() + direction;
        if (p < 0 || p >= score.parts.size()) {
            return;
        }
        Frac at = selected != null ? startOf(selected) : range != null ? range.at1() : cursorAt;
        ChordRef ref = chordAt(p, currentMeasure(), at);
        if (ref != null) {
            select(ref);
        }
    }

    /** Ctrl+Home / Ctrl+End. */
    public void toEnd(boolean last) {
        int m = last ? score.measureCount() - 1 : 0;
        List<Chord> voice = score.parts.get(currentPart()).measures.get(m).voices[0];
        if (!voice.isEmpty()) {
            select(navigated(new ChordRef(currentPart(), m, 0, last ? voice.size() - 1 : 0, -1)));
        }
    }

    // --- marks on their own: dynamics, lines, text ---------------------------------------------

    /** Selects one mark (a dynamic, a line…) on its own. */
    public void selectElement(@Nullable ScoreElement e) {
        selected = null;
        range = null;
        also.clear();
        anchor = null;
        element = e;
        if (e != null && e.measure() < score.measureCount()) {
            cursorMeasure = e.measure();
            if (e.part() >= 0) {
                cursorPart = e.part();
            }
        }
    }

    private boolean exists(ScoreElement e) {
        if (e.kind() == ScoreElement.Kind.LINE) {
            return e.part() < score.parts.size() && e.index() < score.parts.get(e.part()).spanners.size();
        }
        if (e.measure() >= score.measureCount()) {
            return false;
        }
        if (e.kind().onChord()) {
            if (e.part() >= score.parts.size()) {
                return false;
            }
            List<Chord> v = score.parts.get(e.part()).measures.get(e.measure()).voices[e.voice()];
            if (e.chord() >= v.size()) {
                return false;
            }
            Chord c = v.get(e.chord());
            return switch (e.kind()) {
                case DYNAMIC -> c.dynamic != null;
                case ARTICULATION -> c.articulations.contains(Notation.Articulation.values()[e.index()]);
                case ORNAMENT -> c.ornament != Notation.Ornament.NONE;
                case LYRIC -> e.index() < c.lyrics.size() && !c.lyrics.get(e.index()).isEmpty();
                case CHORD_SYMBOL -> c.chordSymbol != null;
                case ARPEGGIO -> c.arpeggio != Notation.Arpeggio.NONE;
                default -> c.staffText != null;
            };
        }
        Bar bar = score.bars.get(e.measure());
        return switch (e.kind()) {
            case TEMPO -> e.index() < bar.tempos.size();
            case SIGN -> bar.markers.contains(Notation.Marker.values()[e.index()]);
            case REHEARSAL -> bar.rehearsal != null;
            default -> bar.endings != 0;
        };
    }

    /** The chord a mark is written on, if it's on one. */
    public @Nullable Chord chordOf(ScoreElement e) {
        if (!e.kind().onChord() || !exists(e)) {
            return null;
        }
        return score.parts.get(e.part()).measures.get(e.measure()).voices[e.voice()].get(e.chord());
    }

    private void onChord(ScoreElement e, Consumer<Chord> change) {
        Part.Measure m = measureCopy(e.part(), e.measure());
        List<Chord> v = m.voices[e.voice()];
        if (e.chord() < v.size()) {
            change.accept(v.get(e.chord()));
            edit(List.of(measureChange(e.part(), e.measure(), m)));
        }
    }

    private void deleteElement(ScoreElement e) {
        if (!exists(e)) {
            return;
        }
        switch (e.kind()) {
            case DYNAMIC -> onChord(e, c -> c.dynamic = null);
            case ARTICULATION -> onChord(e, c -> c.articulations.remove(Notation.Articulation.values()[e.index()]));
            case ORNAMENT -> onChord(e, c -> c.ornament = Notation.Ornament.NONE);
            case LYRIC -> onChord(e, c -> setVerse(c, e.index(), ""));
            case CHORD_SYMBOL -> onChord(e, c -> c.chordSymbol = null);
            case STAFF_TEXT -> onChord(e, c -> c.staffText = null);
            case ARPEGGIO -> onChord(e, c -> c.arpeggio = Notation.Arpeggio.NONE);
            case LINE -> partHeader(e.part(), q -> q.spanners.remove(e.index()));
            case TEMPO -> bar(e.measure(), b -> b.tempos.remove(e.index()));
            case SIGN -> bar(e.measure(), b -> b.markers.remove(Notation.Marker.values()[e.index()]));
            case REHEARSAL -> bar(e.measure(), b -> b.rehearsal = null);
            case ENDING -> bar(e.measure(), b -> b.endings = 0);
        }
    }

    /** The dynamic dropdown: changes a selected dynamic to another. */
    public void changeDynamic(ScoreElement e, Notation.Dynamic dynamic) {
        if (e.kind() == ScoreElement.Kind.DYNAMIC && exists(e)) {
            onChord(e, c -> c.dynamic = dynamic);
        }
    }

    private static void setVerse(Chord c, int verse, String text) {
        while (c.lyrics.size() <= verse) {
            c.lyrics.add("");
        }
        c.lyrics.set(verse, text);
        while (!c.lyrics.isEmpty() && c.lyrics.get(c.lyrics.size() - 1).isEmpty()) {
            c.lyrics.remove(c.lyrics.size() - 1);
        }
    }

    /** Lyric entry: one verse's syllable under a chord ("" removes it). */
    public void setLyric(ChordRef ref, int verse, String text) {
        String clipped = Score.clip(text);
        Chord now = ref.chord() < voiceOf(ref).size() ? voiceOf(ref).get(ref.chord()) : null;
        if (now == null || (verse < now.lyrics.size() ? now.lyrics.get(verse) : "").equals(clipped)) {
            return;
        }
        onChord(ScoreElement.onChord(ScoreElement.Kind.LYRIC, ref.part(), ref.measure(), ref.voice(), ref.chord(), verse),
                c -> setVerse(c, verse, clipped));
    }

    /** Chord symbol entry (Ctrl+K): "" removes it. */
    public void setChordSymbol(ChordRef ref, String text) {
        String clipped = Score.clip(text).strip();
        Chord now = ref.chord() < voiceOf(ref).size() ? voiceOf(ref).get(ref.chord()) : null;
        if (now == null || clipped.equals(now.chordSymbol == null ? "" : now.chordSymbol)) {
            return;
        }
        onChord(ScoreElement.onChord(ScoreElement.Kind.CHORD_SYMBOL, ref.part(), ref.measure(), ref.voice(), ref.chord(), 0),
                c -> c.chordSymbol = clipped.isEmpty() ? null : clipped);
    }

    /** Staff text (Ctrl+T): "" removes it. */
    public void setStaffText(ChordRef ref, String text) {
        String clipped = Score.clip(text).strip();
        Chord now = ref.chord() < voiceOf(ref).size() ? voiceOf(ref).get(ref.chord()) : null;
        if (now == null || clipped.equals(now.staffText == null ? "" : now.staffText)) {
            return;
        }
        onChord(ScoreElement.onChord(ScoreElement.Kind.STAFF_TEXT, ref.part(), ref.measure(), ref.voice(), ref.chord(), 0),
                c -> c.staffText = clipped.isEmpty() ? null : clipped);
    }

    /** A rehearsal mark (Ctrl+M) on a measure: "" removes it. */
    public void setRehearsal(int measure, String text) {
        String clipped = Score.clip(text).strip();
        String now = score.bars.get(measure).rehearsal;
        if (!clipped.equals(now == null ? "" : now)) {
            bar(measure, b -> b.rehearsal = clipped.isEmpty() ? null : clipped);
        }
    }

    /** The next (or previous) chord in a voice, on across barlines, skipping rests if asked; null at either end. */
    public @Nullable ChordRef neighbour(ChordRef ref, int direction, boolean notesOnly) {
        int p = ref.part(), m = ref.measure(), v = ref.voice(), i = ref.chord() + direction;
        while (m >= 0 && m < score.measureCount()) {
            List<Chord> voice = score.parts.get(p).measures.get(m).voices[v];
            while (i >= 0 && i < voice.size()) {
                Chord c = voice.get(i);
                if (!c.isGrace() && (!notesOnly || !c.isRest())) {
                    return new ChordRef(p, m, v, i, -1);
                }
                i += direction;
            }
            m += direction;
            if (m >= 0 && m < score.measureCount()) {
                i = direction > 0 ? 0 : score.parts.get(p).measures.get(m).voices[v].size() - 1;
            }
        }
        return null;
    }

    /**
     * A line (slur, hairpin, 8va…) over what's selected: across a range, from its first chord to its last in
     * each part; on one chord, a slur or glissando runs to the next chord. The same line again takes it off.
     */
    public void addLine(Notation.SpannerType type) {
        List<int[]> parts = new ArrayList<>(); // part, voice
        List<Spanner.Pos[]> spans = new ArrayList<>();
        if (range != null) {
            for (int p = range.part1(); p <= range.part2(); p++) {
                Spanner.Pos first = null, last = null;
                for (int mi = range.measure1(); mi <= range.measure2(); mi++) {
                    List<Chord> v = score.parts.get(p).measures.get(mi).voices[0];
                    List<Frac> starts = VoiceEdit.starts(v);
                    for (int i = 0; i < v.size(); i++) {
                        if (!v.get(i).isGrace() && range.contains(p, mi, starts.get(i))) {
                            Spanner.Pos pos = new Spanner.Pos(mi, starts.get(i));
                            first = first == null ? pos : first;
                            last = pos;
                        }
                    }
                }
                if (first != null) {
                    parts.add(new int[] {p, 0});
                    spans.add(new Spanner.Pos[] {first, last});
                }
            }
        } else if (selected != null && selectedChord() != null) {
            Spanner.Pos start = new Spanner.Pos(selected.measure(), startOf(selected));
            Spanner.Pos end = start;
            if (type == Notation.SpannerType.SLUR || type == Notation.SpannerType.GLISSANDO) {
                ChordRef next = neighbour(selected, 1, type == Notation.SpannerType.SLUR);
                if (next == null) {
                    return;
                }
                end = new Spanner.Pos(next.measure(), startOf(next));
            }
            parts.add(new int[] {selected.part(), selected.voice()});
            spans.add(new Spanner.Pos[] {start, end});
        }
        if (spans.isEmpty()) {
            return;
        }
        List<ScoreChange> changes = new ArrayList<>();
        for (int k = 0; k < spans.size(); k++) {
            int p = parts.get(k)[0], v = parts.get(k)[1];
            Spanner line = new Spanner(type, spans.get(k)[0], spans.get(k)[1], v);
            Part copy = Part.load(score.parts.get(p).save());
            if (!copy.spanners.removeIf(s -> s.type() == type && s.start().equals(line.start()) && s.voice() == line.voice())) {
                copy.spanners.add(line);
            }
            changes.add(new ScoreChange.PartHeader(p, copy.saveHeader()));
        }
        edit(changes);
    }

    /** Ctrl+2…9: the selected chord becomes the start of a tuplet (Ctrl+3 a triplet) in its time. */
    public void makeTuplet(int actual) {
        ChordRef at = selected;
        if (at == null && inputMode) {
            List<Chord> v = score.parts.get(cursorPart).measures.get(cursorMeasure).voices[voice];
            int i = VoiceEdit.indexAt(v, cursorAt);
            at = i < 0 ? null : new ChordRef(cursorPart, cursorMeasure, voice, i, -1);
        }
        if (at == null || at.chord() >= voiceOf(at).size()) {
            return;
        }
        ChordRef ref = at;
        Part.Measure m = measureCopy(ref.part(), ref.measure());
        Frac start = startOf(ref);
        List<Chord> v = m.voices[ref.voice()];
        if (inputMode) {
            // in note input, the tuplet is made at the cursor out of the chosen value, ready to be filled in
            v = new ArrayList<>(VoiceEdit.place(v, score.lengthOf(ref.measure()), start, Chord.rest(duration())));
        }
        List<Chord> made = VoiceEdit.makeTuplet(v, VoiceEdit.indexAt(v, start), actual);
        if (made == null) {
            return;
        }
        m.voices[ref.voice()] = new ArrayList<>(made);
        edit(List.of(measureChange(ref.part(), ref.measure(), m)));
        int index = VoiceEdit.indexAt(voiceOf(ref), start);
        selected = index < 0 ? null : new ChordRef(ref.part(), ref.measure(), ref.voice(), index, -1);
        if (inputMode) {
            cursorPart = ref.part();
            cursorMeasure = ref.measure();
            cursorAt = start;
        }
    }

    /** A grace note (acciaccatura or appoggiatura) before the selected chord, at its top note's pitch; it's then selected. */
    public void addGrace(Notation.Grace type) {
        Chord c = selectedChord();
        if (c == null || c.isRest() || c.isGrace()) {
            return;
        }
        ChordRef ref = selected;
        Note top = c.notes.stream().max((a, b) -> Integer.compare(a.staffStep(), b.staffStep())).orElseThrow();
        Chord grace = Chord.of(new Duration(3, 0), top.withTie(false));
        grace.grace = type;
        Part.Measure m = measureCopy(ref.part(), ref.measure());
        m.voices[ref.voice()].add(ref.chord(), grace);
        edit(List.of(measureChange(ref.part(), ref.measure(), m)));
        select(new ChordRef(ref.part(), ref.measure(), ref.voice(), ref.chord(), -1));
    }

    // --- measures, parts and the header --------------------------------------------------------

    public void insertMeasure(boolean after) {
        int at = currentMeasure() + (after ? 1 : 0);
        edit(List.of(new ScoreChange.InsertMeasure(at)));
    }

    public void appendMeasures(int count) {
        List<ScoreChange> changes = new ArrayList<>();
        for (int i = 0; i < count && score.measureCount() + i < Score.MAX_MEASURES; i++) {
            changes.add(new ScoreChange.InsertMeasure(score.measureCount() + i));
        }
        edit(changes);
    }

    public void deleteMeasures() {
        int from = range != null ? range.measure1() : currentMeasure();
        int to = range != null ? range.measure2() : from;
        List<ScoreChange> changes = new ArrayList<>();
        for (int m = to; m >= from && score.measureCount() - (to - m) > 1; m--) {
            changes.add(new ScoreChange.DeleteMeasure(m));
        }
        selected = null;
        range = null;
        edit(changes);
    }

    public void header(Consumer<Score> change) {
        Score copy = Score.load(score.save());
        change.accept(copy);
        edit(List.of(new ScoreChange.Header(copy.saveHeader())));
    }

    public void partHeader(int p, Consumer<Part> change) {
        Part copy = Part.load(score.parts.get(p).save());
        change.accept(copy);
        edit(List.of(new ScoreChange.PartHeader(p, copy.saveHeader())));
    }

    public void bar(int m, Consumer<Bar> change) {
        Bar copy = Bar.load(score.bars.get(m).save());
        change.accept(copy);
        edit(List.of(new ScoreChange.BarContent(m, copy.save())));
    }

    public void addPart(Instruments.Choice choice) {
        Part part = new Part(autoName(choice, -1), choice.instrument(), choice.clef());
        part.custom = choice.custom();
        for (int m = 0; m < score.measureCount(); m++) {
            part.measures.add(Part.Measure.rest(score.lengthOf(m)));
        }
        edit(List.of(new ScoreChange.AddPart(part.save())));
        cursorPart = score.parts.size() - 1;
        selected = null;
        range = null;
        element = null;
    }

    /** The edit-instrument pencil: a new instrument for a part, renamed to match unless it was given a name of its own. */
    public void setInstrument(int p, Instruments.Choice choice) {
        Part old = score.parts.get(p);
        boolean autoNamed = isAutoName(old.name, Instruments.Choice.of(old));
        boolean autoClef = old.clef == Instruments.Choice.of(old).clef();
        String name = autoName(choice, p);
        partHeader(p, q -> {
            q.instrument = choice.instrument();
            q.custom = choice.custom();
            if (autoNamed) {
                q.name = name;
            }
            if (autoClef) {
                q.clef = choice.clef();
            }
        });
    }

    /** What a part with this instrument is called: its name, numbered if another part already has it ("Bass 2"). */
    private String autoName(Instruments.Choice choice, int except) {
        String base = Instruments.partName(choice.instrument(), choice.custom());
        int same = 0;
        for (int p = 0; p < score.parts.size(); p++) {
            if (p != except && Instruments.partName(score.parts.get(p)).equals(base)) {
                same++;
            }
        }
        return same == 0 ? base : base + " " + (same + 1);
    }

    private static boolean isAutoName(String name, Instruments.Choice choice) {
        String base = Instruments.partName(choice.instrument(), choice.custom());
        return name.isBlank() || name.equals(base) || name.matches(java.util.regex.Pattern.quote(base) + " \\d+")
                || name.equals(name(choice.instrument())) || name.matches("Part \\d+");
    }

    /** An instrument's name, as the instrument list shows it. */
    public static String name(NoteInstrument instrument) {
        return Instruments.name(instrument);
    }

    /** A part's name for an instrument: drums (any of the three) make a drum kit. */
    public static String partName(NoteInstrument instrument) {
        return Instruments.partName(instrument, null);
    }

    public void removePart(int p) {
        selected = null;
        range = null;
        edit(List.of(new ScoreChange.RemovePart(p)));
    }

    public void movePart(int p, int direction) {
        int q = p + direction;
        if (q >= 0 && q < score.parts.size()) {
            edit(List.of(new ScoreChange.SwapParts(p, q)));
            selected = null;
            range = null;
            cursorPart = q;
        }
    }

    /** A new clef from the current measure on (from the start, it's the part's own clef). */
    public void setClef(Clef clef) {
        int p = currentPart(), m = currentMeasure();
        if (m == 0) {
            partHeader(p, part -> part.clef = clef);
        } else {
            Part.Measure measure = measureCopy(p, m);
            measure.clefChange = clef;
            edit(List.of(measureChange(p, m, measure)));
        }
    }

    public void setKey(int fifths) {
        int m = currentMeasure();
        if (m == 0) {
            header(s -> s.key = fifths);
        } else {
            bar(m, b -> b.keyChange = fifths);
        }
    }

    /** A new time signature from the current measure: every measure up to the next change is refitted. */
    public void setTime(Notation.TimeSig time) {
        int from = currentMeasure();
        Score after = Score.load(score.save());
        if (from == 0) {
            after.time = time;
        } else {
            after.bars.get(from).timeChange = time;
        }
        List<ScoreChange> changes = new ArrayList<>();
        changes.add(from == 0 ? new ScoreChange.Header(after.saveHeader()) : new ScoreChange.BarContent(from, after.bars.get(from).save()));
        for (int m = from; m < after.measureCount() && (m == from || after.bars.get(m).timeChange == null); m++) {
            Frac length = after.lengthOf(m);
            for (int p = 0; p < after.parts.size(); p++) {
                Part.Measure measure = after.parts.get(p).measures.get(m);
                for (int v = 0; v < Part.VOICES; v++) {
                    if (v == 0 || !measure.voices[v].isEmpty()) {
                        measure.voices[v] = new ArrayList<>(VoiceEdit.normalize(measure.voices[v], length));
                    }
                }
                changes.add(measureChange(p, m, measure));
            }
        }
        selected = null;
        edit(changes);
    }

    /** A tempo mark where the selection is (replacing one already there). */
    public void setTempo(double bpm, String text) {
        int m = currentMeasure();
        Frac at = selected != null ? startOf(selected) : Frac.ZERO;
        if (m == 0 && at.isZero()) {
            header(s -> s.tempo = bpm);
        }
        bar(m, b -> {
            b.tempos.removeIf(t -> t.at().equals(at));
            b.tempos.add(new Bar.TempoMark(at, bpm, text));
        });
    }

}
