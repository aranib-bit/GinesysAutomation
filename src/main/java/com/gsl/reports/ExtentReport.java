package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.gsl.config.ConfigFactory;
import com.gsl.config.ReportConfig;
import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.AppOperation;
import com.gsl.enums.ISubModule;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.utils.Log;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gsl.enums.AppOperation.*;
import static com.gsl.pages.enums.Finance.Operations.OperationsMenuType.*;
import static com.gsl.pages.enums.Finance.Setup.SetupMenuType.*;
import static com.gsl.pages.enums.topmenucomponent.MainModuleType.*;

/**
 * Owns the top-level {@link ExtentReports} instance (the combined main report) plus one
 * per-module/sub-module/operation "shard" {@link ExtentReports} each test also mirrors into (see
 * {@link #initShards()}) — so a reviewer can open either the full run or just, say, the AP
 * Voucher shard, without the other's noise.
 *
 * <p>{@link #initReports()} must run once at suite start-up before any test creates a node.
 * {@link #recordTestResult(ITestResult, String)} is the per-test bookkeeping entry point:
 * it appends a crash-safe JSON line to the delta file (R-01 Tier 1, survives a JVM crash even if
 * the batched HTML flush below never runs), feeds {@link SummaryReportBuilder}'s per-shard
 * tallies, and periodically flushes the full HTML via {@link #flushReports()} (R-01 Tier 2,
 * batched per {@code report.flushBatchSize} unless {@code report.flushBatchEnabled} is off).
 * {@link #createTest(String, MainModuleType, ISubModule, AppOperation)} attaches the current
 * test to its matching shard; {@link ExtentManager} tracks which {@link ExtentTest} (main and
 * shard) is current on this thread.
 */
public final class ExtentReport {

    private ExtentReport() {
    }

    public static ExtentReports extent;

    // ── R-02: Per-shard ExtentReports instances ───────────────────────────────
    private static final ConcurrentHashMap<ShardKey, ExtentReports> SHARD_EXTENTS =
            new ConcurrentHashMap<>();

    // ── R-01: Batched flush + crash-safe delta append ─────────────────────────

