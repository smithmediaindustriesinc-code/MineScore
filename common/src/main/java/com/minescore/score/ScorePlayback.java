package com.minescore.score;

import com.minescore.MineScoreSettings;
import com.minescore.audio.notes.NoteEvent;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.Articulation;
import com.minescore.score.Notation.Dynamic;
import com.minescore.score.Notation.Grace;
import com.minescore.score.Notation.SpannerType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays a score as note block hits (SCOPE.md §9.9, §9.8): in played order (repeats and jumps followed), at
 * its tempo with ritardando and accelerando, each note held for its length (or struck again every beat, for
 * parts set to re-strike), with dynamics,
 * hairpins, accents, ornaments, grace notes, glissandos and octave lines. {@code NoteRenderer} turns the
 * hits into audio.
 */
public final class ScorePlayback {
    /** Tempo is worked out in steps this long (whole notes), fine enough for any rit. or accel. */
    private static final Frac STEP = Frac.of(1, 96);
    /** How long a grace note sounds before (or on) its beat, in whole notes. */
    private static final Frac GRACE = Frac.of(1, 32);
    /** An arpeggio's notes follow each other this far apart, in seconds (less when the chord is short). */
    private static final double ARPEGGIO_STEP = 0.045;
    /** A trill alternates this fast, in whole notes. */
    private static final Frac TRILL = Frac.of(1, 32);
    /** Dynamics before the first mark. */
    private static final float DEFAULT_LEVEL = Dynamic.MF.volume();

    private ScorePlayback() {}

    /**
     * @param events        every hit, in time order
     * @param seconds       how long it all lasts
     * @param order         the measures in played order
     * @param measureStarts when each played measure starts, in seconds (same order)
     */
    public record Result(List<NoteEvent> events, double seconds, List<Integer> order, double[] measureStarts) {}

    public static Result render(Score score) {
        List<Integer> order = Navigation.order(score);
        Clock clock = new Clock(score, order);
        List<NoteEvent> events = new ArrayList<>();
        boolean anySolo = score.parts.stream().anyMatch(p -> p.solo);
        for (Part part : score.parts) {
            if (part.mute || (anySolo && !part.solo)) {
                continue;
            }
            new PartPlayer(score, part, order, clock, events).play();
        }
        events.sort(Comparator.comparingDouble(NoteEvent::seconds));
        return new Result(events, clock.end(), order, clock.starts);
    }

    /** Where every played measure starts in time, and how far into one a position is. */
    static final class Clock {
        private final Score score;
        private final List<Integer> order;
        final double[] starts;
        /** For each played measure, seconds from its start at each STEP. */
        private final double[][] steps;
        private final Frac[] written; // where each written measure starts, in whole notes

        Clock(Score score, List<Integer> order) {
            this.score = score;
            this.order = order;
            written = new Frac[score.measureCount() + 1];
            written[0] = Frac.ZERO;
            for (int m = 0; m < score.measureCount(); m++) {
                written[m + 1] = written[m].plus(score.lengthOf(m));
            }
            List<Spanner> tempoLines = new ArrayList<>();
            for (Part part : score.parts) {
                for (Spanner s : part.spanners) {
                    if (s.type() == SpannerType.RITARDANDO || s.type() == SpannerType.ACCELERANDO) {
                        tempoLines.add(s);
                    }
                }
            }
            starts = new double[order.size()];
            steps = new double[order.size()][];
            double bpm = score.tempo;
            double t = 0;
            for (int k = 0; k < order.size(); k++) {
                int m = order.get(k);
                Frac length = score.lengthOf(m);
                List<Bar.TempoMark> marks = new ArrayList<>(score.bars.get(m).tempos);
                marks.sort(Comparator.comparing(Bar.TempoMark::at));
                int n = (int) Math.ceil(length.divide(STEP).toDouble());
                double[] at = new double[n + 1];
                int mark = 0;
                for (int i = 0; i < n; i++) {
                    Frac pos = STEP.times(i);
                    while (mark < marks.size() && marks.get(mark).at().compareTo(pos) <= 0) {
                        bpm = marks.get(mark++).bpm();
                    }
                    double factor = 1;
                    Frac abs = written[m].plus(pos);
                    for (Spanner line : tempoLines) {
                        Frac from = absolute(line.start()), to = absolute(line.end());
                        if (abs.compareTo(from) >= 0 && abs.compareTo(to) <= 0 && to.compareTo(from) > 0) {
                            double progress = abs.minus(from).divide(to.minus(from)).toDouble();
                            factor *= line.type() == SpannerType.RITARDANDO ? 1 - 0.4 * progress : 1 + 0.5 * progress;
                        }
                    }
                    Frac step = Frac.min(STEP, length.minus(pos));
                    at[i + 1] = at[i] + step.toDouble() * 4 * 60 / (bpm * factor);
                }
                while (mark < marks.size()) {
                    bpm = marks.get(mark++).bpm();
                }
                starts[k] = t;
                steps[k] = at;
                t += at[n];
            }
        }

