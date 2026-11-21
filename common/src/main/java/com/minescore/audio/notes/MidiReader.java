package com.minescore.audio.notes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Reads Standard MIDI Files (format 0 and 1) into note block hits. Programs map to the nearest note
 * block instrument, channel 10 to drums; channel volume, expression and pan are honoured.
 */
public final class MidiReader {
    private static final int DRUM_CHANNEL = 9;

    private MidiReader() {}

    /** A note as read; {@code end} is its note-off's tick, or -1 if it never had one. */
    public static final class RawNote {
        public final long tick;
        public final int channel, key, velocity, program, volume, pan;
        public long end = -1;

        RawNote(long tick, int channel, int key, int velocity, int program, int volume, int pan) {
            this.tick = tick;
            this.channel = channel;
            this.key = key;
            this.velocity = velocity;
            this.program = program;
            this.volume = volume;
            this.pan = pan;
        }
    }

    /**
     * A MIDI file as written, for importing it as a score (SCOPE.md §9.12).
     *
     * @param tempos           tick -> microseconds per quarter note
     * @param ticksPerQuarter  0 for SMPTE timing, which counts {@code smpteTicksPerSecond} instead
     * @param timeBeats        the first time signature (4/4 if none)
     * @param key              the first key signature in fifths (0 if none)
     */
    public record Parsed(List<RawNote> notes, TreeMap<Long, Integer> tempos, double ticksPerQuarter, double smpteTicksPerSecond,
                         int timeBeats, int timeUnit, int key) {
        public double seconds(long tick) {
            return smpteTicksPerSecond > 0 ? tick / smpteTicksPerSecond : MidiReader.seconds(tempos, tick, ticksPerQuarter);
        }

        public NoteInstrument instrument(RawNote n) {
            return n.channel == DRUM_CHANNEL ? NoteInstrument.forGeneralMidiDrum(n.key) : NoteInstrument.forGeneralMidi(n.program);
        }

        public boolean drum(RawNote n) {
            return n.channel == DRUM_CHANNEL;
        }
    }

    public static List<NoteEvent> read(byte[] file) throws IOException {
        return toEvents(parse(file));
    }

    public static Parsed parse(byte[] file) throws IOException {
        try {
            return parse(ByteBuffer.wrap(file));
        } catch (RuntimeException e) {
            throw new IOException("Not a valid MIDI file", e);
        }
    }

    private static Parsed parse(ByteBuffer in) throws IOException {
        if (in.remaining() < 14 || in.getInt() != 0x4D546864) { // "MThd"
            throw new IOException("Not a MIDI file");
        }
        int headerLength = in.getInt();
        in.getShort(); // format
        int trackCount = in.getShort() & 0xFFFF;
        int division = in.getShort();
        in.position(8 + headerLength);
        // Negative division means SMPTE timing: treat it as a fixed number of ticks per second.
        double ticksPerQuarter = division > 0 ? division : 0;
        double smpteTicksPerSecond = division < 0 ? (-(division >> 8)) * (division & 0xFF) : 0;

        TreeMap<Long, Integer> tempos = new TreeMap<>(); // tick -> microseconds per quarter note
        tempos.put(0L, 500_000);
        int[] time = {0, 0};
        Integer keySignature = null;
        List<RawNote> notes = new ArrayList<>();
        for (int t = 0; t < trackCount && in.remaining() >= 8; t++) {
            int id = in.getInt();
            int length = in.getInt();
            int end = in.position() + length;
            if (id != 0x4D54726B) { // "MTrk"
                in.position(end);
                continue;
            }
            int[] program = new int[16];
            int[] volume = new int[16];
            int[] expression = new int[16];
            int[] pan = new int[16];
            java.util.Arrays.fill(volume, 100);
            java.util.Arrays.fill(expression, 127);
            java.util.Arrays.fill(pan, 64);
            long tick = 0;
            int status = 0;
            java.util.Map<Integer, java.util.ArrayDeque<RawNote>> sounding = new java.util.HashMap<>(); // channel × key -> notes on

            while (in.position() < end) {
                tick += readVarLength(in);
                int b = in.get() & 0xFF;
                if (b < 0x80) { // running status
                    in.position(in.position() - 1);
                } else {
                    status = b;
                }
                if (status == 0xFF) {
                    int type = in.get() & 0xFF;
                    int len = (int) readVarLength(in);
                    if (type == 0x51 && len == 3) {
                        tempos.put(tick, ((in.get() & 0xFF) << 16) | ((in.get() & 0xFF) << 8) | (in.get() & 0xFF));
                    } else if (type == 0x58 && len >= 2 && time[0] == 0) { // the first time signature
                        time[0] = in.get() & 0xFF;
                        time[1] = 1 << Math.min(6, in.get() & 0xFF);
                        in.position(in.position() + len - 2);
                    } else if (type == 0x59 && len >= 1 && keySignature == null) { // the first key signature
                        keySignature = (int) in.get();
                        in.position(in.position() + len - 1);
                    } else {
                        in.position(in.position() + len);
                    }
                    if (type == 0x2F) {
                        break;
                    }
                    continue;
                }
                if (status == 0xF0 || status == 0xF7) {
                    in.position(in.position() + (int) readVarLength(in));
                    continue;
                }
                int channel = status & 0x0F;
                switch (status & 0xF0) {
                    case 0x90 -> {
                        int key = in.get() & 0x7F;
                        int velocity = in.get() & 0x7F;
                        if (velocity > 0) {
                            RawNote note = new RawNote(tick, channel, key, velocity, program[channel],
                                    volume[channel] * expression[channel] / 127, pan[channel]);
                            notes.add(note);
                            sounding.computeIfAbsent(channel * 128 + key, k -> new java.util.ArrayDeque<>()).add(note);
                        } else {
                            release(sounding, channel, key, tick); // note-on at velocity 0 is a note-off
                        }
                    }
                    case 0x80 -> {
                        int key = in.get() & 0x7F;
                        in.get();
                        release(sounding, channel, key, tick);
                    }
                    case 0xA0, 0xE0 -> in.position(in.position() + 2);
                    case 0xB0 -> {
                        int controller = in.get() & 0x7F;
                        int value = in.get() & 0x7F;
                        switch (controller) {
                            case 7 -> volume[channel] = value;
                            case 10 -> pan[channel] = value;
                            case 11 -> expression[channel] = value;
                            default -> { }
                        }
                    }
                    case 0xC0 -> program[channel] = in.get() & 0x7F;
                    case 0xD0 -> in.get();
                    default -> throw new IOException("Unexpected MIDI status byte " + status);
                }
            }
            in.position(end);
        }
        return new Parsed(notes, tempos, ticksPerQuarter, smpteTicksPerSecond, time[0] > 0 ? time[0] : 4, time[0] > 0 ? time[1] : 4,
                keySignature == null ? 0 : Math.max(-7, Math.min(7, keySignature)));
    }

