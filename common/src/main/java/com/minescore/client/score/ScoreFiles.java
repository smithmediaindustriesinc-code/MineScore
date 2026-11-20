package com.minescore.client.score;

import com.minescore.MineScore;
import com.minescore.client.audio.NoteRenderer;
import com.minescore.network.MineScoreNetwork;
import com.minescore.score.Score;
import com.minescore.score.ScoreExport;
import com.minescore.score.ScoreImport;
import com.minescore.score.ScorePackets;
import com.minescore.score.ScorePlayback;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Importing and exporting scores from the editor (SCOPE.md §8.3, M10). Files to import come from
 * {@code blkcdsongs/}, {@code minescore/} or a file picker; exports go to {@code minescore/} or a folder picked
 * in the editor.
 */
public final class ScoreFiles {
    public static final Set<String> IMPORTABLE = Set.of("mid", "midi", "nbs", "minescore");

    /** What a score can be exported as. */
    public enum Format {
        MIDI("mid", "MIDI"), WAV("wav", "WAV"), NBS("nbs", "NBS"), MINESCORE("minescore", "MineScore");

        public final String extension, label;

        Format(String extension, String label) {
            this.extension = extension;
            this.label = label;
        }
    }

    private ScoreFiles() {}

    private static Path game() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    /** The folders import looks in: the Lathe's songs folder and MineScore's own. */
    public static List<Path> folders() {
        return List.of(game().resolve("blkcdsongs"), game().resolve("minescore"));
    }

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Every file in those folders that can be imported, newest first. */
    public static List<Path> importable() {
        List<Path> found = new ArrayList<>();
        for (Path folder : folders()) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(f -> Files.isRegularFile(f) && IMPORTABLE.contains(extension(f))).forEach(found::add);
            } catch (IOException e) {
                MineScore.LOGGER.warn("Couldn't list {}", folder, e);
            }
        }
        found.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return found;
    }

    /** A file read as a score. */
    public static Score read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return switch (extension(file)) {
            case "mid", "midi" -> ScoreImport.fromMidi(bytes, title);
            case "nbs" -> ScoreImport.fromNbs(bytes, title);
            case "minescore" -> ScoreImport.fromMineScore(bytes);
            default -> throw new IOException("MineScore can't import ." + extension(file) + " files");
        };
    }

    /** Sends an imported score up to replace the desk's (keeping the old one as a copy first, if asked). */
    public static void replace(BlockPos desk, Score score, boolean copyFirst) throws IOException {
        byte[] data = ScoreExport.toMineScore(score);
        int total = Math.max(1, (data.length + ScorePackets.UPLOAD_CHUNK - 1) / ScorePackets.UPLOAD_CHUNK);
        if (total > 256) {
            throw new IOException("It's too big to send");
        }
        for (int i = 0; i < total; i++) {
            int from = i * ScorePackets.UPLOAD_CHUNK;
            byte[] chunk = java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + ScorePackets.UPLOAD_CHUNK));
            MineScoreNetwork.sendToServer(new ScorePackets.Replace(desk, i, total, chunk, copyFirst));
        }
    }

    /** Writes a score out (slow for WAV: call off-thread); returns the file it made. */
    public static Path export(Score score, Format format) throws IOException {
        InstrumentPacks.reload();
        byte[] data = switch (format) {
            case MINESCORE -> ScoreExport.toMineScore(score);
            case MIDI -> ScoreExport.toMidi(score, ScorePlayback.render(score).events());
            case NBS -> ScoreExport.toNbs(score, ScorePlayback.render(score).events());
            case WAV -> ScoreExport.toWav(NoteRenderer.render(ScorePlayback.render(score).events()));
        };
        Path folder = EditorPrefs.exportFolder();
        Files.createDirectories(folder);
        String base = (score.title.isBlank() ? "Untitled Score" : score.title).replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        Path file = folder.resolve(base + "." + format.extension);
        for (int n = 2; Files.exists(file); n++) { // never overwrite an earlier export
            file = folder.resolve(base + " (" + n + ")." + format.extension);
        }
        Files.write(file, data);
        return file;
    }

    /** The system's open-file dialog for a file to import (it runs off the game's thread); null if cancelled. */
    public static CompletableFuture<@Nullable Path> pickFile() {
        String start = folders().get(1).toAbsolutePath() + java.io.File.separator;
        return CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = stack.mallocPointer(4);
                for (String p : new String[] {"*.mid", "*.midi", "*.nbs", "*.minescore"}) {
                    patterns.put(stack.UTF8(p));
                }
                patterns.flip();
                String picked = TinyFileDialogs.tinyfd_openFileDialog("Import a score", start, patterns, "MIDI, Note Block Studio or MineScore", false);
                return picked == null ? null : Path.of(picked);
            }
        });
    }

    /** The system's folder dialog, for where exports go; null if cancelled. */
    public static CompletableFuture<@Nullable Path> pickFolder() {
        String start = EditorPrefs.exportFolder().toAbsolutePath().toString();
        return CompletableFuture.supplyAsync(() -> {
            String picked = TinyFileDialogs.tinyfd_selectFolderDialog("Export scores to", start);
            return picked == null ? null : Path.of(picked);
        });
    }
}