        Frac absolute(Spanner.Pos pos) {
            int m = Math.max(0, Math.min(score.measureCount() - 1, pos.measure()));
            return written[m].plus(pos.at());
        }

        Frac absolute(int measure, Frac at) {
            return written[measure].plus(at);
        }

        /** Seconds from the start of the music to a position in played measure {@code k}. */
        double time(int k, Frac pos) {
            double[] at = steps[k];
            double index = pos.divide(STEP).toDouble();
            int i = (int) Math.floor(index);
            if (i >= at.length - 1) {
                return starts[k] + at[at.length - 1] + (index - (at.length - 1)) * (at.length > 1 ? at[at.length - 1] - at[at.length - 2] : 0);
            }
            return starts[k] + at[i] + (index - i) * (at[i + 1] - at[i]);
        }

        double end() {
            int last = order.size() - 1;
            return last < 0 ? 0 : starts[last] + steps[last][steps[last].length - 1];
        }
    }

    /** Plays one part: its dynamics, then each voice in turn. */
    private static final class PartPlayer {
        private final Score score;
        private final Part part;
        private final List<Integer> order;
        private final Clock clock;
        private final List<NoteEvent> events;
        /** Lasting dynamic marks in written order: (where, level). */
        private final List<Map.Entry<Frac, Float>> levels = new ArrayList<>();

        PartPlayer(Score score, Part part, List<Integer> order, Clock clock, List<NoteEvent> events) {
            this.score = score;
            this.part = part;
            this.order = order;
            this.clock = clock;
            this.events = events;
            for (int m = 0; m < part.measures.size(); m++) {
                for (List<Chord> voice : part.measures.get(m).voices) {
                    Frac pos = Frac.ZERO;
                    for (Chord c : voice) {
                        if (c.dynamic != null) {
                            Frac at = clock.absolute(m, pos);
                            if (!c.dynamic.accent()) {
                                levels.add(Map.entry(at, c.dynamic.volume()));
                            } else if (c.dynamic == Dynamic.FP) { // loud, then soft straight after
                                levels.add(Map.entry(at.plus(Frac.of(1, 1024)), Dynamic.P.volume()));
                            }
                        }
                        pos = pos.plus(c.length());
                    }
                }
            }
            levels.sort(Map.Entry.comparingByKey());
        }

        void play() {
            for (int v = 0; v < Part.VOICES; v++) {
                playVoice(v);
            }
        }

        private float levelAt(Frac at) {
            float level = DEFAULT_LEVEL;
            for (Map.Entry<Frac, Float> e : levels) {
                if (e.getKey().compareTo(at) > 0) {
                    break;
                }
                level = e.getValue();
            }
            return level;
        }

