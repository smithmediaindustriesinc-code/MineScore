package com.minescore.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.notes.NoteEvent;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.Articulation;
import com.minescore.score.Notation.Duration;
import com.minescore.score.Notation.Dynamic;
import com.minescore.score.Notation.Marker;
import com.minescore.score.Notation.SpannerType;
import com.minescore.score.Notation.TimeSig;
import com.minescore.score.Notation.Tuplet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The score format (SCOPE.md §8): lengths, spelling, structure, saving, the played order and playback. */
class ScoreTest {
    private static final Duration QUARTER = Duration.QUARTER;
    private static final Duration HALF = new Duration(1, 0);
    private static final Duration WHOLE = new Duration(0, 0);

    private static Score withNotes(int measures, List<Chord> first) {
        Score score = Score.blank(measures);
        score.parts.get(0).measures.get(0).voices[0].clear();
        score.parts.get(0).measures.get(0).voices[0].addAll(first);
        return score;
    }

    @Test
    void lengthsAddUpExactlyEvenWithTuplets() {
        assertEquals(Frac.of(3, 8), new Duration(2, 1).length(), "dotted quarter");
        assertEquals(Frac.of(7, 16), new Duration(2, 2).length(), "double-dotted quarter");
        assertEquals(Frac.of(2, 1), new Duration(Duration.BREVE, 0).length());
        Chord eighthTriplet = Chord.of(new Duration(3, 0), Note.of(0, 0, 4));
        eighthTriplet.tuplet = new Tuplet(3, 2, 1);
        Frac three = eighthTriplet.length().times(3);
        assertEquals(Frac.of(1, 4), three, "three triplet eighths make a quarter");
        assertEquals(Frac.of(3, 4), Rests.length(Rests.fill(Frac.of(3, 4))));
    }

    @Test
    void notesAreSpelledAndPitched() {
        assertEquals(60, Note.of(0, 0, 4).pitch(), "middle C");
        assertEquals(66, Note.of(3, 1, 4).pitch(), "F#4");
        assertEquals(66, Note.of(4, -1, 4).pitch(), "Gb4 sounds the same");
        assertEquals(Note.of(3, 1, 4), Note.fromPitch(66));
        assertEquals(59, Note.of(0, -1, 4).pitch(), "Cb4 is B3");
        assertEquals(72, Note.of(0, 0, 4).transposed(12).pitch());
    }

    @Test
    void measuresStayInStepAcrossParts() {
        Score score = Score.blank(4);
        score.addPart(new Part("Bass", NoteInstrument.BASS, Part.clefFor(NoteInstrument.BASS)));
        assertEquals(Notation.Clef.BASS, score.parts.get(1).clef);
        score.insertMeasure(1);
        score.deleteMeasure(0);
        assertEquals(4, score.measureCount());
        for (Part part : score.parts) {
            assertEquals(4, part.measures.size());
            assertEquals(Frac.ONE, Rests.length(part.measures.get(0).voices[0]));
        }
        score.bars.get(2).timeChange = new TimeSig(3, 4, false);
        assertEquals(Frac.of(3, 4), score.lengthOf(3), "a time change lasts until the next");
        for (int i = 0; i < Score.MAX_PARTS; i++) {
            score.addPart(new Part("x", NoteInstrument.HARP, Notation.Clef.TREBLE));
        }
        assertEquals(Score.MAX_PARTS, score.parts.size());
    }

    @Test
    void everythingSavesAndLoads() {
        Score score = Score.blank(3);
        score.title = "Test";
        score.composer = "Someone";
        score.key = -3;
        score.time = new TimeSig(6, 8, false);
        Chord chord = Chord.of(new Duration(2, 1), Note.of(0, 0, 4).withTie(true), Note.of(2, -1, 4));
        chord.articulations.add(Articulation.STACCATO);
        chord.dynamic = Dynamic.FF;
        chord.lyrics.add("la-");
        chord.chordSymbol = "Cm";
        chord.tuplet = new Tuplet(3, 2, 7);
        score.parts.get(0).measures.get(1).voices[2].add(chord);
        score.parts.get(0).spanners.add(new Spanner(SpannerType.SLUR, new Spanner.Pos(0, Frac.ZERO), new Spanner.Pos(1, Frac.of(1, 4)), 0));
        Bar bar = score.bars.get(1);
        bar.repeatTimes = 3;
        bar.endings = 0b11;
        bar.markers.add(Marker.DS_AL_CODA);
        bar.tempos.add(new Bar.TempoMark(Frac.of(1, 4), 90, "Andante"));
        Score back = Score.load(score.save());
        assertEquals(score.save(), back.save());
        Chord loaded = back.parts.get(0).measures.get(1).voices[2].get(0);
        assertTrue(loaded.notes.get(0).tie());
        assertEquals(Dynamic.FF, loaded.dynamic);
        assertEquals(new Tuplet(3, 2, 7), loaded.tuplet);
    }

