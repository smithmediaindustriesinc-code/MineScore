package com.minescore.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads uncompressed WAV files: 8/16/24/32-bit integer PCM and 32-bit float, any channel count or rate. */
public final class WavDecoder {
    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_FLOAT = 3;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    private WavDecoder() {}

    public static Track decode(byte[] file) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (file.length < 12 || buf.getInt(0) != 0x46464952 || buf.getInt(8) != 0x45564157) { // "RIFF", "WAVE"
            throw new IOException("Not a WAV file");
        }
        int format = -1, channels = 0, rate = 0, bits = 0;
        int dataStart = -1, dataLength = 0;
        int pos = 12;
        while (pos + 8 <= file.length) {
            int id = buf.getInt(pos);
            int size = buf.getInt(pos + 4);
            int body = pos + 8;
            if (id == 0x20746D66) { // "fmt "
                format = buf.getShort(body) & 0xFFFF;
                channels = buf.getShort(body + 2) & 0xFFFF;
                rate = buf.getInt(body + 4);
                bits = buf.getShort(body + 14) & 0xFFFF;
                if (format == FORMAT_EXTENSIBLE && size >= 26) {
                    format = buf.getShort(body + 24) & 0xFFFF;
                }
            } else if (id == 0x61746164) { // "data"
                dataStart = body;
                dataLength = Math.min(size < 0 ? Integer.MAX_VALUE : size, file.length - body);
                break;
            }
            pos = body + size + (size & 1);
        }
        if (format < 0 || dataStart < 0 || channels <= 0 || rate <= 0) {
            throw new IOException("WAV file is missing its format or data");
        }
        if (format != FORMAT_PCM && !(format == FORMAT_FLOAT && bits == 32)) {
            throw new IOException("Unsupported WAV encoding " + format + " (" + bits + "-bit)");
        }
        int bytesPerSample = bits / 8;
        if (bytesPerSample < 1 || bytesPerSample > 4) {
            throw new IOException("Unsupported WAV sample size " + bits);
        }
        int count = dataLength / bytesPerSample;
        short[] samples = new short[count - count % channels];
        for (int i = 0; i < samples.length; i++) {
            int at = dataStart + i * bytesPerSample;
            samples[i] = switch (bytesPerSample) {
                case 1 -> (short) (((file[at] & 0xFF) - 128) << 8);
                case 2 -> buf.getShort(at);
                case 3 -> (short) ((file[at + 1] & 0xFF) | (file[at + 2] << 8));
                default -> format == FORMAT_FLOAT
                        ? (short) Math.max(-32768, Math.min(32767, Math.round(buf.getFloat(at) * 32767)))
                        : (short) (buf.getInt(at) >> 16);
            };
        }
        return Track.convert(samples, channels, rate);
    }
}