        /** Volume at a written point: the dynamic in force, or partway along a hairpin to the next one. */
        private float volumeAt(Spanner.Pos pos) {
            Frac at = clock.absolute(pos.measure(), pos.at());
            for (Spanner s : part.spanners) {
                if ((s.type() == SpannerType.CRESCENDO || s.type() == SpannerType.DIMINUENDO) && s.covers(pos)) {
                    Frac from = clock.absolute(s.start()), to = clock.absolute(s.end());
                    float start = levelAt(from);
                    float target = s.type() == SpannerType.CRESCENDO ? start + 0.25f : start - 0.25f;
                    for (Map.Entry<Frac, Float> e : levels) {
                        if (e.getKey().compareTo(to) >= 0) {
                            target = e.getValue();
                            break;
                        }
                    }
                    double progress = to.compareTo(from) > 0 ? at.minus(from).divide(to.minus(from)).toDouble() : 1;
                    return (float) Math.max(0.05, Math.min(1, start + (target - start) * progress));
                }
            }
            return levelAt(at);
        }

        private int ottava(Spanner.Pos pos) {
            int shift = 0;
            for (Spanner s : part.spanners) {
                if (s.covers(pos)) {
                    shift += switch (s.type()) {
                        case OTTAVA_8VA -> 12;
                        case OTTAVA_8VB -> -12;
                        case OTTAVA_15MA -> 24;
                        default -> 0;
                    };
                }
            }
            return shift;
        }

        /** A tied note still sounding: when it was first struck, and its held hit (-1 when re-struck instead). */
        private record Tied(double start, int event) {}

        /** Whether this part strikes long notes again every beat, rather than holding them (SCOPE.md §9.9). */
        private boolean restrikes() {
            return part.restrike || MineScoreSettings.restrikeLongNotes.getAsBoolean();
        }

        private void playVoice(int voice) {
            Map<Integer, Tied> tiedIn = new HashMap<>(); // sounding pitch -> the tied note it continues
            for (int k = 0; k < order.size(); k++) {
                int m = order.get(k);
                List<Chord> chords = part.measures.get(m).voices[voice];
                Frac length = score.lengthOf(m);
                Frac pos = Frac.ZERO;
                List<Chord> graces = new ArrayList<>();
                for (int c = 0; c < chords.size() && pos.compareTo(length) < 0; c++) {
                    Chord chord = chords.get(c);
                    if (chord.isGrace()) {
                        graces.add(chord);
                        continue;
                    }
                    Frac len = Frac.min(chord.length(), length.minus(pos));
                    if (chord.isRest()) {
                        tiedIn.clear();
                    } else {
                        Chord next = c + 1 < chords.size() ? chords.get(c + 1) : nextMeasureFirst(m, voice);
                        tiedIn = sound(chord, graces, next, k, m, pos, len, voice, tiedIn);
                    }
                    graces.clear();
                    pos = pos.plus(len);
                }
            }
        }

        private Chord nextMeasureFirst(int m, int voice) {
            if (m + 1 >= part.measures.size()) {
                return null;
            }
            List<Chord> chords = part.measures.get(m + 1).voices[voice];
            return chords.isEmpty() ? null : chords.get(0);
        }

