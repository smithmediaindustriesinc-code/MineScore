package com.minescore.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.Duration;
import com.minescore.score.Notation.Tuplet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Note input that overwrites like MuseScore's, and edits that undo exactly (SCOPE.md §9.4). */
class EditingTest {
    private static final Frac BAR = Frac.ONE;
    private static final Duration QUARTER = Duration.QUARTER;
    private static final Duration HALF = new Duration(1, 0);
    private static final Duration EIGHTH = new Duration(3, 0);

    private static Chord note(Duration d, int step) {
        return Chord.of(d, Note.of(step, 0, 4));
    }

    @Test
    void notesFillTheirTimeAndTheRestStaysRests() {
        List<Chord> voice = Rests.fill(BAR);
        voice = VoiceEdit.place(voice, BAR, Frac.ZERO, note(QUARTER, 0));
        voice = VoiceEdit.place(voice, BAR, Frac.of(1, 4), note(QUARTER, 2));
        assertEquals(BAR, Rests.length(voice));
        assertFalse(voice.get(0).isRest());
        assertFalse(voice.get(1).isRest());
        assertTrue(voice.get(2).isRest(), "the second half is still resting");
        assertEquals(List.of(Frac.ZERO, Frac.of(1, 4), Frac.of(1, 2)), VoiceEdit.starts(voice));
    }

    @Test
    void aNoteLandingInsideALongerOneShortensIt() {
        List<Chord> voice = VoiceEdit.place(Rests.fill(BAR), BAR, Frac.ZERO, note(new Duration(0, 0), 0)); // a whole note
        voice = VoiceEdit.place(voice, BAR, Frac.of(1, 2), note(QUARTER, 4));
        assertEquals(BAR, Rests.length(voice));
        assertEquals(HALF, voice.get(0).duration, "the whole note became a half");
        assertFalse(voice.get(0).isRest());
        assertEquals(4, voice.get(1).notes.get(0).step());
        assertTrue(voice.get(2).isRest(), "the last quarter is a rest");
    }

    @Test
    void tooLongForTheBarIsShortenedToFit() {
        List<Chord> voice = VoiceEdit.place(Rests.fill(BAR), BAR, Frac.of(3, 4), note(HALF, 0));
        assertEquals(BAR, Rests.length(voice));
        assertEquals(QUARTER, voice.get(voice.size() - 1).duration);
        assertEquals(new Duration(2, 1), VoiceEdit.fitting(Frac.of(3, 8)), "a dotted quarter fits 3/8");
    }

    @Test
    void cuttingIntoATripletStillAddsUp() {
        List<Chord> voice = Rests.fill(BAR);
        for (int i = 0; i < 3; i++) {
            Chord t = note(EIGHTH, i);
            t.tuplet = new Tuplet(3, 2, 1);
            voice = VoiceEdit.place(voice, BAR, Frac.of(i, 12), t);
        }
        assertEquals(BAR, Rests.length(voice));
        voice = VoiceEdit.place(voice, BAR, Frac.ZERO, note(EIGHTH, 6)); // an eighth over the first triplet and a half
        assertEquals(BAR, Rests.length(voice), "leftovers filled with tuplet rests");
        assertEquals(Frac.of(1, 3), Rests.length(Rests.fill(Frac.of(1, 3))), "a third of a whole note: a triplet half rest");
    }

    @Test
    void deletingLeavesARest() {
        List<Chord> voice = VoiceEdit.place(Rests.fill(BAR), BAR, Frac.ZERO, note(QUARTER, 0));
        voice = VoiceEdit.toRest(voice, 0);
        assertTrue(voice.get(0).isRest());
        assertEquals(BAR, Rests.length(voice));
        assertEquals(-1, VoiceEdit.indexAt(voice, Frac.of(5, 4)));
        assertEquals(0, VoiceEdit.indexAt(voice, Frac.of(1, 8)));
    }

