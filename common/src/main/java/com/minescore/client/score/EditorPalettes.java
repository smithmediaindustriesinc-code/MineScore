package com.minescore.client.score;

import com.minescore.Tr;
import com.minescore.score.Notation.Arpeggio;
import com.minescore.score.Notation.Articulation;
import com.minescore.score.Notation.Barline;
import com.minescore.score.Notation.Clef;
import com.minescore.score.Notation.Dynamic;
import com.minescore.score.Notation.Grace;
import com.minescore.score.Notation.Marker;
import com.minescore.score.Notation.Ornament;
import com.minescore.score.Notation.SpannerType;
import com.minescore.score.Notation.TimeSig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The editor's palettes (SCOPE.md §8.2), as in MuseScore: click an item to put it on what's selected.
 * Items show a notation symbol, or a few letters where notation has none.
 */
public final class EditorPalettes {
    /**
     * One palette item.
     *
     * @param glyph   a symbol from the atlas to show, or null to show {@code text}
     * @param tooltip what it is, in words
     */
    public record Item(String glyph, String text, String tooltip, Consumer<ScoreEditor> apply) {
        /** What it is, in the game's language. */
        @Override
        public String tooltip() {
            return Tr.s(tooltip);
        }

        /** The English it's written in, for the translation list. */
        public String english() {
            return tooltip;
        }
    }

    public record Category(String name, List<Item> items) {}

    public static final List<Category> CATEGORIES = build();

    private EditorPalettes() {}

