package com.gsl.remote.fetcher;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.filter.LogFilter;
import com.gsl.remote.model.*;
import com.gsl.remote.parser.LogParser;
import com.gsl.remote.retry.RetryPolicy;
import com.gsl.remote.validation.LogFetchRequestValidator;
import com.gsl.utils.Log;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches log entries from a Windows server via WinRM using winrm4j.
 *
 * <p>PowerShell command strategy:
 * <ul>
 *   <li>File-based logs: {@code Get-Content} piped through {@code Select-String}</li>
 *   <li>Event Log (path prefix {@code EventLog:}): {@code Get-EventLog} with severity filter</li>
 * </ul>
 *
 * <p>WinRM is stateless HTTP — no connection pool is used; each fetch issues an HTTP POST.
 * Credentials are read from environment variable {@code REMOTE_LOG_WINRM_USER} / {@code REMOTE_LOG_WINRM_PASS}.
 */
public final class WinRmLogFetcher implements LogFetcher {

    private static final String EVENT_LOG_PREFIX = "EventLog:";

    private final LogParser   parser;
    private final LogFilter   filter;
    private final RetryPolicy retryPolicy;

    public WinRmLogFetcher(LogParser parser, LogFilter filter) {
        this(parser, filter, new RetryPolicy(3, 1_000L));
    }

