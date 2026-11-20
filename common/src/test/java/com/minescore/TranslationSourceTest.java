package com.minescore;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.client.score.EditorKeys;
import com.minescore.client.score.EditorPalettes;
import com.minescore.client.score.EditorPrefs;
import com.minescore.client.score.ScoreElement;
import com.minescore.client.score.ScoreFiles;
import com.minescore.config.MineScoreConfig;
import com.minescore.config.Setting;
import com.minescore.score.Instruments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Lists the English the mod keeps as data and shows through {@link Tr} (setting names and help, palette
 * items, key bindings, instruments...) into build/minescore-text.json (with the key {@link Tr#key} gives each), for tools/gen_resources.py to translate.
 * Literal Tr.s, Tr.c and Tr.en text in the code it finds by itself.
 */
class TranslationSourceTest {
    @Test
    void listEnglishText() throws Exception {
        Set<String> text = new TreeSet<>();
        for (MineScoreConfig.Category c : MineScoreConfig.Category.values()) {
            text.add(c.label);
        }
        for (Setting s : MineScoreConfig.all()) {
            text.add(s.label);
            text.add(s.help);
            if (!s.unit.isEmpty()) {
                text.add(s.unit);
            }
            for (String choice : s.choices()) { // as ConfigScreen.choiceLabel shows them
                String spaced = choice.replace('_', ' ').toLowerCase(Locale.ROOT);
                text.add(choice.equals("WSOLA") ? "WSOLA" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1));
            }
        }
        for (EditorPalettes.Category c : EditorPalettes.CATEGORIES) {
            text.add(c.name());
            c.items().forEach(i -> text.add(i.english()));
        }
        for (EditorKeys.Action a : EditorKeys.Action.values()) {
            text.add(a.label);
        }
        for (EditorPrefs.ArrowSelect a : EditorPrefs.ArrowSelect.values()) {
            text.add(a.label);
        }
        for (ScoreElement.Kind k : ScoreElement.Kind.values()) {
            text.add(k.label);
        }
        for (ScoreFiles.Format f : ScoreFiles.Format.values()) {
            text.add(f.label);
        }
        for (NoteInstrument i : NoteInstrument.values()) {
            text.add(Instruments.englishName(i));
        }
        text.removeIf(String::isBlank);
        Path out = Path.of("build", "minescore-text.json");
        Files.createDirectories(out.getParent());
        java.util.Map<String, String> keys = new java.util.TreeMap<>(); // with the key Tr gives each, so Python can check its own
        text.forEach(t -> keys.put(t, Tr.key(t)));
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(keys));
        assertTrue(text.size() > 200, "found " + text.size());
    }
}
