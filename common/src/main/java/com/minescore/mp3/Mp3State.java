package com.minescore.mp3;

import com.minescore.song.SongMeta;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an MP3 Player holds and remembers, kept on the item so it travels with it.
 *
 * <p>Times are in game ticks ({@code now}), which every dimension shares. While playing, the position is
 * worked out from {@link #startTick}; while paused it is {@link #position}.
 */
public final class Mp3State {
    private static final String TAG = "MineScoreMp3";
    public static final int EQ_BANDS = 10;
    public static final float EQ_RANGE = 12;
    /** Pressing Previous later than this into a song restarts it instead. */
    private static final double RESTART_SECONDS = 2;

    public enum Repeat { OFF, ALL, ONE }

    public static final class Playlist {
        public String name;
        public final List<String> songs = new ArrayList<>();

        public Playlist(String name) {
            this.name = name;
        }
    }

    public UUID id = UUID.randomUUID();
    public final List<SongMeta> library = new ArrayList<>();
    public final List<Playlist> playlists = new ArrayList<>();
    /** Song ids in play order. */
    public final List<String> queue = new ArrayList<>();
    /** Where the queue came from: empty for the whole library, otherwise a playlist name. */
    public String queueSource = "";
    public int index;
    public boolean playing;
    public long startTick;
    public double position;
    public boolean shuffle;
    public Repeat repeat = Repeat.OFF;
    public float volume = 0.8f;
    /** How fast songs play: 1 except while docked in a Mixer whose Multi-Effects Unit has a Speed effect. */
    public float speed = 1;
    public final float[] eq = new float[EQ_BANDS];
    /** Server bookkeeping: the player whose inventory it's playing in. */
    public @Nullable UUID holder;
    /** Bluetooth: the speaker it's playing through, if any. */
    public @Nullable GlobalPos link;

    // --- reading and writing -----------------------------------------------------------------

    public static boolean has(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(TAG);
    }

    /** The player's state, creating a fresh one (with a new id) the first time. */
    public static Mp3State read(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(TAG) ? load(root.getCompound(TAG)) : new Mp3State();
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(TAG, save());
    }

    public static @Nullable UUID idOf(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(TAG) && root.getCompound(TAG).hasUUID("Id") ? root.getCompound(TAG).getUUID("Id") : null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        ListTag songs = new ListTag();
        for (SongMeta song : library) {
            CompoundTag s = new CompoundTag();
            s.putString("Id", song.id());
            s.putString("Format", song.format());
            s.putString("Title", song.title());
            s.putDouble("Seconds", song.seconds());
            songs.add(s);
        }
        tag.put("Library", songs);
        ListTag lists = new ListTag();
        for (Playlist p : playlists) {
            CompoundTag l = new CompoundTag();
            l.putString("Name", p.name);
            l.put("Songs", strings(p.songs));
            lists.add(l);
        }
        tag.put("Playlists", lists);
        tag.put("Queue", strings(queue));
        tag.putString("QueueSource", queueSource);
        tag.putInt("Index", index);
        tag.putBoolean("Playing", playing);
        tag.putLong("StartTick", startTick);
        tag.putDouble("Position", position);
        tag.putFloat("Speed", speed);
        tag.putBoolean("Shuffle", shuffle);
        tag.putString("Repeat", repeat.name());
        tag.putFloat("Volume", volume);
        ListTag bands = new ListTag();
        for (float v : eq) {
            bands.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        tag.put("Eq", bands);
        if (holder != null) {
            tag.putUUID("Holder", holder);
        }
        if (link != null) {
            tag.putString("LinkDim", link.dimension().location().toString());
            tag.put("LinkPos", NbtUtils.writeBlockPos(link.pos()));
        }
        return tag;
    }

    public static Mp3State load(CompoundTag tag) {
        Mp3State s = new Mp3State();
        if (tag.hasUUID("Id")) {
            s.id = tag.getUUID("Id");
        }
        for (Tag t : tag.getList("Library", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            SongMeta song = new SongMeta(c.getString("Id"), c.getString("Format"), c.getString("Title"), c.getDouble("Seconds"));
            if (SongMeta.validId(song.id())) {
                s.library.add(song);
            }
        }
        for (Tag t : tag.getList("Playlists", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Playlist p = new Playlist(c.getString("Name"));
            readStrings(c.getList("Songs", Tag.TAG_STRING), p.songs);
            s.playlists.add(p);
        }
        readStrings(tag.getList("Queue", Tag.TAG_STRING), s.queue);
        s.queueSource = tag.getString("QueueSource");
        s.index = tag.getInt("Index");
        s.playing = tag.getBoolean("Playing");
        s.startTick = tag.getLong("StartTick");
        s.position = tag.getDouble("Position");
        s.speed = tag.contains("Speed") ? Math.max(0.25f, Math.min(2, tag.getFloat("Speed"))) : 1;
        s.shuffle = tag.getBoolean("Shuffle");
        try {
            s.repeat = Repeat.valueOf(tag.getString("Repeat"));
        } catch (IllegalArgumentException e) {
            s.repeat = Repeat.OFF;
        }
        s.volume = tag.contains("Volume") ? tag.getFloat("Volume") : 0.8f;
        ListTag bands = tag.getList("Eq", Tag.TAG_FLOAT);
        for (int i = 0; i < EQ_BANDS && i < bands.size(); i++) {
            s.eq[i] = bands.getFloat(i);
        }
        s.holder = tag.hasUUID("Holder") ? tag.getUUID("Holder") : null;
        if (tag.contains("LinkDim")) {
            s.link = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("LinkDim"))),
                    NbtUtils.readBlockPos(tag.getCompound("LinkPos")));
        }
        s.clampIndex();
        return s;
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        values.forEach(v -> list.add(StringTag.valueOf(v)));
        return list;
    }

    private static void readStrings(ListTag list, List<String> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getString(i));
        }
    }

    // --- queries -----------------------------------------------------------------------------

    public Optional<SongMeta> song(String id) {
        return library.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<SongMeta> current() {
        return index >= 0 && index < queue.size() ? song(queue.get(index)) : Optional.empty();
    }

    /** Seconds into the current song. */
    public double position(long now) {
        double p = playing ? (now - startTick) / 20.0 * speed : position;
        return Math.max(0, p);
    }

    public Optional<Playlist> playlist(String name) {
        return playlists.stream().filter(p -> p.name.equals(name)).findFirst();
    }

    // --- library and playlists -------------------------------------------------------------

    /** Adds a song to the library. False if it's already there. */
    public boolean addSong(SongMeta song) {
        if (song(song.id()).isPresent()) {
            return false;
        }
        library.add(song);
        if (queueSource.isEmpty()) {
            queue.add(song.id());
        }
        return true;
    }

    /** Deletes a song from the player entirely: library, playlists and queue. */
    public void removeSong(String id, long now) {
        boolean wasCurrent = current().map(s -> s.id().equals(id)).orElse(false);
        library.removeIf(s -> s.id().equals(id));
        playlists.forEach(p -> p.songs.remove(id));
        int at = queue.indexOf(id);
        if (at >= 0) {
            queue.remove(at);
            if (at < index) {
                index--;
            }
        }
        clampIndex();
        if (wasCurrent) {
            restart(now);
            if (queue.isEmpty()) {
                stop();
            }
        }
    }

    public boolean createPlaylist(String name) {
        String clean = name.strip();
        if (clean.isEmpty() || clean.length() > 32 || playlist(clean).isPresent()) {
            return false;
        }
        playlists.add(new Playlist(clean));
        return true;
    }

    public void deletePlaylist(String name) {
        playlists.removeIf(p -> p.name.equals(name));
        if (queueSource.equals(name)) {
            queueSource = "";
            rebuildQueue(null, new Random());
        }
    }

    public void addToPlaylist(String name, String songId) {
        playlist(name).ifPresent(p -> {
            if (song(songId).isPresent() && !p.songs.contains(songId)) {
                p.songs.add(songId);
            }
        });
    }

    public void removeFromPlaylist(String name, String songId) {
        playlist(name).ifPresent(p -> p.songs.remove(songId));
    }

    // --- transport -------------------------------------------------------------------------

    public void play(long now) {
        if (playing) {
            return;
        }
        if (current().isEmpty()) {
            if (library.isEmpty()) {
                return;
            }
            queueSource = "";
            rebuildQueue(null, new Random());
            index = 0;
            position = 0;
        }
        playing = true;
        startTick = now - Math.round(position * 20 / speed);
    }

    public void pause(long now) {
        if (!playing) {
            return;
        }
        position = position(now);
        playing = false;
    }

    public void toggle(long now) {
        if (playing) {
            pause(now);
        } else {
            play(now);
        }
    }

    public void stop() {
        playing = false;
        position = 0;
    }

    public void next(long now, Random random) {
        if (queue.isEmpty()) {
            return;
        }
        if (index + 1 < queue.size()) {
            index++;
        } else {
            index = 0;
            if (shuffle) {
                rebuildQueue(null, random);
            }
        }
        restart(now);
    }

    public void previous(long now) {
        if (queue.isEmpty()) {
            return;
        }
        if (position(now) > RESTART_SECONDS || index == 0) {
            restart(now);
            return;
        }
        index--;
        restart(now);
    }

    public void seek(double seconds, long now) {
        double length = current().map(SongMeta::seconds).orElse(0.0);
        double target = Math.max(0, Math.min(seconds, length));
        if (playing) {
            startTick = now - Math.round(target * 20 / speed);
        } else {
            position = target;
        }
    }

    /** Plays a song from the whole library, which becomes the queue. */
    public void playFromLibrary(String songId, long now, Random random) {
        queueSource = "";
        rebuildQueue(songId, random);
        index = Math.max(0, queue.indexOf(songId));
        playing = true;
        position = 0;
        startTick = now;
    }

    /** Plays a playlist (from one of its songs, or its start), which becomes the queue. */
    public void playPlaylist(String name, @Nullable String songId, long now, Random random) {
        Optional<Playlist> list = playlist(name);
        if (list.isEmpty() || list.get().songs.isEmpty()) {
            return;
        }
        queueSource = name;
        rebuildQueue(songId, random);
        index = songId == null ? 0 : Math.max(0, queue.indexOf(songId));
        playing = true;
        position = 0;
        startTick = now;
    }

    /** Jumps to a place in the queue. */
    public void jumpTo(int queueIndex, long now) {
        if (queueIndex >= 0 && queueIndex < queue.size()) {
            index = queueIndex;
            playing = true;
            position = 0;
            startTick = now;
        }
    }

    public void setShuffle(boolean on, Random random) {
        shuffle = on;
        String currentId = index < queue.size() ? queue.get(index) : null;
        rebuildQueue(currentId, random);
        index = currentId == null ? 0 : Math.max(0, queue.indexOf(currentId));
    }

    public void cycleRepeat() {
        repeat = Repeat.values()[(repeat.ordinal() + 1) % Repeat.values().length];
    }

    /**
     * Moves on when the current song has ended. Returns true if anything changed.
     */
    public boolean tick(long now, Random random) {
        if (!playing) {
            return false;
        }
        Optional<SongMeta> song = current();
        if (song.isEmpty()) {
            stop();
            return true;
        }
        if (position(now) < song.get().seconds()) {
            return false;
        }
        long endTick = startTick + Math.round(song.get().seconds() * 20 / speed);
        if (repeat == Repeat.ONE) {
            startTick = endTick;
        } else if (index + 1 < queue.size()) {
            index++;
            startTick = endTick;
        } else if (repeat == Repeat.ALL) {
            index = 0;
            if (shuffle) {
                rebuildQueue(null, random);
            }
            startTick = endTick;
        } else {
            index = 0;
            stop();
        }
        return true;
    }

    /** Changes the speed without a jump: the song carries on from where it is now. */
    public void setSpeed(float next, long now) {
        next = Math.max(0.25f, Math.min(2, next));
        if (next == speed) {
            return;
        }
        double at = position(now);
        speed = next;
        if (playing) {
            startTick = now - Math.round(at * 20 / speed);
        }
    }

    private void restart(long now) {
        position = 0;
        startTick = now;
    }

    /** Rebuilds the queue from its source, shuffled if on, with {@code first} (if given) at the front. */
    private void rebuildQueue(@Nullable String first, Random random) {
        queue.clear();
        if (queueSource.isEmpty()) {
            library.forEach(s -> queue.add(s.id()));
        } else {
            playlist(queueSource).ifPresent(p -> p.songs.stream().filter(id -> song(id).isPresent()).forEach(queue::add));
        }
        if (shuffle) {
            Collections.shuffle(queue, random);
            if (first != null && queue.remove(first)) {
                queue.add(0, first);
            }
        }
        clampIndex();
    }

    private void clampIndex() {
        if (index >= queue.size()) {
            index = Math.max(0, queue.size() - 1);
        }
        if (index < 0) {
            index = 0;
        }
    }
}
