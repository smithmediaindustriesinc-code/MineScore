package com.minescore.audio.notes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Note Block Studio songs ({@code .nbs}): the classic format and versions 1-5 of the new one.
 * Layer volume and stereo are honoured; custom instruments fall back to harp.
 */
public final class NbsReader {
    private NbsReader() {}

    /** One note as written: {@code key} 0 is A0, so its MIDI note is {@code key + 21}; key 45 (F♯4) plays the sample as recorded. */
    public record RawNote(int tick, int layer, int instrument, int key, int velocity, int panning, int pitch) {}

    /**
     * A song as written, for importing it as a score too (SCOPE.md §8.3).
     *
     * @param beats       its time signature, in quarter notes a measure
     * @param layerVolume each layer's volume (0–100), by layer
     * @param layerPan    each layer's stereo (0–200, 100 centred), by layer
     */
    public record Parsed(List<RawNote> notes, double ticksPerSecond, int beats, String name, String author,
                         Map<Integer, Integer> layerVolume, Map<Integer, Integer> layerPan) {}

    public static List<NoteEvent> read(byte[] file) throws IOException {
        Parsed parsed = parse(file);
        List<NoteEvent> events = new ArrayList<>(parsed.notes().size());
        for (RawNote n : parsed.notes()) {
            NoteInstrument instrument = NoteInstrument.byNbsIndex(n.instrument());
            double rate = Math.pow(2, (n.key() - 45 + n.pitch() / 100.0) / 12);
            float volume = n.velocity() / 100f * parsed.layerVolume().getOrDefault(n.layer(), 100) / 100f;
            float pan = ((n.panning() - 100) + (parsed.layerPan().getOrDefault(n.layer(), 100) - 100)) / 200f;
            events.add(new NoteEvent(n.tick() / parsed.ticksPerSecond(), instrument, rate, volume, Math.max(-1, Math.min(1, pan)), -1,
                    instrument.pitched() ? n.key() + 21 : -1));
        }
        return events;
    }

    public static Parsed parse(byte[] file) throws IOException {
        try {
            return parse(ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN));
        } catch (RuntimeException e) {
            throw new IOException("Not a valid Note Block Studio file", e);
        }
    }

    private static Parsed parse(ByteBuffer in) {
        int first = in.getShort() & 0xFFFF;
        int version = 0;
        if (first == 0) { // new format
            version = in.get() & 0xFF;
            in.get(); // vanilla instrument count
            if (version >= 3) {
                in.getShort(); // song length
            }
        }
        int layerCount = in.getShort() & 0xFFFF;
        String name = string(in);
        String author = string(in);
        string(in); // original author
        string(in); // description
        double ticksPerSecond = Math.max(0.01, (in.getShort() & 0xFFFF) / 100.0);
        in.get(); // auto-saving
        in.get(); // auto-saving duration
        int beats = Math.max(1, Math.min(32, in.get() & 0xFF)); // time signature
        in.getInt(); // minutes spent
        in.getInt(); // left clicks
        in.getInt(); // right clicks
        in.getInt(); // note blocks added
        in.getInt(); // note blocks removed
        string(in); // imported file name
        if (version >= 4) {
            in.get(); // loop on/off
            in.get(); // max loop count
            in.getShort(); // loop start tick
        }

        List<RawNote> notes = new ArrayList<>();
        int tick = -1;
        while (true) {
            int jump = in.getShort() & 0xFFFF;
            if (jump == 0) {
                break;
            }
            tick += jump;
            int layer = -1;
            while (true) {
                int layerJump = in.getShort() & 0xFFFF;
                if (layerJump == 0) {
                    break;
                }
                layer += layerJump;
                int instrument = in.get() & 0xFF;
                int key = in.get() & 0xFF;
                int velocity = 100;
                int panning = 100;
                int pitch = 0;
                if (version >= 4) {
                    velocity = in.get() & 0xFF;
                    panning = in.get() & 0xFF;
                    pitch = in.getShort();
                }
                notes.add(new RawNote(tick, layer, instrument, key, velocity, panning, pitch));
            }
        }

        Map<Integer, Integer> layerVolume = new HashMap<>();
        Map<Integer, Integer> layerPan = new HashMap<>();
        for (int layer = 0; layer < layerCount && in.remaining() > 0; layer++) {
            string(in); // name
            if (version >= 4) {
                in.get(); // locked
            }
            layerVolume.put(layer, in.get() & 0xFF);
            if (version >= 2) {
                layerPan.put(layer, in.get() & 0xFF);
            }
        }
        return new Parsed(notes, ticksPerSecond, beats, name, author, layerVolume, layerPan);
    }

    private static String string(ByteBuffer in) {
        int length = in.getInt();
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
