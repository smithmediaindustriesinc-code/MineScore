package com.minescore.score;

import com.minescore.audio.notes.NoteInstrument;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * One notehead, spelled the way it's written: a letter, a sharp or flat, and an octave, so F♯ and G♭ stay
 * themselves (SCOPE.md §8.2).
 *
 * @param step   0 = C … 6 = B
 * @param alter  -2 (double flat) … 2 (double sharp)
 * @param octave scientific octave (middle C is C4)
 * @param tie    tied to the same pitch in the next chord of its voice
 * @param drum   on a percussion staff, the drum it plays; null otherwise
 */
public record Note(int step, int alter, int octave, boolean tie, @Nullable NoteInstrument drum) {
    private static final int[] SEMITONES = {0, 2, 4, 5, 7, 9, 11};

    public Note {
        step = Math.floorMod(step, 7);
        alter = Math.max(-2, Math.min(2, alter));
        octave = Math.max(-1, Math.min(9, octave));
    }

    public static Note of(int step, int alter, int octave) {
        return new Note(step, alter, octave, false, null);
    }

    /** The sounding MIDI note (middle C is 60), before clef or 8va shifts. */
    public int pitch() {
        return 12 * (octave + 1) + SEMITONES[step] + alter;
    }

    /** Position on the staff: octave × 7 + letter. One step is one line or space. */
    public int staffStep() {
        return octave * 7 + step;
    }

    public Note withTie(boolean on) {
        return new Note(step, alter, octave, on, drum);
    }

    /** Spelled the plain way for a MIDI note: sharps going up. */
    public static Note fromPitch(int midi) {
        int octave = Math.floorDiv(midi, 12) - 1;
        int pc = Math.floorMod(midi, 12);
        int[] step = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
        int[] alter = {0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0};
        return Note.of(step[pc], alter[pc], octave);
    }

    /** Up or down by semitones, respelled plainly. */
    public Note transposed(int semitones) {
        Note moved = fromPitch(pitch() + semitones);
        return new Note(moved.step, moved.alter, moved.octave, tie, drum);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("s", (byte) step);
        if (alter != 0) {
            tag.putByte("a", (byte) alter);
        }
        tag.putByte("o", (byte) octave);
        if (tie) {
            tag.putBoolean("t", true);
        }
        if (drum != null) {
            tag.putString("d", drum.name());
        }
        return tag;
    }

    public static Note load(CompoundTag tag) {
        NoteInstrument drum = null;
        if (tag.contains("d")) {
            try {
                drum = NoteInstrument.valueOf(tag.getString("d"));
            } catch (IllegalArgumentException ignored) {
                drum = NoteInstrument.SNARE;
            }
        }
        return new Note(tag.getByte("s"), tag.getByte("a"), tag.getByte("o"), tag.getBoolean("t"), drum);
    }
}
