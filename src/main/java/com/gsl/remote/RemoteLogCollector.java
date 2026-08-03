package com.gsl.remote;

import com.gsl.config.ConfigFactory;
import com.gsl.config.RawPropertyReader;
import com.gsl.diagnostics.section.ServerRole;
import com.gsl.remote.detect.OsDetector;
import com.gsl.remote.fetcher.LogFetcher;
import com.gsl.remote.fetcher.SshLogFetcher;
import com.gsl.remote.fetcher.WinRmLogFetcher;
import com.gsl.remote.filter.KeywordTimeWindowFilter;
import com.gsl.remote.model.*;
import com.gsl.remote.parser.RegexLogParser;
import com.gsl.remote.pool.SshConnectionPool;
import com.gsl.remote.retry.RetryPolicy;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Phase 5 orchestrator — collects remote logs from APP and DB servers concurrently.
 *
 * <p>Replaces the ad-hoc {@code fetchRemoteLogs()} helper in {@code FailureBundleOrchestrator}.
 * OS type is auto-detected per host via {@link OsDetector} — callers never specify it.
 *
 * <p>Usage:
 * <pre>
 *   Map&lt;ServerRole, LogFetchResponse&gt; results =
 *       new RemoteLogCollector().collect(appHost, dbHost, start, end);
 * </pre>
 */
public final class RemoteLogCollector {