    public WinRmLogFetcher(LogParser parser, LogFilter filter, RetryPolicy retryPolicy) {
        this.parser      = parser;
        this.filter      = filter;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public boolean supports(OSType osType) {
        return osType == OSType.WINDOWS;
    }

    @Override
    public LogFetchResponse fetch(LogFetchRequest request) throws RemoteCommandException {
        LogFetchRequestValidator.validate(request);

        long start = System.currentTimeMillis();
        MDC.put("winrm.host", request.getHost());

        try {
            int[] retriesUsed = {0};
            LogFetchResponse response = retryPolicy.execute(
                    "WinRM log fetch [" + request.getHost() + "]",
                    () -> {
                        LogFetchResponse r = doFetch(request);
                        retriesUsed[0]++;
                        return r;
                    });

            long durationMs = System.currentTimeMillis() - start;
            Log.info("[WinRmLogFetcher] host={} entries={} retries={} durationMs={}",
                    request.getHost(), response.getEntries().size(),
                    retriesUsed[0] - 1, durationMs);

            return LogFetchResponse.builder()
                    .entries(response.getEntries())
                    .stderr(response.getStderr())
                    .exitCode(response.getExitCode())
                    .retriesUsed(retriesUsed[0] - 1)
                    .durationMs(durationMs)
                    .build();

        } catch (RemoteCommandException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteCommandException("WinRM log fetch failed for " + request.getHost(), e);
        } finally {
            MDC.remove("winrm.host");
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private LogFetchResponse doFetch(LogFetchRequest request) throws Exception {
        String url    = buildWinRmUrl(request);
        String user   = request.getCredentials().getUsername();
        String pass   = request.getCredentials().getPassword();
        String script = buildPowerShellCommand(request);

        Log.debug("[WinRmLogFetcher] url={} script={}", url, script);

        WinRmClientContext ctx = WinRmClientContext.newInstance();
        try {
            WinRmTool tool = WinRmTool.Builder.builder(request.getHost(), user, pass)
                    .useHttps(request.isWinrmUseHttps())
                    .port(request.getPort())
                    .context(ctx)
                    .build();

            WinRmToolResponse response = tool.executePs(script);

            String stdout   = response.getStdOut();
            String stderr   = response.getStdErr();
            int    exitCode = response.getStatusCode();

            if (exitCode != 0) {
                Log.error("[WinRmLogFetcher] Command failed. host={} exitCode={} stderr={}",
                        request.getHost(), exitCode, stderr);
                throw new RemoteCommandException(
                        "WinRM command non-zero exit on " + request.getHost(), exitCode, stderr);
            }

            List<LogEntry> entries = parseAndFilter(stdout, request);
            return LogFetchResponse.builder()
                    .entries(entries)
                    .stderr(stderr != null ? stderr.trim() : "")
                    .exitCode(exitCode)
                    .retriesUsed(0)
                    .durationMs(0)
                    .build();
        } finally {
            ctx.shutdown();
        }
    }

    /**
     * Builds a PowerShell command to read and filter log files.
     *
     * <p>Path patterns support wildcards (e.g. {@code FinanceAPI_*_ERROR}) and auto-rotated
     * files (e.g. {@code ErrorApp*}). Each pattern is first expanded via {@code Get-ChildItem},
     * results sorted by LastWriteTime descending so the most recent files are read first.
     * At most {@code maxRotatedFiles} files are read per pattern to cap I/O on busy servers.
     *
     * <p>If any log path starts with {@code EventLog:}, reads from the Windows Event Log instead.
     */
    private String buildPowerShellCommand(LogFetchRequest request) {
        long max = request.getMaxResultLines();
        int maxRotated = ConfigFactory.getConfig().remoteLogsWinrmRotatedMaxFiles();

        boolean useEventLog = request.getLogPaths().stream()
                .anyMatch(p -> p.startsWith(EVENT_LOG_PREFIX));

        if (useEventLog) {
            String logName = request.getLogPaths().stream()
                    .filter(p -> p.startsWith(EVENT_LOG_PREFIX))
                    .map(p -> p.substring(EVENT_LOG_PREFIX.length()).trim())
                    .findFirst().orElse("Application");
            return String.format(
                    "Get-EventLog -LogName '%s' -EntryType Error,Warning -Newest %d | " +
                    "Format-List TimeGenerated,EntryType,Message | Out-String -Width 512",
                    logName, max);
        }

        // File-based: resolve wildcards via Get-ChildItem (handles date-named + rotated files),
        // sort by LastWriteTime descending and cap per pattern, then read content.
        String pathArray = request.getLogPaths().stream()
                .map(p -> "'" + p.replace("'", "''") + "'")
                .collect(Collectors.joining(","));

        if (request.getSearchKeywords() == null || request.getSearchKeywords().isEmpty()) {
            return String.format(
                    "@(%s) | ForEach-Object { " +
                    "  Get-ChildItem $_ -ErrorAction SilentlyContinue | " +
                    "  Sort-Object LastWriteTime -Descending | Select-Object -First %d " +
                    "} | ForEach-Object { Get-Content $_.FullName -ErrorAction SilentlyContinue } | " +
                    "Select-Object -Last %d",
                    pathArray, maxRotated, max);
        }

        String pattern = request.getSearchKeywords().stream()
                .map(k -> k.replace("'", "''"))
                .collect(Collectors.joining("|"));

        return String.format(
                "@(%s) | ForEach-Object { " +
                "  Get-ChildItem $_ -ErrorAction SilentlyContinue | " +
                "  Sort-Object LastWriteTime -Descending | Select-Object -First %d " +
                "} | ForEach-Object { Get-Content $_.FullName -ErrorAction SilentlyContinue } | " +
                "Select-String -Pattern '%s' | Select-Object -Last %d | " +
                "ForEach-Object { $_.Line }",
                pathArray, maxRotated, pattern, max);
    }

    private List<LogEntry> parseAndFilter(String stdout, LogFetchRequest request) {
        if (stdout == null || stdout.isBlank()) return List.of();

        List<LogEntry> result = new ArrayList<>();
        for (String line : stdout.split("\r?\n")) {
            if (line.isBlank()) continue;
            parser.parse(line).ifPresent(entry -> {
                if (filter.matches(entry)) result.add(entry);
            });
        }
        return result;
    }

    private static String buildWinRmUrl(LogFetchRequest request) {
        String scheme = request.isWinrmUseHttps() ? "https" : "http";
        return scheme + "://" + request.getHost() + ":" + request.getPort() + "/wsman";
    }
}
