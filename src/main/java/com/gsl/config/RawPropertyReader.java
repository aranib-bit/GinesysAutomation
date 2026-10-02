package com.gsl.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads individual property values from config.properties WITHOUT applying
 * Java's backslash-escape rules (which turn \t → TAB, \n → newline, \G → G etc.).
 *
 * <p>Use this instead of the Owner proxy for properties that contain Windows file paths,
 * where backslashes must be preserved verbatim (e.g. remote.*.log.paths).
 */
public final class RawPropertyReader {

    private static final Path CONFIG_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "test", "resources", "config");

    private RawPropertyReader() {}

    /** Active environment's copy of {@code filename} when it exists, otherwise the shared one in config/. */
    private static Path locate(String filename) {
        Path envFile = EnvResolver.envDir().resolve(filename);
        return Files.isRegularFile(envFile) ? envFile : CONFIG_DIR.resolve(filename);
    }

    /**
     * Returns the raw string value for {@code key} from config.properties,
     * or {@code fallback} if the key is absent or the file cannot be read.
     *
     * <p>Backslash sequences are NOT interpreted — the caller receives the exact
     * characters that appear in the file.
     */
    public static String readRaw(String key, String fallback) {
        // Active environment's file first, then the shared one — per key, like the Owner MERGE sources.
        for (Path file : new Path[]{EnvResolver.envDir().resolve("config.properties"), CONFIG_DIR.resolve("config.properties")}) {
            if (!Files.isRegularFile(file)) continue;
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String value = extractValue(line, key);
                    if (value != null) return value;
                }
            } catch (IOException ignored) {
                // File unreadable — try the next one, then the caller's fallback
            }
        }
        return fallback;
    }

    /**
     * Reads all property values from {@code filename} (resolved relative to the same
     * directory as config.properties) in declaration order, preserving backslashes verbatim.
     * Comment lines ({@code #}, {@code !}) and blank lines are skipped.
     * Returns an empty list if the file is absent or unreadable.
     */
    public static List<String> readAllRawValues(String filename) {
        Path file = locate(filename);
        List<String> values = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') continue;
                int sep = indexOfSeparator(trimmed);
                if (sep < 0) continue;
                String value = trimmed.substring(sep + 1).trim();
                if (!value.isBlank()) values.add(value);
            }
        } catch (IOException ignored) {}
        return values;
    }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * If {@code line} sets {@code key}, returns its raw value; otherwise null.
     * Handles both {@code key=value} and {@code key = value} forms.
     * Comment lines (starting with {@code #} or {@code !}) are skipped.
     */
    private static String extractValue(String line, String key) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') return null;

        // Accept both '=' and ':' as separators (Java Properties spec)
        int sep = indexOfSeparator(trimmed);
        if (sep < 0) return null;

        String k = trimmed.substring(0, sep).trim();
        if (!k.equals(key)) return null;

        return trimmed.substring(sep + 1).trim();
    }

    /** Index of the earlier of {@code '='} or {@code ':'} in {@code line}, or -1 if neither is present. */
    private static int indexOfSeparator(String line) {
        int eq    = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0 && colon < 0) return -1;
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }
}
