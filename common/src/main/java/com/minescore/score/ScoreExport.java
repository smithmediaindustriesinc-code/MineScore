package com.minescore.score;

import com.minescore.audio.Track;
import com.minescore.audio.notes.NoteEvent;
import com.minescore.audio.notes.NoteInstrument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtIo;

/**
 * Writes a score out as a file (SCOPE.md §9.12, M10): a MIDI file, a Note Block Studio song or a MineScore
 * file from the score itself; a WAV file from its rendered audio. MIDI and Note Block Studio are written
 * from what playback plays, so repeats, ties, ornaments and dynamics come out as heard.
 */
public final class ScoreExport {
    private static final int TICKS_PER_QUARTER = 480;
    private static final int DRUM_CHANNEL = 9;

    private ScoreExport() {}

    /** The score as it's saved: everything, readable by MineScore's import. */
    public static byte[] toMineScore(Score score) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(score.save(), out);
        return out.toByteArray();
    }

    /** A General MIDI program for each note block instrument, the closest sound it has. */
    private static int program(NoteInstrument instrument) {
        return switch (instrument) {
            case BASS -> 32;
            case GUITAR -> 24;
            case FLUTE -> 73;
            case BELL -> 14;
            case CHIME -> 112;
            case XYLOPHONE -> 13;
            case IRON_XYLOPHONE -> 11;
            case COW_BELL -> 113;
            case DIDGERIDOO -> 109;
            case BIT -> 80;
            case BANJO -> 105;
            case PLING -> 88;
            default -> 46; // harp
        };
    }

    private static int drumKey(NoteInstrument drum) {
        return switch (drum) {
            case BASEDRUM -> 36;
            case HAT -> 42;
            default -> 38;
        };
    }

    /** A standard MIDI file (format 0) of what playback plays: a channel per instrument, drums on channel 10. */
    public static byte[] toMidi(Score score, List<NoteEvent> events) {
        record Message(long tick, int order, byte[] bytes) {}
        List<Message> messages = new ArrayList<>();
        Map<String, Integer> channels = new HashMap<>();
        double ticksPerSecond = score.tempo / 60 * TICKS_PER_QUARTER;
        int next = 0;
        for (NoteEvent e : events) {
            boolean drum = e.midi() < 0;
            String key = e.custom() != null ? e.custom() : e.instrument().name();
            int channel;
            if (drum) {
                channel = DRUM_CHANNEL;
            } else if (channels.containsKey(key)) {
                channel = channels.get(key);
            } else {
                if (next == DRUM_CHANNEL) {
                    next++;
                }
                channel = Math.min(15, next++);
                channels.put(key, channel);
                messages.add(new Message(0, 0, new byte[] {(byte) (0xC0 | channel), (byte) (e.custom() != null ? 46 : program(e.instrument()))}));
            }
            int note = drum ? drumKey(e.instrument()) : Math.max(0, Math.min(127, e.midi()));
            int velocity = Math.max(1, Math.min(127, Math.round(e.volume() * 127)));
            long on = Math.round(e.seconds() * ticksPerSecond);
            double length = e.length() > 0 ? e.length() : drum ? 0.1 : 0.25;
            long off = Math.max(on + 1, Math.round((e.seconds() + length) * ticksPerSecond));
            messages.add(new Message(on, 2, new byte[] {(byte) (0x90 | channel), (byte) note, (byte) velocity}));
            messages.add(new Message(off, 1, new byte[] {(byte) (0x80 | channel), (byte) note, 0}));
        }
        messages.sort(Comparator.comparingLong(Message::tick).thenComparingInt(Message::order)); // note-offs before note-ons
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        int microseconds = (int) Math.round(60e6 / score.tempo);
        writeVar(track, 0);
        track.writeBytes(new byte[] {(byte) 0xFF, 0x51, 3, (byte) (microseconds >> 16), (byte) (microseconds >> 8), (byte) microseconds});
        writeVar(track, 0);
        int unitPower = Integer.numberOfTrailingZeros(score.time.unit());
        track.writeBytes(new byte[] {(byte) 0xFF, 0x58, 4, (byte) score.time.beats(), (byte) unitPower, 24, 8});
        writeVar(track, 0);
        track.writeBytes(new byte[] {(byte) 0xFF, 0x59, 2, (byte) score.key, 0});
        byte[] title = (score.title.isBlank() ? "Untitled Score" : score.title).getBytes(StandardCharsets.UTF_8);
        writeVar(track, 0);
        track.writeBytes(new byte[] {(byte) 0xFF, 0x03});
        writeVar(track, title.length);
        track.writeBytes(title);
        long last = 0;
        for (Message m : messages) {
            writeVar(track, m.tick() - last);
            track.writeBytes(m.bytes());
            last = m.tick();
        }
        writeVar(track, 0);
        track.writeBytes(new byte[] {(byte) 0xFF, 0x2F, 0});
        ByteBuffer out = ByteBuffer.allocate(14 + 8 + track.size());
        out.putInt(0x4D546864).putInt(6).putShort((short) 0).putShort((short) 1).putShort((short) TICKS_PER_QUARTER);
        out.putInt(0x4D54726B).putInt(track.size()).put(track.toByteArray());
        return out.array();
    }

    private static void writeVar(ByteArrayOutputStream out, long value) {
        int buffer = (int) (value & 0x7F);
        while ((value >>= 7) > 0) {
            buffer <<= 8;
            buffer |= (int) ((value & 0x7F) | 0x80);
        }
        while (true) {
            out.write(buffer & 0xFF);
            if ((buffer & 0x80) == 0) {
                break;
            }
            buffer >>>= 8;
        }
    }

    /**
     * A Note Block Studio song (version 5) of what playback plays: a tick per 16th note at the score's tempo,
     * note block instruments as they are (pack instruments as harp), notes outside the note block's range
     * kept at their pitch.
     */
    public static byte[] toNbs(Score score, List<NoteEvent> events) {
        double ticksPerSecond = score.tempo / 60 * 4;
        record Hit(int tick, int instrument, int key, int velocity, int panning) {}
        List<Hit> hits = new ArrayList<>();
        for (NoteEvent e : events) {
            int tick = (int) Math.round(e.seconds() * ticksPerSecond);
            int key = e.midi() < 0 ? 45 : Math.max(0, Math.min(87, e.midi() - 21));
            int instrument = e.custom() != null ? NoteInstrument.HARP.ordinal() : e.instrument().ordinal();
            hits.add(new Hit(tick, instrument, key, Math.max(0, Math.min(100, Math.round(e.volume() * 100))),
                    Math.max(0, Math.min(200, Math.round(100 + e.pan() * 100)))));
        }
        hits.sort(Comparator.comparingInt(Hit::tick));
        int length = hits.isEmpty() ? 0 : hits.get(hits.size() - 1).tick() + 1;
        // every note at the same tick needs its own layer
        int layers = 1;
        for (int i = 0, run = 0; i < hits.size(); i++) {
            run = i > 0 && hits.get(i).tick() == hits.get(i - 1).tick() ? run + 1 : 1;
            layers = Math.max(layers, run);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int lastTick = -1;
        for (int i = 0; i < hits.size(); ) {
            int tick = hits.get(i).tick();
            writeShort(body, tick - lastTick);
            lastTick = tick;
            int lastLayer = -1, layer = 0;
            for (; i < hits.size() && hits.get(i).tick() == tick; i++, layer++) {
                Hit h = hits.get(i);
                writeShort(body, layer - lastLayer);
                lastLayer = layer;
                body.write(h.instrument());
                body.write(h.key());
                body.write(h.velocity());
                body.write(h.panning());
                writeShort(body, 0); // fine pitch
            }
            writeShort(body, 0);
        }
        writeShort(body, 0);
        for (int l = 0; l < layers; l++) {
            writeString(body, "");
            body.write(0); // not locked
            body.write(100); // volume
            body.write(100); // centred
        }
        body.write(0); // no custom instruments

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShort(out, 0); // the new format
        out.write(5); // version
        out.write(16); // vanilla instruments
        writeShort(out, length);
        writeShort(out, layers);
        writeString(out, score.title);
        writeString(out, score.composer);
        writeString(out, "");
        writeString(out, "Exported from MineScore");
        writeShort(out, (int) Math.round(ticksPerSecond * 100));
        out.write(0); // auto-saving off
        out.write(10);
        out.write(Math.max(1, Math.min(32, (int) Math.round(score.time.length().toDouble() * 4)))); // quarter notes a measure
        for (int i = 0; i < 5; i++) {
            writeInt(out, 0); // minutes spent, clicks, blocks added and removed
        }
        writeString(out, "");
        out.write(0); // no loop
        out.write(0);
        writeShort(out, 0);
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v);
        out.writeBytes(b.array());
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    /** 16-bit stereo WAV at 44.1 kHz. */
    public static byte[] toWav(Track track) {
        int frames = track.frames();
        ByteBuffer out = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(36 + frames * 4).putInt(0x45564157); // "RIFF", size, "WAVE"
        out.putInt(0x20746D66).putInt(16).putShort((short) 1).putShort((short) 2).putInt(Track.SAMPLE_RATE)
                .putInt(Track.SAMPLE_RATE * 4).putShort((short) 4).putShort((short) 16); // "fmt "
        out.putInt(0x61746164).putInt(frames * 4); // "data"
        for (int i = 0; i < frames; i++) {
            out.putShort((short) Math.round(Math.max(-1, Math.min(1, track.left(i))) * 32767));
            out.putShort((short) Math.round(Math.max(-1, Math.min(1, track.right(i))) * 32767));
        }
        return out.array();
    }
}
