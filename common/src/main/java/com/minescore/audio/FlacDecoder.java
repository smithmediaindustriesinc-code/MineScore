package com.minescore.audio;

import java.io.IOException;

/**
 * A FLAC decoder (format spec: xiph.org/flac/format.html), written for MineScore so no third-party
 * library is needed. Handles every subframe type, all channel decorrelation modes and 8–32 bit audio;
 * it reads the stream front to back and doesn't verify checksums.
 */
public final class FlacDecoder {
    private FlacDecoder() {}

    public static Track decode(byte[] file) throws IOException {
        try {
            return new Reader(file).decode();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("FLAC file ended early", e);
        }
    }

    private static final class Reader {
        private final byte[] data;
        private long bit; // read position in bits

        private int sampleRate;
        private int channels;
        private int bitsPerSample;

        Reader(byte[] data) {
            this.data = data;
        }

        Track decode() throws IOException {
            if (data.length < 4 || data[0] != 'f' || data[1] != 'L' || data[2] != 'a' || data[3] != 'C') {
                throw new IOException("Not a FLAC file");
            }
            bit = 32;
            boolean last;
            do {
                last = read(1) == 1;
                int type = read(7);
                int length = read(24);
                long next = bit + length * 8L;
                if (type == 0) { // STREAMINFO
                    read(16);
                    read(16);
                    read(24);
                    read(24);
                    sampleRate = read(20);
                    channels = read(3) + 1;
                    bitsPerSample = read(5) + 1;
                }
                bit = next;
            } while (!last);
            if (sampleRate == 0) {
                throw new IOException("FLAC file has no stream info");
            }

            ShortList out = new ShortList();
            while (bit / 8 + 2 < data.length) {
                if (!frame(out)) {
                    break;
                }
            }
            return Track.convert(out.toArray(), channels, sampleRate);
        }

        /** Decodes one frame into interleaved 16-bit samples. False at the end of the stream. */
        private boolean frame(ShortList out) throws IOException {
            align();
            // Find the next frame sync code (0xFFF8 or 0xFFF9).
            int start = (int) (bit / 8);
            while (start + 1 < data.length && !((data[start] & 0xFF) == 0xFF && (data[start + 1] & 0xFE) == 0xF8)) {
                start++;
            }
            if (start + 1 >= data.length) {
                return false;
            }
            bit = start * 8L + 16;
            int blockSizeCode = read(4);
            int rateCode = read(4);
            int assignment = read(4);
            int sizeCode = read(3);
            read(1);
            skipUtf8Number();
            int blockSize = switch (blockSizeCode) {
                case 1 -> 192;
                case 2, 3, 4, 5 -> 576 << (blockSizeCode - 2);
                case 6 -> read(8) + 1;
                case 7 -> read(16) + 1;
                case 0 -> throw new IOException("Reserved FLAC block size");
                default -> 256 << (blockSizeCode - 8);
            };
            if (rateCode == 12) {
                read(8);
            } else if (rateCode == 13 || rateCode == 14) {
                read(16);
            }
            int bps = switch (sizeCode) {
                case 1 -> 8;
                case 2 -> 12;
                case 4 -> 16;
                case 5 -> 20;
                case 6 -> 24;
                case 7 -> 32;
                default -> bitsPerSample;
            };
            read(8); // header CRC

            int frameChannels = assignment < 8 ? assignment + 1 : 2;
            long[][] samples = new long[frameChannels][blockSize];
            for (int ch = 0; ch < frameChannels; ch++) {
                boolean side = (assignment == 8 && ch == 1) || (assignment == 9 && ch == 0) || (assignment == 10 && ch == 1);
                subframe(samples[ch], blockSize, bps + (side ? 1 : 0));
            }
            align();
            read(16); // frame CRC

            for (int i = 0; i < blockSize; i++) {
                switch (assignment) {
                    case 8 -> samples[1][i] = samples[0][i] - samples[1][i]; // left/side
                    case 9 -> samples[0][i] = samples[0][i] + samples[1][i]; // side/right
                    case 10 -> { // mid/side
                        long mid = (samples[0][i] << 1) | (samples[1][i] & 1);
                        long sideSample = samples[1][i];
                        samples[0][i] = (mid + sideSample) >> 1;
                        samples[1][i] = (mid - sideSample) >> 1;
                    }
                    default -> { }
                }
                for (int ch = 0; ch < frameChannels; ch++) {
                    long s = samples[ch][i];
                    s = bps > 16 ? s >> (bps - 16) : s << (16 - bps);
                    out.add((short) Math.max(-32768, Math.min(32767, s)));
                }
            }
            return true;
        }

