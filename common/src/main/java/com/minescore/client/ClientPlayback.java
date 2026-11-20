package com.minescore.client;

import com.minescore.network.PlaybackPackets;
import com.minescore.playback.Playback;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** What is playing in the client's dimension. Plain data only, like {@link ClientCables}. */
public final class ClientPlayback {
    private static final Map<BlockPos, Playback> PLAYING = new LinkedHashMap<>();
    /** Bumped on every change so the audio engine knows to look again. */
    private static int version;

    private ClientPlayback() {}

    public static Collection<Playback> all() {
        return Collections.unmodifiableCollection(PLAYING.values());
    }

    public static @Nullable Playback at(BlockPos source) {
        return PLAYING.get(source);
    }

    public static int version() {
        return version;
    }

    public static void onSnapshot(PlaybackPackets.Snapshot packet) {
        PLAYING.clear();
        packet.playing().forEach(p -> PLAYING.put(p.source(), p));
        version++;
    }

    public static void onUpdate(PlaybackPackets.Update packet) {
        PLAYING.put(packet.playback().source(), packet.playback());
        version++;
    }

    public static void onStop(PlaybackPackets.Stop packet) {
        PLAYING.remove(packet.source());
        version++;
    }

    public static void clear() {
        PLAYING.clear();
        version++;
    }
}
