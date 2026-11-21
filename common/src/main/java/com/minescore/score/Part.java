package com.minescore.score;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.Clef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** One instrument's staff: its measures, its lines, and how it sounds in the mixer. */
public final class Part {
    public static final int VOICES = 4;

    public String name;
    public NoteInstrument instrument;
    /** A resource pack's instrument it plays instead ({@link Instruments}), or null; {@code instrument} is then the fallback. */
    public @Nullable String custom;
    public Clef clef;
    public float volume = 1;
    /** -1 left … 1 right. */
    public float pan;
    public boolean mute;
    public boolean solo;
    /** Long notes are struck again every beat instead of held (SCOPE.md §9.9). */
    public boolean restrike;
    public final List<Measure> measures = new ArrayList<>();
    public final List<Spanner> spanners = new ArrayList<>();

    public Part(String name, NoteInstrument instrument, Clef clef) {
        this.name = name;
        this.instrument = instrument;
        this.clef = clef;
    }

    /** A sensible clef for an instrument: percussion for drums, bass for low ones, treble otherwise. */
    public static Clef clefFor(NoteInstrument instrument) {
        if (!instrument.pitched()) {
            return Clef.PERCUSSION;
        }
        return switch (instrument) {
            case BASS, DIDGERIDOO -> Clef.BASS;
            default -> Clef.TREBLE;
        };
    }

    /** One measure of this part: up to four voices, each a run of chords and rests. */
    public static final class Measure {
        @SuppressWarnings("unchecked")
        public final List<Chord>[] voices = new List[VOICES];
        /** A new clef from this measure on, or null. */
        public @Nullable Clef clefChange;

        public Measure() {
            for (int i = 0; i < VOICES; i++) {
                voices[i] = new ArrayList<>();
            }
        }

        /** A measure of one whole-measure rest in voice 1. */
        public static Measure rest(Frac length) {
            Measure m = new Measure();
            m.voices[0].addAll(Rests.fill(length));
            return m;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            for (int v = 0; v < VOICES; v++) {
                if (!voices[v].isEmpty()) {
                    ListTag list = new ListTag();
                    voices[v].forEach(c -> list.add(c.save()));
                    tag.put("v" + v, list);
                }
            }
            if (clefChange != null) {
                tag.putByte("clef", (byte) clefChange.ordinal());
            }
            return tag;
        }

        public static Measure load(CompoundTag tag) {
            Measure m = new Measure();
            for (int v = 0; v < VOICES; v++) {
                for (Tag t : tag.getList("v" + v, Tag.TAG_COMPOUND)) {
                    m.voices[v].add(Chord.load((CompoundTag) t));
                }
            }
            m.clefChange = tag.contains("clef") ? Chord.pick(Clef.values(), tag.getByte("clef"), null) : null;
            return m;
        }
    }

    /** The clef in force in a measure. */
    public Clef clefAt(int measure) {
        Clef clef = this.clef;
        for (int i = 0; i <= measure && i < measures.size(); i++) {
            if (measures.get(i).clefChange != null) {
                clef = measures.get(i).clefChange;
            }
        }
        return clef;
    }

    public CompoundTag save() {
        CompoundTag tag = saveHeader();
        ListTag list = new ListTag();
        measures.forEach(m -> list.add(m.save()));
        tag.put("Measures", list);
        return tag;
    }

    /** Everything but its measures: name, instrument, clef, mixer settings and lines. */
    public CompoundTag saveHeader() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Instrument", instrument.name());
        if (custom != null) {
            tag.putString("Custom", custom);
        }
        tag.putByte("Clef", (byte) clef.ordinal());
        tag.putFloat("Volume", volume);
        tag.putFloat("Pan", pan);
        tag.putBoolean("Mute", mute);
        tag.putBoolean("Solo", solo);
        if (restrike) {
            tag.putBoolean("Restrike", true);
        }
        ListTag lines = new ListTag();
        spanners.forEach(s -> lines.add(s.save()));
        tag.put("Spanners", lines);
        return tag;
    }

    public static Part load(CompoundTag tag) {
        Part part = new Part("", NoteInstrument.HARP, Clef.TREBLE);
        part.loadHeader(tag);
        for (Tag t : tag.getList("Measures", Tag.TAG_COMPOUND)) {
            part.measures.add(Measure.load((CompoundTag) t));
        }
        return part;
    }

    public void loadHeader(CompoundTag tag) {
        name = Score.clip(tag.getString("Name"));
        try {
            instrument = NoteInstrument.valueOf(tag.getString("Instrument"));
        } catch (IllegalArgumentException e) {
            instrument = NoteInstrument.HARP; // missing instruments fall back to harp (SCOPE.md §9.11)
        }
        custom = tag.contains("Custom") ? tag.getString("Custom") : null;
        clef = Chord.pick(Clef.values(), tag.getByte("Clef"), Clef.TREBLE);
        volume = tag.contains("Volume") ? Math.max(0, Math.min(1, tag.getFloat("Volume"))) : 1;
        pan = Math.max(-1, Math.min(1, tag.getFloat("Pan")));
        mute = tag.getBoolean("Mute");
        solo = tag.getBoolean("Solo");
        restrike = tag.getBoolean("Restrike");
        spanners.clear();
        for (Tag t : tag.getList("Spanners", Tag.TAG_COMPOUND)) {
            spanners.add(Spanner.load((CompoundTag) t));
        }
    }
}
