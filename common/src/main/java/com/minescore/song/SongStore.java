package com.minescore.song;

import com.google.gson.JsonParser;
import com.minescore.MineScore;
import com.minescore.audio.Loudness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Songs cut on this server, kept in its own install folder ({@code <server>/minescore/cache}), not the
 * world, so every world on the server shares them. In singleplayer that's the game folder, which is
 * also the client's cache, so nothing is copied twice.
 */
public final class SongStore {
    private static final Map<MinecraftServer, SongStore> STORES = new HashMap<>();

    private final Path dir;
    private final Map<String, Optional<Loudness>> loudness = new HashMap<>();

    private SongStore(Path dir) {
        this.dir = dir;
    }

    public static synchronized SongStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, s -> new SongStore(cacheDir(s.getServerDirectory().toPath())));
    }

    public static synchronized void forget(MinecraftServer server) {
        STORES.remove(server);
    }

    /** {@code <root>/minescore/cache}, shared by the server and client caches. */
    public static Path cacheDir(Path root) {
        Path dir = root.resolve("minescore").resolve("cache");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't create {}", dir, e);
        }
        return dir;
    }

    public boolean has(String id) {
        return SongMeta.validId(id) && meta(id).isPresent();
    }

    public Optional<SongMeta> meta(String id) {
        if (!SongMeta.validId(id)) {
            return Optional.empty();
        }
        Path file = dir.resolve(id + ".json");
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            SongMeta meta = SongMeta.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            return Files.isRegularFile(dir.resolve(id + "." + meta.format())) ? Optional.of(meta) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read song info {}", file, e);
            return Optional.empty();
        }
    }

    public @Nullable byte[] read(SongMeta meta) {
        try {
            return Files.readAllBytes(dir.resolve(meta.id() + "." + meta.format()));
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't read song {}", meta.id(), e);
            return null;
        }
    }

    public void write(SongMeta meta, byte[] data) throws IOException {
        Files.write(dir.resolve(meta.id() + "." + meta.format()), data);
        if (!Files.exists(dir.resolve(meta.id() + ".json"))) {
            Files.writeString(dir.resolve(meta.id() + ".json"), meta.toJson(0), StandardCharsets.UTF_8);
        }
    }

    /**
     * The stored details for a song being cut again, with its length taken from the new measurement if
     * the stored one is off by more than a second (it was measured by an older, broken decoder).
     */
    public synchronized SongMeta correctLength(SongMeta measured) {
        SongMeta stored = meta(measured.id()).orElse(measured);
        if (Math.abs(stored.seconds() - measured.seconds()) <= 1) {
            return stored;
        }
        SongMeta fixed = new SongMeta(stored.id(), stored.format(), stored.title(), measured.seconds());
        Path file = dir.resolve(fixed.id() + ".json");
        try {
            int copies = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get("copies").getAsInt();
            Files.writeString(file, fixed.toJson(copies), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't update song info {}", fixed.id(), e);
        }
        return fixed;
    }

    /** Keeps a song's loudness (SCOPE.md §6.5), measured by the player who cut it. Bad data is ignored. */
    public synchronized void writeLoudness(String id, byte[] data) {
        if (!SongMeta.validId(id)) {
            return;
        }
        Path file = dir.resolve(id + ".loud");
        try {
            Loudness decoded = Loudness.decode(data);
            if (!Files.exists(file)) {
                Files.write(file, data);
            }
            loudness.put(id, Optional.of(decoded));
        } catch (IOException e) {
            MineScore.LOGGER.warn("Ignoring bad loudness data for {}", id, e);
        }
    }

    /** A song's loudness, if anyone measured it (songs cut before M5 have none). */
    public synchronized Optional<Loudness> loudness(String id) {
        if (!SongMeta.validId(id)) {
            return Optional.empty();
        }
        return loudness.computeIfAbsent(id, key -> {
            Path file = dir.resolve(key + ".loud");
            try {
                return Files.isRegularFile(file) ? Optional.of(Loudness.decode(Files.readAllBytes(file))) : Optional.empty();
            } catch (IOException e) {
                MineScore.LOGGER.warn("Couldn't read loudness {}", file, e);
                return Optional.empty();
            }
        });
    }

    /** Counts a new copy of a song and returns its number (the first copy ever made is 1). */
    public synchronized int nextCopyNumber(SongMeta meta) {
        Path file = dir.resolve(meta.id() + ".json");
        try {
            int copies = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get("copies").getAsInt() + 1;
            Files.writeString(file, meta.toJson(copies), StandardCharsets.UTF_8);
            return copies;
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't update copy count for {}", meta.id(), e);
            return 1;
        }
    }
}
