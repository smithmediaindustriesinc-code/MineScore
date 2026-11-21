package com.minescore.score;

import com.minescore.audio.notes.MidiReader;
import com.minescore.audio.notes.NbsReader;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.TimeSig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a MIDI file, a Note Block Studio song or a MineScore file into a score (SCOPE.md §9.12, M10).
 *
 * <p>MIDI notes are placed on the nearest 16th note (or the grid the settings pick) and keep their lengths; Note Block Studio notes (which
 * have none) last until the next note of their instrument, at most a quarter. Each MIDI channel's
 * instrument, and each Note Block Studio instrument, becomes a part (up to 16); all the drums share one
 * percussion part. Chords sound together in one voice: a chord lasts until the next one starts, notes too
 * long for their measure are tied over the barline, and the gaps become rests.
 */
public final class ScoreImport {
    /** Positions are counted in 64ths of a whole note; imports land on 16ths. */
    private static final int UNIT = 64;
    /** A Note Block Studio tick: a 16th note. */
    private static final int NBS_TICK = 4;

    private ScoreImport() {}

    /**
     * One note to place.
     *
     * @param start  where it starts, in 64ths of a whole note
     * @param length how long it lasts, likewise
     * @param group  the part it belongs to
     * @param drum   the drum it plays, in the drum part; null for pitched notes
     */
    private record In(long start, long length, int midi, NoteInstrument instrument, String group, @Nullable NoteInstrument drum) {}

    private record TempoAt(long at, double bpm) {}

    public static Score fromMineScore(byte[] bytes) throws IOException {
        try {
            return Score.load(NbtIo.readCompressed(new ByteArrayInputStream(bytes)));
        } catch (RuntimeException e) {
            throw new IOException("Not a MineScore file", e);
        }
    }

    public static Score fromMidi(byte[] bytes, String title) throws IOException {
        MidiReader.Parsed midi = MidiReader.parse(bytes);
        List<In> notes = new ArrayList<>();
        for (MidiReader.RawNote n : midi.notes()) {
            long start = units(midi, n.tick);
            long end = n.end > n.tick ? units(midi, n.end) : start + grid();
            NoteInstrument instrument = midi.instrument(n);
            if (midi.drum(n)) {
                notes.add(new In(start, grid(), n.key, NoteInstrument.BASEDRUM, "drums", instrument.pitched() ? NoteInstrument.SNARE : instrument));
            } else {
                notes.add(new In(start, Math.max(grid(), end - start), n.key, instrument, n.channel + ":" + instrument.name(), null));
            }
        }
        List<TempoAt> tempos = new ArrayList<>();
        double bpm = 120;
        if (midi.ticksPerQuarter() > 0) {
            for (Map.Entry<Long, Integer> e : midi.tempos().entrySet()) {
                double at = 60e6 / Math.max(1, e.getValue());
                if (e.getKey() == 0) {
                    bpm = at;
                } else {
                    tempos.add(new TempoAt(snap(Math.round(e.getKey() * 16.0 / midi.ticksPerQuarter())), at));
                }
            }
        }
        return build(notes, bpm, new TimeSig(midi.timeBeats(), midi.timeUnit(), false), midi.key(), title, tempos);
    }

    /** A MIDI tick in 64ths of a whole note (a quarter is 16), on the 16th grid; SMPTE files count 120 BPM. */
    private static long units(MidiReader.Parsed midi, long tick) {
        if (midi.ticksPerQuarter() > 0) {
            return snap(Math.round(tick * 16.0 / midi.ticksPerQuarter()));
        }
        return snap(Math.round(midi.seconds(tick) * 2 * 16));
    }

    /** The step MIDI notes land on, in 64ths of a whole note (a 16th unless the settings say otherwise). */
    private static int grid() {
        return com.minescore.config.MineScoreConfig.importGrid();
    }

    private static long snap(long units) {
        int grid = grid();
        return Math.round(units / (double) grid) * grid;
    }

    public static Score fromNbs(byte[] bytes, String title) throws IOException {
        NbsReader.Parsed nbs = NbsReader.parse(bytes);
        // one tick is a 16th note; each note lasts until its instrument's next one, at most a quarter
        Map<Integer, TreeSet<Integer>> ticksOf = new HashMap<>();
        for (NbsReader.RawNote n : nbs.notes()) {
            ticksOf.computeIfAbsent(n.instrument(), k -> new TreeSet<>()).add(n.tick());
        }
        List<In> notes = new ArrayList<>();
        for (NbsReader.RawNote n : nbs.notes()) {
            NoteInstrument instrument = NoteInstrument.byNbsIndex(n.instrument());
            long start = n.tick() * (long) NBS_TICK;
            if (!instrument.pitched()) {
                notes.add(new In(start, NBS_TICK, -1, NoteInstrument.BASEDRUM, "drums", instrument));
                continue;
            }
            Integer next = ticksOf.get(n.instrument()).higher(n.tick());
            long length = Math.min(16, next == null ? 16 : (next - n.tick()) * (long) NBS_TICK);
            notes.add(new In(start, length, n.key() + 21, instrument, instrument.name(), null));
        }
        String name = nbs.name().isBlank() ? title : nbs.name();
        Score score = build(notes, nbs.ticksPerSecond() * 60 / 4, new TimeSig(nbs.beats(), 4, false), 0, name, List.of());
        score.composer = Score.clip(nbs.author());
        return score;
    }

