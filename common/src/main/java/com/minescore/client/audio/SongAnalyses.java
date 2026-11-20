package com.minescore.client.audio;

import com.minescore.MineScore;
import com.minescore.MineScoreSettings;
import com.minescore.audio.Track;
import com.minescore.audio.fx.Fx;
import com.minescore.audio.fx.SongAnalysis;
import com.minescore.meu.EffectChain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Each song's tempo, key and tuning, worked out once in the background the first time an MEU needs them. */
public final class SongAnalyses {
    private static final Map<String, SongAnalysis> DONE = new HashMap<>();
    private static final Set<String> WORKING = new HashSet<>();

    private SongAnalyses() {}

    /** The analysis of a loaded track, or {@link SongAnalysis#UNKNOWN} until it's ready. */
    public static SongAnalysis get(String trackId, Track track) {
        SongAnalysis done = DONE.get(trackId);
        if (done != null) {
            return done;
        }
        if (WORKING.add(trackId)) {
            CompletableFuture.supplyAsync(() -> SongAnalysis.of(track), Util.backgroundExecutor()).whenComplete((result, failure) ->
                    Minecraft.getInstance().execute(() -> {
                        WORKING.remove(trackId);
                        if (failure != null) {
                            MineScore.LOGGER.warn("Couldn't analyse {}", trackId, failure);
                        }
                        DONE.put(trackId, failure == null ? result : SongAnalysis.UNKNOWN);
                    }));
        }
        return SongAnalysis.UNKNOWN;
    }

    /** The analysis of a track if it's loaded and done, without starting anything. */
    public static SongAnalysis peek(String trackId) {
        Track track = TrackLibrary.get(trackId);
        return track == null ? SongAnalysis.UNKNOWN : get(trackId, track);
    }

    /** What the effects need to know, for a song through a unit. */
    public static Fx.Context context(String trackId, Track track, EffectChain chain) {
        SongAnalysis analysis = get(trackId, track);
        return new Fx.Context(analysis.bpm(), analysis.tuningHz(), chain.expression(), MineScoreSettings.effectsHighQuality.getAsBoolean());
    }
}