        /** Plays one chord; returns the ties it carries into the next one. */
        private Map<Integer, Tied> sound(Chord chord, List<Chord> graces, Chord next, int k, int m, Frac pos, Frac len, int voice,
                                         Map<Integer, Tied> tiedIn) {
            Spanner.Pos at = new Spanner.Pos(m, pos);
            double start = clock.time(k, pos);
            double end = clock.time(k, pos.plus(len));
            float volume = chord.dynamic != null && chord.dynamic.accent() ? chord.dynamic.volume() : volumeAt(at);
            if (chord.articulations.contains(Articulation.MARCATO)) {
                volume *= 1.4f;
            } else if (chord.articulations.contains(Articulation.ACCENT)) {
                volume *= 1.3f;
            } else if (chord.articulations.contains(Articulation.TENUTO)) {
                volume *= 1.1f;
            }
            volume = Math.min(1, volume * part.volume);
            boolean shortNote = chord.articulations.contains(Articulation.STACCATO) || chord.articulations.contains(Articulation.STACCATISSIMO);
            double hold = chord.articulations.contains(Articulation.FERMATA) ? 1.5 : 1;
            // held notes last their written time: half of it staccato, a third staccatissimo
            double sounding = chord.articulations.contains(Articulation.STACCATISSIMO) ? 0.3 : shortNote ? 0.5 : 1;
            boolean restrike = restrikes();
            int shift = part.clefAt(m).octaveShift() + ottava(at);
            double beat = clock.time(k, pos.plus(score.timeAt(m).beat())) - start; // one beat here, however short the note
            double graceSeconds = Math.max(0.03, GRACE.toDouble() * 4 * 60 / bpmNear(beat, score.timeAt(m).beat()));

            // Grace notes: just before the beat, or (appoggiatura) taking half of the note.
            double mainStart = start;
            for (int g = 0; g < graces.size(); g++) {
                Chord grace = graces.get(g);
                boolean appoggiatura = grace.grace == Grace.APPOGGIATURA;
                double when = appoggiatura ? start : start - (graces.size() - g) * graceSeconds;
                if (appoggiatura) {
                    mainStart = start + (end - start) / 2;
                }
                for (Note n : grace.notes) {
                    hit(Math.max(0, when), n, n.pitch() + shift, volume * 0.9f, restrike ? -1 : appoggiatura ? (end - start) / 2 : graceSeconds);
                }
            }

            double noteEnd = start + (end - start) * hold;
            // an arpeggio rolls the chord: each note a little after the one below (above, rolled down)
            List<Note> rollOrder = new ArrayList<>(chord.notes);
            rollOrder.sort(Comparator.comparingInt(Note::pitch));
            if (chord.arpeggio == Notation.Arpeggio.DOWN) {
                java.util.Collections.reverse(rollOrder);
            }
            double roll = chord.arpeggio == Notation.Arpeggio.NONE ? 0
                    : Math.min(ARPEGGIO_STEP, (end - mainStart) / (chord.notes.size() + 1));
            Map<Integer, Tied> carried = new HashMap<>();
            double chordStart = mainStart;
            for (int i = 0; i < chord.notes.size(); i++) {
                Note note = chord.notes.get(i);
                int pitch = note.pitch() + shift;
                Tied tied = tiedIn.get(pitch);
                mainStart = chordStart + roll * rollOrder.indexOf(note);
                double strikeFrom = tied != null ? tied.start() : mainStart;
                int event = -1;
                if (tied == null) {
                    boolean top = i == chord.notes.size() - 1;
                    double length = restrike ? -1 : Math.max(0.03, (noteEnd - mainStart) * (note.tie() ? 1 : sounding));
                    if (top && chord.ornament != Notation.Ornament.NONE) {
                        event = ornament(chord.ornament, note, pitch, mainStart, end, volume, graceSeconds, restrike ? -1 : mainStart + length);
                    } else {
                        event = hit(mainStart, note, pitch, volume, length);
                    }
                } else if (tied.event() >= 0) {
                    // a tie holds the first note on through this one
                    event = tied.event();
                    NoteEvent first = events.get(event);
                    double until = start + (noteEnd - start) * (note.tie() ? 1 : sounding);
                    events.set(event, first.withLength(Math.max(first.length(), until - first.seconds())));
                }
                if (restrike && !shortNote && beat > 0.05) { // re-struck every beat while it lasts, in step across ties
                    double from = tied != null ? start - 0.001 : mainStart + 0.01;
                    for (double t = strikeFrom + beat; t < noteEnd - 0.02; t += beat) {
                        if (t >= from) {
                            hit(t, note, pitch, volume * 0.85f, -1);
                        }
                    }
                }
                if (note.tie()) {
                    carried.put(pitch, new Tied(strikeFrom, event));
                }
            }
            glissando(chord, next, at, voice, shift, chordStart, end, volume, restrike);
            return carried;
        }

