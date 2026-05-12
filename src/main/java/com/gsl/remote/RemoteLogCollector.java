package com.gsl.remote;

import com.gsl.config.ConfigFactory;
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
import com.gsl.utils.Log;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

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

        Map<ServerRole, LogFetchResponse> result = new EnumMap<>(ServerRole.class);
        result.put(ServerRole.APP, null);
        result.put(ServerRole.DB,  null);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (appHost != null && !appHost.isBlank()) {
            futures.add(CompletableFuture.runAsync(
                    () -> result.put(ServerRole.APP, collectOne(ServerRole.APP, appHost, start, end)),
                    EXECUTOR));
        }
        if (dbHost != null && !dbHost.isBlank()) {
            futures.add(CompletableFuture.runAsync(
                    () -> result.put(ServerRole.DB, collectOne(ServerRole.DB, dbHost, start, end)),
                    EXECUTOR));
        }

        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(120, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                Log.warn("[RemoteLogCollector] Timed out waiting for remote log collection (120s cap)");
            } catch (Exception e) {
                Log.warn("[RemoteLogCollector] Error during concurrent log collection: {}", e.getMessage());
            }
        }

        return Collections.unmodifiableMap(result);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private LogFetchResponse collectOne(ServerRole role, String host, Instant start, Instant end) {
        try {
            List<String> keywords = keywords();

            // Detect OS — SSH probe with 5-second timeout; failure → WINDOWS
            Credentials sshCreds = Credentials.fromSshEnv();
            int sshPort = sshPortFor(role);
            OSType osType = OsDetector.detect(host, sshPort, sshCreds);

            Credentials creds = (osType == OSType.LINUX) ? sshCreds : Credentials.fromWinRmEnv();
            if (creds == null) {
                Log.warn("[RemoteLogCollector] No credentials for {} ({}) — skipping", role.label, osType);
                return null;
            }

            LogFetchRequest request = buildRequest(role, host, osType, creds, start, end);
            LogFetcher fetcher = selectFetcher(osType, keywords, start, end);
            return fetcher.fetch(request);

        } catch (Exception e) {
            Log.warn("[RemoteLogCollector] Collection failed for {} host={}: {}",
                    role.label, host, e.getMessage());
            return null;
        }
    }

    private LogFetchRequest buildRequest(ServerRole role, String host, OSType osType,
                                          Credentials creds, Instant start, Instant end) {
        boolean isApp = role == ServerRole.APP;

        int port = (osType == OSType.LINUX) ? sshPortFor(role) : winrmPortFor(role);
        boolean useHttps = isApp
                ? ConfigFactory.getConfig().remoteAppServerWinrmUseHttps()
                : ConfigFactory.getConfig().remoteDbServerWinrmUseHttps();

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
                .connectTimeoutSeconds(ConfigFactory.getConfig().remoteSshConnectTimeoutSeconds())
                .readTimeoutSeconds(ConfigFactory.getConfig().remoteSshReadTimeoutSeconds())
                .commandTimeoutSeconds(ConfigFactory.getConfig().remoteSshCommandTimeoutSeconds())
                .retryCount(ConfigFactory.getConfig().remoteSshRetryCount())
                .maxResultLines(ConfigFactory.getConfig().remoteLogsMaxLines())
                .winrmUseHttps(useHttps)
                .build();
    }

    private LogFetcher selectFetcher(OSType osType, List<String> keywords, Instant start, Instant end) {
        if (osType == OSType.LINUX) {
            return new SshLogFetcher(
                    new RegexLogParser(),
                    new KeywordTimeWindowFilter(start, end, keywords),
                    new RetryPolicy(ConfigFactory.getConfig().remoteSshRetryCount(), 1_000L),
                    pool);
        }
        return new WinRmLogFetcher(
                new RegexLogParser(),
                new KeywordTimeWindowFilter(start, end, keywords),
                new RetryPolicy(ConfigFactory.getConfig().remoteSshRetryCount(), 1_000L));
    }

    private List<String> logPaths(ServerRole role) {
        boolean isApp = role == ServerRole.APP;

        // New per-server config keys take priority; fall back to legacy shared keys
        String raw = isApp
                ? firstNonBlank(ConfigFactory.getConfig().remoteAppServerLogPaths(),
                                ConfigFactory.getConfig().remoteLogsAppLogPaths())
                : firstNonBlank(ConfigFactory.getConfig().remoteDbServerLogPaths(),
                                ConfigFactory.getConfig().remoteLogsDbLogPaths());

        return Arrays.asList(raw.split(",\\s*"));
    }

    private List<String> keywords() {
        return Arrays.asList(
                ConfigFactory.getConfig().remoteLogsKeywords().split(",\\s*"));
    }

    private static int sshPortFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getConfig().remoteAppServerSshPort()
                : ConfigFactory.getConfig().remoteDbServerSshPort();
    }

    private static int winrmPortFor(ServerRole role) {
        return (role == ServerRole.APP)
                ? ConfigFactory.getConfig().remoteAppServerWinrmPort()
                : ConfigFactory.getConfig().remoteDbServerWinrmPort();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }
}
