package com.minescore.client.audio;

import com.minescore.audio.Track;
import com.minescore.audio.notes.NoteEvent;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.audio.notes.NoteSustain;
import java.io.IOException;
import java.io.InputStream;
import com.minescore.score.Instruments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Renders note block hits (from MIDI, Note Block Studio or scores) to audio, using the note block
 * sounds of this player's current resource packs.
 */
public final class NoteRenderer {
    /** Headroom so many simultaneous notes rarely need scaling down. */
    private static final float MASTER = 0.45f;

    private NoteRenderer() {}

    public static Track render(List<NoteEvent> events) throws IOException {
        if (events.isEmpty()) {
            throw new IOException("The song has no notes");
        }
        Map<String, float[]> samples = new HashMap<>();
        Map<String, Optional<NoteSustain.Loop>> loops = new HashMap<>();
        double end = 0;
        for (NoteEvent e : events) {
            String key = soundOf(e);
            float[] sample = samples.get(key);
            if (sample == null) {
                sample = load(key, e.instrument());
                samples.put(key, sample);
            }
            double natural = sample.length / (double) Track.SAMPLE_RATE / e.rate();
            if (e.held()) {
                float[] s = sample;
                int center = centerOf(e);
                Optional<NoteSustain.Loop> loop = loops.computeIfAbsent(key,
                        k -> Optional.ofNullable(NoteSustain.find(s, Track.SAMPLE_RATE, center)));
                double held = e.length() + NoteSustain.RELEASE;
                end = Math.max(end, e.seconds() + (loop.isPresent() ? held : Math.min(held, natural)));
            } else {
                end = Math.max(end, e.seconds() + natural);
            }
        }
        int frames = (int) Math.ceil(end * Track.SAMPLE_RATE) + 1;
        float[] left = new float[frames];
        float[] right = new float[frames];
        for (NoteEvent e : events) {
            float[] sample = samples.get(soundOf(e));
            int start = (int) Math.round(e.seconds() * Track.SAMPLE_RATE);
            float leftGain = e.volume() * MASTER * Math.min(1, 1 - e.pan());
            float rightGain = e.volume() * MASTER * Math.min(1, 1 + e.pan());
            if (e.held()) { // held to its length, or cut short (SCOPE.md §8.1)
                float[] note = NoteSustain.render(sample, loops.get(soundOf(e)).orElse(null), e.rate(),
                        (int) Math.round(e.length() * Track.SAMPLE_RATE), (int) Math.round(NoteSustain.RELEASE * Track.SAMPLE_RATE));
                for (int i = 0; i < note.length && start + i < frames; i++) {
                    left[start + i] += note[i] * leftGain;
                    right[start + i] += note[i] * rightGain;
                }
                continue;
            }
            int length = (int) (sample.length / e.rate());
            for (int i = 0; i < length && start + i < frames; i++) {
                double src = i * e.rate();
                int a = (int) src;
                double t = src - a;
                float v = (float) (sample[a] * (1 - t) + (a + 1 < sample.length ? sample[a + 1] : 0) * t);
                left[start + i] += v * leftGain;
                right[start + i] += v * rightGain;
            }
        }
        float peak = 0;
        for (int i = 0; i < frames; i++) {
            peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
        }
        float scale = peak > 0.95f ? 0.95f / peak : 1;
        short[] out = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            out[i * 2] = (short) Math.round(left[i] * scale * 32767);
            out[i * 2 + 1] = (short) Math.round(right[i] * scale * 32767);
        }
        return Track.stereo(out);
    }

    /** The sound event a hit plays: a pack instrument's own (if that pack is still loaded), or its note block sound. */
    private static String soundOf(NoteEvent e) {
        Instruments.Custom custom = Instruments.custom(e.custom());
        return custom != null ? custom.sound() : "minecraft:" + e.instrument().soundEvent();
    }

    private static int centerOf(NoteEvent e) {
        Instruments.Custom custom = Instruments.custom(e.custom());
        return custom != null ? custom.center() : e.instrument().center();
    }

    /** A sound's sample as mono floats at 44.1 kHz, as the resource packs currently provide it (a harp if it's missing). */
    private static float[] load(String soundId, NoteInstrument fallback) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        WeighedSoundEvents event = mc.getSoundManager().getSoundEvent(new ResourceLocation(soundId));
        if (event == null) {
            event = mc.getSoundManager().getSoundEvent(new ResourceLocation("minecraft", fallback.soundEvent()));
        }
        if (event == null) {
            throw new IOException("Missing note block sound " + soundId);
        }
        Sound sound = event.getSound(RandomSource.create(0));
        try (InputStream in = mc.getResourceManager().open(sound.getPath())) {
            Track track = OggDecoder.decode(in.readAllBytes());
            float[] mono = new float[track.frames()];
            for (int i = 0; i < mono.length; i++) {
                mono[i] = (track.left(i) + track.right(i)) * 0.5f * sound.getVolume().sample(RandomSource.create(0));
            }
            return mono;
        }
    }
}
