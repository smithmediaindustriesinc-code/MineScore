package com.minescore.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.score.Notation.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exporting a score and importing it again keeps its notes where they were (SCOPE.md §9.12, M10). */
class ImportExportTest {
    private static final Duration QUARTER = Duration.QUARTER, HALF = new Duration(1, 0), WHOLE = new Duration(0, 0);

    /** C4 quarter, E4 half, quarter rest; then a whole G4. */
    private static Score tune() {
        Score score = Score.blank(2);
        score.title = "Tune";
        List<Chord> first = score.parts.get(0).measures.get(0).voices[0];
        first.clear();
        first.addAll(List.of(Chord.of(QUARTER, Note.of(0, 0, 4)), Chord.of(HALF, Note.of(2, 0, 4)), Chord.rest(QUARTER)));
        List<Chord> second = score.parts.get(0).measures.get(1).voices[0];
        second.clear();
        second.add(Chord.of(WHOLE, Note.of(4, 0, 4)));
        return score;
    }

    /** Every note in a part's first voice: (measure, start, MIDI note, length). */
    private static List<String> notes(Score score) {
        List<String> out = new java.util.ArrayList<>();
        Part part = score.parts.get(0);
        for (int m = 0; m < score.measureCount(); m++) {
            List<Chord> voice = part.measures.get(m).voices[0];
            List<Frac> starts = VoiceEdit.starts(voice);
            for (int i = 0; i < voice.size(); i++) {
                for (Note n : voice.get(i).notes) {
                    out.add(m + "@" + starts.get(i) + ":" + n.pitch() + "/" + voice.get(i).length());
                }
            }
        }
        return out;
    }

    @Test
    void midiRoundTrip() throws Exception {
        Score tune = tune();
        byte[] midi = ScoreExport.toMidi(tune, ScorePlayback.render(tune).events());
        Score back = ScoreImport.fromMidi(midi, "Tune");
        assertEquals(notes(tune), notes(back), "the same notes, at the same places, for the same lengths");
        assertEquals(120, back.tempo, 0.01);
        assertEquals(4, back.time.beats());
    }

    @Test
    void nbsRoundTrip() throws Exception {
        Score tune = tune();
        byte[] nbs = ScoreExport.toNbs(tune, ScorePlayback.render(tune).events());
        Score back = ScoreImport.fromNbs(nbs, "Tune");
        assertEquals("Tune", back.title);
        List<String> notes = notes(back);
        assertEquals(3, notes.size(), "Note Block Studio keeps when each note plays: " + notes);
        assertTrue(notes.get(0).startsWith("0@0/1:60/"));
        assertTrue(notes.get(1).startsWith("0@1/4:64/"));
        assertTrue(notes.get(2).startsWith("1@0/1:67/"));
        assertEquals(120, back.tempo, 0.01);
    }

    @Test
    void mineScoreFilesKeepEverything() throws Exception {
        Score tune = tune();
        tune.parts.get(0).measures.get(0).voices[0].get(0).dynamic = Notation.Dynamic.P;
        Score back = ScoreImport.fromMineScore(ScoreExport.toMineScore(tune));
        assertEquals(tune.save().toString(), back.save().toString());
    }

    @Test
    void longNotesAreTiedOverTheBarlineAndFlatKeysSpellFlat() throws Exception {
        Score score = Score.blank(2);
        score.key = -3; // E flat major
        List<Chord> first = score.parts.get(0).measures.get(0).voices[0];
        first.clear();
        first.add(Chord.rest(HALF));
        first.add(Chord.of(HALF, Note.of(2, -1, 4).withTie(true))); // Eb4 tied into a whole note: a dotted whole
        List<Chord> second = score.parts.get(0).measures.get(1).voices[0];
        second.clear();
        second.add(Chord.of(WHOLE, Note.of(2, -1, 4)));
        Score back = ScoreImport.fromMidi(ScoreExport.toMidi(score, ScorePlayback.render(score).events()), "Tied");
        Chord tied = back.parts.get(0).measures.get(0).voices[0].get(1);
        assertTrue(tied.notes.get(0).tie(), "tied over the barline");
        assertEquals(-1, tied.notes.get(0).alter(), "E flat, not D sharp");
        assertFalse(back.parts.get(0).measures.get(1).voices[0].get(0).notes.get(0).tie());
    }
}
