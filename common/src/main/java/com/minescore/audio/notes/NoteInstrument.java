package com.minescore.audio.notes;

/**
 * The note block instruments, in Note Block Studio's order.
 *
 * @param soundEvent the vanilla sound event it plays
 * @param center     the MIDI note its sample plays at normal speed (the middle of its two-octave range);
 *                   -1 for unpitched percussion
 */
public enum NoteInstrument {
    HARP("block.note_block.harp", 66),
    BASS("block.note_block.bass", 42),
    BASEDRUM("block.note_block.basedrum", -1),
    SNARE("block.note_block.snare", -1),
    HAT("block.note_block.hat", -1),
    GUITAR("block.note_block.guitar", 54),
    FLUTE("block.note_block.flute", 78),
    BELL("block.note_block.bell", 90),
    CHIME("block.note_block.chime", 90),
    XYLOPHONE("block.note_block.xylophone", 90),
    IRON_XYLOPHONE("block.note_block.iron_xylophone", 66),
    COW_BELL("block.note_block.cow_bell", 78),
    DIDGERIDOO("block.note_block.didgeridoo", 42),
    BIT("block.note_block.bit", 66),
    BANJO("block.note_block.banjo", 66),
    PLING("block.note_block.pling", 66);

    private final String soundEvent;
    private final int center;

    NoteInstrument(String soundEvent, int center) {
        this.soundEvent = soundEvent;
        this.center = center;
    }

    public String soundEvent() {
        return soundEvent;
    }

    public boolean pitched() {
        return center >= 0;
    }

    /** The MIDI note its sample plays at as recorded, or -1 for drums. */
    public int center() {
        return center;
    }

    /**
     * Playback speed for a score's note (SCOPE.md §9.9): two octaves past the note block's own range either
     * way by stretching the sample; anything further moves by octaves until it fits.
     */
    public double rateStretched(int midiNote) {
        if (!pitched()) {
            return 1;
        }
        int note = midiNote;
        while (note < center - 36) {
            note += 12;
        }
        while (note > center + 36) {
            note -= 12;
        }
        return Math.pow(2, (note - center) / 12.0);
    }

    /** Whether a note is outside what a note block can play (shown orange in the editor). */
    public boolean outsideNaturalRange(int midiNote) {
        return pitched() && Math.abs(midiNote - center) > 12;
    }

    /**
     * Playback speed for a MIDI note. Notes outside the instrument's two octaves move by octaves until
     * they fit, as a note block can only reach 0.5x to 2x speed.
     */
    public double rateFor(int midiNote) {
        if (!pitched()) {
            return 1;
        }
        int note = midiNote;
        while (note < center - 12) {
            note += 12;
        }
        while (note > center + 12) {
            note -= 12;
        }
        return Math.pow(2, (note - center) / 12.0);
    }

    public static NoteInstrument byNbsIndex(int index) {
        NoteInstrument[] all = values();
        return index >= 0 && index < all.length ? all[index] : HARP;
    }

    /** The closest note block instrument for a General MIDI program (0-127). */
    public static NoteInstrument forGeneralMidi(int program) {
        if (program < 8) {
            return HARP; // pianos
        }
        if (program < 16) { // chromatic percussion
            return switch (program) {
                case 11 -> IRON_XYLOPHONE;
                case 12, 13 -> XYLOPHONE;
                case 15 -> HARP;
                default -> BELL;
            };
        }
        if (program < 24) {
            return FLUTE; // organs
        }
        if (program < 32) {
            return GUITAR;
        }
        if (program < 40) {
            return BASS;
        }
        if (program < 56) {
            return FLUTE; // strings, ensembles
        }
        if (program < 64) {
            return BIT; // brass
        }
        if (program < 80) {
            return FLUTE; // reeds, pipes
        }
        if (program < 88) {
            return BIT; // synth leads
        }
        if (program < 96) {
            return PLING; // synth pads
        }
        if (program < 104) {
            return CHIME; // synth effects
        }
        if (program < 112) { // ethnic
            return switch (program) {
                case 104, 105, 106 -> BANJO;
                case 107 -> HARP;
                case 108 -> XYLOPHONE;
                case 109 -> DIDGERIDOO;
                default -> FLUTE;
            };
        }
        return switch (program) { // percussive and sound effects
            case 112 -> BELL;
            case 113 -> COW_BELL;
            case 114 -> IRON_XYLOPHONE;
            case 116, 117 -> BASEDRUM;
            case 118 -> SNARE;
            default -> HAT;
        };
    }

    /** The closest note block sound for a General MIDI drum (channel 10) key. */
    public static NoteInstrument forGeneralMidiDrum(int key) {
        return switch (key) {
            case 35, 36, 41, 43, 45, 47, 48, 50 -> BASEDRUM; // kicks and toms
            case 37, 38, 39, 40 -> SNARE; // snares, rimshot, clap
            case 56 -> COW_BELL;
            default -> HAT; // hi-hats, cymbals, tambourine and the rest
        };
    }
}
