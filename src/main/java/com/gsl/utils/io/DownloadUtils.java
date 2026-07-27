package com.gsl.utils.io;

import com.gsl.utils.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Waits for a browser download to finish landing in a directory. New infrastructure — no
 * download-handling utility existed anywhere in this framework before Account Class's
 * Export-to-Excel test needed one.
 */
public final class DownloadUtils {

    private DownloadUtils() {}

    /**
     * Polls {@code dir} for a file whose name contains {@code filenameContains} (case-insensitive)
     * and does not end in {@code .crdownload}/{@code .tmp} (still-downloading marker extensions),
     * then confirms its size is stable across two consecutive checks before returning it.
     *
     * @throws IllegalStateException if no such file appears within {@code timeoutSeconds}
     */
    public static Path waitForDownloadedFile(Path dir, String filenameContains, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Path candidate = null;

        while (System.currentTimeMillis() < deadline) {
            candidate = findCandidate(dir, filenameContains);
            if (candidate != null && isStable(candidate)) {
                Log.info("[DownloadUtils] Download stable: {}", candidate);
                return candidate;
            }
            sleep(500);
        }

        throw new IllegalStateException("No stable download matching '" + filenameContains
                + "' appeared in '" + dir + "' within " + timeoutSeconds + "s"
                + (candidate != null ? " (last seen: " + candidate + ", still growing/partial)" : ""));
    }

    private static Path findCandidate(Path dir, String filenameContains) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            Path best = null;
            long bestModified = -1;
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.endsWith(".crdownload") || name.endsWith(".tmp")) {
                    continue;
                }
                if (!name.toLowerCase().contains(filenameContains.toLowerCase())) {
                    continue;
                }
                long modified = Files.getLastModifiedTime(p).toMillis();
                if (modified > bestModified) {
                    best = p;
                    bestModified = modified;
                }
            }
            return best;
        } catch (IOException e) {
            Log.warn("[DownloadUtils] Error listing '{}': {}", dir, e.getMessage());
            return null;
        }
    }

    private static boolean isStable(Path file) {
        try {
            long size1 = Files.size(file);
            sleep(300);
            long size2 = Files.size(file);
            return size1 == size2 && size1 > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes every file directly inside {@code dir} (not the directory itself). */
    public static void cleanDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            Log.warn("[DownloadUtils] Error cleaning '{}': {}", dir, e.getMessage());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