    @Test
    void changesApplyAndUndoExactly() {
        Score score = Score.blank(3);
        String before = score.save().toString();
        Part.Measure measure = new Part.Measure();
        measure.voices[0].addAll(VoiceEdit.place(Rests.fill(BAR), BAR, Frac.ZERO, note(HALF, 2)));
        Part bass = new Part("Bass", NoteInstrument.BASS, Notation.Clef.BASS);
        bass.measures.add(Part.Measure.rest(BAR));
        List<ScoreChange> edit = List.of(
                new ScoreChange.MeasureContent(0, 1, measure.save()),
                new ScoreChange.InsertMeasure(0),
                new ScoreChange.AddPart(bass.save()),
                new ScoreChange.DeleteMeasure(3));
        List<ScoreChange> undo = ScoreChange.applyAll(score, edit);
        assertNotNull(undo);
        assertEquals(2, score.parts.size());
        assertEquals(3, score.measureCount());
        assertEquals(3, score.parts.get(1).measures.size(), "a new part gets every measure");
        assertNotNull(ScoreChange.applyAll(score, undo));
        assertEquals(before, score.save().toString(), "undo puts it all back");

        Score other = Score.blank(1);
        String untouched = other.save().toString();
        assertNull(ScoreChange.applyAll(other, List.of(new ScoreChange.InsertMeasure(0), new ScoreChange.RemovePart(0))),
                "a score keeps one part, so this edit is refused");
        assertEquals(untouched, other.save().toString(), "and nothing of it stays applied");
    }

    @Test
    void aShorterRestSplitsTheRestTheCleanWay() {
        List<Chord> voice = Rests.fill(BAR); // one whole rest
        voice = VoiceEdit.place(voice, BAR, Frac.ZERO, Chord.rest(QUARTER));
        assertEquals(List.of(QUARTER, QUARTER, HALF), voice.stream().map(c -> c.duration).toList(),
                "a quarter rest, then a quarter and a half rest: each on a multiple of its own length");
        assertTrue(voice.stream().allMatch(Chord::isRest));
    }

    @Test
    void aNoteTooLongForTheMeasureIsTiedIntoTheNext() {
        Chord dotted = Chord.of(new Duration(1, 1), Note.of(0, 0, 4)); // a dotted half
        List<Chord> pieces = VoiceEdit.tiedPieces(dotted, Frac.of(1, 2), Frac.of(1, 2), true, true);
        assertEquals(List.of(HALF), pieces.stream().map(c -> c.duration).toList());
        assertTrue(pieces.get(0).notes.get(0).tie(), "tied on over the barline");
        List<Chord> rest = VoiceEdit.tiedPieces(dotted, Frac.ZERO, Frac.of(1, 4), false, false);
        assertEquals(List.of(QUARTER), rest.stream().map(c -> c.duration).toList(), "the dot's quarter in the next measure");
        assertFalse(rest.get(0).notes.get(0).tie());
    }

    @Test
    void aQuarterBecomesATripletOfEighths() {
        List<Chord> voice = VoiceEdit.place(Rests.fill(BAR), BAR, Frac.ZERO, note(QUARTER, 0));
        List<Chord> made = VoiceEdit.makeTuplet(voice, 0, 3);
        assertNotNull(made);
        assertEquals(BAR, Rests.length(made));
        assertEquals(EIGHTH, made.get(0).duration);
        assertEquals(3, made.get(0).tuplet.actual());
        assertFalse(made.get(0).isRest());
        assertTrue(made.get(1).isRest() && made.get(2).isRest());
        assertEquals(Frac.of(1, 4), VoiceEdit.starts(made).get(3), "the music after it hasn't moved");
    }

    @Test
    void linesMoveWithTheirMeasures() {
        Score score = Score.blank(4);
        score.parts.get(0).spanners.add(new Spanner(Notation.SpannerType.SLUR, new Spanner.Pos(1, Frac.ZERO), new Spanner.Pos(2, Frac.ZERO), 0));
        score.insertMeasure(0);
        assertEquals(2, score.parts.get(0).spanners.get(0).start().measure());
        assertEquals(3, score.parts.get(0).spanners.get(0).end().measure());
        score.deleteMeasure(0);
        assertEquals(1, score.parts.get(0).spanners.get(0).start().measure());
    }
}