    private static final AtomicInteger FLUSH_COUNTER = new AtomicInteger(0);
    private static       Path          DELTA_FILE_PATH;
    private static final Object        DELTA_LOCK    = new Object();

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One-time suite start-up: creates the main {@link ExtentReports} + its Spark HTML reporter,
     * registers a shutdown-hook emergency flush (so an OOM/SIGTERM still writes whatever test
     * nodes already exist), initializes the crash-safe delta file (R-01 Tier 1), and registers
     * every per-module/sub-module/operation shard file (R-02, see {@link #initShards()}).
     */
    public static void initReports() {
        extent = new ExtentReports();
        ExtentSparkReporter sparkReporter = new ExtentSparkReporter(FrameworkConstants.getReportPath());
        // Without this, the HTML is written using the JVM's platform-default charset (not UTF-8
        // on this Windows/JDK17 setup), so every non-ASCII character used in step names/table
        // cells (→, —, •, etc.) gets replaced with U+FFFD when the browser renders the file as
        // UTF-8 per its own <meta charset> tag.
        sparkReporter.config().setEncoding("UTF-8");
        // P4: widen the built-in timestamp column so timestamps never wrap
        sparkReporter.config().setCss(
            ".col-timestamp, td.timestamp, th.timestamp, " +
            ".step-time, .test-time-info, td:first-child.timestamp-cell {" +
            "  min-width: 160px !important;" +
            "  white-space: nowrap !important;" +
            "}"
        );
        extent.attachReporter(sparkReporter);

        // Emergency flush on JVM shutdown (OOM, SIGTERM, etc.) — best-effort so any
        // already-created test nodes are written to the HTML file even if TestNG
        // callbacks never fire.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (extent != null) extent.flush();
                SHARD_EXTENTS.values().forEach(e -> {
                    try { e.flush(); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {
                // Heap may already be exhausted; nothing more we can do.
            }
        }, "extent-shutdown-flush"));

        // R-01 Tier 1: create crash-safe delta file (one JSON line per test)
        DELTA_FILE_PATH = Paths.get(FrameworkConstants.getReportDir(), "live", "run_delta.ndjson");
        try {
            Files.createDirectories(DELTA_FILE_PATH.getParent());
            Files.writeString(DELTA_FILE_PATH, "", StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            Log.info("[ExtentReport] Delta file initialized: {}", DELTA_FILE_PATH);
        } catch (IOException e) {
            Log.warn("[ExtentReport] Could not create delta file: {}", e.getMessage());
        }

        // R-02: Register per-module/submodule/operation shard files
        initShards();
    }

    /** Registers one shard {@link ExtentReports} file per module/sub-module/operation combination
     *  this framework covers — every module/operation pairing a test could run under must be
     *  registered here up front so {@link #createTest(String, MainModuleType, ISubModule,
     *  AppOperation)} always resolves a shard, even for a shard that ends up with zero tests. */
    private static void initShards() {
        // Finance › Vouchers
        register(FINANCE, ARVOUCHER,     ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, APVOUCHER,     ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE, WORKBENCH);
        register(FINANCE, ITEM,          ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, CATEGORY,      ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, BRS,           NONE);

        // Finance › Journals
        register(FINANCE, GENJOURNAL,    ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, ARAPJOURNAL,   ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, DEBITJOURNAL,  ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, CREDITJOURNAL, ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, DOCADJUST,     QUICKSETOFF);

        // Finance › Retail Journals
        register(FINANCE, POSJOURNAL,    NONE);
        register(FINANCE, GVJOURNALS,    NONE);

        // Finance › TDS
        register(FINANCE, TDSJOURNAL,    ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);
        register(FINANCE, TDSREVJOURNAL, ADD, MODIFY, DELETE, RELEASE, REVERT, PRINT, AUTORELEASE);

        // Finance › Setup › Chart of Accounts
        register(FINANCE, ACCOUNTGROUP,       ADD, MODIFY, DELETE, EDITGRP, EDITLEGGRP, EDITLEGSEQ);
        register(FINANCE, ACCOUNTCLASS,        ADD, MODIFY);
        register(FINANCE, LEDGER,              ADD, MODIFY, DELETE);
        register(FINANCE, SUBLEDGEROTHERS,     ADD, MODIFY);
        register(FINANCE, SUBLEDGEREMPLOYEES,  ADD, MODIFY);
        register(FINANCE, NARRATION,           ADD, MODIFY, DELETE);

        // Finance › Setup › TDS
        register(FINANCE, TDSSOURCE,    ADD, MODIFY);
        register(FINANCE, TDSSECTION,   ADD, MODIFY);
        register(FINANCE, TDSAGENCY,    ADD, MODIFY);

        // Finance › Setup › Cheque
        register(FINANCE, CHEQUEBOOK,   ADD, MODIFY, DELETE);
        register(FINANCE, PRINTCHEQUE,  NONE);

        // Finance › Setup › Budget & Opening
        register(FINANCE, FINANCIALBUDGET,  NONE);
        register(FINANCE, OPENINGLEDGER,    ADD, MODIFY, DELETE);
        register(FINANCE, OPENINGSUBLEDGER, ADD, MODIFY, DELETE);
    }

    /** Registers one shard file per {@code op} in {@code ops} for the given module/sub-module. */
    private static void register(MainModuleType mod, ISubModule sm, AppOperation... ops) {
        for (AppOperation op : ops) {
            createShardFile(mod, sm, op);
        }
    }

    /** Creates one shard's HTML file path/directory and its own {@link ExtentReports} instance,
     *  storing it under a {@link ShardKey} for later lookup by {@link #createTest(String,
     *  MainModuleType, ISubModule, AppOperation)}. */
    private static void createShardFile(MainModuleType mod, ISubModule sm, AppOperation op) {
        String dir  = mod.getName().toLowerCase()
                         .replace(" & ", "_and_").replace(" ", "_");
        String slug = sm.getShortCode().toLowerCase()
                + (op == NONE ? "" : "_" + op.name().toLowerCase());
        String path = FrameworkConstants.getReportDir() + "/" + dir + "/"
                + slug + "_" + LocalDate.now() + ".html";

        try {
            Files.createDirectories(Paths.get(FrameworkConstants.getReportDir(), dir));
        } catch (IOException e) {
            Log.warn("[ExtentReport] Could not create shard dir {}: {}", dir, e.getMessage());
        }

        ExtentSparkReporter spark = new ExtentSparkReporter(path);
        spark.config().setEncoding("UTF-8");
        spark.config().setDocumentTitle(sm.getName()
            + (op == NONE ? "" : " — " + op.getDisplayName()));
        spark.config().setCss(
            ".col-timestamp{min-width:160px!important;white-space:nowrap!important}");

        ExtentReports shardExtent = new ExtentReports();
        shardExtent.attachReporter(spark);
        SHARD_EXTENTS.put(new ShardKey(mod, sm, op), shardExtent);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * R-01 entry point — replaces direct flushReports() calls after each test.
     * Appends one JSON line to the crash-safe delta file (Tier 1, always, ~1 ms),
     * then does a full HTML flush every flushBatchSize tests (Tier 2).
     */
    public static void recordTestResult(ITestResult result, String status) {
        appendToDelta(result, status);

        // R-02: accumulate per-shard stats for summary.html
        ExtentTest shardTest = ExtentManager.getShardTest();
        // Determine the ShardKey by reverse-looking up which shard has this test node.
        // We stored it under the key used in createTest(name, module, subModule, op),
        // so we track it via a separate ThreadLocal in ExtentManager.
        ShardKey currentKey = shardTest != null ? ExtentManager.getCurrentShardKey() : null;
        if (currentKey != null) {
            SummaryReportBuilder.record(currentKey, status);
        } else {
            // No shard context — e.g. a @DataProvider-level failure/skip (TestNG's synthetic
            // wrapper for a provider that threw) never reaches the annotated test method, so
            // never resolves a module/subModule/operation. Previously silently dropped here,
            // which let a real failure go entirely uncounted in summary.html's own totals — see
            // SummaryReportBuilder#UNCATEGORIZED's Javadoc.
            SummaryReportBuilder.recordUncategorized(status);
        }

        int count = FLUSH_COUNTER.incrementAndGet();
        ReportConfig cfg = ConfigFactory.getReportConfig();
        if (!cfg.flushBatchEnabled()) {
            // Batching disabled — flush the full HTML report after every test case.
            flushReports();
            Log.debug("[ExtentReport] Per-test flush at test #{} (batch disabled)", count);
        } else {
            int batchSize = cfg.flushBatchSize();
            if (count % batchSize == 0) {
                flushReports();
                Log.debug("[ExtentReport] Batch flush at test #{}", count);
            }
        }
    }

    private static void appendToDelta(ITestResult result, String status) {
        if (DELTA_FILE_PATH == null) return;
        try {
            String line = buildDeltaJson(result, status) + "\n";
            synchronized (DELTA_LOCK) {
                Files.writeString(DELTA_FILE_PATH, line,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            Log.warn("[ExtentReport] Delta append failed: {}", e.getMessage());
        }
    }

    private static String buildDeltaJson(ITestResult result, String status) {
        return String.format(
            "{\"testName\":\"%s\",\"status\":\"%s\",\"durationMs\":%d,\"timestamp\":\"%s\"}",
            escapeJson(result.getName()),
            status,
            result.getEndMillis() - result.getStartMillis(),
            Instant.now().toString()
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * P2: Sets the descriptive sentence on the current thread's ExtentTest node.
     * Called immediately after createTest() so the description appears under the test title.
     */
    public static void setTestDescription(String description) {
        if (description == null || description.isBlank()) return;
        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) {
            test.getModel().setDescription(description);
        }
    }

    /** Returns the main (non-shard) {@link ExtentReports} instance. */
    public static ExtentReports getExtentReports() {
        return extent;
    }

    /** Flushes the main report and every shard report to disk, then republishes
     *  {@code reports/latest/index.html} for CI pickup (see {@link #copyToLatest()}). Called
     *  directly by {@link #recordTestResult}'s batching logic; safe to call more often. */
    public static void flushReports() {
        // Serialised: data-provider threads all reach here via recordTestResult. Extent walks its
        // internal HashMaps while flushing, so a concurrent flush or test/attribute add surfaces as a
        // ConcurrentModificationException — run 20261031_031748 (AP-P1) lost a SKIP to it because it
        // was thrown out of TestListener.onTestSkipped and aborted that method's data-provider pool.
        // A reporting hiccup must never change a test's outcome, so it is retried once and then logged.
        synchronized (FLUSH_LOCK) {
            flushQuietly(extent, "main");
            SHARD_EXTENTS.values().forEach(e -> flushQuietly(e, "shard"));
        }
        // Phase0: every flush (per-test, batched, or suite-end) also republishes
        // reports/latest/index.html, so the fixed-path file CI publishes/serves stays
        // live throughout the run instead of only updating once at suite finish.
        copyToLatest();
    }

    private static final Object FLUSH_LOCK = new Object();

    private static void flushQuietly(ExtentReports target, String label) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                target.flush();
                return;
            } catch (java.util.ConcurrentModificationException e) {
                if (attempt == 2) {
                    Log.warn("[ExtentReport] {} flush skipped after a concurrent modification; the next flush will pick it up", label);
                }
            } catch (Exception e) {
                Log.warn("[ExtentReport] {} flush failed: {}", label, e.toString());
                return;
            }
        }
    }

    // ── Phase 0: copy run report to reports/latest/ for CI pickup ──────────────
    // Runs on every flushReports() call — see comment above — so this fixed path
    // is live-updating during the run, not just written once at suite end.

    static void copyToLatest() {
        try {
            Path runDir    = Paths.get(FrameworkConstants.getRunReportDir());
            Path latestDir = Paths.get(FrameworkConstants.getLatestReportDir());
            Files.createDirectories(latestDir);
            Path runIndex = runDir.resolve("index.html");
            patchDeadCdnReferences(runIndex);
            Files.copy(
                runIndex,
                latestDir.resolve("index.html"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            Log.info("[Phase0] Latest report copied to {}", latestDir.resolve("index.html"));
        } catch (Exception e) {
            Log.warn("[Phase0] Could not copy report to latest/: {}", e.getMessage());
        }
    }

    // ExtentSparkReporter's built-in HTML template hardcodes a <script> tag pointing at
    // cdn.rawgit.com, a CDN that was permanently shut down in 2019 — the report renders
    // blank wherever that script is required to run. jsdelivr's "gh" proxy serves the exact
    // same file straight from the same GitHub repo (extent-framework/extent-github-cdn),
    // which the report's own CSS <link> already relies on, so it's a safe drop-in swap.
    private static void patchDeadCdnReferences(Path indexHtml) {
        try {
            // ISO-8859-1 maps every byte 0-255 to exactly one char with no decode failures —
            // unlike Files.readString(UTF_8), which throws MalformedInputException the moment the
            // report contains any byte sequence that isn't valid UTF-8 (this report's generation
            // pipeline is not guaranteed to write pure UTF-8 throughout). Writing back with the
            // same charset round-trips every byte unchanged except inside our own replacement.
            byte[] bytes = Files.readAllBytes(indexHtml);
            String html = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            String patched = html.replaceAll(
                "https://cdn\\.rawgit\\.com/([^/\"']+)/([^/\"']+)/([^/\"']+)/([^\"'\\s]+)",
                "https://cdn.jsdelivr.net/gh/$1/$2@$3/$4"
            );
            if (!patched.equals(html)) {
                Files.write(indexHtml, patched.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                Log.info("[Phase0] Patched dead cdn.rawgit.com reference(s) in report HTML");
            }
        } catch (Exception e) {
            Log.warn("[Phase0] Could not patch CDN references: {}", e.getMessage());
        }
    }

    /** Creates the main-report test node and makes it the current thread's {@link ExtentTest} —
     *  call once per test from {@code onTestStart}, before the shard-attaching overload below. */
    public static void createTest(String testCaseName) {
        ExtentTest test = extent.createTest(testCaseName);
        ExtentManager.setExtentTest(test);
    }

    /**
     * R-02: Attach the current test to its matching shard report. The summary-report
     * node is created once by {@link #createTest(String)} in onTestStart and reused
     * here (creating it again would duplicate the test in the summary report). The
     * shard test is tracked separately via ExtentManager.setShardTest().
     */
    public static void createTest(String testCaseName,
                                  MainModuleType module,
                                  ISubModule subModule,
                                  AppOperation operation) {
        // Main report node was already created by createTest(String) in onTestStart;
        // reuse it (do NOT create a second one) to avoid duplicate entries in the
        // summary report. This overload only attaches the matching shard node.

        // Shard report node — mirror the same test in the per-operation shard
        ShardKey key = new ShardKey(module, subModule, operation);
        ExtentReports shardExtent = SHARD_EXTENTS.get(key);
        if (shardExtent == null) {
            // Fallback: NONE shard (BRS, POSJOURNAL, etc.)
            shardExtent = SHARD_EXTENTS.get(new ShardKey(module, subModule, NONE));
        }
        if (shardExtent != null) {
            ExtentTest shardTest = shardExtent.createTest(testCaseName);
            ExtentManager.setShardTest(shardTest);
            // track resolved key (NONE fallback resolves to different key than requested)
            ShardKey resolvedKey = SHARD_EXTENTS.containsKey(key) ? key
                    : new ShardKey(module, subModule, NONE);
            ExtentManager.setCurrentShardKey(resolvedKey);
        } else {
            ExtentManager.setShardTest(null);
            ExtentManager.setCurrentShardKey(null);
            Log.warn("[ExtentReport] No shard registered for {}/{}/{}", module, subModule, operation);
        }
    }

    /** Returns every registered shard's {@link ExtentReports}, keyed by {@link ShardKey} — used
     *  by {@link SummaryReportBuilder#groupShards()} as the source of truth for which shards
     *  exist (including ones with zero tests). */
    public static ConcurrentHashMap<ShardKey, ExtentReports> getShardExtents() {
        return SHARD_EXTENTS;
    }

    /** Assigns {@code author} (an enum constant's name) as the current test's Extent "author" tag. */
    public static void assignAuthor(Enum<?> author) {
        ExtentManager.getExtentTest().assignAuthor(author.toString());
    }

}
