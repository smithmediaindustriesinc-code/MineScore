package com.minescore.client.audio;

import com.minescore.MineScore;
import com.minescore.audio.SynthRecipes;
import com.minescore.audio.SynthSound;
import com.minescore.audio.Track;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * Plays {@link SynthSound}s: each is synthesized once into an OpenAL buffer, then played as a one-shot
 * source where it happened, at the Blocks volume. The sound style comes from the top resource pack with an
 * {@code assets/minescore/minescore/sound_style.json} ({@code {"style": "retro"}}): the built-in Retro and
 * Studio sound packs, or the mod's own default.
 */
public final class SynthSounds {
    private static final ResourceLocation STYLE_FILE = MineScore.id("minescore/sound_style.json");
    private static final Map<SynthSound, Integer> BUFFERS = new EnumMap<>(SynthSound.class);
    private static final List<Integer> PLAYING = new ArrayList<>();
    private static SynthRecipes.Style style = SynthRecipes.Style.MINECRAFT;

    private SynthSounds() {}

    public static void play(SynthSound.Play packet) {
        Minecraft mc = Minecraft.getInstance();
        float volume = mc.options.getSoundSourceVolume(SoundSource.BLOCKS);
        if (volume <= 0) {
            return;
        }
        Integer buffer = BUFFERS.computeIfAbsent(packet.sound(), SynthSounds::upload);
        if (buffer == null || buffer == 0) {
            return;
        }
        AL10.alGetError();
        int source = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            return; // out of sources: skip it rather than steal one
        }
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSource3f(source, AL10.AL_POSITION, (float) packet.x(), (float) packet.y(), (float) packet.z());
        AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, (float) SynthSound.RANGE);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1);
        AL10.alSourcef(source, AL10.AL_GAIN, volume);
        AL10.alSourcef(source, AL10.AL_PITCH, 0.92f + new Random().nextFloat() * 0.16f); // no two quite alike
        AL10.alSourcePlay(source);
        PLAYING.add(source);
    }

    /** Every client tick: frees sources that have finished. */
    public static void tick() {
        Iterator<Integer> it = PLAYING.iterator();
        while (it.hasNext()) {
            int source = it.next();
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alDeleteSources(source);
                it.remove();
            }
        }
    }

    /** Resources reloaded: picks up the sound style, remaking the sounds if it changed. */
    public static void reload(ResourceManager resources) {
        SynthRecipes.Style picked = SynthRecipes.Style.MINECRAFT;
        for (Resource resource : resources.getResourceStack(STYLE_FILE)) { // the last is the top pack's
            try (Reader reader = resource.openAsReader()) {
                picked = SynthRecipes.Style.of(JsonParser.parseReader(reader).getAsJsonObject().get("style").getAsString());
            } catch (Exception e) {
                MineScore.LOGGER.warn("Couldn't read the sound style in {} ({})", STYLE_FILE, resource.sourcePackId(), e);
            }
        }
        if (picked != style) {
            style = picked;
            Minecraft.getInstance().execute(() -> {
                BUFFERS.values().forEach(buffer -> {
                    if (buffer != null && buffer != 0) {
                        AL10.alDeleteBuffers(buffer);
                    }
                });
                BUFFERS.clear();
            });
        }
    }

    /** Minecraft restarted its sound engine, taking our buffers and sources with it. */
    public static void forget() {
        BUFFERS.clear();
        PLAYING.clear();
    }

    private static Integer upload(SynthSound sound) {
        short[] pcm = SynthRecipes.render(sound, style);
        ByteBuffer data = ByteBuffer.allocateDirect(pcm.length * 2).order(ByteOrder.nativeOrder());
        data.asShortBuffer().put(pcm);
        AL10.alGetError();
        int buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, Track.SAMPLE_RATE);
        return AL10.alGetError() == AL10.AL_NO_ERROR ? buffer : 0;
    }
}
