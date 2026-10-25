package com.gsl.reports;

import com.gsl.constants.FrameworkConstants;
import com.gsl.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bundles this run's audience-segmented log files into a single {@code reports/runs/<runId>/logs.zip},
 * alongside that run's own report — so a run's logs become as self-contained an artifact as its
 * report already is (see {@link FrameworkConstants#getRunReportDir()}'s own "each run is fully
 * self-contained" Javadoc).
 *
 * <p>Scopes each archived file to "bytes appended during this run" via a byte-offset snapshot
 * rather than relying on Log4j2's own rollover — {@code OnStartupTriggeringPolicy} was tried first
 * and reverted: this project runs as a single long-lived, multi-threaded JVM per suite (not one
 * JVM per run), and the policy fired repeatedly WITHIN one run (confirmed live: 30 rollovers of
 * {@code framework.log} in the last 10 minutes of one run, reports/runs/20260917_005651) — most
 * likely clashing with this config's own {@code monitorInterval="30"} reload — which meant the zip
 * only ever captured the last fragment before a run, not the whole thing. This class instead
 * records each file's size in {@link #markRunStart()} (called from {@code
 * TestListener#onStart(ISuite)}) and only copies bytes at-or-after that offset in {@link
 * #archiveRunLogs()} (called from {@code TestListener#onFinish(ISuite)}) — correct regardless of
 * whether Log4j2's own size/time rollover fires during the run, and with zero effect on that
 * rollover's own behavior or retention policy.
 *
 * <p>A missing/unreadable log file is skipped with a warning rather than failing the whole
 * archive — losing one file's contribution to a diagnostic bundle shouldn't take the others down.
 */
public final class LogArchiver {

    private LogArchiver() {}

    private static final String[] LOG_FILE_NAMES = {
            "business.log", "ui-actions.log", "db-validation.log",
            "diagnostics.log", "framework.log", "trace.log"
    };

    private static final Map<String, Long> runStartOffsets = new HashMap<>();
    private static long runStartMillis;

    /** Records each live log file's current size — call once, at the true start of a run
     *  ({@code TestListener#onStart(ISuite)}), before this run writes a single line. */
    public static void markRunStart() {
        Path logsDir = Paths.get(System.getProperty("user.dir"), "logs");
        runStartOffsets.clear();
        runStartMillis = System.currentTimeMillis();
        for (String fileName : LOG_FILE_NAMES) {
            Path source = logsDir.resolve(fileName);
            long size = Files.isRegularFile(source) ? source.toFile().length() : 0L;
            runStartOffsets.put(fileName, size);
        }
    }

    /**
     * Log4j2 size rollover moves older parts of a log into {@code <name>-<date>-<n>.log.gz}; the live file then holds
     * only the tail. Every rolled part written since the run started belongs to this run, so it goes into the zip
     * under {@code rolled/} (unchanged, still gzipped).
     */
    private static void addRolledFiles(ZipOutputStream zos, Path logsDir) {
        if (runStartMillis <= 0 || !Files.isDirectory(logsDir)) return;
        try (java.util.stream.Stream<Path> files = Files.list(logsDir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".log.gz"))
                    .filter(f -> f.toFile().lastModified() >= runStartMillis)
                    .sorted(java.util.Comparator.comparingLong(f -> f.toFile().lastModified()))
                    .toList()) {
                zos.putNextEntry(new ZipEntry("rolled/" + p.getFileName()));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            Log.warn("[LogArchiver] Rolled log files not added — {}", e.getMessage());
        }
    }

    public static void archiveRunLogs() {
        Path logsDir = Paths.get(System.getProperty("user.dir"), "logs");
        Path zipPath = Paths.get(FrameworkConstants.getRunReportDir(), "logs.zip");

        try {
            Files.createDirectories(zipPath.getParent());
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (String fileName : LOG_FILE_NAMES) {
                    Path source = logsDir.resolve(fileName);
                    if (!Files.isRegularFile(source)) {
                        Log.warn("[LogArchiver] Skipping '{}' — not found under {}", fileName, logsDir);
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(fileName));
                    try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
                        long size = channel.size();
                        long recordedOffset = runStartOffsets.getOrDefault(fileName, 0L);
                        // If the file is now SMALLER than the offset recorded at run start, Log4j2's
                        // own size/time rollover fired mid-run and this is a fresh post-rollover
                        // file — the whole thing belongs to this run, so start from 0 instead of an
                        // offset that no longer means anything for this file.
                        long startAt = recordedOffset <= size ? recordedOffset : 0L;
                        channel.position(startAt);
                        try (InputStream in = Channels.newInputStream(channel)) {
                            in.transferTo(zos);
                        }
                    }
                    zos.closeEntry();
                }
                addRolledFiles(zos, logsDir);
            }
            Log.info("[LogArchiver] Archived this run's logs to {}", zipPath);
        } catch (IOException e) {
            Log.warn("[LogArchiver] Failed to archive run logs to {} — {}", zipPath, e.getMessage());
        } finally {
            runStartOffsets.clear();
            runStartMillis = 0;
        }
    }
}
