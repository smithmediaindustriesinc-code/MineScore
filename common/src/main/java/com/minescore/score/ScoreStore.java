package com.minescore.score;

import com.minescore.MineScore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * A world's scores (SCOPE.md §9.1), one compressed file each in {@code <world>/minescore/scores/}. Score
 * items only carry an ID, so a big score never bloats an item or a packet. Loaded scores stay in memory
 * while the server runs.
 */
public final class ScoreStore {
    private static final Map<MinecraftServer, ScoreStore> STORES = new HashMap<>();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");

    private final Path dir;
    private final Map<String, Entry> loaded = new HashMap<>();

    /** A score and how many copies of it have been made (copies are numbered from it). */
    private static final class Entry {
        final Score score;
        int copies;

        Entry(Score score, int copies) {
            this.score = score;
            this.copies = copies;
        }
    }

    private ScoreStore(Path dir) {
        this.dir = dir;
    }

    public static synchronized ScoreStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, s -> new ScoreStore(s.getWorldPath(LevelResource.ROOT).resolve("minescore").resolve("scores")));
    }

    public static synchronized void forget(MinecraftServer server) {
        STORES.remove(server);
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** Stores a new score; returns its ID. */
    public String create(Score score) {
        String id = UUID.randomUUID().toString().replace("-", "");
        loaded.put(id, new Entry(score, 0));
        save(id);
        return id;
    }

    public @Nullable Score get(String id) {
        Entry entry = entry(id);
        return entry == null ? null : entry.score;
    }

    /** Writes a score (after it's been edited) to disk. */
    public void save(String id) {
        Entry entry = loaded.get(id);
        if (entry == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Score", entry.score.save());
        tag.putInt("Copies", entry.copies);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(id + ".tmp");
            NbtIo.writeCompressed(tag, tmp.toFile());
            Files.move(tmp, dir.resolve(id + ".dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save score {}", id, e);
        }
    }

    /** Puts a whole new score in place of one (an import), keeping its ID and copy count; saves it. */
    public void replace(String id, Score score) {
        Entry entry = entry(id);
        if (entry == null) {
            return;
        }
        loaded.put(id, new Entry(score, entry.copies));
        save(id);
    }

    /** A new, separate score with the same content; editing it never changes the original. */
    public @Nullable String copy(String id) {
        Score score = get(id);
        return score == null ? null : create(score.copy());
    }

    /** The next copy number for an original: 1, 2, 3… */
    public int nextCopyNumber(String originalId) {
        Entry entry = entry(originalId);
        if (entry == null) {
            return 1;
        }
        entry.copies++;
        save(originalId);
        return entry.copies;
    }

    private @Nullable Entry entry(String id) {
        if (!validId(id)) {
            return null;
        }
        Entry entry = loaded.get(id);
        if (entry != null) {
            return entry;
        }
        Path file = dir.resolve(id + ".dat");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toFile());
            entry = new Entry(Score.load(tag.getCompound("Score")), tag.getInt("Copies"));
            loaded.put(id, entry);
            return entry;
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read score {}", id, e);
            return null;
        }
    }
}
