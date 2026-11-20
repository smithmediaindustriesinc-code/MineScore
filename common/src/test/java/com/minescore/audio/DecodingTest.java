package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class DecodingTest {
    /** A PCM WAV with the given 16-bit samples. */
    private static byte[] wav(int channels, int rate, short... samples) {
        int data = samples.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + data).putInt(0x45564157);
        b.putInt(0x20746D66).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate)
                .putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        b.putInt(0x61746164).putInt(data);
        for (short s : samples) {
            b.putShort(s);
        }
        return b.array();
    }

    @Test
    void readsStereoWavAtTheNativeRate() throws IOException {
        Track track = WavDecoder.decode(wav(2, 44100, (short) 16384, (short) -16384, (short) 0, (short) 32767));
        assertEquals(2, track.frames());
        assertEquals(0.5f, track.left(0), 1e-4);
        assertEquals(-0.5f, track.right(0), 1e-4);
    }

    @Test
    void monoGoesToBothSidesAndIsResampled() throws IOException {
        short[] samples = new short[22050];
        java.util.Arrays.fill(samples, (short) 8192);
        Track track = WavDecoder.decode(wav(1, 22050, samples));
        assertEquals(44100, track.frames(), 2);
        assertEquals(track.left(1000), track.right(1000));
        assertEquals(0.25f, track.left(1000), 1e-4);
    }

    @Test
    void rejectsFilesThatAreNotWav() {
        assertThrows(IOException.class, () -> WavDecoder.decode(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
    }

    @Test
    void demoTrackHasEveryBand() {
        Track demo = DemoTrack.get();
        assertTrue(demo.seconds() > 15 && demo.seconds() < 20, "demo is " + demo.seconds() + " s");
        double[] energy = new double[3];
        com.minescore.wiring.Gauge[] bands = com.minescore.wiring.Gauge.values();
        for (int b = 0; b < 3; b++) {
            Crossover filter = Crossover.forBand(bands[b], Track.SAMPLE_RATE, 200, 2000);
            for (int i = 0; i < demo.frames(); i++) {
                float out = filter.process(OutputChannel.MONO.pick(demo.left(i), demo.right(i)));
                energy[b] += out * out;
            }
        }
        for (int b = 0; b < 3; b++) {
            assertTrue(energy[b] / demo.frames() > 1e-4, bands[b] + " band is nearly silent");
        }
    }
}
