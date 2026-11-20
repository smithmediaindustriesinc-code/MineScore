package com.minescore.client.audio;

import com.minescore.audio.Equalizer;
import com.minescore.audio.Track;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

/**
 * A track streamed in stereo straight into the listener's ears (not placed in the world), with an
 * optional equaliser. Used for the Lathe's preview and for MP3 Players carried in an inventory.
 */
final class StereoStream {
    private static final int BLOCK = Track.SAMPLE_RATE / 20;
    private static final int BUFFERS = 6;
    private static final int MAX_DRIFT = Track.SAMPLE_RATE * 15 / 100;

    private final Track track;
    private final int source;
    private final int[] buffers = new int[BUFFERS];
    private final Deque<Integer> free = new ArrayDeque<>();
    private final Deque<long[]> queued = new ArrayDeque<>();
    private final ShortBuffer scratch = MemoryUtil.memAllocShort(BLOCK * 2);
    private final Equalizer left = new Equalizer();
    private final Equalizer right = new Equalizer();
    private long nextFrame;
    private int ticksSinceDriftCheck;

    private StereoStream(Track track, int source) {
        this.track = track;
        this.source = source;
        AL10.alGenBuffers(buffers);
        for (int b : buffers) {
            free.add(b);
        }
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
    }

    static @Nullable StereoStream create(Track track) {
        AL10.alGetError();
        int source = AL10.alGenSources();
        return AL10.alGetError() == AL10.AL_NO_ERROR ? new StereoStream(track, source) : null;
    }

    void start(long frame) {
        AL10.alSourceStop(source);
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        free.clear();
        queued.clear();
        for (int b : buffers) {
            free.add(b);
        }
        left.reset();
        right.reset();
        nextFrame = Math.max(0, frame);
    }

    /**
     * @param expectedFrame where the game clock says playback should be, or -1 to just play on
     * @param eq            equaliser gains in dB, or null for none
     * @return false once the track has finished
     */
    boolean tick(long expectedFrame, float gain, boolean paused, @Nullable float[] eq) {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed && !queued.isEmpty(); i++) {
            free.add(AL10.alSourceUnqueueBuffers(source));
            queued.poll();
        }
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (paused) {
            if (state == AL10.AL_PLAYING) {
                AL10.alSourcePause(source);
            }
            return true;
        }
        if (expectedFrame >= 0 && ++ticksSinceDriftCheck >= 20 && !queued.isEmpty()) {
            ticksSinceDriftCheck = 0;
            long played = queued.peek()[1] + AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            if (Math.abs(played - expectedFrame) > MAX_DRIFT) {
                start(expectedFrame);
            }
        }
        if (eq != null) {
            left.setGains(eq, Track.SAMPLE_RATE);
            right.setGains(eq, Track.SAMPLE_RATE);
        }
        while (!free.isEmpty() && nextFrame < track.frames()) {
            int n = (int) Math.min(BLOCK, track.frames() - nextFrame);
            scratch.clear();
            for (int i = 0; i < n; i++) {
                int f = (int) (nextFrame + i);
                float l = track.left(f);
                float r = track.right(f);
                if (eq != null) {
                    l = left.process(l);
                    r = right.process(r);
                }
                scratch.put((short) Math.max(-32768, Math.min(32767, Math.round(l * 32767))));
                scratch.put((short) Math.max(-32768, Math.min(32767, Math.round(r * 32767))));
            }
            scratch.flip();
            int buffer = free.poll();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, scratch, Track.SAMPLE_RATE);
            AL10.alSourceQueueBuffers(source, buffer);
            queued.add(new long[] {buffer, nextFrame});
            nextFrame += n;
        }
        if (queued.isEmpty()) {
            return false;
        }
        if (state != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
        return true;
    }

    /** @param alive false after Minecraft's sound engine restarted, when the OpenAL objects are already gone */
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
