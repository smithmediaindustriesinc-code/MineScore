package com.minescore.client.audio;

import com.minescore.MineScoreSettings;
import com.minescore.audio.Crossover;
import com.minescore.audio.Equalizer;
import com.minescore.audio.Track;
import com.minescore.playback.SpeakerInfo;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

/**
 * One speaker playing one band and channel of a track, as its own directional OpenAL source. Each voice
 * keeps itself in time with the game clock, so voices started at different moments stay together.
 */
final class SpeakerVoice {
    /** Frames per streamed buffer (50 ms). */
    private static final int BLOCK = Track.SAMPLE_RATE / 20;
    private static final int BUFFERS = 6;
    /** How far a voice may drift from the game clock before it jumps back into place. */
    private static final int MAX_DRIFT = Track.SAMPLE_RATE * 15 / 100;
    private static final int DRIFT_CHECK_TICKS = 20;
    /**
     * Samples are written at a quarter of full scale and played back four times louder, so an Amplifier's
     * +12 dB EQ boost doesn't clip.
     */
    private static final float HEADROOM = 0.25f;
    /** Room for the headroom makeup times a 400% boost. */
    private static final float MAX_GAIN = 16;

    /** Updated in place (see {@link #update}) when only the volume, range or EQ changes. */
    SpeakerInfo info;
    /** Sound through blocks: the smoothed loss in dB, the latest measured loss, and ticks until the next check. */
    double occlusionDb;
    double occlusionTarget;
    int occlusionCheckIn;
    /** Room Sound Physics: the smoothed boost from its room in dB, the latest measured one, and ticks until the next check. */
    double roomDb;
    double roomTarget;
    int roomCheckIn;
    final String trackId;
    final long startTick;

    /** What it plays: the song, or the song through its system's Multi-Effects Unit (shared with the other speakers). */
    final AudioSource track;
    /** The band filter, or null for a full-range source. */
    private final @Nullable Crossover filter;
    private final Equalizer equalizer = new Equalizer();
    private final float headroom;
    private final int source;
    private final Deque<Integer> free = new ArrayDeque<>();
    /**
     * Queued buffers, oldest first: {buffer, the track frame it starts at, its loudness (RMS, as float bits),
     * how many times it crosses zero}. The playing one moves the speaker's cone (SpeakerRenderer).
     */
    private final Deque<long[]> queued = new ArrayDeque<>();
    private final ShortBuffer scratch = MemoryUtil.memAllocShort(BLOCK);
    private final int[] buffers = new int[BUFFERS];
    private long nextFrame;
    /** Played from a Vinyl Player: gets the record's static (VinylStatic). */
    private boolean vinyl;
    private final VinylStatic noise = new VinylStatic();
    private int ticksSinceDriftCheck;

