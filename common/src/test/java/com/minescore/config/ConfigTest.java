package com.minescore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.MineScoreSettings;
import com.minescore.power.PowerSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The config menu's settings (SCOPE.md §9): values stay in range, old files move over, presets and imports apply. */
class ConfigTest {
    @AfterEach
    void defaults() {
        MineScoreConfig.all().forEach(Setting::reset);
    }

    @Test
    void valuesStayInTheirRange() {
        assertTrue(MineScoreConfig.SYSTEM_CAP.decode("-5"));
        assertEquals(1, MineScoreConfig.SYSTEM_CAP.integer(), "clamped up to its least");
        assertFalse(MineScoreConfig.BLOWOUTS.decode("maybe"), "a toggle takes only true or false");
        assertTrue(MineScoreConfig.BLOWOUTS.bool());
        assertTrue(MineScoreConfig.SPEED_ALGORITHM.decode("tape"));
        assertEquals("TAPE", MineScoreConfig.SPEED_ALGORITHM.text(), "choices match whatever the case");
        assertFalse(MineScoreConfig.POWER_MODE.decode("solar"));
        assertEquals("none", MineScoreConfig.POWER_MODE.next(false));
    }

    @Test
    void theOldForgeFilesMoveOverOnce(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("minescore-common.toml"), """
                #How far a speaker at 100% volume is heard
                speakerRange = 48.0
                [power]
                \tmode = "NONE"
                \tsystemCap = 750
                [create]
                \tmaxRpm = 96
                """);
        Files.writeString(dir.resolve("minescore-client.toml"), "invisibleWires = true\nspeedAlgorithm = \"PHASE_VOCODER\"\n");
        MineScoreConfig.load(dir);
        assertEquals(48, MineScoreSettings.hearingRange(), 1e-9);
        assertFalse(PowerSettings.energyMode());
        assertEquals(750, PowerSettings.systemCap.getAsInt());
        assertEquals(96, MineScoreConfig.CREATE_MAX_RPM.integer());
        assertTrue(MineScoreSettings.invisibleWires.getAsBoolean());
        assertEquals("PHASE_VOCODER", MineScoreSettings.speedAlgorithm.get());
        assertTrue(Files.isRegularFile(dir.resolve("minescore-server.json")));
        assertTrue(Files.isRegularFile(dir.resolve("minescore-client.json")));

        MineScoreConfig.all().forEach(Setting::reset);
        MineScoreConfig.load(dir); // the new files win from now on
        assertEquals(750, PowerSettings.systemCap.getAsInt());
    }

    @Test
    void exportsImportAndPresetsApply() {
        MineScoreConfig.SPEAKER_RANGE.decode("64");
        MineScoreConfig.ROOM_SOUND.decode("false");
        Map<String, String> exported = MineScoreConfig.fromJson(MineScoreConfig.toJson());
        MineScoreConfig.all().forEach(Setting::reset);
        assertTrue(MineScoreConfig.apply(exported, null));
        assertEquals(64, MineScoreConfig.SPEAKER_RANGE.number(), 1e-9);
        assertFalse(MineScoreConfig.ROOM_SOUND.bool());

        assertFalse(MineScoreConfig.apply(Map.of("speakerRange", "16"), Setting.Side.CLIENT), "a server setting isn't changed as a client one");
        MineScoreConfig.Preset defaults = MineScoreConfig.builtInPresets().get(0);
        MineScoreConfig.apply(defaults.values(), null);
        assertTrue(MineScoreConfig.SPEAKER_RANGE.isDefault() && MineScoreConfig.ROOM_SOUND.isDefault(), "Defaults puts everything back");
    }
}