        private void subframe(long[] out, int blockSize, int bps) throws IOException {
            read(1);
            int type = read(6);
            int wasted = 0;
            if (read(1) == 1) {
                wasted = 1;
                while (read(1) == 0) {
                    wasted++;
                }
                bps -= wasted;
            }
            if (type == 0) {
                long v = readSigned(bps);
                java.util.Arrays.fill(out, v);
            } else if (type == 1) {
                for (int i = 0; i < blockSize; i++) {
                    out[i] = readSigned(bps);
                }
            } else if (type >= 8 && type <= 12) {
                int order = type - 8;
                for (int i = 0; i < order; i++) {
                    out[i] = readSigned(bps);
                }
                residual(out, blockSize, order);
                for (int i = order; i < blockSize; i++) {
                    out[i] += switch (order) {
                        case 1 -> out[i - 1];
                        case 2 -> 2 * out[i - 1] - out[i - 2];
                        case 3 -> 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3];
                        case 4 -> 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4];
                        default -> 0;
                    };
                }
            } else if (type >= 32) {
                int order = type - 31;
                for (int i = 0; i < order; i++) {
                    out[i] = readSigned(bps);
                }
                int precision = read(4) + 1;
                int shift = (int) readSigned(5);
                long[] coefficients = new long[order];
                for (int i = 0; i < order; i++) {
                    coefficients[i] = readSigned(precision);
                }
                residual(out, blockSize, order);
                for (int i = order; i < blockSize; i++) {
                    long sum = 0;
                    for (int j = 0; j < order; j++) {
                        sum += coefficients[j] * out[i - 1 - j];
                    }
                    out[i] += sum >> shift;
                }
            } else {
                throw new IOException("Reserved FLAC subframe type " + type);
            }
            if (wasted > 0) {
                for (int i = 0; i < blockSize; i++) {
                    out[i] <<= wasted;
                }
            }
        }

        /** Reads Rice-coded residuals into out[order..]. */
        private void residual(long[] out, int blockSize, int order) throws IOException {
            int method = read(2);
            int paramBits = method == 0 ? 4 : 5;
            int escape = method == 0 ? 15 : 31;
            int partitionOrder = read(4);
            int partitions = 1 << partitionOrder;
            int i = order;
            for (int p = 0; p < partitions; p++) {
                int count = (blockSize >> partitionOrder) - (p == 0 ? order : 0);
                int param = read(paramBits);
                if (param == escape) {
                    int raw = read(5);
                    for (int n = 0; n < count; n++) {
                        out[i++] = raw == 0 ? 0 : readSigned(raw);
                    }
                } else {
                    for (int n = 0; n < count; n++) {
                        long quotient = 0;
                        while (read(1) == 0) {
                            quotient++;
                        }
                        long u = (quotient << param) | (param == 0 ? 0 : readLong(param));
                        out[i++] = (u >>> 1) ^ -(u & 1);
                    }
                }
            }
        }

        private void skipUtf8Number() {
            int first = read(8);
            int extra = 0;
            for (int mask = 0x80; (first & mask) != 0 && mask > 0; mask >>= 1) {
                extra++;
            }
            for (int i = 1; i < extra; i++) {
                read(8);
            }
        }

        private void align() {
            bit = (bit + 7) & ~7L;
        }

        private int read(int n) {
            return (int) readLong(n);
        }

        private long readLong(int n) {
            long v = 0;
            for (int i = 0; i < n; i++) {
                int b = data[(int) (bit >> 3)] >> (7 - (bit & 7)) & 1;
                v = (v << 1) | b;
                bit++;
            }
            return v;
        }

        private long readSigned(int n) {
            long v = readLong(n);
            return n == 0 ? 0 : (v << (64 - n)) >> (64 - n);
        }
    }

    /** A growable short array. */
    static final class ShortList {
        private short[] values = new short[1 << 16];
        private int size;

        void add(short v) {
            if (size == values.length) {
                values = java.util.Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = v;
        }

        int size() {
            return size;
        }

        short[] toArray() {
            return java.util.Arrays.copyOf(values, size);
        }
    }
}
