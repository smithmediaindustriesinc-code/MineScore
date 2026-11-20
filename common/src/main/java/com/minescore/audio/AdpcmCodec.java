package com.minescore.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * IMA ADPCM, 4 bits per sample: how songs that have no compressed file of their own (WAV, and MIDI, NBS
 * and scores rendered with note block sounds) are shared with other players. About a quarter the size
 * of 16-bit audio and indistinguishable from it through Minecraft speakers.
 *
 * <p>Layout: "MSAD", int frames, then blocks of up to {@link #BLOCK} frames; each block has, per
 * channel, a short predictor and a byte step index, then that channel's samples packed two per byte.
 */
public final class AdpcmCodec {
    public static final String EXTENSION = "msad";
    private static final int MAGIC = 0x4441534D; // "MSAD" little-endian
    private static final int BLOCK = 4096;

    private static final int[] STEPS = {
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
        107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428,
        4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767,
    };
    private static final int[] INDEX_STEP = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};

    private AdpcmCodec() {}

    public static byte[] encode(Track track) {
        int frames = track.frames();
        int blocks = (frames + BLOCK - 1) / BLOCK;
        ByteBuffer out = ByteBuffer.allocate(8 + blocks * 2 * 3 + (frames + 1) / 2 * 2 + blocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC).putInt(frames);
        for (int start = 0; start < frames; start += BLOCK) {
            int n = Math.min(BLOCK, frames - start);
            for (int ch = 0; ch < 2; ch++) {
                int predictor = sample(track, start, ch);
                int index = 0;
                out.putShort((short) predictor).put((byte) index);
                int packed = 0;
                for (int i = 0; i < n; i++) {
                    int target = sample(track, start + i, ch);
                    int step = STEPS[index];
                    int diff = target - predictor;
                    int code = 0;
                    if (diff < 0) {
                        code = 8;
                        diff = -diff;
                    }
                    int delta = step >> 3;
                    if (diff >= step) {
                        code |= 4;
                        diff -= step;
                        delta += step;
                    }
                    if (diff >= step >> 1) {
                        code |= 2;
                        diff -= step >> 1;
                        delta += step >> 1;
                    }
                    if (diff >= step >> 2) {
                        code |= 1;
                        delta += step >> 2;
                    }
                    predictor = clamp(predictor + ((code & 8) != 0 ? -delta : delta));
                    index = Math.max(0, Math.min(88, index + INDEX_STEP[code]));
                    if ((i & 1) == 0) {
                        packed = code;
                    } else {
                        out.put((byte) (packed | code << 4));
                    }
                }
                if ((n & 1) == 1) {
                    out.put((byte) packed);
                }
            }
        }
        byte[] result = new byte[out.position()];
        out.flip().get(result);
        return result;
    }

    public static Track decode(byte[] data) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 8 || in.getInt() != MAGIC) {
            throw new IOException("Not a MineScore song file");
        }
        int frames = in.getInt();
        short[] samples = new short[frames * 2];
        try {
            for (int start = 0; start < frames; start += BLOCK) {
                int n = Math.min(BLOCK, frames - start);
                for (int ch = 0; ch < 2; ch++) {
                    int predictor = in.getShort();
                    int index = in.get();
                    int packed = 0;
                    for (int i = 0; i < n; i++) {
                        int code;
                        if ((i & 1) == 0) {
                            packed = in.get() & 0xFF;
                            code = packed & 0xF;
                        } else {
                            code = packed >> 4;
                        }
                        int step = STEPS[index];
                        int delta = step >> 3;
                        if ((code & 4) != 0) {
                            delta += step;
                        }
                        if ((code & 2) != 0) {
                            delta += step >> 1;
                        }
                        if ((code & 1) != 0) {
                            delta += step >> 2;
                        }
                        predictor = clamp(predictor + ((code & 8) != 0 ? -delta : delta));
                        index = Math.max(0, Math.min(88, index + INDEX_STEP[code]));
                        samples[(start + i) * 2 + ch] = (short) predictor;
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("MineScore song file is damaged", e);
        }
        return Track.stereo(samples);
    }

    private static int sample(Track track, int frame, int channel) {
        return Math.round((channel == 0 ? track.left(frame) : track.right(frame)) * 32768);
    }

    private static int clamp(int v) {
        return Math.max(-32768, Math.min(32767, v));
    }
}
