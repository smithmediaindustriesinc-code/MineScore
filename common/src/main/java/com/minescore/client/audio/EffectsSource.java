package com.minescore.client.audio;

import com.minescore.MineScoreSettings;
import com.minescore.audio.Track;
import com.minescore.audio.fx.EffectProcessor;
import com.minescore.audio.fx.Fx;
import com.minescore.audio.fx.TimeStretch;
import com.minescore.meu.EffectChain;

/**
 * A song as it comes out of a Multi-Effects Unit: stretched to the unit's speed, then run through its cards.
 * One is shared by every speaker of a system, so the effects run once per system, not once per speaker.
 * It renders ahead into a ring buffer that all the speakers read from; they all play close to the same
 * moment, so a few seconds of buffer covers them.
 */
final class EffectsSource implements AudioSource {
    private static final int CAPACITY = Track.SAMPLE_RATE * 4;
    private static final int BLOCK = 1024;

    private final Track track;
    private final double speed;
    private final TimeStretch stretch;
    private final EffectProcessor processor;
    private EffectChain chain;
    private Fx.Context context;
    private final float[] ringLeft = new float[CAPACITY], ringRight = new float[CAPACITY];
    private final float[] blockLeft = new float[BLOCK], blockRight = new float[BLOCK];
    private long renderedTo = Long.MIN_VALUE;

    EffectsSource(Track track, EffectChain chain, Fx.Context context) {
        this.track = track;
        this.speed = chain.speed();
        this.chain = chain;
        this.context = context;
        this.stretch = TimeStretch.create(TimeStretch.Algorithm.byName(MineScoreSettings.speedAlgorithm.get()), track, speed,
                context.high());
        this.processor = new EffectProcessor(chain, context);
    }

    /** New knob settings (or song analysis): effects carry on with their state. */
    void update(EffectChain next, Fx.Context nextContext) {
        if (!next.equals(chain) || !nextContext.equals(context)) {
            chain = next;
            context = nextContext;
            processor.update(next, nextContext);
        }
    }

    @Override
    public long frames() {
        return (long) (track.frames() / speed);
    }

    @Override
    public float left(long frame) {
        if (frame < 0 || frame >= frames()) {
            return 0;
        }
        if (renderedTo == Long.MIN_VALUE || frame < renderedTo - CAPACITY + BLOCK || frame > renderedTo + CAPACITY) {
            seek(frame); // a jump (a new listener, or one far behind): start over from here
        }
        while (frame >= renderedTo) {
            render();
        }
        return ringLeft[(int) (frame % CAPACITY)];
    }

    @Override
    public float right(long frame) {
        if (frame < 0 || frame >= frames() || frame >= renderedTo || frame < renderedTo - CAPACITY) {
            return 0;
        }
        return ringRight[(int) (frame % CAPACITY)];
    }

    private void seek(long frame) {
        stretch.seek(frame * speed);
        processor.reset();
        renderedTo = frame;
    }

    private void render() {
        stretch.read(blockLeft, blockRight, BLOCK);
        processor.process(blockLeft, blockRight, BLOCK);
        for (int i = 0; i < BLOCK; i++) {
            int index = (int) ((renderedTo + i) % CAPACITY);
            ringLeft[index] = blockLeft[i];
            ringRight[index] = blockRight[i];
        }
        renderedTo += BLOCK;
    }

    /** The whole song through the effects, for printing onto a record. Slow: call off-thread. */
    static Track renderAll(Track track, EffectChain chain, Fx.Context context) {
        EffectsSource source = new EffectsSource(track, chain, context);
        long frames = source.frames();
        short[] out = new short[(int) frames * 2];
        for (long f = 0; f < frames; f++) {
            out[(int) f * 2] = toShort(source.left(f));
            out[(int) f * 2 + 1] = toShort(source.right(f));
        }
        return Track.stereo(out);
    }

    private static short toShort(float v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767)));
    }
}
