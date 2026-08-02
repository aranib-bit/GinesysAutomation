package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.gsl.config.ConfigFactory;
import com.gsl.config.ExecutionConfig;
import com.gsl.config.FrameworkConfig;
import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.AppOperation;
import com.gsl.enums.ISubModule;
import com.gsl.listeners.SuiteCircuitBreaker;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.utils.Log;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds summary.html after the suite finishes.
 *
 * Layout:
 *  ┌─ Circuit breaker banner (only when tripped) ────────────────────────────┐
 *  │  Run metadata row                                                         │
 *  │  Pass/Fail/Skip totals                                                    │
 *  ├─ MODULE ─────────────────────────────────────────────────────────────────┤
 *  │    ── Group ──────────────────────────────────────────────────────────── │
 *  │    SubModule  Operation  Pass  Fail  Skip  Pass%  Health bar  Open ↗     │
 *  └───────────────────────────────────────────────────────────────────────── ┘
 */
public final class SummaryReportBuilder {

    private SummaryReportBuilder() {}

    // Accumulates per-shard test results during the run
    private static final ConcurrentHashMap<ShardKey, ShardStats> STATS = new ConcurrentHashMap<>();

    public static void record(ShardKey key, String status) {
        STATS.computeIfAbsent(key, k -> new ShardStats()).record(status);
    }

    /** @deprecated use {@link #build(ISuite)} so the class-level summary can be rendered too. */
    @Deprecated
    public static void build() {
        build(null);
    }

    public static void build(ISuite suite) {
        String path = FrameworkConstants.getReportDir() + "/summary.html";
        try {
            Files.createDirectories(Paths.get(FrameworkConstants.getReportDir()));
            Files.writeString(Paths.get(path), buildHtml(suite));
            Log.info("[SummaryReportBuilder] summary.html written to: {}", path);
        } catch (IOException e) {
            Log.warn("[SummaryReportBuilder] Failed to write summary.html: {}", e.getMessage());
        }
    }

    private static String buildHtml(ISuite suite) {
        ExecutionConfig cfg = ConfigFactory.getExecutionConfig();
        FrameworkConfig fw  = ConfigFactory.getConfig();
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html lang='en'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>Test Suite Summary</title>")
          .append(styles())
          .append("</head><body>");

        // Circuit breaker banner
        if (cfg.suiteAbortEnabled() && SuiteCircuitBreaker.isTripped()) {
            int pass  = SuiteCircuitBreaker.getPassCount();
            int fail  = SuiteCircuitBreaker.getFailCount();
            int total = pass + fail;
            double rate = total > 0 ? (double) fail / total * 100.0 : 0;
            sb.append("<div class='cb-banner'>")
              .append("⚠&nbsp; <strong>SUITE ABORTED — Circuit Breaker Tripped</strong><br>")
              .append(String.format("Failure rate <strong>%.1f%%</strong> (%d/%d) exceeded threshold %d%%<br>",
                      rate, fail, total, cfg.suiteAbortOnFailurePercent()))
              .append(String.format("Min tests before check: %d &nbsp;|&nbsp; Check interval: every %d &nbsp;|&nbsp; Threshold: %d%%",
                      cfg.suiteAbortMinTestsBeforeCheck(), cfg.suiteAbortCheckInterval(),
                      cfg.suiteAbortOnFailurePercent()))
              .append("</div>");
        }

        // Run metadata
        int totalPass = STATS.values().stream().mapToInt(s -> s.pass).sum();
        int totalFail = STATS.values().stream().mapToInt(s -> s.fail).sum();
        int totalSkip = STATS.values().stream().mapToInt(s -> s.skip).sum();
        int grandTotal = totalPass + totalFail + totalSkip;
        double passRate = grandTotal > 0 ? (double) totalPass / grandTotal * 100.0 : 0;
        String runDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        sb.append("<div class='meta-row'>")
          .append("<span>Run: ").append(runDate).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Env: ").append(fw.env()).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Browser: ").append(fw.browser().toUpperCase()).append("</span>")
          .append("</div>");

