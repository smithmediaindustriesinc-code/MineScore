package com.minescore.client.audio;

import com.minescore.MineScore;
import com.minescore.MineScoreSettings;
import com.minescore.audio.Directivity;
import com.minescore.audio.Occlusion;
import com.minescore.audio.RoomAcoustics;
import com.minescore.audio.Track;
import com.minescore.client.ClientPlayback;
import com.minescore.playback.Playback;
import com.minescore.playback.SpeakerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Plays every speaker system near the player. Called once per client tick; each nearby speaker gets a
 * {@link SpeakerVoice}, up to {@link MineScoreSettings#maxSpeakerVoices()}, nearest first.
 */
public final class AudioEngine {
    /** How often, in ticks, to re-check which speakers are in range. */
    private static final int RECONCILE_TICKS = 10;
    /** Extra range beyond hearing distance so voices are ready before they become audible. */
    private static final double RANGE_MARGIN = 8;
    /** Sound through blocks is re-checked 5 times a second... */
    private static final int OCCLUSION_TICKS = 4;
    /** ...and each tick moves this share of the way to the new loss: there within about a quarter second. */
    private static final double OCCLUSION_GLIDE = 0.4;
    /** Room Sound Physics checks are heavier (64 rays), so they run every 2 seconds. */
    private static final int ROOM_TICKS = 40;

    private static final Map<String, SpeakerVoice> VOICES = new LinkedHashMap<>();
    /** How each audible speaker's cone is moving this tick: {level, pitch in Hz}, by speaker (SpeakerRenderer). */
    private static final Map<BlockPos, float[]> CONES = new HashMap<>();
    /** What each playing system's speakers read from, by {@link #sourceKey}. */
    private static final Map<String, AudioSource> SOURCES = new HashMap<>();
    /** The start tick last seen for each source, to notice when something is played again. */
    private static final Map<BlockPos, Long> STARTS_SEEN = new HashMap<>();
    private static int seenVersion = -1;
    private static int ticksSinceReconcile;
    private static boolean warnedNoSources;

    private AudioEngine() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll();
            return;
        }
        if (ClientPlayback.version() != seenVersion || ++ticksSinceReconcile >= RECONCILE_TICKS) {
            seenVersion = ClientPlayback.version();
            ticksSinceReconcile = 0;
            reconcile(mc);
        }
        SynthSounds.tick();
        boolean paused = mc.isPaused();
        float volume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        double now = mc.level.getGameTime() + mc.getFrameTime();
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        PreviewPlayer.tick(volume);
        EditorPlayer.tick(volume);
        HeadphonePlayer.tick(mc, now, volume, paused);
        Iterator<SpeakerVoice> it = VOICES.values().iterator();
        while (it.hasNext()) {
            SpeakerVoice voice = it.next();
            updateOcclusion(mc, voice, listener);
            updateRoom(mc, voice);
            float gain = gain(voice.info, listener, volume) * surroundings(voice);
            if (!voice.tick(expectedFrame(voice.startTick, now), gain, paused)) {
                voice.close(true);
                it.remove();
            }
        }
        updateCones(mc, paused);
    }

    /** What the speakers' cones do this tick, and the music notes rising from the loud ones. */
    private static void updateCones(Minecraft mc, boolean paused) {
        CONES.clear();
        if (paused) {
            return;
        }
        for (SpeakerVoice voice : VOICES.values()) {
            float level = voice.level();
            float[] cone = CONES.get(voice.info.pos());
            if (cone == null || level > cone[0]) {
                CONES.put(voice.info.pos(), new float[] {level, voice.pitch()});
            }
            if (com.minescore.config.MineScoreConfig.PARTICLES.bool() && level > 0.02f
                    && mc.level.random.nextFloat() < Math.min(0.4f, level * 0.6f)) {
                Vec3 at = voice.info.front().add(voice.info.direction().scale(0.15));
                int band = voice.info.band() == null ? 1 : voice.info.band().ordinal();
                mc.level.addParticle(com.minescore.registry.ModParticles.NOTE.get(), at.x + (mc.level.random.nextDouble() - 0.5) * 0.4,
                        at.y + 0.3, at.z + (mc.level.random.nextDouble() - 0.5) * 0.4, band, 0, 0);
            }
        }
    }

    /** How the speaker at {@code pos} is being driven: {level, pitch in Hz}, or null while it's silent or out of earshot. */
    public static float @org.jetbrains.annotations.Nullable [] cone(BlockPos pos) {
        return CONES.get(pos);
    }

    /** Starts voices for speakers that came into range and stops the rest. */
    private static void reconcile(Minecraft mc) {
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        record Wanted(String key, Playback playback, SpeakerInfo speaker, AudioSource track, double distance) {}
        Map<String, AudioSource> sourcesInUse = new HashMap<>();
        List<Wanted> wanted = new ArrayList<>();
        double now = mc.level.getGameTime() + mc.getFrameTime();
        for (Playback playback : ClientPlayback.all()) {
            Long seen = STARTS_SEEN.put(playback.source(), playback.startTick());
            if (seen == null || seen != playback.startTick()) {
                TrackLibrary.retry(playback.track()); // played again: give a track that failed another go
                // "Now Playing" whenever a disc starts (not only the first time a block plays), if it's in earshot.
                if (playback.hasDisc() && !playback.speakers().isEmpty() && com.minescore.config.MineScoreConfig.NOW_PLAYING.bool()
                        && playback.speakers().get(0).front().distanceTo(listener) < playback.speakers().get(0).range()) {
                    mc.gui.setNowPlaying(playback.disc().getHoverName());
                }
            }
            Track decoded = TrackLibrary.get(playback.track());
            if (decoded == null) {
                continue; // still decoding, or failed (already logged)
            }
            AudioSource track = source(playback, decoded);
            sourcesInUse.put(sourceKey(playback), track);
            if (expectedFrame(playback.startTick(), now) >= track.frames()) {
                continue; // already over
            }
            for (SpeakerInfo speaker : playback.speakers()) {
                double distance = speaker.front().distanceTo(listener);
                if (distance <= speaker.range() + RANGE_MARGIN) {
                    wanted.add(new Wanted(playback.source().asLong() + "/" + speaker.pos().asLong() + "/" + speaker.band(),
                            playback, speaker, track, distance));
                }
            }
        }
        SOURCES.keySet().retainAll(sourcesInUse.keySet());
        wanted.sort(Comparator.comparingDouble(Wanted::distance));
        Map<String, Wanted> keep = new HashMap<>();
        for (Wanted w : wanted.subList(0, Math.min(wanted.size(), MineScoreSettings.maxSpeakerVoices()))) {
            keep.put(w.key(), w);
        }

        Iterator<Map.Entry<String, SpeakerVoice>> it = VOICES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SpeakerVoice> entry = it.next();
            Wanted w = keep.get(entry.getKey());
            SpeakerVoice voice = entry.getValue();
            boolean same = w != null && sameSound(voice.info, w.speaker()) && voice.startTick == w.playback().startTick()
                    && voice.trackId.equals(w.playback().track()) && voice.track == w.track();
            if (!same) {
                voice.close(true);
                it.remove();
            } else {
                voice.update(w.speaker()); // picks up volume, range and EQ changes without restarting
            }
        }

        float volume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        for (Wanted w : keep.values()) {
            if (VOICES.containsKey(w.key())) {
                continue;
            }
            SpeakerVoice voice = SpeakerVoice.create(w.speaker(), w.playback().track(), w.playback().startTick(), w.track(),
                    w.playback().kind() == com.minescore.playback.Playback.Kind.VINYL);
            if (voice == null) {
                if (!warnedNoSources) {
                    warnedNoSources = true;
                    MineScore.LOGGER.warn("OpenAL has no free sources; some speakers will stay silent");
                }
                continue;
            }
            voice.occlusionTarget = voice.occlusionDb = pathLoss(mc, w.speaker(), listener);
            voice.occlusionCheckIn = (int) (Math.abs(w.speaker().pos().asLong()) % OCCLUSION_TICKS); // spread the checks out
            voice.roomTarget = voice.roomDb = roomBoost(mc, w.speaker());
            voice.roomCheckIn = (int) (Math.abs(w.speaker().pos().asLong()) % ROOM_TICKS);
            voice.start(expectedFrame(w.playback().startTick(), now), gain(w.speaker(), listener, volume) * surroundings(voice));
            VOICES.put(w.key(), voice);
        }
    }

    private static String sourceKey(Playback playback) {
        return playback.source().asLong() + "/" + playback.track() + "/" + playback.startTick() + "/" + (playback.effects() != null);
    }

    /**
     * What a playback's speakers play: the song itself, or (with a Multi-Effects Unit) the song through its
     * effects, one shared source per system so the effects run once.
     */
    private static AudioSource source(Playback playback, Track decoded) {
        String key = sourceKey(playback);
        AudioSource existing = SOURCES.get(key);
        if (playback.effects() == null) {
            if (existing == null) {
                existing = new AudioSource.Plain(decoded);
                SOURCES.put(key, existing);
            }
            return existing;
        }
        var context = SongAnalyses.context(playback.track(), decoded, playback.effects());
        if (existing instanceof EffectsSource effects) {
            effects.update(playback.effects(), context);
            return effects;
        }
        EffectsSource created = new EffectsSource(decoded, playback.effects(), context);
        SOURCES.put(key, created);
        return created;
    }

    /** Same speaker playing the same way, perhaps at a different volume, range or EQ. */
    private static boolean sameSound(SpeakerInfo a, SpeakerInfo b) {
        return a.pos().equals(b.pos()) && a.front().equals(b.front()) && a.direction().equals(b.direction())
                && a.band() == b.band() && a.channel() == b.channel();
    }

    /** Re-checks the blocks between a speaker and the listener now and then, and glides toward the result. */
    private static void updateOcclusion(Minecraft mc, SpeakerVoice voice, Vec3 listener) {
        if (--voice.occlusionCheckIn <= 0) {
            voice.occlusionCheckIn = OCCLUSION_TICKS;
            voice.occlusionTarget = pathLoss(mc, voice.info, listener);
        }
        voice.occlusionDb += (voice.occlusionTarget - voice.occlusionDb) * OCCLUSION_GLIDE;
    }

    /** What the blocks round a speaker do to it: walls in the way muffle it, a sealed room it shares with you fills it out. */
    private static float surroundings(SpeakerVoice voice) {
        double db = -voice.occlusionDb * Occlusion.bandScale(voice.info.band())
                + voice.roomDb * RoomAcoustics.listenerShare(voice.occlusionDb);
        return (float) Math.pow(10, db / 20);
    }

    /** Re-measures a speaker's room every couple of seconds (rooms change rarely), gliding to the result. */
    private static void updateRoom(Minecraft mc, SpeakerVoice voice) {
        if (--voice.roomCheckIn <= 0) {
            voice.roomCheckIn = ROOM_TICKS;
            voice.roomTarget = roomBoost(mc, voice.info);
        }
        voice.roomDb += (voice.roomTarget - voice.roomDb) * OCCLUSION_GLIDE;
    }

    private static double roomBoost(Minecraft mc, SpeakerInfo speaker) {
        if (!MineScoreSettings.roomSoundPhysics.getAsBoolean() || mc.level == null) {
            return 0;
        }
        double seal = RoomAcoustics.seal(RoomAcoustics.enclosure(mc.level, speaker.front(), speaker.pos()));
        return RoomAcoustics.boostDb(seal, speaker.band());
    }

    private static double pathLoss(Minecraft mc, SpeakerInfo speaker, Vec3 listener) {
        if (!MineScoreSettings.soundThroughBlocks() || mc.level == null) {
            return 0;
        }
        return Occlusion.pathDb(mc.level, speaker.front(), listener, speaker.pos());
    }

    /** A speaker's volume toward the listener: system gain, directivity along the sound's path, and the slider. */
    private static float gain(SpeakerInfo speaker, Vec3 listener, float volume) {
        return speaker.gain() * volume
                * Directivity.gain(speaker.front(), speaker.direction(), listener, MineScoreSettings.behindVolume());
    }

    private static long expectedFrame(long startTick, double gameTime) {
        return (long) Math.floor((gameTime - startTick) / 20.0 * Track.SAMPLE_RATE);
    }

    public static void stopAll() {
        CONES.clear();
        PreviewPlayer.stop();
        HeadphonePlayer.stop(true);
        VOICES.values().forEach(voice -> voice.close(true));
        VOICES.clear();
        SOURCES.clear();
        STARTS_SEEN.clear();
        seenVersion = -1;
    }

    /** Minecraft restarted its sound engine, taking our OpenAL sources with it. */
    public static void onSoundEngineReloaded() {
        PreviewPlayer.forget();
        EditorPlayer.forget();
        SynthSounds.forget();
        HeadphonePlayer.stop(false);
        VOICES.values().forEach(voice -> voice.close(false));
        VOICES.clear();
        seenVersion = -1;
    }
}
