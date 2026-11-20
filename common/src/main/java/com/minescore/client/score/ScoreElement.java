package com.minescore.client.score;

/**
 * One mark in a score that can be selected on its own (SCOPE.md §8.1): a dynamic, articulation, ornament,
 * lyric, chord symbol, text, tempo mark, line or sign.
 *
 * @param part    its part, or -1 for marks that belong to a whole measure (tempo, signs, rehearsal marks)
 * @param measure its measure
 * @param voice   for marks on a chord, the chord's voice
 * @param chord   for marks on a chord, the chord's index in its voice
 * @param index   which one: the articulation's or sign's ordinal, the lyric's verse, the tempo mark's or the
 *                line's place in its list
 */
public record ScoreElement(Kind kind, int part, int measure, int voice, int chord, int index) {
    public enum Kind {
        DYNAMIC("Dynamic"), ARTICULATION("Articulation"), ORNAMENT("Ornament"), LYRIC("Lyric"), CHORD_SYMBOL("Chord symbol"),
        STAFF_TEXT("Staff text"), ARPEGGIO("Arpeggio"), TEMPO("Tempo mark"), LINE("Line"), SIGN("Sign"), REHEARSAL("Rehearsal mark"),
        ENDING("Ending");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        /** Whether it's written on a chord, rather than on a measure or a part. */
        public boolean onChord() {
            return ordinal() <= ARPEGGIO.ordinal();
        }
    }

    public static ScoreElement onChord(Kind kind, int part, int measure, int voice, int chord, int index) {
        return new ScoreElement(kind, part, measure, voice, chord, index);
    }

    public static ScoreElement onBar(Kind kind, int measure, int index) {
        return new ScoreElement(kind, -1, measure, 0, 0, index);
    }

    public static ScoreElement line(int part, int index) {
        return new ScoreElement(Kind.LINE, part, 0, 0, 0, index);
    }
}