    private SpeakerVoice(SpeakerInfo info, String trackId, long startTick, AudioSource track, int source) {
        this.info = info;
        this.trackId = trackId;
        this.startTick = startTick;
        this.track = track;
        this.source = source;
        this.filter = info.band() == null ? null
                : Crossover.forBand(info.band(), Track.SAMPLE_RATE, MineScoreSettings.lowSplit(), MineScoreSettings.highSplit());
        equalizer.setGains(info.eq().gains(), Track.SAMPLE_RATE);
        AL10.alGenBuffers(buffers);
        for (int buffer : buffers) {
            free.add(buffer);
        }
        AL10.alSource3f(source, AL10.AL_POSITION, (float) info.front().x, (float) info.front().y, (float) info.front().z);
        // No OpenAL cone: AudioEngine works out directional volume itself from the sound's path (Directivity).
        AL10.alSourcef(source, AL10.AL_CONE_INNER_ANGLE, 360);
        AL10.alSourcef(source, AL10.AL_CONE_OUTER_ANGLE, 360);
        AL10.alSourcef(source, AL10.AL_CONE_OUTER_GAIN, 1);
        // Linear fade to silence at the hearing range, measured from the front face to the listener.
        AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, info.range());
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, MAX_GAIN);
        AL10.alGetError();
        // An OpenAL that won't play a source louder than 100% gets no headroom (and boosts stop at 100%).
        headroom = AL10.alGetSourcef(source, AL10.AL_MAX_GAIN) >= MAX_GAIN - 0.01f ? HEADROOM : 1;
    }

    /** The same speaker with new settings: the volume follows at once, the EQ from the next buffer. */
    void update(SpeakerInfo next) {
        if (!next.eq().equals(info.eq())) {
            equalizer.setGains(next.eq().gains(), Track.SAMPLE_RATE);
        }
        if (next.range() != info.range()) {
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, next.range());
        }
        info = next;
    }

    /** A new voice, or null if OpenAL has no sources to spare. */
    static @Nullable SpeakerVoice create(SpeakerInfo info, String trackId, long startTick, AudioSource track, boolean vinyl) {
        AL10.alGetError();
        int source = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            return null;
        }
        SpeakerVoice voice = new SpeakerVoice(info, trackId, startTick, track, source);
        voice.vinyl = vinyl;
        return voice;
    }

    /** Begins playing from a track frame (negative means "not yet": silence until the start). */
    void start(long frame, float gain) {
        AL10.alSourceStop(source);
        unqueueAll();
        if (filter != null) {
            filter.reset();
        }
        equalizer.reset();
        nextFrame = frame;
        noise.reset();
        AL10.alSourcef(source, AL10.AL_GAIN, gain / headroom);
        fill();
        if (!queued.isEmpty()) {
            AL10.alSourcePlay(source);
        }
    }

    /** @return false once the track has finished playing */
    boolean tick(long expectedFrame, float gain, boolean paused) {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed && !queued.isEmpty(); i++) {
            free.add(AL10.alSourceUnqueueBuffers(source));
            queued.poll();
        }
        AL10.alSourcef(source, AL10.AL_GAIN, gain / headroom);

        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (paused) {
            if (state == AL10.AL_PLAYING) {
                AL10.alSourcePause(source);
            }
            return true;
        }

        if (++ticksSinceDriftCheck >= DRIFT_CHECK_TICKS && !queued.isEmpty()) {
            ticksSinceDriftCheck = 0;
            long played = queued.peek()[1] + AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            if (Math.abs(played - expectedFrame) > MAX_DRIFT) {
                start(expectedFrame, gain);
                return true;
            }
        }

        fill();
        if (queued.isEmpty()) {
            return nextFrame < track.frames();
        }
        if (state != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source); // resume after a pause or recover from running dry
        }
        return true;
    }

    private void fill() {
        while (!free.isEmpty() && nextFrame < track.frames()) {
            scratch.clear();
            double energy = 0;
            int crossings = 0;
            float last = 0;
            float staticLevel = vinyl && com.minescore.config.MineScoreConfig.VINYL_STATIC.bool()
                    ? (float) com.minescore.config.MineScoreConfig.VINYL_STATIC_AMOUNT.number() / 100f : 0;
            for (int i = 0; i < BLOCK; i++) {
                long frame = nextFrame + i;
                float sample = 0;
                if (frame >= 0 && frame < track.frames()) {
                    sample = info.channel().pick(track.left(frame), track.right(frame));
                    if (staticLevel > 0) {
                        sample += noise.next(frame, trackId.hashCode()) * staticLevel;
                    }
                }
                float shaped = equalizer.process(filter == null ? sample : filter.process(sample));
                energy += shaped * shaped;
                if ((shaped > 0) != (last > 0)) {
                    crossings++;
                }
                last = shaped;
                float out = shaped * headroom;
                scratch.put((short) Math.max(-32768, Math.min(32767, Math.round(out * 32767))));
            }
            scratch.flip();
            int buffer = free.poll();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, scratch, Track.SAMPLE_RATE);
            AL10.alSourceQueueBuffers(source, buffer);
            queued.add(new long[] {buffer, nextFrame, Float.floatToIntBits((float) Math.sqrt(energy / BLOCK)), crossings});
            nextFrame += BLOCK;
        }
    }

    /** How hard the speaker is driven right now: the playing buffer's loudness times the system's gain at it. */
    float level() {
        long[] playing = queued.peek();
        return playing == null ? 0 : Float.intBitsToFloat((int) playing[2]) * info.gain();
    }

    /** Roughly the pitch it's playing now, in Hz, from how often the sound crosses zero. */
    float pitch() {
        long[] playing = queued.peek();
        return playing == null ? 0 : playing[3] * (float) Track.SAMPLE_RATE / (2f * BLOCK);
    }

    private void unqueueAll() {
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        free.clear();
        queued.clear();
        for (int buffer : buffers) {
            free.add(buffer);
        }
    }

    /**
     * @param alive false after Minecraft's sound engine restarted, when the old OpenAL objects are already gone
     */
    void close(boolean alive) {
        if (alive) {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffers);
        }
        MemoryUtil.memFree(scratch);
    }
}
