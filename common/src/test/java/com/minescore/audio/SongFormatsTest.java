package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.audio.notes.MidiReader;
import com.minescore.audio.notes.NbsReader;
import com.minescore.audio.notes.NoteEvent;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.song.SongMeta;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SongFormatsTest {
    // --- FLAC: a minimal encoder producing one verbatim and one Rice-coded (fixed order 2) channel ---

    private static final class Bits {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int current;
        private int count;

        void put(long value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                current = (current << 1) | (int) ((value >> i) & 1);
                if (++count == 8) {
                    out.write(current);
                    current = 0;
                    count = 0;
                }
            }
        }

        void align() {
            while (count != 0) {
                put(0, 1);
            }
        }

        byte[] bytes() {
            align();
            return out.toByteArray();
        }
    }

    private static byte[] flac(short[] left, short[] right) {
        int n = left.length;
        Bits b = new Bits();
        b.put('f', 8);
        b.put('L', 8);
        b.put('a', 8);
        b.put('C', 8);
        b.put(1, 1); // last metadata block
        b.put(0, 7); // STREAMINFO
        b.put(34, 24);
        b.put(n, 16);
        b.put(n, 16);
        b.put(0, 24);
        b.put(0, 24);
        b.put(44100, 20);
        b.put(1, 3); // 2 channels
        b.put(15, 5); // 16 bits
        b.put(n, 36);
        b.put(0, 64);
        b.put(0, 64); // MD5
        // frame header
        b.put(0xFFF8, 16);
        b.put(7, 4); // block size: 16-bit value follows
        b.put(9, 4); // 44.1 kHz
        b.put(1, 4); // two independent channels
        b.put(4, 3); // 16 bits
        b.put(0, 1);
        b.put(0, 8); // frame number 0
        b.put(n - 1, 16);
        b.put(0, 8); // CRC-8 (not checked)
        // channel 0: verbatim
        b.put(0, 1);
        b.put(1, 6);
        b.put(0, 1);
        for (short s : left) {
            b.put(s & 0xFFFF, 16);
        }
        // channel 1: fixed order 2 with one Rice partition, parameter 4
        b.put(0, 1);
        b.put(10, 6);
        b.put(0, 1);
        b.put(right[0] & 0xFFFF, 16);
        b.put(right[1] & 0xFFFF, 16);
        b.put(0, 2); // 4-bit Rice parameters
        b.put(0, 4); // partition order 0
        b.put(4, 4);
        for (int i = 2; i < n; i++) {
            long residual = right[i] - (2L * right[i - 1] - right[i - 2]);
            long u = residual >= 0 ? residual << 1 : ((-residual) << 1) - 1;
            for (long q = u >> 4; q > 0; q--) {
                b.put(0, 1);
            }
            b.put(1, 1);
            b.put(u & 15, 4);
        }
        b.align();
        b.put(0, 16); // CRC-16 (not checked)
        return b.bytes();
    }

    @Test
    void flacDecodesVerbatimAndRiceCodedChannels() throws IOException {
        int n = 300;
        short[] left = new short[n];
        short[] right = new short[n];
        for (int i = 0; i < n; i++) {
            left[i] = (short) (Math.sin(i / 7.0) * 12000);
            right[i] = (short) (Math.sin(i / 11.0) * 9000 + i % 3);
        }
        Track track = FlacDecoder.decode(flac(left, right));
        assertEquals(n, track.frames());
        for (int i = 0; i < n; i++) {
            assertEquals(left[i] / 32768f, track.left(i), 1e-6, "left " + i);
            assertEquals(right[i] / 32768f, track.right(i), 1e-6, "right " + i);
        }
    }

    @Test
    void adpcmRoundTripsCloselyAndShrinksToAQuarter() throws IOException {
        Track demo = DemoTrack.get();
        byte[] packed = AdpcmCodec.encode(demo);
        assertTrue(packed.length < demo.frames() * 4 / 3.5, "packed to " + packed.length + " bytes");
        Track back = AdpcmCodec.decode(packed);
        assertEquals(demo.frames(), back.frames());
        double error = 0;
        double signal = 0;
        for (int i = 0; i < demo.frames(); i++) {
            error += Math.pow(demo.left(i) - back.left(i), 2) + Math.pow(demo.right(i) - back.right(i), 2);
            signal += Math.pow(demo.left(i), 2) + Math.pow(demo.right(i), 2);
        }
        double snr = 10 * Math.log10(signal / error);
        assertTrue(snr > 25, "signal-to-noise " + snr + " dB");
    }

    // --- MIDI ---

    private static byte[] midi() {
        ByteBuffer b = ByteBuffer.allocate(128);
        b.putInt(0x4D546864).putInt(6).putShort((short) 0).putShort((short) 1).putShort((short) 96);
        byte[] track = {
            0x00, (byte) 0xFF, 0x51, 0x03, 0x07, (byte) 0xA1, 0x20,     // tempo 500,000 us/quarter (120 bpm)
            0x00, (byte) 0xC0, 33,                                        // channel 1: program 33 (bass)
            0x00, (byte) 0x90, 42, 100,                                   // F#2 at tick 0
            0x60, 0x40, 64,                                               // running status: E4 at tick 96 (0.5 s)
            0x00, (byte) 0x99, 38, 127,                                   // drums: snare at 0.5 s
            0x00, (byte) 0xFF, 0x2F, 0x00,                                // end of track
        };
        b.putInt(0x4D54726B).putInt(track.length).put(track);
        byte[] out = new byte[b.position()];
        b.flip().get(out);
        return out;
    }

    @Test
    void midiNotesGetTimesInstrumentsAndPitches() throws IOException {
        List<NoteEvent> notes = MidiReader.read(midi());
        assertEquals(3, notes.size());
        NoteEvent first = notes.get(0);
        assertEquals(0, first.seconds(), 1e-9);
        assertEquals(NoteInstrument.BASS, first.instrument());
        assertEquals(1.0, first.rate(), 1e-9, "F#2 is the bass sample's own pitch");
        NoteEvent second = notes.stream().filter(e -> e.instrument() == NoteInstrument.BASS && e.seconds() > 0).findFirst().orElseThrow();
        assertEquals(0.5, second.seconds(), 1e-9);
        assertEquals(Math.pow(2, (64 - 12 - 42) / 12.0), second.rate(), 1e-9, "E4 is out of bass range, so an octave down");
        assertTrue(notes.stream().anyMatch(e -> e.instrument() == NoteInstrument.SNARE));
    }

    // --- Note Block Studio ---

    private static byte[] nbs() {
        ByteBuffer b = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).put((byte) 5).put((byte) 16).putShort((short) 10).putShort((short) 1);
        for (String s : new String[] {"Test", "", "", ""}) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            b.putInt(bytes.length).put(bytes);
        }
        b.putShort((short) 1000); // 10 ticks per second
        b.put((byte) 0).put((byte) 0).put((byte) 4).putInt(0).putInt(0).putInt(0).putInt(0).putInt(0);
        b.putInt(0); // import name
        b.put((byte) 0).put((byte) 0).putShort((short) 0); // loop
        // tick 0, layer 0: harp key 45 (F#4); tick 5, layer 0: bell key 57
        b.putShort((short) 1).putShort((short) 1).put((byte) 0).put((byte) 45).put((byte) 100).put((byte) 100).putShort((short) 0);
        b.putShort((short) 0);
        b.putShort((short) 5).putShort((short) 1).put((byte) 7).put((byte) 57).put((byte) 50).put((byte) 100).putShort((short) 0);
        b.putShort((short) 0);
        b.putShort((short) 0);
        // layer 0
        b.putInt(0).put((byte) 0).put((byte) 80).put((byte) 100);
        byte[] out = new byte[b.position()];
        b.flip().get(out);
        return out;
    }

    @Test
    void nbsNotesFollowTicksKeysAndLayerVolume() throws IOException {
        List<NoteEvent> notes = NbsReader.read(nbs());
        assertEquals(2, notes.size());
        assertEquals(0, notes.get(0).seconds(), 1e-9);
        assertEquals(NoteInstrument.HARP, notes.get(0).instrument());
        assertEquals(1.0, notes.get(0).rate(), 1e-9);
        assertEquals(0.8f, notes.get(0).volume(), 1e-6);
        assertEquals(0.5, notes.get(1).seconds(), 1e-9);
        assertEquals(NoteInstrument.BELL, notes.get(1).instrument());
        assertEquals(2.0, notes.get(1).rate(), 1e-9, "12 keys up is double speed");
        assertEquals(0.4f, notes.get(1).volume(), 1e-6);
    }

    @Test
    void instrumentsStayWithinTheirTwoOctaves() {
        for (NoteInstrument instrument : NoteInstrument.values()) {
            for (int note = 0; note < 128; note++) {
                double rate = instrument.rateFor(note);
                assertTrue(rate >= 0.5 - 1e-9 && rate <= 2 + 1e-9, instrument + " note " + note + " rate " + rate);
            }
        }
    }

    @Test
    void songIdsMustLookLikeHashes() {
        assertTrue(SongMeta.validId("a".repeat(64)));
        assertFalse(SongMeta.validId("../../etc/passwd"));
        assertFalse(SongMeta.validId("A".repeat(64)));
        assertFalse(SongMeta.validFormat("exe"));
    }
}
