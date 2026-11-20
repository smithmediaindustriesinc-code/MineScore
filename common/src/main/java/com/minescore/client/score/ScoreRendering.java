package com.minescore.client.score;

import com.minescore.MineScoreSettings;
import com.minescore.audio.AdpcmCodec;
import com.minescore.audio.Loudness;
import com.minescore.audio.Track;
import com.minescore.client.ClientSongs;
import com.minescore.client.audio.NoteRenderer;
import com.minescore.network.MineScoreNetwork;
import com.minescore.score.Score;
import com.minescore.score.ScorePackets;
import com.minescore.score.ScorePlayback;
import com.minescore.song.SongMeta;
import com.minescore.song.SongPackets;
import com.minescore.song.SongTransfers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Turns a score into a song, on the client of the player who asked (SCOPE.md §7): rendered to note-block
 * audio as the editor plays it, then handed to the server like any cut file. Used by the desk's Play
 * (§3.6) and the Lathe's Cut (§3.1).
 */
public final class ScoreRendering {
    /** A rendered score, ready to upload. */
    public record Rendered(SongMeta meta, byte[] payload, Loudness loudness) {}

    private ScoreRendering() {}

    /** Renders a score (slow: call off-thread). */
    public static Rendered render(Score score) throws java.io.IOException {
        Track track = NoteRenderer.render(ScorePlayback.render(score).events());
        byte[] payload = AdpcmCodec.encode(track);
        Loudness loudness = Loudness.measure(track, MineScoreSettings.lowSplit(), MineScoreSettings.highSplit());
        String title = score.title.isBlank() ? "Untitled Score" : score.title;
        return new Rendered(new SongMeta(SongTransfers.sha256(payload), AdpcmCodec.EXTENSION, title, track.seconds()), payload, loudness);
    }

    /** The desk's Play: renders the score on it and asks the desk to play it. */
    public static void forDesk(BlockPos desk, Score score) {
        renderThen(score, rendered -> MineScoreNetwork.sendToServer(new ScorePackets.DeskPlay(desk, rendered.meta(),
                rendered.payload().length, rendered.loudness().encode())));
    }

    /** The Lathe's Cut with a Score in it: renders it and cuts it like a song file. */
    public static void forLathe(BlockPos lathe, Score score) {
        String title = score.title.isBlank() ? "Untitled Score" : score.title;
        renderThen(score, rendered -> MineScoreNetwork.sendToServer(new SongPackets.Cut(lathe, rendered.meta(), rendered.payload().length,
                title, rendered.loudness().encode())));
    }

    private static void renderThen(Score score, Consumer<Rendered> send) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try {
                return render(score);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((rendered, failure) -> {
            if (failure != null) {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("msg.minescore.score.render_failed", cause.getMessage())
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
            ClientSongs.INSTANCE.prepare(rendered.meta(), rendered.payload()); // uploaded if the server asks for it
            send.accept(rendered);
        }, mc);
    }
}
