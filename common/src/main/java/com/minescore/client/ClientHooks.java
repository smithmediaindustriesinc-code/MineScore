package com.minescore.client;

import com.minescore.hub.HubPackets;
import com.minescore.song.SongPackets;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Things only the client can do (packet handlers, opening screens), called from shared code. The client
 * installs the real ones at startup, so a dedicated server never loads client classes.
 */
public interface ClientHooks {
    void onSendSong(SongPackets.SendSong packet);

    void onDownloadChunk(SongPackets.DownloadChunk packet);

    void onLatheStatus(SongPackets.LatheStatus packet);

    /** A piece of a score for the editor arrived. */
    default void onScoreSnapshot(com.minescore.score.ScorePackets.Snapshot packet) {}

    /** Someone else edited the score this player has open. */
    default void onScoreEdited(com.minescore.score.ScorePackets.Edited packet) {}

    default void onScorePeer(com.minescore.score.ScorePackets.PeerCursor packet) {}

    /** Plays one of MineScore's synthesized sounds. */
    default void onSynthSound(com.minescore.audio.SynthSound.Play packet) {}

    /** Opens the MineScore Manual. */
    default void openManual() {}

    /** Opens the screen of an MP3 Player in the player's own inventory. */
    void openMp3(UUID id);

    /** Opens the screen of the MP3 Player docked in the Mixer at {@code pos}. */
    void openMixer(BlockPos pos);

    void openSpeaker(BlockPos pos);

    void openAmplifier(BlockPos pos);

    /** A Channel Manager's speaker list arrived: open its screen, or refresh it. */
    void onChannels(HubPackets.Channels packet);

    static ClientHooks get() {
        return Holder.hooks;
    }

    static void set(ClientHooks hooks) {
        Holder.hooks = hooks;
    }

    final class Holder {
        private static ClientHooks hooks = new ClientHooks() {
            @Override
            public void onSendSong(SongPackets.SendSong packet) {}

            @Override
            public void onDownloadChunk(SongPackets.DownloadChunk packet) {}

            @Override
            public void onLatheStatus(SongPackets.LatheStatus packet) {}

            @Override
            public void openMp3(UUID id) {}

            @Override
            public void openMixer(BlockPos pos) {}

            @Override
            public void openSpeaker(BlockPos pos) {}

            @Override
            public void openAmplifier(BlockPos pos) {}

            @Override
            public void onChannels(HubPackets.Channels packet) {}
        };

        private Holder() {}
    }
}
