package com.minescore.client.audio;

import com.minescore.MineScore;
import com.minescore.audio.DemoTrack;
import com.minescore.audio.Track;
import com.minescore.client.ClientSongs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Turns track ids into decoded audio, off the main thread, keeping the last few in memory.
 *
 * <p>Ids: {@code minescore:demo} (built in), {@code song:<id>} (a song cut to a record, from this client's
 * cache, downloaded from the server the first time) or {@code file:<name>} (a file in this client's
 * {@code blkcdsongs} folder).
 */
public final class TrackLibrary {
    private static final int CACHE_SIZE = 6;
    private static final Map<String, CompletableFuture<Track>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Track>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** Thrown while a song is still downloading: not an error, so nothing is reported. */
    private static final class NotYetDownloaded extends IOException {
        NotYetDownloaded() {
            super("still downloading");
        }
    }

    private TrackLibrary() {}

    /** The track if it's decoded; otherwise starts decoding it and returns null. Also null if it failed. */
    public static @Nullable Track get(String id) {
        CompletableFuture<Track> future = CACHE.computeIfAbsent(id, key -> CompletableFuture.supplyAsync(() -> {
            try {
                return load(key);
            } catch (NotYetDownloaded e) {
                return null;
            } catch (IOException | RuntimeException e) {
                MineScore.LOGGER.warn("Couldn't load track {}: {}", key, e.getMessage());
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat().addMessage(
                        Component.translatable("msg.minescore.track_failed", key, reason).withStyle(ChatFormatting.RED)));
                return null;
            }
        }, Util.backgroundExecutor()));
        return future.isDone() ? future.getNow(null) : null;
    }

    /** Lets a track that failed (or wasn't downloaded yet) be tried again. */
    public static void retry(String id) {
        CompletableFuture<Track> future = CACHE.get(id);
        if (future != null && future.isDone() && future.getNow(null) == null) {
            CACHE.remove(id);
        }
    }

    /** The folder players put songs in (created if missing). */
    public static Path songsDir() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("blkcdsongs");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't create {}", dir, e);
        }
        return dir;
    }

    /** A file in {@code blkcdsongs}, refusing names that try to leave it. */
    public static Path songFile(String name) throws IOException {
        Path dir = songsDir().toAbsolutePath().normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir)) {
            throw new IOException("Track path leaves the blkcdsongs folder");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("No file named \"" + file.getFileName() + "\" in " + dir);
        }
        return file;
    }

    private static Track load(String id) throws IOException {
        if (DemoTrack.ID.equals(id)) {
            return DemoTrack.get();
        }
        if (id.startsWith("song:")) {
            String songId = id.substring(5);
            Path file = ClientSongs.cached(songId);
            if (file == null) {
                Minecraft.getInstance().execute(() -> ClientSongs.INSTANCE.request(songId));
                throw new NotYetDownloaded();
            }
            return SongFiles.decode(SongFiles.extension(file), Files.readAllBytes(file));
        }
        if (id.startsWith("file:")) {
            Path file = songFile(id.substring(5));
            return SongFiles.decode(SongFiles.extension(file), Files.readAllBytes(file));
        }
        throw new IOException("Unknown track id");
    }
}
