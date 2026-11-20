package com.minescore.client.audio;

import com.minescore.audio.Track;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Decodes Ogg Vorbis with the stb_vorbis copy that ships with Minecraft's client. */
public final class OggDecoder {
    private OggDecoder() {}

    public static Track decode(byte[] file) throws IOException {
        ByteBuffer data = MemoryUtil.memAlloc(file.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(file).flip();
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
            if (pcm == null) {
                throw new IOException("Not a valid Ogg Vorbis file");
            }
            try {
                short[] samples = new short[pcm.remaining()];
                pcm.get(samples);
                return Track.convert(samples, channels.get(0), rate.get(0));
            } finally {
                // stb_vorbis allocates with LWJGL's allocator (jemalloc in Minecraft), so it must go back the same way:
                // the C library's free on it corrupts the native heap and crashes the game.
                MemoryUtil.memFree(pcm);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