        sb.append("<div class='totals'>")
          .append("<span class='t-total'>Total: ").append(grandTotal).append("</span>")
          .append(" &nbsp; <span class='t-pass'>✓ Pass: ").append(totalPass).append("</span>")
          .append(" &nbsp; <span class='t-fail'>✗ Fail: ").append(totalFail).append("</span>")
          .append(" &nbsp; <span class='t-skip'>○ Skip: ").append(totalSkip).append("</span>")
          .append(String.format(" &nbsp; <span class='t-rate'>Pass%%: %.1f%%</span>", passRate))
          .append("</div>");

        sb.append(performanceSummarySection());
        sb.append(classLevelSummarySection(suite));

        sb.append("<hr>");

        // Group shards by module then by ISubModule.getGroup()
        Map<MainModuleType, Map<String, List<Map.Entry<ShardKey, ShardStats>>>> grouped =
                groupShards();

        for (Map.Entry<MainModuleType, Map<String, List<Map.Entry<ShardKey, ShardStats>>>> modEntry
                : grouped.entrySet()) {
            MainModuleType module = modEntry.getKey();
            sb.append("<div class='module-header'>").append(module.getName().toUpperCase())
              .append("</div>");

            for (Map.Entry<String, List<Map.Entry<ShardKey, ShardStats>>> grpEntry
                    : modEntry.getValue().entrySet()) {
                String groupName = grpEntry.getKey();
                List<Map.Entry<ShardKey, ShardStats>> rows = grpEntry.getValue();

                sb.append("<div class='group-header'>── ").append(groupName).append(" ──</div>");
                sb.append("<table class='shard-table'><thead><tr>")
                  .append("<th>SubModule</th><th>Operation</th>")
                  .append("<th>Pass</th><th>Fail</th><th>Skip</th>")
                  .append("<th>Pass%</th><th>Health</th><th>Link</th>")
                  .append("</tr></thead><tbody>");

                for (Map.Entry<ShardKey, ShardStats> row : rows) {
                    ShardKey key   = row.getKey();
                    ShardStats st  = row.getValue();
                    int tot = st.pass + st.fail + st.skip;
                    double pct = tot > 0 ? (double) st.pass / tot * 100.0 : 0;
                    String healthBar = healthBar(pct);
                    String opLabel = key.operation() == AppOperation.NONE
                            ? "—" : key.operation().getDisplayName();
                    String shardFileName = buildShardFileName(key);

                    sb.append("<tr>")
                      .append("<td>").append(key.subModule().getName()).append("</td>")
                      .append("<td>").append(opLabel).append("</td>")
                      .append("<td class='pass'>").append(st.pass).append("</td>")
                      .append("<td class='fail'>").append(st.fail).append("</td>")
                      .append("<td class='skip'>").append(st.skip).append("</td>")
                      .append(String.format("<td>%.1f%%</td>", pct))
                      .append("<td>").append(healthBar).append("</td>")
                      .append("<td><a href='").append(shardFileName).append("' target='_blank'>Open ↗</a></td>")
                      .append("</tr>");
                }
                sb.append("</tbody></table>");
            }
        }

        sb.append("<div class='footer'>Report generated: ").append(runDate).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static Map<MainModuleType, Map<String, List<Map.Entry<ShardKey, ShardStats>>>> groupShards() {
        Map<MainModuleType, Map<String, List<Map.Entry<ShardKey, ShardStats>>>> result =
                new LinkedHashMap<>();

        // Use registered shards from ExtentReport as the source of truth (includes zero-test shards)
        ConcurrentHashMap<ShardKey, ExtentReports> registered = ExtentReport.getShardExtents();

        List<ShardKey> keys = new ArrayList<>(registered.keySet());
        keys.sort(Comparator
                .<ShardKey, String>comparing(k -> k.module().getName())
                .thenComparing(k -> k.subModule().getGroup())
                .thenComparing(k -> k.subModule().getName())
                .thenComparing(k -> k.operation().name()));

        for (ShardKey key : keys) {
            result.computeIfAbsent(key.module(), m -> new LinkedHashMap<>())
                  .computeIfAbsent(key.subModule().getGroup(), g -> new ArrayList<>())
                  .add(Map.entry(key, STATS.getOrDefault(key, new ShardStats())));
        }
        return result;
    }

