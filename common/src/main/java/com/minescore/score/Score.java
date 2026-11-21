package com.minescore.score;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Notation.TimeSig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A whole score (SCOPE.md §9): its titles, the measures every part shares ({@link Bar}), and the parts.
 * Every part always has exactly as many measures as there are bars.
 */
public final class Score {
    public static final int MAX_PARTS = 16;
    public static final int MAX_MEASURES = 2000;
    public static final int MAX_TEXT = 64;

    public String title = "";
    public String subtitle = "";
    public String composer = "";
    public String lyricist = "";
    public TimeSig time = TimeSig.COMMON;
    /** Fifths: -7 (seven flats) … 7 (seven sharps). */
    public int key;
    /** Quarter notes a minute at the start. */
    public double tempo = 120;
    public final List<Bar> bars = new ArrayList<>();
    public final List<Part> parts = new ArrayList<>();

    /** A new score: one Harp part, 4/4, C major, 120 BPM, and {@code measures} empty measures. */
    public static Score blank(int measures) {
        Score score = new Score();
        score.addPart(new Part("Harp", NoteInstrument.HARP, Notation.Clef.TREBLE));
        score.appendMeasures(measures);
        return score;
    }

    public int measureCount() {
        return bars.size();
    }

    /** The time signature in force in a measure. */
    public TimeSig timeAt(int measure) {
        TimeSig sig = time;
        for (int i = 0; i <= measure && i < bars.size(); i++) {
            if (bars.get(i).timeChange != null) {
                sig = bars.get(i).timeChange;
            }
        }
        return sig;
    }

    /** The key in force in a measure, in fifths. */
    public int keyAt(int measure) {
        int k = key;
        for (int i = 0; i <= measure && i < bars.size(); i++) {
            if (bars.get(i).keyChange != null) {
                k = bars.get(i).keyChange;
            }
        }
        return k;
    }

    /** How long a measure lasts in whole notes: its own length if irregular, else its time signature's. */
    public Frac lengthOf(int measure) {
        Bar bar = bars.get(measure);
        return bar.actualLength != null ? bar.actualLength : timeAt(measure).length();
    }

    /** Adds a part with rest-filled measures to match the score. False if it's full. */
    public boolean addPart(Part part) {
        if (parts.size() >= MAX_PARTS) {
            return false;
        }
        part.measures.clear();
        for (int i = 0; i < bars.size(); i++) {
            part.measures.add(Part.Measure.rest(lengthOf(i)));
        }
        parts.add(part);
        return true;
    }

    /** Adds empty measures at the end, as many as fit under the limit. */
    public void appendMeasures(int count) {
        for (int n = 0; n < count && bars.size() < MAX_MEASURES; n++) {
            insertMeasure(bars.size());
        }
    }

    /** Inserts an empty measure before {@code index} in every part. */
    public boolean insertMeasure(int index) {
        if (bars.size() >= MAX_MEASURES || index < 0 || index > bars.size()) {
            return false;
        }
        bars.add(index, new Bar());
        Frac length = lengthOf(index);
        for (Part part : parts) {
            part.measures.add(index, Part.Measure.rest(length));
            part.spanners.replaceAll(sp -> shifted(sp, index, 1)); // lines after it move along with their notes
        }
        return true;
    }

    /** Deletes a measure from every part (a score always keeps at least one). */
    public boolean deleteMeasure(int index) {
        if (bars.size() <= 1 || index < 0 || index >= bars.size()) {
            return false;
        }
        bars.remove(index);
        for (Part part : parts) {
            part.measures.remove(index);
            part.spanners.removeIf(s -> s.start().measure() == index || s.end().measure() == index);
            part.spanners.replaceAll(sp -> shifted(sp, index + 1, -1));
        }
        return true;
    }

    /** A line with its ends from measure {@code from} on moved by {@code by} measures. */
    private static Spanner shifted(Spanner s, int from, int by) {
        Spanner.Pos start = s.start().measure() >= from ? new Spanner.Pos(s.start().measure() + by, s.start().at()) : s.start();
        Spanner.Pos end = s.end().measure() >= from ? new Spanner.Pos(s.end().measure() + by, s.end().at()) : s.end();
        return new Spanner(s.type(), start, end, s.voice());
    }

    // --- saving -----------------------------------------------------------------------------

    /** The titles, starting time and key, and tempo: everything but the measures and parts. */
    public CompoundTag saveHeader() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Title", title);
        tag.putString("Subtitle", subtitle);
        tag.putString("Composer", composer);
        tag.putString("Lyricist", lyricist);
        tag.putIntArray("Time", new int[] {time.beats(), time.unit(), time.symbol() ? 1 : 0});
        tag.putByte("Key", (byte) key);
        tag.putDouble("Tempo", tempo);
        return tag;
    }

    public void loadHeader(CompoundTag tag) {
        title = clip(tag.getString("Title"));
        subtitle = clip(tag.getString("Subtitle"));
        composer = clip(tag.getString("Composer"));
        lyricist = clip(tag.getString("Lyricist"));
        int[] t = tag.getIntArray("Time");
        if (t.length == 3) {
            time = new TimeSig(t[0], t[1], t[2] != 0);
        }
        key = Math.max(-7, Math.min(7, tag.getByte("Key")));
        tempo = tag.contains("Tempo") ? Math.max(10, Math.min(400, tag.getDouble("Tempo"))) : 120;
    }

    public CompoundTag save() {
        CompoundTag tag = saveHeader();
        ListTag barList = new ListTag();
        bars.forEach(b -> barList.add(b.save()));
        tag.put("Bars", barList);
        ListTag partList = new ListTag();
        parts.forEach(p -> partList.add(p.save()));
        tag.put("Parts", partList);
        return tag;
    }

    /** Reads a score, trimming anything past the limits and padding parts so every part has every measure. */
    public static Score load(CompoundTag tag) {
        Score score = new Score();
        score.loadHeader(tag);
        for (Tag b : tag.getList("Bars", Tag.TAG_COMPOUND)) {
            if (score.bars.size() < MAX_MEASURES) {
                score.bars.add(Bar.load((CompoundTag) b));
            }
        }
        if (score.bars.isEmpty()) {
            score.bars.add(new Bar());
        }
        for (Tag p : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            if (score.parts.size() >= MAX_PARTS) {
                break;
            }
            Part part = Part.load((CompoundTag) p);
            while (part.measures.size() > score.bars.size()) {
                part.measures.remove(part.measures.size() - 1);
            }
            while (part.measures.size() < score.bars.size()) {
                part.measures.add(Part.Measure.rest(score.lengthOf(part.measures.size())));
            }
            score.parts.add(part);
        }
        return score;
    }

    public Score copy() {
        return load(save());
    }

    public static String clip(String text) {
        String s = text == null ? "" : text.strip();
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }
}
