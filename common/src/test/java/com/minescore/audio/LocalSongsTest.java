package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Decodes whatever MP3, WAV and FLAC files the dev client's blkcdsongs folder has. Skipped if none. */
class LocalSongsTest {
    @Test
    void decodesRealSongs() throws IOException {
        Path dir = Path.of(System.getProperty("minescore.songs", "blkcdsongs"));
        assumeTrue(Files.isDirectory(dir), "no blkcdsongs folder");
        List<Path> songs;
        try (Stream<Path> list = Files.list(dir)) {
            songs = list.filter(p -> p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(mp3|wav|flac)$")).toList();
        }
        assumeTrue(!songs.isEmpty(), "no songs to try");
        for (Path song : songs) {
            byte[] bytes = Files.readAllBytes(song);
            String name = song.getFileName().toString().toLowerCase(Locale.ROOT);
            long start = System.nanoTime();
            Track track = name.endsWith(".mp3") ? Mp3Decoder.decode(bytes)
                    : name.endsWith(".flac") ? FlacDecoder.decode(bytes) : WavDecoder.decode(bytes);
            byte[] shared = AdpcmCodec.encode(track);
            double took = (System.nanoTime() - start) / 1e9;
            System.out.printf("%s: %.1f s of audio, %d MB file, shared as %.1f MB, decoded+packed in %.1f s%n",
                    song.getFileName(), track.seconds(), bytes.length >> 20, shared.length / 1048576.0, took);
            // A real song's length should roughly match its size (at least 32 kbps, at most CD-quality WAV).
            double minSeconds = bytes.length / (name.endsWith(".mp3") ? 40_000.0 : 200_000.0);
            assertTrue(track.seconds() > minSeconds, song.getFileName() + " decoded to only " + track.seconds() + " s");
        }
    }
}
