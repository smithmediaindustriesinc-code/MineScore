package com.minescore.score;

import com.minescore.Tr;
import com.minescore.audio.notes.NoteInstrument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Every instrument a score can use (SCOPE.md §8.3): the note block's own, plus any that resource packs add.
 * A pack lists its instruments in {@code assets/<namespace>/minescore/instruments.json}, each with its
 * sound and range; the client reads them whenever its resources load. A part whose pack instrument is
 * missing plays as a harp.
 */
public final class Instruments {
    /**
     * An instrument from a resource pack.
     *
     * @param id      what parts save it as ({@code <namespace>:<name>})
     * @param sound   the sound event it plays (from the pack's {@code sounds.json})
     * @param center  the MIDI note its sound plays at as recorded
     * @param low     the lowest MIDI note it plays well (lower ones show orange in the editor)
     * @param high    the highest
     * @param pitched false for a drum, which always plays its whole hit
     */
    public record Custom(String id, String name, String sound, int center, int low, int high, boolean pitched) {}

    /** An instrument picked for a part: a note block one, or a pack's ({@code custom}, with {@code instrument} its fallback). */
    public record Choice(NoteInstrument instrument, @Nullable String custom) {
        public static Choice of(NoteInstrument instrument) {
            return new Choice(instrument, null);
        }

        public static Choice of(Part part) {
            return new Choice(part.instrument, part.custom);
        }

        /** The key it's enabled or disabled by in the preferences. */
        public String key() {
            return custom != null ? custom : instrument.pitched() ? instrument.name() : "DRUMS";
        }

        public String name() {
            Custom c = Instruments.custom(custom);
            return c != null ? c.name() : instrument.pitched() ? Instruments.name(instrument) : Tr.s("Drum kit");
        }

        public boolean pitched() {
            Custom c = Instruments.custom(custom);
            return c != null ? c.pitched() : instrument.pitched();
        }

        /** A sensible clef: percussion for drums, bass for low instruments, treble otherwise. */
        public Notation.Clef clef() {
            Custom c = Instruments.custom(custom);
            if (c == null) {
                return Part.clefFor(instrument);
            }
            return !c.pitched() ? Notation.Clef.PERCUSSION : c.center() < 55 ? Notation.Clef.BASS : Notation.Clef.TREBLE;
        }
    }

    private static final Map<String, Custom> CUSTOM = new LinkedHashMap<>();

    private Instruments() {}

    /** The instruments the client's resource packs define now. */
    public static synchronized void setCustom(Collection<Custom> all) {
        CUSTOM.clear();
        all.forEach(c -> CUSTOM.put(c.id(), c));
    }

    public static synchronized @Nullable Custom custom(@Nullable String id) {
        return id == null ? null : CUSTOM.get(id);
    }

    public static synchronized List<Custom> customs() {
        return new ArrayList<>(CUSTOM.values());
    }

    /** A note block instrument's name, as the instrument list shows it, in the game's language. */
    public static String name(NoteInstrument instrument) {
        return Tr.s(englishName(instrument));
    }

    /** A note block instrument's name in English. */
    public static String englishName(NoteInstrument instrument) {
        return switch (instrument) {
            case BASEDRUM -> "Bass Drum";
            case SNARE -> "Snare Drum";
            case HAT -> "Hi-hat";
            case IRON_XYLOPHONE -> "Iron Xylophone";
            case COW_BELL -> "Cow Bell";
            default -> {
                String id = instrument.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(id.charAt(0)) + id.substring(1);
            }
        };
    }

    /** What a part with this instrument is called before it's numbered: its name, or "Drums" for any drum. */
    public static String partName(NoteInstrument instrument, @Nullable String custom) {
        Custom c = custom(custom);
        if (c != null) {
            return c.name();
        }
        return instrument.pitched() ? name(instrument) : "Drums";
    }

    public static String partName(Part part) {
        return partName(part.instrument, part.custom);
    }

    /** Whether a part's notes have pitch (not a drum kit). */
    public static boolean pitched(Part part) {
        Custom c = custom(part.custom);
        return c != null ? c.pitched() : part.instrument.pitched();
    }

    /** Whether a sounding note is outside what the part's instrument plays well (drawn orange). */
    public static boolean outsideRange(Part part, int midi) {
        Custom c = custom(part.custom);
        return c != null ? c.pitched() && (midi < c.low() || midi > c.high()) : part.instrument.outsideNaturalRange(midi);
    }

    /**
     * Playback speed of a pack instrument's sound for a note: up to three octaves either way by stretching
     * it, then by octaves until it fits, as for note block sounds.
     */
    public static double rate(Custom c, int midi) {
        if (!c.pitched()) {
            return 1;
        }
        int note = midi;
        while (note < c.center() - 36) {
            note += 12;
        }
        while (note > c.center() + 36) {
            note -= 12;
        }
        return Math.pow(2, (note - c.center()) / 12.0);
    }
}
