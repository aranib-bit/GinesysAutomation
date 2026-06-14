package com.gsl.remote.fetcher;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.filter.LogFileNameDateFilter;
import com.gsl.remote.filter.LogFilter;
import com.gsl.remote.model.*;
import com.gsl.remote.parser.LogFormat;
import com.gsl.remote.parser.LogParser;
import com.gsl.remote.retry.RetryPolicy;
import com.gsl.remote.validation.LogFetchRequestValidator;
import com.gsl.utils.Log;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    .filesRead(response.getFilesRead())
                    .fileErrors(response.getFileErrors())
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

            // Select-String exits 1 when no lines match — same as grep on Linux.
            // Treat exit 1 with empty stderr as "no data found", not a command failure.
            if (exitCode != 0 && !(exitCode == 1 && (stderr == null || stderr.isBlank()))) {
                Log.error("[WinRmLogFetcher] Command failed. host={} exitCode={} stderr={}",
                        request.getHost(), exitCode, stderr);
                throw new RemoteCommandException(
                        "WinRM command non-zero exit on " + request.getHost(), exitCode, stderr);
            }

            ParseResult parsed = parseAndFilter(stdout, request);
            return LogFetchResponse.builder()
                    .entries(parsed.entries())
                    .filesRead(parsed.filesRead())
                    .fileErrors(parsed.fileErrors())
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
     * <p>File-based commands emit a {@code ###FILE:fullpath###} sentinel before each file's
     * content so the Java side can group log lines by source file.
     *
     * <p>If any log path starts with {@code EventLog:}, reads from the Windows Event Log instead
     * (no per-file grouping in that case).
     */
    private String buildPowerShellCommand(LogFetchRequest request) {
        long max = request.getMaxResultLines();
        int maxRotated = ConfigFactory.getRemoteLogConfig().remoteLogsWinrmRotatedMaxFiles();

        boolean useEventLog = request.getLogPaths().stream()
                .anyMatch(p -> p.startsWith(EVENT_LOG_PREFIX));

        if (useEventLog) {
            String logName = request.getLogPaths().stream()
                    .filter(p -> p.startsWith(EVENT_LOG_PREFIX))
                    .map(p -> p.substring(EVENT_LOG_PREFIX.length()).trim())
                    .findFirst().orElse("Application");
            return String.format(
                    "$ProgressPreference='SilentlyContinue'; " +
                    "Get-EventLog -LogName '%s' -EntryType Error,Warning -Newest %d | " +
                    "Format-List TimeGenerated,EntryType,Message | Out-String -Width 512",
                    logName, max);
        }

        // File-based: resolve wildcards via Get-ChildItem (handles date-named + rotated files),
        // sort by LastWriteTime descending and cap per pattern, then read content.
        // Each file is preceded by a ###FILE:fullpath### sentinel for per-file grouping in Java.
        String pathArray = request.getLogPaths().stream()
                .map(p -> "'" + p.replace("'", "''") + "'")
                .collect(Collectors.joining(","));

        String dateFilterClause = LogFileNameDateFilter.buildPsDateFilterClause(
                request.getStartUtc(), request.getEndUtc());

        // Build the per-file content reader.
        // When the time window is known we stream the full file and emit only lines whose
        // leading timestamp falls inside [startTs, endTs]; continuation lines (no timestamp)
        // inherit the in-window state of the preceding entry so multi-line SQL blocks are
        // kept whole. This replaces the old "Select-Object -Last N" tail which silently
        // dropped early-in-file entries whenever the log file spanned more than N lines.
        String startStr = LogFileNameDateFilter.serverTimeString(request.getStartUtc());
        String endStr   = LogFileNameDateFilter.serverTimeString(request.getEndUtc());
        boolean hasWindow = (startStr != null && endStr != null);

        String fileReadCmd;
        if (hasWindow && (request.getSearchKeywords() == null || request.getSearchKeywords().isEmpty())) {
            // No-keyword case: stream file with timestamp-based window tracking.
            fileReadCmd =
                    "$inWin = $false; " +
                    "Get-Content $f.FullName -ErrorAction Stop | ForEach-Object { " +
                    "  if ($_ -match '^[^\\d]{0,10}(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2})') { " +
                    "    try { $ts = [DateTime]::ParseExact(($Matches[1] -replace 'T',' '), " +
                    "      'yyyy-MM-dd HH:mm:ss', $null); " +
                    "      $inWin = ($ts -ge $startTs -and $ts -le $endTs) " +
                    "    } catch { $inWin = $false } " +
                    "  }; " +
                    "  if ($inWin) { $_ } " +
                    "};";
        } else if (hasWindow) {
            // Keyword case: accumulate per-entry blocks, flush only when keyword matches.
            String pattern = request.getSearchKeywords().stream()
                    .map(k -> k.replace("'", "''"))
                    .collect(Collectors.joining("|"));
            fileReadCmd = String.format(
                    "$inWin = $false; " +
                    "$blk = [System.Collections.Generic.List[string]]::new(); " +
                    "Get-Content $f.FullName -ErrorAction Stop | ForEach-Object { " +
                    "  if ($_ -match '^[^\\d]{0,10}(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2})') { " +
                    "    if ($blk.Count -gt 0) { " +
                    "      if ($blk | Where-Object { $_ -match '%s' }) { $blk | Write-Output } " +
                    "      $blk.Clear() " +
                    "    }; " +
                    "    try { $ts = [DateTime]::ParseExact(($Matches[1] -replace 'T',' '), " +
                    "      'yyyy-MM-dd HH:mm:ss', $null); " +
                    "      $inWin = ($ts -ge $startTs -and $ts -le $endTs) " +
                    "    } catch { $inWin = $false } " +
                    "  }; " +
                    "  if ($inWin) { [void]$blk.Add($_) } " +
                    "}; " +
                    "if ($blk.Count -gt 0 -and ($blk | Where-Object { $_ -match '%s' })) " +
                    "  { $blk | Write-Output };",
                    pattern, pattern);
        } else if (request.getSearchKeywords() == null || request.getSearchKeywords().isEmpty()) {
            // No time window, no keywords: tail fallback.
            fileReadCmd = String.format(
                    "Get-Content $f.FullName -ErrorAction Stop | Select-Object -Last %d;", max);
        } else {
            // No time window, with keywords: Select-String fallback.
            String pattern = request.getSearchKeywords().stream()
                    .map(k -> k.replace("'", "''"))
                    .collect(Collectors.joining("|"));
            fileReadCmd = String.format(
                    "Get-Content $f.FullName -ErrorAction Stop | " +
                    "Select-String -Pattern '%s' | ForEach-Object { $_.Line };", pattern);
        }

        String windowInit = hasWindow
                ? String.format("$startTs = [DateTime]::ParseExact('%s', 'yyyy-MM-dd HH:mm:ss', $null); " +
                                "$endTs   = [DateTime]::ParseExact('%s', 'yyyy-MM-dd HH:mm:ss', $null); ",
                                startStr, endStr)
                : "";

        return String.format(
                "$ProgressPreference='SilentlyContinue'; " +
                "%s" +  // time-window variable init (empty when no window)
                "foreach ($p in @(%s)) { " +
                "  try { " +
                "    $files = @(Get-ChildItem $p -ErrorAction Stop%s | " +
                "      Sort-Object LastWriteTime -Descending | Select-Object -First %d); " +
                "  } catch { " +
                "    Write-Output ('###FILE_ERROR:' + $p + ':::' + $_.Exception.Message + '###'); " +
                "    continue; " +
                "  } " +
                "  if ($files.Count -eq 0) { " +
                "    Write-Output ('###FILE_ERROR:' + $p + ':::No matching files###'); " +
                "    continue; " +
                "  } " +
                "  foreach ($f in $files) { " +
                "    Write-Output ('###FILE:' + $f.FullName + '###'); " +
                "    try { %s } catch { " +  // file content read command
                "      Write-Output ('###FILE_ERROR:' + $f.FullName + ':::' + $_.Exception.Message + '###'); " +
                "    } " +
                "  } " +
                "}",
                windowInit, pathArray, dateFilterClause, maxRotated, fileReadCmd);
    }

    private record ParseResult(List<LogEntry> entries, List<String> filesRead, Map<String, String> fileErrors) {}

    private ParseResult parseAndFilter(String stdout, LogFetchRequest request) {
        if (stdout == null || stdout.isBlank())
            return new ParseResult(List.of(), List.of(), Map.of());

        List<LogEntry>       entries     = new ArrayList<>();
        List<String>         filesRead   = new ArrayList<>();
        Map<String, String>  fileErrors  = new LinkedHashMap<>();
        String               currentFile = null;

        List<String> block     = new ArrayList<>();
        String       blockFile = null;

        for (String line : stdout.split("\r?\n")) {
            if (line.isBlank()) continue;

            if (line.startsWith("###FILE:") && line.endsWith("###")) {
                flushBlock(block, blockFile, entries, filter);
                block.clear();
                currentFile = line.substring(8, line.length() - 3);
                filesRead.add(currentFile);
                continue;
            }
            if (line.startsWith("###FILE_ERROR:") && line.endsWith("###")) {
                flushBlock(block, blockFile, entries, filter);
                block.clear();
                String rest    = line.substring("###FILE_ERROR:".length(), line.length() - 3);
                int    sep     = rest.indexOf(":::");
                String errPath = sep >= 0 ? rest.substring(0, sep) : rest;
                String errMsg  = sep >= 0 ? rest.substring(sep + 3) : "unknown error";
                fileErrors.put(errPath, errMsg);
                if (!filesRead.contains(errPath)) filesRead.add(errPath);
                continue;
            }

            if (LogFormat.ENTRY_START.matcher(line).find()) {
                flushBlock(block, blockFile, entries, filter);
                block.clear();
                blockFile = currentFile;
            }
            block.add(line);
        }
        flushBlock(block, blockFile, entries, filter);
        return new ParseResult(entries, filesRead, fileErrors);
    }

    private void flushBlock(List<String> block, String file,
                             List<LogEntry> entries, LogFilter filter) {
        if (block.isEmpty()) return;
        parser.parse(block.get(0)).ifPresentOrElse(base -> {
            String fullMsg = block.size() == 1
                    ? base.getMessage()
                    : base.getMessage() + "\n" + String.join("\n", block.subList(1, block.size()));
            LogEntry assembled = LogEntry.builder()
                    .timestamp(base.getTimestamp())
                    .level(base.getLevel())
                    .message(fullMsg)
                    .rawLine(String.join("\n", block))
                    .sourceFile(file)
                    .build();
            if (filter.matches(assembled)) entries.add(assembled);
        }, () -> {
            // Orphaned continuation lines before the first timestamped entry — emit as-is
            for (String raw : block) {
                parser.parse(raw).ifPresent(e -> {
                    if (filter.matches(e)) entries.add(LogEntry.builder()
                            .timestamp(e.getTimestamp()).level(e.getLevel())
                            .message(e.getMessage()).rawLine(e.getRawLine())
                            .sourceFile(file).build());
                });
            }
        });
    }

    private static String buildWinRmUrl(LogFetchRequest request) {
        String scheme = request.isWinrmUseHttps() ? "https" : "http";
        return scheme + "://" + request.getHost() + ":" + request.getPort() + "/wsman";
    }
}
