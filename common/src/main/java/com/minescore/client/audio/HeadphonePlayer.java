package com.minescore.client.audio;

import com.minescore.audio.Track;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.mp3.Mp3State;
import com.minescore.song.SongMeta;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The MP3 Player in the player's own inventory, playing in their ears only, with its EQ. Follows the
 * item's state (synced from the server) and the game clock.
 */
final class HeadphonePlayer {
    private static @Nullable StereoStream stream;
    private static @Nullable String playingKey;

    private HeadphonePlayer() {}

    static void tick(Minecraft mc, double now, float volume, boolean paused) {
        Mp3State state = playingHere(mc);
        Optional<SongMeta> song = state == null ? Optional.empty() : state.current();
        if (song.isEmpty()) {
            stop(true);
            return;
        }
        String key = state.id + "/" + song.get().id() + "/" + state.startTick;
        long expected = (long) Math.floor((now - state.startTick) / 20.0 * Track.SAMPLE_RATE);
        if (!key.equals(playingKey)) {
            Track track = TrackLibrary.get(song.get().trackId());
            if (track == null || expected >= track.frames()) {
                return; // downloading or decoding, or over (the server moves the queue on shortly)
            }
            stop(true);
            stream = StereoStream.create(track);
            if (stream == null) {
                return;
            }
            stream.start(expected);
            playingKey = key;
        }
        if (!stream.tick(expected, volume * state.volume, paused, state.eq)) {
            stop(true); // song over; the server moves the queue on
        }
    }

    /** The MP3 Player playing in this player's own ears, if any. */
    private static @Nullable Mp3State playingHere(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        UUID me = mc.player.getUUID();
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof Mp3PlayerItem && Mp3State.has(stack)) {
                Mp3State state = Mp3State.read(stack);
                if (state.playing && state.link == null && me.equals(state.holder)) {
                    return state;
                }
            }
        }
        return null;
    }

    static void stop(boolean alive) {
        if (stream != null) {
            stream.close(alive);
        }
        stream = null;
        playingKey = null;
    }
}
