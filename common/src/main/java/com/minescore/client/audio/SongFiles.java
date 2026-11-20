package com.minescore.client.audio;

import com.minescore.audio.AdpcmCodec;
import com.minescore.MineScoreSettings;
import com.minescore.audio.FlacDecoder;
import com.minescore.audio.Loudness;
import com.minescore.audio.Mp3Decoder;
import com.minescore.audio.Track;
import com.minescore.audio.WavDecoder;
import com.minescore.audio.fx.Fx;
import com.minescore.audio.fx.SongAnalysis;
import com.minescore.meu.EffectChain;
import org.jetbrains.annotations.Nullable;
import com.minescore.audio.notes.MidiReader;
import com.minescore.audio.notes.NbsReader;
import com.minescore.song.SongMeta;
import com.minescore.song.SongTransfers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Every audio format MineScore reads, and turning a player's file into a song to share. */
public final class SongFiles {
    /** File types the Lathe lists and cuts. */
    public static final Set<String> CUTTABLE = Set.of("mp3", "ogg", "wav", "flac", "mid", "midi", "nbs");
    /** Formats shared as-is; everything else is rendered or converted to MineScore's ADPCM format. */
    private static final Set<String> SHARED_AS_IS = Set.of("mp3", "ogg", "flac");

    private SongFiles() {}

    /** A song ready to cut: its details, the exact bytes the server will store, and how loud it is. */
    public record Prepared(SongMeta meta, byte[] payload, Loudness loudness) {}

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The file name without its extension: a record's default title. */
    public static String title(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    public static Track decode(String extension, byte[] bytes) throws IOException {
        return switch (extension) {
            case "wav" -> WavDecoder.decode(bytes);
            case "ogg" -> OggDecoder.decode(bytes);
            case "mp3" -> Mp3Decoder.decode(bytes);
            case "flac" -> FlacDecoder.decode(bytes);
            case AdpcmCodec.EXTENSION -> AdpcmCodec.decode(bytes);
            case "mid", "midi" -> NoteRenderer.render(MidiReader.read(bytes));
            case "nbs" -> NoteRenderer.render(NbsReader.read(bytes));
            default -> throw new IOException("Unsupported file type ." + extension);
        };
    }

    /**
     * Decodes a file to check it plays and measure it, and picks what to upload. With a Multi-Effects Unit
     * linked to the Lathe, the song is run through its effects first, so they're printed onto the record.
     * Slow: call off-thread.
     */
    public static Prepared prepare(Path file, String title, @Nullable EffectChain effects) throws IOException {
        String extension = extension(file);
        byte[] bytes = Files.readAllBytes(file);
        Track track = decode(extension, bytes);
        boolean asIs = SHARED_AS_IS.contains(extension);
        if (effects != null && !effects.passesThrough()) {
            SongAnalysis analysis = SongAnalysis.of(track);
            track = EffectsSource.renderAll(track, effects, new Fx.Context(analysis.bpm(), analysis.tuningHz(), effects.expression(),
                    MineScoreSettings.effectsHighQuality.getAsBoolean()));
            asIs = false; // the printed song is new audio
        }
        byte[] payload = asIs ? bytes : AdpcmCodec.encode(track);
        String format = asIs ? extension : AdpcmCodec.EXTENSION;
        Loudness loudness = Loudness.measure(track, MineScoreSettings.lowSplit(), MineScoreSettings.highSplit());
        return new Prepared(new SongMeta(SongTransfers.sha256(payload), format, title, track.seconds()), payload, loudness);
    }
}