    // Bounded 2-thread daemon pool — one thread per server (APP + DB run concurrently).
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "remote-log-collector");
        t.setDaemon(true);
        return t;
    });

    // Fires the unloaded-config sanity log at most once per JVM.
    private static final java.util.concurrent.atomic.AtomicBoolean CONFIG_SANITY_CHECKED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final SshConnectionPool pool;

    public RemoteLogCollector() {
        this.pool = SshConnectionPool.getInstance();
    }

    /**
     * Collects logs from APP and DB servers concurrently.
     *
     * @param appHost hostname/IP of the app server; null or blank = skip
     * @param dbHost  hostname/IP of the DB server;  null or blank = skip
     * @param start   test start instant (UTC)
     * @param end     test end instant   (UTC)
     * @return unmodifiable map — a null value for a role means collection was skipped or failed
     */
    public Map<ServerRole, LogFetchResponse> collect(
            String appHost, String dbHost, Instant start, Instant end) {

        warnIfConfigUnloaded();
        warnIfHostUnreachable(appHost, ServerRole.APP);
        warnIfHostUnreachable(dbHost,  ServerRole.DB);

        // Fire-and-forget: kick off collection in the background and return immediately — never
        // block the calling test thread on slow WinRM/SSH I/O. This used to block here for up to
        // 120s via .get(), which held the caller's Grid session hostage well past when
        // SessionPool.giveBack() should run (ITestListener callbacks fire before @AfterMethod),
        // starving the session pool and contributing to Grid's idle-session timeout being
        // exceeded on other threads. The DiagnosticReport this test attaches to Extent simply
        // won't include remote-log content for this run (the same as any other skip/failure case,
        // already handled downstream via a null map value) — whatever the background fetch
        // eventually finds is instead written to a plain text file under the report run
        // directory for later inspection.
        if (appHost != null && !appHost.isBlank()) {
            CompletableFuture.supplyAsync(() -> collectOne(ServerRole.APP, appHost, start, end), EXECUTOR)
                    .whenComplete((resp, ex) -> persistAsyncResult(ServerRole.APP, start, resp, ex));
        }
        if (dbHost != null && !dbHost.isBlank()) {
            CompletableFuture.supplyAsync(() -> collectOne(ServerRole.DB, dbHost, start, end), EXECUTOR)
                    .whenComplete((resp, ex) -> persistAsyncResult(ServerRole.DB, start, resp, ex));
        }

        Map<ServerRole, LogFetchResponse> result = new EnumMap<>(ServerRole.class);
        result.put(ServerRole.APP, null);
        result.put(ServerRole.DB,  null);
        return Collections.unmodifiableMap(result);
    }

    /** Writes a background-completed remote-log fetch to disk once it arrives, since by then the
     *  synchronous DiagnosticReport/ExtentTest node for this test has already been built and
     *  attached and can no longer be updated in place. */
    private void persistAsyncResult(ServerRole role, Instant testStart, LogFetchResponse resp, Throwable ex) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(
                    com.gsl.constants.FrameworkConstants.getReportDir(), "remote-logs-async");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(role.label + "_" + testStart.toEpochMilli() + ".log");

            if (ex != null) {
                java.nio.file.Files.writeString(file, "Async remote-log collection failed: " + ex.getMessage());
            } else if (resp != null && resp.getEntries() != null && !resp.getEntries().isEmpty()) {
                List<String> lines = resp.getEntries().stream()
                        .map(com.gsl.remote.model.LogEntry::getRawLine)
                        .collect(Collectors.toList());
                java.nio.file.Files.write(file, lines);
            } else {
                return; // nothing to write — skipped or empty, no need for a placeholder file
            }
            Log.info("[RemoteLogCollector] Background collection for {} finished — written to {}", role.label, file);
        } catch (Exception e) {
            Log.warn("[RemoteLogCollector] Could not persist async remote-log result for {}: {}", role.label, e.getMessage());
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    // One-shot startup sanity log: if remote-log capture is enabled but every host
    // AND every credential key resolves blank, Owner almost certainly didn't load
    // remote-log.properties (wrong JVM working dir / missing file). Surface that
    // explicitly so the user isn't left chasing a generic "credentials not configured".
    private static void warnIfConfigUnloaded() {
        if (!CONFIG_SANITY_CHECKED.compareAndSet(false, true)) return;
        var cfg = ConfigFactory.getRemoteLogConfig();
        if ("NEVER".equalsIgnoreCase(cfg.remoteLogsCaptureMode())) return;
        boolean allBlank =
                isBlank(cfg.remoteAppServerHost())
                        && isBlank(cfg.remoteDbServerHost())
                        && isBlank(cfg.remoteAppServerWinrmUsername())
                        && isBlank(cfg.remoteDbServerWinrmUsername())
                        && isBlank(cfg.remoteAppServerSshUsername())
                        && isBlank(cfg.remoteDbServerSshUsername());
        if (allBlank) {
            Log.warn("[RemoteLogCollector] remote-log.properties looks unloaded — every host/credential key is blank. "
                    + "Expected at {}/src/test/resources/config/remote-log.properties. "
                    + "Verify the JVM working directory points at the project root.",
                    System.getProperty("user.dir"));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private LogFetchResponse collectOne(ServerRole role, String host, Instant start, Instant end) {
        try {
            List<String> keywords = keywords();

            // Detect OS — SSH probe uses env-var-only creds (no config context in OsDetector)
            int sshPort = sshPortFor(role);
            OSType osType = OsDetector.detect(host, sshPort, Credentials.fromSshEnv());

            // Resolve effective credentials: env vars take priority, config.properties is fallback
            Credentials creds = (osType == OSType.LINUX)
                    ? Credentials.fromSshEnvOrConfig(sshUsernameFor(role), sshPasswordFor(role))
                    : Credentials.fromWinRmEnvOrConfig(winrmUsernameFor(role), winrmPasswordFor(role));

            if (creds == null) {
                Log.warn("[RemoteLogCollector] No credentials for {} ({}) — skipping", role.label, osType);
                LogFetchResponse.SkipReason reason = (osType == OSType.LINUX)
                        ? LogFetchResponse.SkipReason.CREDS_MISSING_SSH
                        : LogFetchResponse.SkipReason.CREDS_MISSING_WINRM;
                return LogFetchResponse.builder()
                        .skipReason(reason)
                        .entries(List.of())
                        .stderr("")
                        .exitCode(0)
                        .retriesUsed(0)
                        .durationMs(0)
                        .configuredPaths(logPaths(role))
                        .timeWindowStart(start)
                        .timeWindowEnd(end)
                        .build();
            }

            LogFetchRequest request = buildRequest(role, host, osType, creds, start, end);
            LogFetcher fetcher = selectFetcher(osType, keywords, start, end);
            LogFetchResponse raw = fetcher.fetch(request);
            return raw.toBuilder()
                    .configuredPaths(request.getLogPaths())
                    .timeWindowStart(request.getStartUtc())
                    .timeWindowEnd(request.getEndUtc())
                    .build();

        } catch (Throwable e) {
            Log.warn("[RemoteLogCollector] Collection failed for {} host={}: {}",
                    role.label, host, e.getMessage());
            ReportLogger.frameworkWarn("[RemoteLogCollector] Collection failed for {} host={}: {}",
                    role.label, host, e.getMessage());
            return LogFetchResponse.builder()
                    .skipReason(LogFetchResponse.SkipReason.FETCH_FAILED)
                    .entries(List.of())
                    .stderr("Log collection failed: " + e.getMessage())
                    .exitCode(-1)
                    .retriesUsed(0)
                    .durationMs(0)
                    .configuredPaths(logPaths(role))
                    .timeWindowStart(start)
                    .timeWindowEnd(end)
                    .build();
        }
    }

    private static void warnIfHostUnreachable(String host, ServerRole role) {
        if (host == null || host.isBlank()) return;
        try {
            if (!java.net.InetAddress.getByName(host).isReachable(3_000)) {
                Log.warn("[RemoteLogCollector] {} host {} did not respond to ping — "
                        + "remote log collection will likely fail. "
                        + "Verify the host is up and WinRM/SSH port is open.",
                        role.label, host);
            }
        } catch (Exception e) {
            Log.warn("[RemoteLogCollector] Could not probe {} host {}: {}",
                    role.label, host, e.getMessage());
        }
    }

    private LogFetchRequest buildRequest(ServerRole role, String host, OSType osType,
                                          Credentials creds, Instant start, Instant end) {
        boolean isApp = role == ServerRole.APP;

        int port = (osType == OSType.LINUX) ? sshPortFor(role) : winrmPortFor(role);
        boolean useHttps = isApp
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerWinrmUseHttps()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerWinrmUseHttps();

        List<String> logPaths = logPaths(role);
        List<String> keywords = keywords();

        return LogFetchRequest.builder()
                .host(host)
                .port(port)
                .osType(osType)
                .credentials(creds)
                .startUtc(start)
                .endUtc(end)
                .logPaths(logPaths)
                .searchKeywords(keywords)
                .connectTimeoutSeconds(ConfigFactory.getRemoteLogConfig().remoteSshConnectTimeoutSeconds())
                .readTimeoutSeconds(ConfigFactory.getRemoteLogConfig().remoteSshReadTimeoutSeconds())
                .commandTimeoutSeconds(ConfigFactory.getRemoteLogConfig().remoteSshCommandTimeoutSeconds())
                .retryCount(ConfigFactory.getRemoteLogConfig().remoteSshRetryCount())
                .maxResultLines(ConfigFactory.getRemoteLogConfig().remoteLogsMaxLines())
                .winrmUseHttps(useHttps)
                .build();
    }

    private LogFetcher selectFetcher(OSType osType, List<String> keywords, Instant start, Instant end) {
        if (osType == OSType.LINUX) {
            return new SshLogFetcher(
                    new RegexLogParser(),
                    new KeywordTimeWindowFilter(start, end, keywords),
                    new RetryPolicy(ConfigFactory.getRemoteLogConfig().remoteSshRetryCount(), 1_000L),
                    pool);
        }
        return new WinRmLogFetcher(
                new RegexLogParser(),
                new KeywordTimeWindowFilter(start, end, keywords),
                new RetryPolicy(ConfigFactory.getRemoteLogConfig().remoteSshRetryCount(), 1_000L));
    }

    private List<String> logPaths(ServerRole role) {
        boolean isApp = role == ServerRole.APP;

        // File-based config takes priority for APP server: one path per line, backslashes safe.
        if (isApp) {
            String fileName = ConfigFactory.getRemoteLogConfig().remoteAppServerLogPathsFile();
            if (fileName != null && !fileName.isBlank()) {
                List<String> paths = RawPropertyReader.readAllRawValues(fileName);
                if (!paths.isEmpty()) return paths;
                Log.warn("[RemoteLogCollector] '{}' not found or empty — falling back to config key", fileName);
            }
        }

        // Read log paths via RawPropertyReader to preserve Windows backslashes verbatim.
        // Java's Properties.load() treats '\' as an escape character, silently dropping
        // backslashes (e.g. C:\GSL → C:GSL) and converting \t → TAB, \n → newline.
        String primaryKey = isApp ? "remote.app.server.log.paths" : "remote.db.server.log.paths";
        String legacyKey  = isApp ? "remote.logs.app.log.paths"   : "remote.logs.db.log.paths";
        String defaultVal = isApp ? "/var/log/ginesys/app.log"    : "/var/log/postgresql/postgresql.log";

        String raw = firstNonBlank(
                RawPropertyReader.readRaw(primaryKey, null),
                firstNonBlank(
                        RawPropertyReader.readRaw(legacyKey, null),
                        defaultVal));

        return Arrays.asList(raw.split(",\\s*"));
    }

    private List<String> keywords() {
        String raw = ConfigFactory.getRemoteLogConfig().remoteLogsKeywords();
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",\\s*"))
                .filter(k -> !k.isBlank())
                .collect(Collectors.toList());
    }

    private static int sshPortFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerSshPort()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerSshPort();
    }

    private static int winrmPortFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerWinrmPort()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerWinrmPort();
    }

    private static String winrmUsernameFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerWinrmUsername()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerWinrmUsername();
    }

    private static String winrmPasswordFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerWinrmPassword()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerWinrmPassword();
    }

    private static String sshUsernameFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerSshUsername()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerSshUsername();
    }

    private static String sshPasswordFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerSshPassword()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerSshPassword();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }
}
