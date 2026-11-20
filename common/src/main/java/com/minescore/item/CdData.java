package com.minescore.item;

import com.minescore.song.SongMeta;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What's engraved on a Vinyl Record.
 *
 * @param title  shown as the record's name
 * @param cutBy  the player who cut the original
 * @param copy   0 for the original, otherwise its number among every copy made of that song
 */
public record CdData(SongMeta song, String title, String cutBy, int copy) {
    private static final String TAG = "MineScoreSong";

    public static @Nullable CdData read(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG)) {
            return null;
        }
        CompoundTag tag = root.getCompound(TAG);
        SongMeta song = new SongMeta(tag.getString("Id"), tag.getString("Format"), tag.getString("Title"), tag.getDouble("Seconds"));
        if (!SongMeta.validId(song.id())) {
            return null;
        }
        return new CdData(song, tag.getString("Title"), tag.getString("CutBy"), tag.getInt("Copy"));
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", song.id());
        tag.putString("Format", song.format());
        tag.putString("Title", title);
        tag.putDouble("Seconds", song.seconds());
        tag.putString("CutBy", cutBy);
        tag.putInt("Copy", copy);
        stack.getOrCreateTag().put(TAG, tag);
    }

    /** "3:07", or "1:02:45" for anything over an hour. */
    public String length() {
        long total = Math.round(song.seconds());
        long h = total / 3600;
        long m = total / 60 % 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
