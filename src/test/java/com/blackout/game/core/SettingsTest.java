package com.blackout.game.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaultsMatchTheControlsDocument() {
        InputMap m = new InputMap();
        assertEquals(KeyCode.W, m.get(Action.FORWARD, 0));
        assertEquals(KeyCode.M, m.get(Action.MAP, 0));
        assertTrue(m.matches(Action.INTERACT, KeyCode.E));      // one interact key, as in CONTROLS.md
        assertTrue(m.matches(Action.INTERACT, KeyCode.F));      // the old door key still works
        assertTrue(m.down(Action.SPRINT, EnumSet.of(KeyCode.SHIFT)));
        assertFalse(m.down(Action.SPRINT, Set.of()));
    }

    @Test
    void rebindingStealsTheKeyFromTheOldAction() {
        InputMap m = new InputMap();
        assertTrue(m.rebind(Action.MAP, 0, KeyCode.J));
        assertTrue(m.matches(Action.MAP, KeyCode.J));
        assertFalse(m.matches(Action.MAP, KeyCode.M));
        assertTrue(m.rebind(Action.CROUCH, 0, KeyCode.J));      // J now belongs to crouch only
        assertFalse(m.matches(Action.MAP, KeyCode.J));
        assertNull(m.get(Action.MAP, 0));
    }

    @Test
    void reservedKeysCannotBeBound() {
        InputMap m = new InputMap();
        assertFalse(m.rebind(Action.MAP, 0, KeyCode.ESCAPE));
        assertFalse(m.rebind(Action.MAP, 0, KeyCode.DIGIT3));
        assertTrue(m.matches(Action.MAP, KeyCode.M));
    }

    @Test
    void settingsSurviveSaveAndLoad() {
        Settings s = new Settings(dir);
        s.quality = Settings.Quality.LOW;
        s.volume = 0.25;
        s.sensitivity = 1.7;
        s.fovScale = 1.1;
        s.fullscreen = false;
        s.input.rebind(Action.MAP, 0, KeyCode.N);
        s.input.clear(Action.INTERACT, 1);
        s.save();
        Settings t = new Settings(dir);
        t.load();
        assertEquals(Settings.Quality.LOW, t.quality);
        assertEquals(0.25, t.volume);
        assertEquals(1.7, t.sensitivity);
        assertEquals(1.1, t.fovScale);
        assertFalse(t.fullscreen);
        assertTrue(t.input.matches(Action.MAP, KeyCode.N));
        assertNull(t.input.get(Action.INTERACT, 1));
    }

    @Test
    void badSettingsFileFallsBackToSafeValues() throws IOException {
        Files.writeString(dir.resolve("settings.properties"),
                "quality=ULTRA\nvolume=abc\nsensitivity=9999\nfovScale=-5\nbind.MAP.0=NOT_A_KEY\nbind.JUMP.0=ESCAPE\n");
        Settings s = new Settings(dir);
        s.load();
        assertEquals(Settings.Quality.HIGH, s.quality);
        assertEquals(0.8, s.volume);
        assertEquals(2.5, s.sensitivity);
        assertEquals(0.8, s.fovScale);
        assertTrue(s.input.matches(Action.MAP, KeyCode.M));
        assertTrue(s.input.matches(Action.JUMP, KeyCode.SPACE));
    }

    @Test
    void oldSettingsFileKeepsNewInteractDefaults() throws IOException {
        // files written before the unified interact key had F = door and E = sleep, with no version line
        Files.writeString(dir.resolve("settings.properties"), "volume=0.5\nbind.INTERACT.0=F\nbind.INTERACT.1=NONE\nbind.SLEEP.0=E\n");
        Settings s = new Settings(dir);
        s.load();
        assertEquals(0.5, s.volume);                            // other settings are still read
        assertTrue(s.input.matches(Action.INTERACT, KeyCode.E)); // bindings are reset to the new defaults
        assertTrue(s.input.matches(Action.INTERACT, KeyCode.F));
    }
}