    private static Score build(List<In> notes, double bpm, TimeSig time, int key, String title, List<TempoAt> tempos) throws IOException {
        if (notes.isEmpty()) {
            throw new IOException("It has no notes");
        }
        long measureUnits = (long) time.beats() * UNIT / time.unit();
        long end = 0;
        Map<String, List<In>> groups = new LinkedHashMap<>();
        List<In> sorted = new ArrayList<>(notes);
        sorted.sort((a, b) -> Long.compare(a.start(), b.start()));
        for (In n : sorted) {
            end = Math.max(end, n.start() + n.length());
            if (groups.containsKey(n.group()) || groups.size() < Score.MAX_PARTS) {
                groups.computeIfAbsent(n.group(), g -> new ArrayList<>()).add(n);
            }
        }
        int measures = (int) Math.max(1, Math.min(Score.MAX_MEASURES, (end + measureUnits - 1) / measureUnits));
        Score score = new Score();
        score.title = Score.clip(title);
        score.time = time;
        score.key = key;
        score.tempo = Math.max(10, Math.min(400, bpm));
        score.appendMeasures(measures);
        Map<String, Integer> named = new HashMap<>();
        for (List<In> group : groups.values()) {
            In first = group.get(0);
            boolean drums = first.drum() != null;
            Notation.Clef clef;
            if (drums) {
                clef = Notation.Clef.PERCUSSION;
            } else {
                double average = group.stream().mapToInt(In::midi).average().orElse(60);
                clef = average < 57 ? Notation.Clef.BASS : Notation.Clef.TREBLE;
            }
            String base = Instruments.partName(first.instrument(), null);
            int count = named.merge(base, 1, Integer::sum);
            Part part = new Part(count == 1 ? base : base + " " + count, first.instrument(), clef);
            score.addPart(part);
            write(score, part, group, measureUnits, key);
        }
        for (TempoAt t : tempos) {
            int m = (int) (t.at() / measureUnits);
            if (m < measures) {
                score.bars.get(m).tempos.add(new Bar.TempoMark(Frac.of(t.at() - m * measureUnits, UNIT), t.bpm(), ""));
            }
        }
        return score;
    }

    /** One part's notes, written into its measures: chords in time order, rests between, ties over barlines. */
    private static void write(Score score, Part part, List<In> notes, long measureUnits, int key) {
        List<List<Chord>> voices = new ArrayList<>();
        for (int m = 0; m < score.measureCount(); m++) {
            voices.add(new ArrayList<>());
        }
        long total = measureUnits * score.measureCount();
        long pos = 0;
        int i = 0;
        while (i < notes.size()) {
            long start = notes.get(i).start();
            if (start >= total) {
                break;
            }
            Chord chord = new Chord(Notation.Duration.QUARTER);
            long length = 0;
            int j = i;
            while (j < notes.size() && notes.get(j).start() == start) { // everything starting together is one chord
                In n = notes.get(j++);
                Note note = n.drum() != null ? drumNote(n.drum()) : spell(n.midi(), key);
                if (chord.notes.stream().noneMatch(x -> x.staffStep() == note.staffStep()) && chord.notes.size() < 16) {
                    chord.notes.add(note);
                }
                length = Math.max(length, n.length());
            }
            long next = j < notes.size() ? notes.get(j).start() : total;
            long stop = Math.min(Math.min(start + length, next), total); // until the next chord starts
            if (start < pos) { // overlapped the last one: it starts where that ended
                start = pos;
            }
            if (stop > start) {
                place(score, voices, pos, start, null, measureUnits);
                place(score, voices, start, stop, chord, measureUnits);
                pos = stop;
            }
            i = j;
        }
        place(score, voices, pos, total, null, measureUnits);
        for (int m = 0; m < score.measureCount(); m++) {
            part.measures.get(m).voices[0].clear();
            part.measures.get(m).voices[0].addAll(VoiceEdit.tidyRests(voices.get(m)));
        }
    }

    /** Fills [from, to) with a chord (tied across pieces and barlines) or, for null, with rests. */
    private static void place(Score score, List<List<Chord>> voices, long from, long to, @Nullable Chord chord, long measureUnits) {
        boolean first = true;
        for (long at = from; at < to; ) {
            int m = (int) (at / measureUnits);
            long measureEnd = (m + 1) * measureUnits;
            long stop = Math.min(to, measureEnd);
            Frac start = Frac.of(at - m * measureUnits, UNIT), span = Frac.of(stop - at, UNIT);
            if (chord == null) {
                voices.get(m).addAll(Rests.fillAt(start, span, 0));
            } else {
                voices.get(m).addAll(VoiceEdit.tiedPieces(chord, start, span, first, stop < to));
            }
            first = false;
            at = stop;
        }
    }

    /** A drum's line on the percussion staff: bass drum low, snare in the middle, hi-hat high. */
    private static Note drumNote(NoteInstrument drum) {
        return switch (drum) {
            case BASEDRUM -> new Note(3, 0, 4, false, NoteInstrument.BASEDRUM);
            case HAT -> new Note(4, 0, 5, false, NoteInstrument.HAT);
            default -> new Note(0, 0, 5, false, NoteInstrument.SNARE);
        };
    }

    /** A MIDI note spelled for the key: with flats in flat keys, sharps otherwise. */
    static Note spell(int midi, int key) {
        if (key >= 0) {
            return Note.fromPitch(midi);
        }
        int octave = Math.floorDiv(midi, 12) - 1;
        int pc = Math.floorMod(midi, 12);
        int[] step = {0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6};
        int[] alter = {0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, 0};
        return Note.of(step[pc], alter[pc], octave);
    }
}