    private static List<Category> build() {
        List<Category> all = new ArrayList<>();

        List<Item> clefs = new ArrayList<>();
        clefs.add(new Item("clef_treble", "", "Treble clef", e -> e.setClef(Clef.TREBLE)));
        clefs.add(new Item("clef_bass", "", "Bass clef", e -> e.setClef(Clef.BASS)));
        clefs.add(new Item("clef_c", "", "Alto clef", e -> e.setClef(Clef.ALTO)));
        clefs.add(new Item("clef_c", "T", "Tenor clef", e -> e.setClef(Clef.TENOR)));
        clefs.add(new Item("clef_treble_8vb", "", "Treble clef, an octave down", e -> e.setClef(Clef.TREBLE_8VB)));
        clefs.add(new Item("clef_percussion", "", "Percussion clef", e -> e.setClef(Clef.PERCUSSION)));
        all.add(new Category("Clefs", clefs));

        List<Item> keys = new ArrayList<>();
        String[] majors = {"C♭", "G♭", "D♭", "A♭", "E♭", "B♭", "F", "C", "G", "D", "A", "E", "B", "F♯", "C♯"};
        for (int fifths = -7; fifths <= 7; fifths++) {
            int f = fifths;
            String name = majors[f + 7] + " major";
            keys.add(new Item(null, f == 0 ? "♮" : Math.abs(f) + (f > 0 ? "♯" : "♭"), name, e -> e.setKey(f)));
        }
        all.add(new Category("Key signatures", keys));

        List<Item> times = new ArrayList<>();
        int[][] sigs = {{2, 4}, {3, 4}, {4, 4}, {5, 4}, {6, 8}, {9, 8}, {12, 8}, {2, 2}, {3, 8}, {7, 8}};
        for (int[] t : sigs) {
            times.add(new Item(null, t[0] + "/" + t[1], t[0] + "/" + t[1] + " time", e -> e.setTime(new TimeSig(t[0], t[1], false))));
        }
        times.add(new Item("time_common", "", "Common time (4/4)", e -> e.setTime(new TimeSig(4, 4, true))));
        times.add(new Item("time_cut", "", "Cut time (2/2)", e -> e.setTime(new TimeSig(2, 2, true))));
        all.add(new Category("Time signatures", times));

        List<Item> dynamics = new ArrayList<>();
        for (Dynamic d : Dynamic.values()) {
            String text = d.name().toLowerCase(Locale.ROOT);
            dynamics.add(new Item(null, text, text, e -> e.setDynamic(d)));
        }
        all.add(new Category("Dynamics", dynamics));

        List<Item> articulations = new ArrayList<>();
        articulations.add(new Item("staccato", "", "Staccato", e -> e.toggleArticulation(Articulation.STACCATO)));
        articulations.add(new Item("staccatissimo", "", "Staccatissimo", e -> e.toggleArticulation(Articulation.STACCATISSIMO)));
        articulations.add(new Item("tenuto", "", "Tenuto", e -> e.toggleArticulation(Articulation.TENUTO)));
        articulations.add(new Item("accent", "", "Accent", e -> e.toggleArticulation(Articulation.ACCENT)));
        articulations.add(new Item("marcato", "", "Marcato", e -> e.toggleArticulation(Articulation.MARCATO)));
        articulations.add(new Item("fermata", "", "Fermata", e -> e.toggleArticulation(Articulation.FERMATA)));
        all.add(new Category("Articulations", articulations));

        List<Item> ornaments = new ArrayList<>();
        ornaments.add(new Item("trill", "", "Trill", e -> e.setOrnament(Ornament.TRILL)));
        ornaments.add(new Item("mordent", "", "Mordent", e -> e.setOrnament(Ornament.MORDENT)));
        ornaments.add(new Item("inverted_mordent", "", "Upper mordent", e -> e.setOrnament(Ornament.INVERTED_MORDENT)));
        ornaments.add(new Item("turn", "", "Turn", e -> e.setOrnament(Ornament.TURN)));
        all.add(new Category("Ornaments", ornaments));

        List<Item> lines = new ArrayList<>();
        lines.add(new Item(null, "⌒", "Slur (S)", e -> e.addLine(SpannerType.SLUR)));
        lines.add(new Item(null, "<", "Crescendo hairpin (<)", e -> e.addLine(SpannerType.CRESCENDO)));
        lines.add(new Item(null, ">", "Diminuendo hairpin (>)", e -> e.addLine(SpannerType.DIMINUENDO)));
        lines.add(new Item(null, "8va", "8va: an octave higher", e -> e.addLine(SpannerType.OTTAVA_8VA)));
        lines.add(new Item(null, "8vb", "8vb: an octave lower", e -> e.addLine(SpannerType.OTTAVA_8VB)));
        lines.add(new Item(null, "15ma", "15ma: two octaves higher", e -> e.addLine(SpannerType.OTTAVA_15MA)));
        lines.add(new Item(null, "gl.", "Glissando to the next note", e -> e.addLine(SpannerType.GLISSANDO)));
        lines.add(new Item(null, "rit.", "Ritardando: slowing down", e -> e.addLine(SpannerType.RITARDANDO)));
        lines.add(new Item(null, "acc.", "Accelerando: speeding up", e -> e.addLine(SpannerType.ACCELERANDO)));
        all.add(new Category("Lines", lines));

        List<Item> notes = new ArrayList<>();
        notes.add(new Item(null, "gr/", "Grace note: acciaccatura (/)", e -> e.addGrace(Grace.ACCIACCATURA)));
        notes.add(new Item(null, "gr", "Grace note: appoggiatura", e -> e.addGrace(Grace.APPOGGIATURA)));
        String[] tuplets = {"Duplet", "Triplet", "Quadruplet", "Quintuplet", "Sextuplet", "Septuplet"};
        for (int n = 2; n <= 7; n++) {
            int actual = n;
            notes.add(new Item(null, String.valueOf(n), tuplets[n - 2] + " (Ctrl+" + n + ")", e -> e.makeTuplet(actual)));
        }
        all.add(new Category("Grace notes & tuplets", notes));

        List<Item> text = new ArrayList<>();
        text.add(new Item(null, "la", "Lyrics (Ctrl+L)", e -> e.wantsText = ScoreElement.Kind.LYRIC));
        text.add(new Item(null, "C7", "Chord symbol (Ctrl+K)", e -> e.wantsText = ScoreElement.Kind.CHORD_SYMBOL));
        text.add(new Item(null, "Abc", "Staff text (Ctrl+T)", e -> e.wantsText = ScoreElement.Kind.STAFF_TEXT));
        text.add(new Item(null, "[A]", "Rehearsal mark (Ctrl+M)", e -> e.wantsText = ScoreElement.Kind.REHEARSAL));
        all.add(new Category("Text", text));

        List<Item> arpeggios = new ArrayList<>();
        arpeggios.add(new Item("arpeggio", "", "Arpeggio: the chord rolled upward", e -> e.setArpeggio(Arpeggio.NORMAL)));
        arpeggios.add(new Item("arpeggio_up", "", "Arpeggio up (arrow)", e -> e.setArpeggio(Arpeggio.UP)));
        arpeggios.add(new Item("arpeggio_down", "", "Arpeggio down: rolled from the top", e -> e.setArpeggio(Arpeggio.DOWN)));
        all.add(new Category("Arpeggios", arpeggios));

        List<Item> tempos = new ArrayList<>();
        Object[][] marks = {{"Largo", 50}, {"Adagio", 70}, {"Andante", 90}, {"Moderato", 110}, {"Allegro", 132}, {"Presto", 180}};
        for (Object[] t : marks) {
            String name = (String) t[0];
            int bpm = (Integer) t[1];
            tempos.add(new Item(null, String.valueOf(bpm), name + " (" + bpm + ")", e -> e.setTempo(bpm, name)));
        }
        all.add(new Category("Tempo", tempos));

        List<Item> repeats = new ArrayList<>();
        repeats.add(new Item(null, "|:", "Start repeat", e -> e.bar(e.currentMeasure(), b -> b.repeatStart = !b.repeatStart)));
        repeats.add(new Item(null, ":|", "End repeat", e -> e.bar(e.currentMeasure(), b -> b.repeatTimes = b.repeatTimes == 2 ? 0 : 2)));
        repeats.add(new Item(null, "1.", "1st ending", e -> e.bar(e.currentMeasure(), b -> b.endings ^= 1)));
        repeats.add(new Item(null, "2.", "2nd ending", e -> e.bar(e.currentMeasure(), b -> b.endings ^= 2)));
        repeats.add(new Item(null, "3.", "3rd ending", e -> e.bar(e.currentMeasure(), b -> b.endings ^= 4)));
        repeats.add(new Item(null, "4.", "4th ending", e -> e.bar(e.currentMeasure(), b -> b.endings ^= 8)));
        repeats.add(new Item(null, ":|×3", "End repeat, played 3 times", e -> e.bar(e.currentMeasure(), b -> b.repeatTimes = b.repeatTimes == 3 ? 0 : 3)));
        repeats.add(new Item(null, ":|×4", "End repeat, played 4 times", e -> e.bar(e.currentMeasure(), b -> b.repeatTimes = b.repeatTimes == 4 ? 0 : 4)));
        repeats.add(new Item("segno", "", "Segno", e -> toggle(e, Marker.SEGNO)));
        repeats.add(new Item("coda", "", "Coda", e -> toggle(e, Marker.CODA)));
        repeats.add(new Item(null, "→⊕", "To Coda", e -> toggle(e, Marker.TO_CODA)));
        repeats.add(new Item(null, "Fine", "Fine", e -> toggle(e, Marker.FINE)));
        repeats.add(new Item(null, "DC", "D.C.", e -> toggle(e, Marker.DC)));
        repeats.add(new Item(null, "DC-F", "D.C. al Fine", e -> toggle(e, Marker.DC_AL_FINE)));
        repeats.add(new Item(null, "DC-⊕", "D.C. al Coda", e -> toggle(e, Marker.DC_AL_CODA)));
        repeats.add(new Item(null, "DS", "D.S.", e -> toggle(e, Marker.DS)));
        repeats.add(new Item(null, "DS-F", "D.S. al Fine", e -> toggle(e, Marker.DS_AL_FINE)));
        repeats.add(new Item(null, "DS-⊕", "D.S. al Coda", e -> toggle(e, Marker.DS_AL_CODA)));
        all.add(new Category("Repeats & jumps", repeats));

        List<Item> barlines = new ArrayList<>();
        barlines.add(new Item(null, "|", "Normal barline", e -> e.bar(e.currentMeasure(), b -> b.barline = Barline.NORMAL)));
        barlines.add(new Item(null, "||", "Double barline", e -> e.bar(e.currentMeasure(), b -> b.barline = Barline.DOUBLE)));
        barlines.add(new Item(null, "|▌", "Final barline", e -> e.bar(e.currentMeasure(), b -> b.barline = Barline.FINAL)));
        barlines.add(new Item(null, "¦", "Dashed barline", e -> e.bar(e.currentMeasure(), b -> b.barline = Barline.DASHED)));
        all.add(new Category("Barlines", barlines));
        return all;
    }

    private static void toggle(ScoreEditor e, Marker marker) {
        e.bar(e.currentMeasure(), b -> {
            if (!b.markers.remove(marker)) {
                b.markers.add(marker);
            }
        });
    }
}
