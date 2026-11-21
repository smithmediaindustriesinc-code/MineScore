package com.minescore.client.audio;

import com.minescore.MineScore;
import com.minescore.audio.Track;
import com.minescore.audio.notes.NoteEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * The score editor's playback (SCOPE.md §9.9): heard only by the player editing, straight into their ears,
 * from wherever they start it. The score is rendered to note-block audio in the background first.
 */
public final class EditorPlayer {
    private static @Nullable CompletableFuture<Track> rendering;
    private static @Nullable StereoStream stream;
    /** Where in the score playback started, in seconds. */
    private static double from;
    private static long startedNanos;
    private static int generation;
    /** Where a selected stretch ends, in score seconds, or -1 to play to the end. */
    private static double until = -1;
    /** How long the last notes of a stretch may ring on after it. */
    private static final double RING = 0.6;

    private EditorPlayer() {}

    /**
     * Plays a score's hits from {@code fromSeconds} on, up to {@code toSeconds} (a selected stretch), or to the
     * end if that's not after the start.
     */
    public static void play(List<NoteEvent> events, double fromSeconds, double toSeconds) {
        stop();
        until = toSeconds > fromSeconds ? toSeconds : -1;
        List<NoteEvent> shifted = new ArrayList<>();
        for (NoteEvent e : events) {
            if (e.seconds() >= fromSeconds - 0.001 && (until < 0 || e.seconds() < until - 0.001)) {
                shifted.add(e.earlier(fromSeconds));
            }
        }
        if (shifted.isEmpty()) {
            return;
        }
        from = fromSeconds;
        int mine = ++generation;
        rendering = CompletableFuture.supplyAsync(() -> {
            try {
                return NoteRenderer.render(shifted);
            } catch (Exception e) {
                MineScore.LOGGER.warn("Couldn't play the score", e);
                return null;
            }
        });
        rendering.thenRun(() -> {
            if (mine != generation) {
                rendering = null;
            }
        });
    }

    public static boolean active() {
        return rendering != null || stream != null;
    }

    /** Where playback is in the score, in seconds, or -1 when nothing's playing yet. */
    public static double seconds() {
        return stream == null ? -1 : from + (System.nanoTime() - startedNanos) / 1e9;
    }

    static void tick(float volume) {
        if (rendering != null && rendering.isDone()) {
            Track track = rendering.getNow(null);
            rendering = null;
            if (track != null) {
                stream = StereoStream.create(track);
                if (stream != null) {
                    stream.start(0);
                    startedNanos = System.nanoTime();
                }
            }
        }
        if (stream != null && (!stream.tick(-1, volume, false, null) || (until >= 0 && seconds() > until + RING))) {
            stop();
        }
    }

    public static void stop() {
        generation++;
        rendering = null;
        if (stream != null) {
            stream.close(true);
        }
        stream = null;
    }

    /** Minecraft's sound engine restarted, taking our OpenAL objects with it. */
    static void forget() {
        if (stream != null) {
            stream.close(false);
        }
        stream = null;
        rendering = null;
    }
}