    private static List<NoteEvent> toEvents(Parsed parsed) {
        List<RawNote> notes = parsed.notes();
        double smpteTicksPerSecond = parsed.smpteTicksPerSecond();
        TreeMap<Long, Integer> tempos = parsed.tempos();
        double ticksPerQuarter = parsed.ticksPerQuarter();
        List<NoteEvent> events = new ArrayList<>(notes.size());
        for (RawNote n : notes) {
            double seconds = parsed.seconds(n.tick);
            NoteInstrument instrument = parsed.instrument(n);
            double rate = instrument.rateFor(n.key);
            float vol = (n.velocity / 127f) * (n.volume / 127f);
            float panValue = (n.pan - 64) / 64f;
            // notes last as long as the file holds them (SCOPE.md §9.9); drums always play their whole hit
            double length = -1;
            if (n.end > n.tick && n.channel != DRUM_CHANNEL) {
                double off = smpteTicksPerSecond > 0 ? n.end / smpteTicksPerSecond : seconds(tempos, n.end, ticksPerQuarter);
                length = Math.max(0.03, off - seconds);
            }
            events.add(new NoteEvent(seconds, instrument, rate, vol, Math.max(-1, Math.min(1, panValue)), length,
                    n.channel == DRUM_CHANNEL ? -1 : n.key));
        }
        events.sort(Comparator.comparingDouble(NoteEvent::seconds));
        return events;
    }

    /** A note-off: the oldest note still sounding on that key ends here. */
    private static void release(java.util.Map<Integer, java.util.ArrayDeque<RawNote>> sounding, int channel, int key, long tick) {
        java.util.ArrayDeque<RawNote> on = sounding.get(channel * 128 + key);
        RawNote note = on == null ? null : on.poll();
        if (note != null) {
            note.end = tick;
        }
    }

    /** Seconds from the start to {@code tick}, following every tempo change before it. */
    private static double seconds(TreeMap<Long, Integer> tempos, long tick, double ticksPerQuarter) {
        double total = 0;
        long previousTick = 0;
        int tempo = 500_000;
        for (var entry : tempos.headMap(tick, true).entrySet()) {
            total += (entry.getKey() - previousTick) * tempo / 1e6 / ticksPerQuarter;
            previousTick = entry.getKey();
            tempo = entry.getValue();
        }
        return total + (tick - previousTick) * tempo / 1e6 / ticksPerQuarter;
    }

    private static long readVarLength(ByteBuffer in) {
        long value = 0;
        int b;
        do {
            b = in.get() & 0xFF;
            value = (value << 7) | (b & 0x7F);
        } while ((b & 0x80) != 0);
        return value;
    }
}
