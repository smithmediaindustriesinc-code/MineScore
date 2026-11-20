package com.minescore.client.audio;

import com.minescore.audio.Track;
import org.jetbrains.annotations.Nullable;

/** Plays a song straight into the player's ears, in stereo, for the Lathe's Preview button. */
public final class PreviewPlayer {
    private static @Nullable String trackId;
    private static @Nullable StereoStream stream;

    private PreviewPlayer() {}

    /** Starts previewing a track id (decoding it first if needed); playing it again stops it. */
    public static void toggle(String id) {
        boolean same = id.equals(trackId);
        stop();
        if (!same) {
            trackId = id;
        }
    }

    public static boolean playing(String id) {
        return id.equals(trackId);
    }

    public static boolean active() {
        return trackId != null;
    }

    static void tick(float volume) {
        if (trackId == null) {
            return;
        }
        if (stream == null) {
            Track track = TrackLibrary.get(trackId);
            if (track == null) {
                return; // still decoding
            }
            stream = StereoStream.create(track);
            if (stream == null) {
                trackId = null;
                return;
            }
            stream.start(0);
        }
        if (!stream.tick(-1, volume, false, null)) {
            stop(); // finished
        }
    }

    public static void stop() {
        if (stream != null) {
            stream.close(true);
        }
        stream = null;
        trackId = null;
    }

    /** Minecraft's sound engine restarted and already took our OpenAL objects with it. */
    static void forget() {
        if (stream != null) {
            stream.close(false);
        }
        stream = null;
        trackId = null;
    }
}