        /** The tempo near a point, worked back from how long a beat took. */
        private static double bpmNear(double beatSeconds, Frac beat) {
            return beatSeconds > 0 ? beat.toDouble() * 4 * 60 / beatSeconds : 120;
        }

        /**
         * Plays an ornament; returns its last hit, which holds until {@code until} (when held at all; -1 for
         * hits only).
         */
        private int ornament(Notation.Ornament ornament, Note note, int pitch, double start, double end, float volume, double fast, double until) {
            boolean held = until > 0;
            switch (ornament) {
                case TRILL -> {
                    boolean upper = false;
                    int last = -1;
                    for (double t = start; t < end - 0.01; t += fast) {
                        last = hit(t, note, upper ? pitch + 2 : pitch, volume * (upper ? 0.85f : 1), held ? fast : -1);
                        upper = !upper;
                    }
                    return last;
                }
                case MORDENT, INVERTED_MORDENT -> {
                    int neighbour = ornament == Notation.Ornament.MORDENT ? pitch - 1 : pitch + 2;
                    hit(start, note, pitch, volume, held ? fast : -1);
                    hit(start + fast, note, neighbour, volume * 0.85f, held ? fast : -1);
                    return hit(start + 2 * fast, note, pitch, volume, held ? Math.max(fast, until - start - 2 * fast) : -1);
                }
                case TURN -> {
                    int[] turn = {pitch + 2, pitch, pitch - 1, pitch};
                    int last = -1;
                    for (int i = 0; i < turn.length; i++) {
                        boolean end4 = i == turn.length - 1;
                        last = hit(start + i * fast, note, turn[i], volume * (i == 0 ? 1 : 0.85f),
                                held ? (end4 ? Math.max(fast, until - start - 3 * fast) : fast) : -1);
                    }
                    return last;
                }
                default -> {
                    return hit(start, note, pitch, volume, held ? until - start : -1);
                }
            }
        }

        private void glissando(Chord chord, Chord next, Spanner.Pos at, int voice, int shift, double start, double end, float volume, boolean restrike) {
            if (next == null || next.isRest() || chord.isRest()) {
                return;
            }
            boolean gliss = part.spanners.stream().anyMatch(s -> s.type() == SpannerType.GLISSANDO && s.voice() == voice
                    && s.start().equals(at));
            if (!gliss) {
                return;
            }
            Note from = chord.notes.get(chord.notes.size() - 1);
            int a = from.pitch() + shift, b = next.notes.get(next.notes.size() - 1).pitch() + shift;
            int steps = Math.abs(b - a);
            for (int i = 1; i < steps; i++) {
                double t = start + (end - start) * i / steps;
                hit(t, from, a + Integer.signum(b - a) * i, volume * 0.8f, restrike ? -1 : (end - start) / steps);
            }
        }

        /** Adds one hit, held for {@code length} seconds (pitched instruments; -1 plays it out); returns where it went. */
        private int hit(double seconds, Note note, int pitch, float volume, double length) {
            NoteInstrument instrument = part.instrument;
            if (note.drum() != null) {
                instrument = note.drum();
            }
            float v = Math.max(0, Math.min(1, volume));
            Instruments.Custom custom = note.drum() == null ? Instruments.custom(part.custom) : null;
            if (custom != null) { // a resource pack's instrument (SCOPE.md §9.11)
                events.add(new NoteEvent(seconds, NoteInstrument.HARP, Instruments.rate(custom, pitch), v, part.pan, custom.pitched() ? length : -1,
                        custom.pitched() ? pitch : -1, custom.id()));
                return events.size() - 1;
            }
            double rate = instrument.pitched() ? instrument.rateStretched(pitch) : 1;
            events.add(new NoteEvent(seconds, instrument, rate, v, part.pan, instrument.pitched() ? length : -1, instrument.pitched() ? pitch : -1));
            return events.size() - 1;
        }
    }
}
