package com.minescore.score;

/** The small fixed vocabularies of notation (SCOPE.md §9.8). Their ordinals are saved, so only add at the end. */
public final class Notation {
    private Notation() {}

    /**
     * A written note value.
     *
     * @param log  -1 breve, 0 whole, 1 half, 2 quarter … 6 sixty-fourth
     * @param dots 0–2
     */
    public record Duration(int log, int dots) {
        public static final int BREVE = -1, SIXTY_FOURTH = 6;
        public static final Duration QUARTER = new Duration(2, 0);

        public Duration {
            log = Math.max(BREVE, Math.min(SIXTY_FOURTH, log));
            dots = Math.max(0, Math.min(2, dots));
        }

        /** Its length in whole notes, before any tuplet: each dot adds half the previous value. */
        public Frac length() {
            Frac base = log < 0 ? Frac.of(2, 1) : Frac.of(1, 1L << log);
            Frac total = base;
            Frac add = base;
            for (int i = 0; i < dots; i++) {
                add = add.times(Frac.of(1, 2));
                total = total.plus(add);
            }
            return total;
        }
    }

    /**
     * A tuplet: {@code actual} notes in the time of {@code normal} (a triplet is 3:2). Every chord in one
     * shares the same {@code id} within its voice and measure.
     */
    public record Tuplet(int actual, int normal, int id) {
        public Tuplet {
            actual = Math.max(2, Math.min(32, actual));
            normal = Math.max(1, Math.min(32, normal));
        }

        public Frac scale() {
            return Frac.of(normal, actual);
        }
    }

    public enum Clef {
        TREBLE(34, 0), BASS(22, 0), ALTO(28, 0), TENOR(26, 0), TREBLE_8VB(34, -12), PERCUSSION(34, 0);

        /** The note on the middle line as a staff step (octave × 7 + letter: B4 for treble, D3 for bass…). */
        private final int middleLine;
        /** How far it sounds from where it's written, in semitones (treble 8vb sounds an octave down). */
        private final int octaveShift;

        Clef(int middleLine, int octaveShift) {
            this.middleLine = middleLine;
            this.octaveShift = octaveShift;
        }

        public int middleLine() {
            return middleLine;
        }

        public int octaveShift() {
            return octaveShift;
        }
    }

    /** How a measure ends. */
    public enum Barline { NORMAL, DOUBLE, FINAL, DASHED }

    public enum Articulation { STACCATO, STACCATISSIMO, TENUTO, ACCENT, MARCATO, FERMATA }

    public enum Ornament { NONE, TRILL, MORDENT, INVERTED_MORDENT, TURN }

    public enum Grace { NONE, ACCIACCATURA, APPOGGIATURA }

    /** A chord rolled from one note to the next instead of struck at once: a plain arpeggio rolls upward. */
    public enum Arpeggio { NONE, NORMAL, UP, DOWN }

    /** Dynamic marks and the volume each sets (a note block's 0–1). */
    public enum Dynamic {
        PPP(0.15f), PP(0.25f), P(0.38f), MP(0.5f), MF(0.62f), F(0.75f), FF(0.88f), FFF(1f),
        SF(0.88f, true), SFZ(0.95f, true), FP(0.75f, true);

        private final float volume;
        /** Only the note it's on is loud; the music goes back to the level before it (fp drops to p). */
        private final boolean accent;

        Dynamic(float volume) {
            this(volume, false);
        }

        Dynamic(float volume, boolean accent) {
            this.volume = volume;
            this.accent = accent;
        }

        public float volume() {
            return volume;
        }

        public boolean accent() {
            return accent;
        }
    }

    /** Signs that steer playback round the score. */
    public enum Marker { SEGNO, CODA, TO_CODA, FINE, DC, DC_AL_FINE, DC_AL_CODA, DS, DS_AL_FINE, DS_AL_CODA }

    /** Lines drawn from one point in a part to another. */
    public enum SpannerType { SLUR, CRESCENDO, DIMINUENDO, OTTAVA_8VA, OTTAVA_8VB, OTTAVA_15MA, GLISSANDO, RITARDANDO, ACCELERANDO }

    /**
     * A time signature.
     *
     * @param symbol show it as common time (4/4) or cut time (2/2)
     */
    public record TimeSig(int beats, int unit, boolean symbol) {
        public static final TimeSig COMMON = new TimeSig(4, 4, false);

        public TimeSig {
            beats = Math.max(1, Math.min(32, beats));
            unit = Integer.highestOneBit(Math.max(1, Math.min(64, unit)));
        }

        public Frac length() {
            return Frac.of(beats, unit);
        }

        /** The beat a long note is re-struck on: the dotted beat in compound time (6/8 → dotted quarter). */
        public Frac beat() {
            return beats % 3 == 0 && beats > 3 && unit >= 8 ? Frac.of(3, unit) : Frac.of(1, unit);
        }
    }
}