    @Test
    void repeatsEndingsAndJumpsArePlayedInOrder() {
        Score score = Score.blank(6);
        // |: 0 1 [1. 2 :|] [2. 3] 4 5
        score.bars.get(0).repeatStart = true;
        score.bars.get(2).endings = 0b01;
        score.bars.get(2).repeatTimes = 2;
        score.bars.get(3).endings = 0b10;
        assertEquals(List.of(0, 1, 2, 0, 1, 3, 4, 5), Navigation.order(score));

        Score dc = Score.blank(4); // 0 1(Fine) 2 3(D.C. al Fine)
        dc.bars.get(1).markers.add(Marker.FINE);
        dc.bars.get(3).markers.add(Marker.DC_AL_FINE);
        assertEquals(List.of(0, 1, 2, 3, 0, 1), Navigation.order(dc));

        Score ds = Score.blank(6); // 0 1(Segno) 2(To Coda) 3(D.S. al Coda) 4(Coda) 5
        ds.bars.get(1).markers.add(Marker.SEGNO);
        ds.bars.get(2).markers.add(Marker.TO_CODA);
        ds.bars.get(3).markers.add(Marker.DS_AL_CODA);
        ds.bars.get(4).markers.add(Marker.CODA);
        assertEquals(List.of(0, 1, 2, 3, 1, 2, 4, 5), Navigation.order(ds));
    }

    @Test
    void notesPlayOnTimeAndAreHeldForTheirLength() {
        // 120 BPM: a quarter is 0.5 s. A quarter, then a half note held for its second, then a quarter rest.
        Score held = withNotes(1, List.of(Chord.of(QUARTER, Note.of(0, 0, 4)), Chord.of(HALF, Note.of(2, 0, 4)), Chord.rest(QUARTER)));
        List<NoteEvent> notes = ScorePlayback.render(held).events();
        assertEquals(2, notes.size(), "one hit per note");
        assertEquals(0.5, notes.get(0).length(), 1e-6);
        assertEquals(1.0, notes.get(1).length(), 1e-6, "the half note lasts a second");
        assertTrue(notes.get(1).held());
    }

    @Test
    void longNotesAreStruckAgainInARestrikingPart() {
        // A quarter, then a half note (struck again on its 2nd beat), then a quarter rest.
        Score score = withNotes(1, List.of(Chord.of(QUARTER, Note.of(0, 0, 4)), Chord.of(HALF, Note.of(2, 0, 4)), Chord.rest(QUARTER)));
        score.parts.get(0).restrike = true;
        ScorePlayback.Result result = ScorePlayback.render(score);
        List<Double> times = result.events().stream().map(NoteEvent::seconds).toList();
        assertEquals(3, times.size());
        assertEquals(0.0, times.get(0), 1e-6);
        assertEquals(0.5, times.get(1), 1e-6);
        assertEquals(1.0, times.get(2), 1e-6, "the half note again on its second beat");
        assertEquals(2.0, result.seconds(), 1e-6);
        assertEquals(1.0, result.events().get(0).rate() * Math.pow(2, (66 - 60) / 12.0), 1e-6, "C4 on a harp tuned to F#4");
    }

    @Test
    void tiedNotesAreOneNoteAndStaccatoIsNotStruckAgain() {
        Score tied = withNotes(2, List.of(Chord.rest(HALF), Chord.of(HALF, Note.of(0, 0, 4).withTie(true))));
        tied.parts.get(0).measures.get(1).voices[0].clear();
        tied.parts.get(0).measures.get(1).voices[0].add(Chord.of(WHOLE, Note.of(0, 0, 4)));
        List<NoteEvent> held = ScorePlayback.render(tied).events();
        assertEquals(1, held.size(), "held: one note through the tie");
        assertEquals(3.0, held.get(0).length(), 1e-6, "from 1.0 s to the end of the second measure");
        tied.parts.get(0).restrike = true;
        List<NoteEvent> events = ScorePlayback.render(tied).events();
        // struck at 1.0 s, then every beat through both measures: 1.5, 2.0, 2.5, 3.0, 3.5
        assertEquals(6, events.size());
        assertEquals(1.0, events.get(0).seconds(), 1e-6);
        assertEquals(2.0, events.get(2).seconds(), 1e-6, "no fresh strike where the tie lands, just the beat");

        Chord staccato = Chord.of(HALF, Note.of(0, 0, 4));
        staccato.articulations.add(Articulation.STACCATO);
        List<NoteEvent> shortOne = ScorePlayback.render(withNotes(1, List.of(staccato, Chord.rest(HALF)))).events();
        assertEquals(1, shortOne.size());
        assertEquals(0.5, shortOne.get(0).length(), 1e-6, "staccato sounds half its length");
    }