    private static String buildShardFileName(ShardKey key) {
        String dir = key.module().getName().toLowerCase()
                        .replace(" & ", "_and_").replace(" ", "_");
        String slug = key.subModule().getShortCode().toLowerCase()
                + (key.operation() == AppOperation.NONE ? "" : "_" + key.operation().name().toLowerCase());
        return dir + "/" + slug + "_" + java.time.LocalDate.now() + ".html";
    }

    private static String healthBar(double pct) {
        int filled = (int) Math.round(pct / 10.0);
        StringBuilder bar = new StringBuilder("<span class='health-bar'>");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "■" : "░");
        }
        bar.append("</span>");
        return bar.toString();
    }

    /**
     * Performance Report suite summary — Sessions Created / Recoveries / Navigation
     * Skipped-vs-Performed / Users Assigned. Only shown when session reuse was actually exercised
     * (sessionsCreated &gt; 0) — tests that never call SessionManager leave these counters at 0,
     * so this section stays absent from their reports rather than showing a wall of zeroes.
     */
    private static String performanceSummarySection() {
        int sessions = PerformanceSummaryStats.sessionsCreated();
        if (sessions == 0) return "";

        int recoveries         = PerformanceSummaryStats.recoveries();
        int navSkipped         = PerformanceSummaryStats.navigationSkipped();
        int navPerformed       = PerformanceSummaryStats.navigationPerformed();
        int usersAssigned      = PerformanceSummaryStats.usersAssigned().size();

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='perf-summary'>")
          .append("<div class='perf-title'>Performance Summary — Session Reuse</div>")
          .append("<span>Sessions Created: ").append(sessions).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Recoveries: ").append(recoveries).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Navigation Skipped: ").append(navSkipped).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Navigation Performed: ").append(navPerformed).append("</span>")
          .append(" &nbsp;|&nbsp; <span>Users Assigned: ").append(usersAssigned).append("</span>")
          .append("</div>");
        return sb.toString();
    }

    /**
     * Per-test-class summary: invocations/pass/fail/skip (from TestNG's own suite results — no
     * new instrumentation needed for those) plus cumulative UserPool hold-time and cold-start
     * warm-up cost (from {@link PoolAttributionStats}, fed by {@code UserPool}/{@code SessionManager}/
     * {@code NavigationUtils}). Exists specifically so "which class is consuming disproportionate
     * shared-pool time" is answered by one glance at this table instead of a manual report read —
     * that manual read is exactly what a real incident (AccountGroupLifecycle starving AP/AR
     * Voucher of pool users) required before this section existed.
     */
    private static String classLevelSummarySection(ISuite suite) {
        Map<String, int[]> resultsByClass = new TreeMap<>(); // [pass, fail, skip]
        if (suite != null) {
            for (ISuiteResult sr : suite.getResults().values()) {
                ITestContext ctx = sr.getTestContext();
                tally(resultsByClass, ctx.getPassedTests().getAllResults(), 0);
                tally(resultsByClass, ctx.getFailedTests().getAllResults(), 1);
                tally(resultsByClass, ctx.getSkippedTests().getAllResults(), 2);
            }
        }

        Map<String, Long> holdSeconds   = PoolAttributionStats.holdSecondsByClass();
        Map<String, Long> coldStartMs   = PoolAttributionStats.coldStartMsByClass();
        Map<String, Long> coldStartCnt  = PoolAttributionStats.coldStartCountByClass();

        Set<String> allClasses = new TreeSet<>();
        allClasses.addAll(resultsByClass.keySet());
        allClasses.addAll(holdSeconds.keySet());
        if (allClasses.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='class-summary'>")
          .append("<div class='perf-title'>Class-Level Summary — Shared Pool Attribution</div>")
          .append("<table class='shard-table'><thead><tr>")
          .append("<th>Class</th><th>Pass</th><th>Fail</th><th>Skip</th>")
          .append("<th>Pool Hold (s)</th><th>Cold-Start Threads</th><th>Cold-Start Cost (s)</th>")
          .append("</tr></thead><tbody>");

        for (String className : allClasses) {
            int[] pfs = resultsByClass.getOrDefault(className, new int[3]);
            long hold = holdSeconds.getOrDefault(className, 0L);
            long coldCount = coldStartCnt.getOrDefault(className, 0L);
            double coldSeconds = coldStartMs.getOrDefault(className, 0L) / 1000.0;

            sb.append("<tr>")
              .append("<td>").append(className).append("</td>")
              .append("<td class='pass'>").append(pfs[0]).append("</td>")
              .append("<td class='fail'>").append(pfs[1]).append("</td>")
              .append("<td class='skip'>").append(pfs[2]).append("</td>")
              .append("<td>").append(hold).append("</td>")
              .append("<td>").append(coldCount).append("</td>")
              .append(String.format("<td>%.1f</td>", coldSeconds))
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static void tally(Map<String, int[]> resultsByClass, Set<ITestResult> results, int slot) {
        for (ITestResult r : results) {
            String className = r.getTestClass().getRealClass().getSimpleName();
            resultsByClass.computeIfAbsent(className, k -> new int[3])[slot]++;
        }
    }

    private static String styles() {
        return "<style>" +
            "body{font-family:Arial,sans-serif;margin:20px;color:#333}" +
            ".cb-banner{background:#b71c1c;color:#fff;padding:14px 20px;border-radius:6px;margin-bottom:16px;line-height:1.7}" +
            ".meta-row{font-size:13px;color:#555;margin-bottom:6px}" +
            ".totals{font-size:14px;margin-bottom:12px}" +
            ".t-pass{color:#2e7d32}.t-fail{color:#b71c1c}.t-skip{color:#757575}.t-rate{font-weight:bold}" +
            ".perf-summary{background:#e0f7fa;border-left:3px solid #00838f;padding:8px 14px;" +
                "border-radius:4px;margin-bottom:12px;font-size:13px}" +
            ".perf-title{font-weight:bold;color:#00838f;margin-bottom:4px}" +
            ".class-summary{background:#fff3e0;border-left:3px solid #ad1457;padding:8px 14px;" +
                "border-radius:4px;margin-bottom:12px;font-size:13px}" +
            "hr{border:none;border-top:2px solid #e0e0e0;margin:12px 0}" +
            ".module-header{font-size:15px;font-weight:bold;background:#1565c0;color:#fff;" +
                "padding:6px 12px;border-radius:4px;margin-top:20px;margin-bottom:4px}" +
            ".group-header{font-size:13px;color:#555;margin:10px 0 4px 6px;font-style:italic}" +
            ".shard-table{border-collapse:collapse;width:100%;margin-bottom:10px;font-size:13px}" +
            ".shard-table th{background:#f5f5f5;border:1px solid #ddd;padding:6px 10px;text-align:left}" +
            ".shard-table td{border:1px solid #eee;padding:5px 10px}" +
            ".shard-table tr:hover td{background:#fafafa}" +
            "td.pass{color:#2e7d32;font-weight:bold}" +
            "td.fail{color:#b71c1c;font-weight:bold}" +
            "td.skip{color:#757575}" +
            ".health-bar{letter-spacing:1px;font-size:12px;color:#1565c0}" +
            "a{color:#1565c0;text-decoration:none}" +
            "a:hover{text-decoration:underline}" +
            ".footer{font-size:11px;color:#aaa;margin-top:20px;text-align:center}" +
            "</style>";
    }

    // ── Inner stat holder ──────────────────────────────────────────────────────

    public static final class ShardStats {
        public volatile int pass;
        public volatile int fail;
        public volatile int skip;

        synchronized void record(String status) {
            switch (status) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                default     -> skip++;
            }
        }
    }
}
