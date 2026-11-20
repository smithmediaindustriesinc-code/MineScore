package com.minescore.audio.notes;

import org.jetbrains.annotations.Nullable;

/**
 * One note block hit.
 *
 * @param seconds when it plays, from the start of the song
 * @param rate    playback speed of the instrument's sample (1 = as recorded)
 * @param volume  0 to 1
 * @param pan     -1 (left) to 1 (right)
 * @param length  how long the note lasts in seconds, held or cut short to fit (SCOPE.md §8.1); 0 or less to
 *                let the sample play out as a note block does
 * @param midi    the MIDI note it sounds (for exporting), or -1 for an unpitched hit
 * @param custom  a resource pack's instrument it plays instead of {@code instrument}, or null (SCOPE.md §8.3)
 */
public record NoteEvent(double seconds, NoteInstrument instrument, double rate, float volume, float pan, double length, int midi,
                        @Nullable String custom) {
    /** A hit that plays its whole sample. */
    public NoteEvent(double seconds, NoteInstrument instrument, double rate, float volume, float pan) {
        this(seconds, instrument, rate, volume, pan, -1, -1, null);
    }

    public NoteEvent(double seconds, NoteInstrument instrument, double rate, float volume, float pan, double length, int midi) {
        this(seconds, instrument, rate, volume, pan, length, midi, null);
    }

    /** Whether it's held (or cut) to a length: only pitched instruments are; drums always play their whole hit. */
    public boolean held() {
        return length > 0 && (custom != null || instrument.pitched());
    }

    public NoteEvent withLength(double newLength) {
        return new NoteEvent(seconds, instrument, rate, volume, pan, newLength, midi, custom);
    }

    /** The same hit, {@code by} seconds earlier. */
    public NoteEvent earlier(double by) {
        return new NoteEvent(seconds - by, instrument, rate, volume, pan, length, midi, custom);
    }
}
