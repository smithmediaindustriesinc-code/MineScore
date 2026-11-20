package com.minescore.score;

import com.minescore.score.Notation.Barline;
import com.minescore.score.Notation.Marker;
import com.minescore.score.Notation.TimeSig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * What one measure number means for every part at once: time and key changes, tempo marks, repeats,
 * endings, jump signs and its barline (SCOPE.md §8.2).
 */
public final class Bar {
    /** A new time signature from here, or null. */
    public @Nullable TimeSig timeChange;
    /** A new key from here in fifths (-7 = 7 flats … 7 = 7 sharps), or null. */
    public @Nullable Integer keyChange;
    /** An irregular length (a pickup measure), or null for the time signature's. */
    public @Nullable Frac actualLength;
    public boolean repeatStart;
    /** 0: no end repeat; n ≥ 2: play the section n times in all. */
    public int repeatTimes;
    /** Which endings this measure belongs to, as bits (bit 0 = 1st ending); 0 for none. */
    public int endings;
    public Barline barline = Barline.NORMAL;
    public final Set<Marker> markers = EnumSet.noneOf(Marker.class);
    public @Nullable String rehearsal;
    public final List<TempoMark> tempos = new ArrayList<>();

    /**
     * A tempo mark in a measure.
     *
     * @param at  where, in whole notes from the measure's start
     * @param bpm quarter notes a minute
     */
    public record TempoMark(Frac at, double bpm, String text) {
        public TempoMark {
            bpm = Math.max(10, Math.min(400, bpm));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (timeChange != null) {
            tag.putIntArray("ts", new int[] {timeChange.beats(), timeChange.unit(), timeChange.symbol() ? 1 : 0});
        }
        if (keyChange != null) {
            tag.putByte("key", (byte) (int) keyChange);
        }
        if (actualLength != null) {
            tag.putString("len", actualLength.toString());
        }
        if (repeatStart) {
            tag.putBoolean("rs", true);
        }
        if (repeatTimes > 0) {
            tag.putByte("re", (byte) repeatTimes);
        }
        if (endings != 0) {
            tag.putInt("end", endings);
        }
        if (barline != Barline.NORMAL) {
            tag.putByte("bl", (byte) barline.ordinal());
        }
        if (!markers.isEmpty()) {
            int bits = 0;
            for (Marker m : markers) {
                bits |= 1 << m.ordinal();
            }
            tag.putInt("mk", bits);
        }
        if (rehearsal != null) {
            tag.putString("rh", rehearsal);
        }
        if (!tempos.isEmpty()) {
            ListTag list = new ListTag();
            for (TempoMark t : tempos) {
                CompoundTag mark = new CompoundTag();
                mark.putString("at", t.at().toString());
                mark.putDouble("bpm", t.bpm());
                mark.putString("text", t.text());
                list.add(mark);
            }
            tag.put("tempo", list);
        }
        return tag;
    }

    public static Bar load(CompoundTag tag) {
        Bar bar = new Bar();
        if (tag.contains("ts")) {
            int[] t = tag.getIntArray("ts");
            if (t.length == 3) {
                bar.timeChange = new TimeSig(t[0], t[1], t[2] != 0);
            }
        }
        bar.keyChange = tag.contains("key") ? Math.max(-7, Math.min(7, (int) tag.getByte("key"))) : null;
        bar.actualLength = tag.contains("len") ? Frac.parse(tag.getString("len")) : null;
        bar.repeatStart = tag.getBoolean("rs");
        bar.repeatTimes = Math.max(0, Math.min(16, tag.getByte("re")));
        bar.endings = tag.getInt("end");
        bar.barline = Chord.pick(Barline.values(), tag.getByte("bl"), Barline.NORMAL);
        int bits = tag.getInt("mk");
        for (Marker m : Marker.values()) {
            if ((bits & (1 << m.ordinal())) != 0) {
                bar.markers.add(m);
            }
        }
        bar.rehearsal = tag.contains("rh") ? tag.getString("rh") : null;
        for (Tag t : tag.getList("tempo", Tag.TAG_COMPOUND)) {
            CompoundTag mark = (CompoundTag) t;
            bar.tempos.add(new TempoMark(Frac.parse(mark.getString("at")), mark.getDouble("bpm"), mark.getString("text")));
        }
        return bar;
    }
}
