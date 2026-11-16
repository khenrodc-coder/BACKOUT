package com.blackout.game.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Where BLACKOUT keeps its data, and crash-safe file writing. */
public final class FileUtil {

    private FileUtil() {
    }

    /** {@code ~/.blackout} (override with {@code -Dblackout.home=...}). */
    public static Path dataDir() {
        String base = System.getProperty("blackout.home", System.getProperty("user.home", "."));
        return Path.of(base, ".blackout");
    }

    /** Writes to a temporary file first, then replaces the target, so a crash never leaves a half-written file. */
    public static void atomicWrite(Path target, byte[] data) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
