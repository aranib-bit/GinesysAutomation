package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.reports.profile.BrowserConfig;
import com.gsl.reports.profile.DatabaseConfig;
import com.gsl.reports.profile.DiagnosticsConfig;
import com.gsl.reports.profile.ExecutionConfig;
import com.gsl.reports.profile.NetworkConfig;
import com.gsl.reports.profile.PerformanceConfig;
import com.gsl.reports.profile.RemoteLogConfig;
import com.gsl.reports.profile.RemoteServerConfig;
import com.gsl.reports.profile.RuntimeExecutionProfile;
import com.gsl.reports.profile.TestDataConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Renders a {@link RuntimeExecutionProfile} into the Extent report — both the compact
 * dashboard system-info panel ({@link #renderDashboard}) and the full, section-by-section
 * config tables ({@link #renderAllSections}) — and can also serialize the whole profile to a
 * {@code runtime-snapshot.json} file for offline inspection ({@link #generateJsonSnapshot}).
 *
 * <p>All credentials/secrets are expected to already be masked on the {@link RuntimeExecutionProfile}
 * itself (see {@code RuntimeProfileBuilder}) — this class only formats what it is given, it does
 * not decide what is safe to show.
 */
public final class ReportConfigRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String snapshotPath;

    private ReportConfigRenderer() {
    }

    /**
     * Writes Level-1 execution metadata to the ExtentReports dashboard system info panel.
     * No sensitive data, no full paths.
     */
    public static void renderDashboard(ExtentReports extent, RuntimeExecutionProfile profile) {
        ExecutionConfig e = profile.getExecution();
        BrowserConfig b = profile.getBrowser();
        DatabaseConfig db = profile.getDatabase();
        TestDataConfig td = profile.getTestData();
        PerformanceConfig p = profile.getPerformance();

        extent.setSystemInfo("Environment",       nullSafe(e.getEnvironment()));
        extent.setSystemInfo("Browser",           nullSafe(e.getBrowser()));
        extent.setSystemInfo("Target OS",         nullSafe(com.gsl.config.TargetOsResolver.name()));
        extent.setSystemInfo("Session Cap",       nullSafe(com.gsl.config.ConcurrencyCap.describe().isBlank() ? "none" : com.gsl.config.ConcurrencyCap.describe()));
        extent.setSystemInfo("Run Mode",          nullSafe(e.getRunMode()));
        extent.setSystemInfo("Execution Type",    nullSafe(e.getExecutionType()));
        extent.setSystemInfo("Thread Count",      String.valueOf(e.getThreadCount()));
        extent.setSystemInfo("Concurrent Users",  String.valueOf(e.getConcurrentUserCount()));
        extent.setSystemInfo("Application Type",  nullSafe(e.getApplicationType()));
        extent.setSystemInfo("Headless",          String.valueOf(b.isHeadless()));
        extent.setSystemInfo("Network Logs",      b2s(profile.getNetwork().isEnabled()));
        extent.setSystemInfo("DB Host",           nullSafe(db.getHost()));
        extent.setSystemInfo("DB Pool",           nullSafe(db.getPoolName()));
        extent.setSystemInfo("Test Data Source",  nullSafe(td.getSource()));
        extent.setSystemInfo("Performance Timing",b2s(p.isTimingEnabled()));
        extent.setSystemInfo("Custom Metrics",    b2s(p.isCustomMetricsEnabled()));
    }

    /**
     * Serializes the full profile to {user.dir}/runtime-snapshot.json.
     * Full log paths and all non-sensitive config values are included.
     * All credentials are already masked in the profile object itself.
     */
    public static String generateJsonSnapshot(RuntimeExecutionProfile profile) throws Exception {
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
        Path path = Path.of(System.getProperty("user.dir"), "runtime-snapshot.json");
        Files.writeString(path, json);
        snapshotPath = path.toAbsolutePath().toString();
        return snapshotPath;
    }

    /** The absolute path last written by {@link #generateJsonSnapshot}, or {@code null} if it hasn't run yet. */
    public static String getSnapshotPath() {
        return snapshotPath;
    }

    /**
     * Renders all 10 config sections as ExtentReports markup tables.
     * Must be called after ExtentReport.createTest() has created a test node on this thread.
     */
    public static void renderAllSections(RuntimeExecutionProfile profile) {
        renderEnvironmentSection(profile.getExecution());
        renderBrowserSection(profile.getBrowser());
        renderDatabaseSection(profile.getDatabase());
        renderNetworkSection(profile.getNetwork());
        renderDiagnosticsSection(profile.getDiagnostics());
        renderRemoteLogSection(profile.getRemoteLogs());
        renderRemoteServerSection(profile.getRemoteServer());
        renderTestDataSection(profile.getTestData());
        renderPerformanceSection(profile.getPerformance());
        renderAppLogSourcesSection(profile.getAppLogSources());
    }

    /** Renders the Environment &amp; Execution Configuration table on the current test node. */
    private static void renderEnvironmentSection(ExecutionConfig e) {
        String[][] table = {
                {"Field", "Value"},
                {"Environment",       nullSafe(e.getEnvironment())},
                {"Application URL",   nullSafe(e.getApplicationUrl())},
                {"Grid URL",          nullSafe(e.getGridUrl())},
                {"Docker Grid URL",   nullSafe(e.getDockerGridUrl())},
                {"App Server Host",   nullSafe(e.getRemoteAppServerHost())},
                {"Run Mode",          nullSafe(e.getRunMode())},
                {"Execution Type",    nullSafe(e.getExecutionType())},
                {"Thread Count",      String.valueOf(e.getThreadCount())},
                {"Concurrent Users",  String.valueOf(e.getConcurrentUserCount())},
                {"Application Type",  nullSafe(e.getApplicationType())}
        };
        ExtentManager.getExtentTest().info("<b>Environment &amp; Execution Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Browser Configuration table on the current test node. */
    private static void renderBrowserSection(BrowserConfig b) {
        String[][] table = {
                {"Field", "Value"},
                {"Headless Mode",             String.valueOf(b.isHeadless())},
                {"Incognito Mode",            String.valueOf(b.isIncognito())},
                {"Accept Insecure Certs",     String.valueOf(b.isAcceptInsecureCerts())},
                {"Item Scan Timeout (ms)",    String.valueOf(b.getItemScanTimeout())},
                {"Sync Timeout (s)",          String.valueOf(b.getSyncTimeoutSeconds())},
                {"ExtJS Timeout (s)",         String.valueOf(b.getExtjsTimeout())},
                {"ExtJS Min Timeout (s)",     String.valueOf(b.getExtjsMinTimeout())},
                {"Overlay Retry Count",       String.valueOf(b.getClickOverlayRetryMax())},
                {"Doc Selector Mode",         nullSafe(b.getDocSelectorMode())},
                {"Overfetch Multiplier",      String.valueOf(b.getDocSelectorOverfetchMultiplier())},
                {"Retry Backoff (ms)",        String.valueOf(b.getDocSelectorRetryBackoffMs())},
                {"Default Doc Count",         String.valueOf(b.getDocSelectorDocumentCountDefault())}
        };
        ExtentManager.getExtentTest().info("<b>Browser Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Database Configuration table on the current test node. */
    private static void renderDatabaseSection(DatabaseConfig db) {
        String[][] table = {
                {"Field", "Value"},
                {"Database Type",                 nullSafe(db.getType())},
                {"Database Name",                 nullSafe(db.getName())},
                {"Database Host",                 nullSafe(db.getHost())},
                {"Database Port",                 nullSafe(db.getPort())},
                {"Username",                      db.getUsername()},
                {"Password",                      db.getPassword()},
                {"Pool Name",                     nullSafe(db.getPoolName())},
                {"Max Pool Size",                 String.valueOf(db.getMaximumPoolSize())},
                {"Min Idle",                      String.valueOf(db.getMinimumIdle())},
                {"Idle Timeout (ms)",             String.valueOf(db.getIdleTimeout())},
                {"Connection Timeout (ms)",       String.valueOf(db.getConnectionTimeout())},
                {"Max Lifetime (ms)",             String.valueOf(db.getMaxLifetime())},
                {"Leak Detection Threshold (ms)", String.valueOf(db.getLeakDetectionThreshold())}
        };
        ExtentManager.getExtentTest().info("<b>Database Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Network &amp; Browser Logging Configuration table on the current test node. */
    private static void renderNetworkSection(NetworkConfig n) {
        String[][] table = {
                {"Field", "Value"},
                {"Network Mode",               nullSafe(n.getNetworkMode())},
                {"Network Logging",            b2s(n.isEnabled())},
                {"Capture Failed Only",        b2s(n.isCaptureFailedOnly())},
                {"Capture Headers",            b2s(n.isCaptureHeaders())},
                {"Capture Body",               b2s(n.isCaptureBody())},
                {"URL Filter",                 nullSafe(n.getUrlFilter())},
                {"Show All On Pass",           b2s(n.isPassShowAll())},
                {"Timing Log On",              nullSafe(n.getTimingLogOn())},
                {"Cookies Log On",             nullSafe(n.getCookiesLogOn())},
                {"Call Stack Log On",          nullSafe(n.getCallstackLogOn())},
                {"Browser Logs Enabled",       b2s(n.isBrowserLogsEnabled())},
                {"Capture On Failure",         b2s(n.isBrowserLogsCaptureOnFailure())},
                {"Fail On JS Error",           b2s(n.isBrowserLogsFailOnJsError())},
                {"Fail On Critical JS Error",  b2s(n.isBrowserLogsFailOnCriticalJsError())},
                {"Capture Only",               b2s(n.isBrowserLogsCaptureOnly())},
                {"Browser Log Level",          nullSafe(n.getBrowserLogsLevel())}
        };
        ExtentManager.getExtentTest().info("<b>Network &amp; Browser Logging Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Diagnostics Configuration table on the current test node. */
    private static void renderDiagnosticsSection(DiagnosticsConfig d) {
        String[][] table = {
                {"Field", "Value"},
                {"HTML Capture On Failure",     b2s(d.isHtmlCaptureOnElementFailure())},
                {"Driver Capability Capture",   b2s(d.isDriverCapabilitiesEnabled())},
                {"Active Popup Detection",      b2s(d.isActivePopupEnabled())},
                {"DB State Snapshot",           b2s(d.isDbStateEnabled())},
                {"Thread Dump Capture",         b2s(d.isThreadDumpEnabled())},
                {"JS Error JSON Export",        b2s(d.isJsErrorJsonExportEnabled())},
                {"UI Diagnostics",              b2s(d.isUiDiagnosticsEnabled())},
                {"App Server Util",             b2s(d.isAppServerUtilEnabled())},
                {"App Server Host",             nullSafe(d.getAppServerHost())},
                {"App Server OS",               nullSafe(d.getAppServerType())},
                {"DB Server Util",              b2s(d.isDbServerUtilEnabled())},
                {"DB Server Host",              nullSafe(d.getDbServerHost())},
                {"DB Server OS",                nullSafe(d.getDbServerType())},
                {"Event Log Capture",           b2s(d.isEventLogEnabled())},
                {"Event Log Lines",             String.valueOf(d.getEventLogLines())}
        };
        ExtentManager.getExtentTest().info("<b>Diagnostics Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Remote Log Collection Configuration table on the current test node. */
    private static void renderRemoteLogSection(RemoteLogConfig rl) {
        String[][] table = {
                {"Field", "Value"},
                {"Capture Mode",             nullSafe(rl.getCaptureMode())},
                {"Max Log Lines",            String.valueOf(rl.getMaxLines())},
                {"Debug Enabled",            b2s(rl.isDebugEnabled())},
                {"SSH Retry Count",          String.valueOf(rl.getSshRetryCount())},
                {"SSH Command Timeout (s)",  String.valueOf(rl.getSshCommandTimeoutSeconds())},
                {"WinRM Timeout (s)",        String.valueOf(rl.getWinrmOperationTimeoutSeconds())},
                {"Server Timezone Offset",   nullSafe(rl.getServerTimezoneOffset())},
                {"Keywords",                 nullSafe(rl.getKeywords())}
        };
        ExtentManager.getExtentTest().info("<b>Remote Log Collection Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Remote Server Configuration table (SSH/WinRM hosts, ports, masked credentials) on the current test node. */
    private static void renderRemoteServerSection(RemoteServerConfig rs) {
        String[][] table = {
                {"Field", "Value"},
                {"App Server Host",        nullSafe(rs.getAppHost())},
                {"App SSH Port",           String.valueOf(rs.getAppSshPort())},
                {"App SSH Username",       rs.getAppSshUsername()},
                {"App SSH Password",       rs.getAppSshPassword()},
                {"App WinRM Port",         String.valueOf(rs.getAppWinrmPort())},
                {"App WinRM HTTPS",        b2s(rs.isAppWinrmUseHttps())},
                {"App WinRM Username",     rs.getAppWinrmUsername()},
                {"App WinRM Password",     rs.getAppWinrmPassword()},
                {"DB Server Host",         nullSafe(rs.getDbHost())},
                {"DB SSH Port",            String.valueOf(rs.getDbSshPort())},
                {"DB SSH Username",        rs.getDbSshUsername()},
                {"DB SSH Password",        rs.getDbSshPassword()},
                {"DB WinRM Port",          String.valueOf(rs.getDbWinrmPort())},
                {"DB WinRM HTTPS",         b2s(rs.isDbWinrmUseHttps())},
                {"DB WinRM Username",      rs.getDbWinrmUsername()},
                {"DB WinRM Password",      rs.getDbWinrmPassword()}
        };
        ExtentManager.getExtentTest().info("<b>Remote Server Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Effective per-area mode: OFF (not run) / FAILURES_ONLY (run, report failures only) / FULL (run, full report). */
    private static String validationModes() {
        try {
            com.gsl.validation.ValidationSwitches.Area[] areas = com.gsl.validation.ValidationSwitches.Area.values();
            StringBuilder sb = new StringBuilder();
            for (com.gsl.validation.ValidationSwitches.Area a : areas) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(a).append("=").append(com.gsl.validation.ValidationSwitches.mode(a));
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return "unresolved: " + e.getMessage();
        }
    }

    /** Renders the Test Data Configuration table (data source and file paths) on the current test node. */
    private static void renderTestDataSection(TestDataConfig td) {
        String[][] table = {
                {"Field", "Value"},
                {"Test Data Source",     nullSafe(td.getSource())},
                {"DB Available (db.available)", nullSafe(td.getDbAvailable())},
                {"Validation without DB (validation.nodb.mode)", nullSafe(td.getValidationNoDbMode())},
                {"Validation modes (UI / API / DB)", validationModes()},
                {"Master Test Data",     nullSafe(td.getMasterTestData())},
                {"Receipts Data",        nullSafe(td.getReceiptsData())},
                {"Inventory Group Data", nullSafe(td.getInventoryGroupData())},
                {"Inventory Item Data",  nullSafe(td.getInventoryItemData())},
                {"AR Voucher Data",      nullSafe(td.getArVoucherData())},
                {"Users List Data",      nullSafe(td.getUsersListData())}
        };
        ExtentManager.getExtentTest().info("<b>Test Data Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Performance Configuration table on the current test node. */
    private static void renderPerformanceSection(PerformanceConfig p) {
        String[][] table = {
                {"Field", "Value"},
                {"Performance Timing",          b2s(p.isTimingEnabled())},
                {"Action Trail Buffer Size",     String.valueOf(p.getActionTrailBufferSize())},
                {"Action Trail Display Limit",   String.valueOf(p.getActionTrailDisplayLimit())},
                {"Custom Metrics",               b2s(p.isCustomMetricsEnabled())},
                {"Custom Metrics Mode",          nullSafe(p.getCustomMetricsCaptureMode())},
                {"Slow Resource Threshold (ms)", String.valueOf(p.getSlowResourceThresholdMs())},
                {"User Timing Warn (ms)",        String.valueOf(p.getUserTimingWarnMs())},
                {"Fail On Long Task Count",      String.valueOf(p.getFailOnLongTaskCount())}
        };
        ExtentManager.getExtentTest().info("<b>Performance Configuration</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders the Application Log Sources table (one row per configured log path) on the current test node; no-op if {@code sources} is empty. */
    private static void renderAppLogSourcesSection(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) return;
        String[][] table = new String[sources.size() + 1][2];
        table[0] = new String[]{"Log Source", "Status"};
        int i = 1;
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            table[i][0] = entry.getKey();
            table[i][1] = entry.getValue();
            i++;
        }
        ExtentManager.getExtentTest().info("<b>Application Log Sources</b>");
        ExtentManager.getExtentTest().info(MarkupHelper.createTable(table));
    }

    /** Renders a config value for display, substituting {@code "Not Configured"} for null/blank. */
    private static String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "Not Configured" : value;
    }

    /** Renders a boolean config flag as {@code "enabled"}/{@code "disabled"}. */
    private static String b2s(boolean value) {
        return value ? "enabled" : "disabled";
    }
}
