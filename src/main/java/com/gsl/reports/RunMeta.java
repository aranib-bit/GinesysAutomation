package com.gsl.reports;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Run-level facts for the coverage workbook's Dashboard and Notes sheets: where/when/how the run
 * executed. Captured from {@code TestListener.onStart(ISuite)} ({@link #begin}) and completed in
 * {@code onFinish(ISuite)} ({@link #finish}); every lookup is best-effort - a missing value is
 * blank, never an exception.
 */
@Value
@Builder(toBuilder = true)
public class RunMeta {

    @Builder.Default String runId = "";
    @Builder.Default String env = "";
    @Builder.Default String browser = "";
    @Builder.Default String branch = "";
    @Builder.Default String user = "";
    @Builder.Default String machine = "";
    /** TestNG suite name. */
    @Builder.Default String suiteName = "";
    /** Suite XML file name (stands in for the Maven profile: {@code testng-gv-ui.xml}); may be blank. */
    @Builder.Default String suiteFile = "";
    /** OS of the Grid node ({@code linux}/{@code windows}); filled in {@link #finish}. */
    @Builder.Default String targetOs = "";
    /** Distinct {@code browser version / platform (driver x)} combinations the run actually used; filled in {@link #finish}, blank if no session was created. */
    @Builder.Default List<String> browserVersions = List.of();
    /** Concurrent-session cap in effect (e.g. {@code max.concurrent.sessions.windows=2}); blank = none. */
    @Builder.Default String sessionCap = "";
    /** Non-empty when one browser ran on more than one major version — the run mixes builds. */
    @Builder.Default String versionDrift = "";
    Instant start;
    Instant end;
    /** Human-readable active group/tag filters, e.g. {@code gv.group=MANDATORY}; empty = none. */
    @Builder.Default List<String> activeFilters = List.of();

    /** Wall-clock duration, or {@code null} when either end is unknown. */
    public Duration wall() {
        return start == null || end == null ? null : Duration.between(start, end);
    }

    /** Wall-clock seconds, or {@code 0} when unknown. */
    public double wallSeconds() {
        Duration d = wall();
        return d == null ? 0 : d.toMillis() / 1000.0;
    }

    private static volatile RunMeta current;

    /** Starts the run record (call from {@code ISuiteListener.onStart}); never throws. */
    public static void begin(String suiteName, String suiteFile, String env, String browser,
                             List<String> activeFilters) {
        try {
            current = RunMeta.builder().suiteName(nz(suiteName)).suiteFile(nz(suiteFile)).env(nz(env))
                    .browser(nz(browser)).branch(detectBranch()).user(nz(System.getProperty("user.name")))
                    .machine(nz(firstNonBlank(System.getenv("COMPUTERNAME"), System.getenv("HOSTNAME"))))
                    .start(Instant.now()).activeFilters(activeFilters == null ? List.of() : List.copyOf(activeFilters))
                    .build();
        } catch (Throwable t) {
            current = RunMeta.builder().start(Instant.now()).build();
        }
    }

    /** Completes the record with the end time and the run folder name (call from {@code onFinish}). */
    public static RunMeta finish(String runId) {
        RunMeta c = current;
        if (c == null) c = RunMeta.builder().build();
        RunMeta.RunMetaBuilder b = c.toBuilder().runId(nz(runId)).end(Instant.now());
        try {
            b.targetOs(com.gsl.config.TargetOsResolver.name());
            b.sessionCap(com.gsl.config.ConcurrencyCap.describe());
            b.browserVersions(BrowserFingerprint.browserVersions());
            b.versionDrift(describeDrift());
        } catch (Throwable ignored) {
            // best-effort: leave the new fields blank rather than fail the report
        }
        return b.build();
    }

    /** {@code chrome: 141, 140} for every browser seen on more than one major version; blank when consistent. Also logs a WARN (informational only). */
    private static String describeDrift() {
        StringBuilder sb = new StringBuilder();
        BrowserFingerprint.majorsByBrowser().forEach((browser, majors) -> {
            if (majors.size() > 1) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(browser).append(": ").append(String.join(", ", majors));
            }
        });
        if (sb.length() > 0) {
            com.gsl.utils.Log.warn("[BrowserVersions] This run used more than one browser major version ({}) — "
                    + "results from different builds are mixed in one report.", sb);
        }
        return sb.toString();
    }

    /** The recorded run start, or {@code null} when {@link #begin} was not called. */
    public static RunMeta peek() {
        return current;
    }

    /**
     * Git branch: CI env var ({@code BRANCH_NAME}/{@code GIT_BRANCH}, stripped of any
     * {@code origin/} prefix) first, else parsed from {@code .git/HEAD} found by walking up from
     * the working directory. Blank when neither is available.
     */
    static String detectBranch() {
        return detectBranch(System::getenv, Paths.get("").toAbsolutePath());
    }

    static String detectBranch(Function<String, String> env, Path start) {
        String b = firstNonBlank(env.apply("BRANCH_NAME"), env.apply("GIT_BRANCH"));
        if (b != null) return b.startsWith("origin/") ? b.substring(7) : b;
        try {
            for (Path p = start; p != null; p = p.getParent()) {
                Path head = p.resolve(".git").resolve("HEAD");
                if (Files.isRegularFile(head)) {
                    String line = Files.readString(head).strip();
                    return line.startsWith("ref: refs/heads/") ? line.substring("ref: refs/heads/".length())
                            : (line.length() > 8 ? line.substring(0, 8) : line);
                }
            }
        } catch (Exception ignored) {
            // blank branch
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