    @Test
    void dynamicsHairpinsAndTempoMarks() {
        Chord soft = Chord.of(QUARTER, Note.of(0, 0, 4));
        soft.dynamic = Dynamic.P;
        Chord loud = Chord.of(QUARTER, Note.of(0, 0, 4));
        loud.dynamic = Dynamic.F;
        Score score = withNotes(1, List.of(soft, Chord.of(QUARTER, Note.of(0, 0, 4)), Chord.of(QUARTER, Note.of(0, 0, 4)), loud));
        score.parts.get(0).spanners.add(new Spanner(SpannerType.CRESCENDO, new Spanner.Pos(0, Frac.ZERO), new Spanner.Pos(0, Frac.of(3, 4)), 0));
        List<NoteEvent> events = ScorePlayback.render(score).events();
        assertTrue(events.get(1).volume() > events.get(0).volume() && events.get(2).volume() > events.get(1).volume(), "growing");
        assertEquals(Dynamic.F.volume(), events.get(3).volume(), 1e-4);

        Score slower = withNotes(1, List.of(Chord.of(QUARTER, Note.of(0, 0, 4)), Chord.of(QUARTER, Note.of(0, 0, 4))));
        slower.bars.get(0).tempos.add(new Bar.TempoMark(Frac.of(1, 4), 60, ""));
        List<NoteEvent> e = ScorePlayback.render(slower).events();
        assertEquals(0.5, e.get(1).seconds(), 1e-6);
        assertEquals(0.5 + 3 * 1.0, ScorePlayback.render(slower).seconds(), 1e-6, "three quarters at 60 BPM after the mark");
    }

    @Test
    void mutedPartsAreSilentAndDrumsUseTheirSound() {
        Score score = withNotes(1, List.of(Chord.of(WHOLE, Note.of(0, 0, 4))));
        score.parts.get(0).mute = true;
        assertTrue(ScorePlayback.render(score).events().isEmpty());
        Score drums = Score.blank(1);
        Part kit = new Part("Drums", NoteInstrument.SNARE, Notation.Clef.PERCUSSION);
        drums.parts.clear();
        drums.addPart(kit);
        kit.measures.get(0).voices[0].clear();
        kit.measures.get(0).voices[0].add(Chord.of(WHOLE, new Note(4, 0, 4, false, NoteInstrument.BASEDRUM)));
        NoteEvent hit = ScorePlayback.render(drums).events().get(0);
        assertEquals(NoteInstrument.BASEDRUM, hit.instrument());
        assertEquals(1.0, hit.rate(), 1e-9);
        assertFalse(NoteInstrument.HARP.outsideNaturalRange(60));
        assertTrue(NoteInstrument.HARP.outsideNaturalRange(90));
        assertEquals(Math.pow(2, 24 / 12.0), NoteInstrument.HARP.rateStretched(66 + 24), 1e-9, "stretched, not folded");
    }

    @Test
    void anArpeggioRollsTheChord() {
        Chord chord = Chord.of(WHOLE, Note.of(4, 0, 4), Note.of(0, 0, 4), Note.of(2, 0, 4)); // G, C, E, written out of order
        chord.arpeggio = Notation.Arpeggio.NORMAL;
        List<NoteEvent> up = ScorePlayback.render(withNotes(1, List.of(chord))).events();
        assertEquals(3, up.size());
        assertTrue(up.get(0).rate() < up.get(1).rate() && up.get(1).rate() < up.get(2).rate(), "lowest first, then upward");
        assertTrue(up.get(1).seconds() > up.get(0).seconds() && up.get(2).seconds() > up.get(1).seconds());
        chord.arpeggio = Notation.Arpeggio.DOWN;
        List<NoteEvent> down = ScorePlayback.render(withNotes(1, List.of(chord))).events();
        assertTrue(down.get(0).rate() > down.get(2).rate(), "rolled down: the top note first");
        assertEquals(chord.arpeggio, Chord.load(chord.save()).arpeggio, "it saves");
    }
}
