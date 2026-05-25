package com.gsl.remote.fetcher;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.filter.LogFileNameDateFilter;
import com.gsl.remote.filter.LogFilter;
import com.gsl.remote.model.*;
import com.gsl.remote.parser.LogFormat;
import com.gsl.remote.parser.LogParser;
import com.gsl.remote.pool.SshConnectionPool;
import com.gsl.remote.retry.RetryPolicy;
import com.gsl.remote.validation.LogFetchRequestValidator;
import com.gsl.utils.Log;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import org.apache.commons.io.IOUtils;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Fetches log lines from a Linux server over SSH using the SSHJ library.
 *
 * Command strategy:
 *   grep -h KEYWORD /path/to/log | tail -n MAX_LINES
 *
 * Input validation is delegated to {@link LogFetchRequestValidator}.
 * Host-key verification mode is config-driven ({@code remote.logs.ssh.host.key.verify}).
 * When a {@link SshConnectionPool} is provided, connections are pooled across tests.
 */
public final class SshLogFetcher implements LogFetcher {

    private final LogParser         parser;
    private final LogFilter         filter;
    private final RetryPolicy       retryPolicy;
    private final SshConnectionPool pool; // null = create/close per fetch

    public SshLogFetcher(LogParser parser, LogFilter filter) {
        this(parser, filter, new RetryPolicy(3, 1_000L), null);
    }

    public SshLogFetcher(LogParser parser, LogFilter filter, RetryPolicy retryPolicy) {
        this(parser, filter, retryPolicy, null);
    }

    public SshLogFetcher(LogParser parser, LogFilter filter,
                         RetryPolicy retryPolicy, SshConnectionPool pool) {
        this.parser      = parser;
        this.filter      = filter;
        this.retryPolicy = retryPolicy;
        this.pool        = pool;
    }

    @Override
    public boolean supports(OSType osType) {
        return osType == OSType.LINUX;
    }

    @Override
    public LogFetchResponse fetch(LogFetchRequest request) throws RemoteCommandException {
        LogFetchRequestValidator.validate(request);

        long start = System.currentTimeMillis();

        MDC.put("ssh.host", request.getHost());
        try {
            int[] retriesUsed = {0};
            LogFetchResponse response = retryPolicy.execute(
                    "SSH log fetch [" + request.getHost() + "]",
                    () -> {
                        LogFetchResponse r = doFetch(request);
                        retriesUsed[0]++;
                        return r;
                    });

            long durationMs = System.currentTimeMillis() - start;
            Log.info("[SshLogFetcher] host={} entries={} retries={} durationMs={}",
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
            throw new RemoteCommandException("SSH log fetch failed for " + request.getHost(), e);
        } finally {
            MDC.remove("ssh.host");
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private LogFetchResponse doFetch(LogFetchRequest request) throws IOException {
        if (pool != null) {
            return doFetchPooled(request);
        }
        try (SSHClient ssh = buildClient(request)) {
            ssh.connect(request.getHost(), request.getPort());
            ssh.authPassword(
                    request.getCredentials().getUsername(),
                    request.getCredentials().getPassword());
            return executeCommand(ssh, request);
        }
    }

    private LogFetchResponse doFetchPooled(LogFetchRequest request) throws IOException {
        SSHClient client = null;
        try {
            client = pool.borrow(
                    request.getHost(), request.getPort(),
                    request.getCredentials(),
                    request.getConnectTimeoutSeconds(),
                    request.getReadTimeoutSeconds(),
                    ConfigFactory.getConfig().remoteSshHostKeyVerify());
            return executeCommand(client, request);
        } catch (RemoteCommandException e) {
            throw e;
        } catch (Exception e) {
            pool.invalidate(request.getHost(), request.getPort(), client);
            client = null;
            throw new IOException("Pooled SSH fetch failed for " + request.getHost(), e);
        } finally {
            if (client != null) pool.returnClient(request.getHost(), request.getPort(), client);
        }
    }

    private LogFetchResponse executeCommand(SSHClient ssh, LogFetchRequest request) throws IOException {
        String command = buildCommand(request);
        Log.debug("[SshLogFetcher] Executing: {}", command);

        try (Session session = ssh.startSession();
             Command cmd    = session.exec(command)) {

            String stdout = IOUtils.toString(cmd.getInputStream(), StandardCharsets.UTF_8);
            String stderr = IOUtils.toString(cmd.getErrorStream(), StandardCharsets.UTF_8);

            cmd.join(request.getCommandTimeoutSeconds(), TimeUnit.SECONDS);

            Integer exitCode = cmd.getExitStatus();
            if (exitCode == null) {
                Log.error("[SshLogFetcher] Command timed out. host={} timeout={}s",
                        request.getHost(), request.getCommandTimeoutSeconds());
                throw new RemoteCommandException(
                        "Remote command timeout on " + request.getHost(), -1, "");
            }

            if (exitCode != 0 && exitCode != 1) {
                // grep returns 1 when no lines match — that is not an error
                Log.error("[SshLogFetcher] Command failed. host={} exitCode={} stderr={}",
                        request.getHost(), exitCode, stderr);
                throw new RemoteCommandException(
                        "Remote command non-zero exit on " + request.getHost(),
                        exitCode, stderr);
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
        }
    }

    private SSHClient buildClient(LogFetchRequest request) {
        SSHClient ssh = new SSHClient();
        SshConnectionPool.applyHostKeyVerification(
                ssh, ConfigFactory.getConfig().remoteSshHostKeyVerify());
        ssh.setConnectTimeout(request.getConnectTimeoutSeconds() * 1_000);
        ssh.setTimeout(request.getReadTimeoutSeconds() * 1_000);
        return ssh;
    }

    private String buildCommand(LogFetchRequest request) {
        long max = request.getMaxResultLines();

        // Per-file loop: emit ###FILE:path### before each file, then read content.
        // On error emit ###FILE_ERROR:path:::msg### so Java can report per-file failures
        // while continuing to other files.
        String dateGuard = LogFileNameDateFilter.buildBashDateGuard(
                request.getStartUtc(), request.getEndUtc());

        String startStr = LogFileNameDateFilter.serverTimeString(request.getStartUtc());
        String endStr   = LogFileNameDateFilter.serverTimeString(request.getEndUtc());
        boolean hasWindow = (startStr != null && endStr != null);

        StringBuilder sb = new StringBuilder();

        // When a time window is known, initialise shell variables used inside the per-file loop.
        if (hasWindow) {
            sb.append("_start='").append(startStr).append("'; ");
            sb.append("_end='").append(endStr).append("'; ");
        }

        sb.append("for f in");
        for (String p : request.getLogPaths()) {
            sb.append(" '").append(p.replace("'", "'\\''")).append("'");
        }
        sb.append("; do for ff in $f; do [ -f \"$ff\" ] || continue; ");
        if (!dateGuard.isEmpty()) {
            sb.append(dateGuard);
        }
        sb.append("echo \"###FILE:$ff###\"; ");

        if (hasWindow && request.getSearchKeywords().isEmpty()) {
            // Stream the full file; emit only lines whose leading timestamp is within the window.
            // Continuation lines (no timestamp) inherit the in-window state of the preceding entry
            // so multi-line SQL blocks are kept whole. Replaces the old "tail -n N" which silently
            // dropped early-in-file entries when the log file exceeded N lines.
            sb.append("{ _inwin=0; ")
              .append("while IFS= read -r _ln || [ -n \"$_ln\" ]; do ")
              .append("  if printf '%s' \"$_ln\" | grep -qE '^[^0-9]{0,10}[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}'; then ")
              .append("    _ts=$(printf '%s' \"$_ln\" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}' | head -1); ")
              .append("    if [ -n \"$_ts\" ] && [ \"$_ts\" \\>= \"$_start\" ] && [ \"$_ts\" \\<= \"$_end\" ]; then _inwin=1; else _inwin=0; fi; ")
              .append("  fi; ")
              .append("  if [ \"$_inwin\" = '1' ]; then printf '%s\\n' \"$_ln\"; fi; ")
              .append("done < \"$ff\"; } 2>/tmp/__ferr_$$ || ")
              .append("echo \"###FILE_ERROR:$ff:::$(cat /tmp/__ferr_$$ 2>/dev/null)###\"; ")
              .append("rm -f /tmp/__ferr_$$; ");
        } else if (hasWindow) {
            // Keyword case: stream with time-window tracking, accumulate blocks, flush when keyword matches.
            String pattern = request.getSearchKeywords().stream()
                    .map(k -> k.replace("'", "'\\''"))
                    .collect(Collectors.joining("|"));
            sb.append("{ _inwin=0; _blk=''; ")
              .append("while IFS= read -r _ln || [ -n \"$_ln\" ]; do ")
              .append("  if printf '%s' \"$_ln\" | grep -qE '^[^0-9]{0,10}[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}'; then ")
              .append("    if [ -n \"$_blk\" ] && printf '%s' \"$_blk\" | grep -qiE '").append(pattern).append("'; then printf '%s\\n' \"$_blk\"; fi; ")
              .append("    _blk=''; ")
              .append("    _ts=$(printf '%s' \"$_ln\" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}' | head -1); ")
              .append("    if [ -n \"$_ts\" ] && [ \"$_ts\" \\>= \"$_start\" ] && [ \"$_ts\" \\<= \"$_end\" ]; then _inwin=1; else _inwin=0; fi; ")
              .append("  fi; ")
              .append("  if [ \"$_inwin\" = '1' ]; then _blk=\"${_blk:+$_blk$'\\n'}$_ln\"; fi; ")
              .append("done < \"$ff\"; ")
              .append("if [ -n \"$_blk\" ] && printf '%s' \"$_blk\" | grep -qiE '").append(pattern).append("'; then printf '%s\\n' \"$_blk\"; fi; ")
              .append("} 2>/tmp/__ferr_$$ || ")
              .append("echo \"###FILE_ERROR:$ff:::$(cat /tmp/__ferr_$$ 2>/dev/null)###\"; ")
              .append("rm -f /tmp/__ferr_$$; ");
        } else if (request.getSearchKeywords().isEmpty()) {
            // No time window, no keywords: tail fallback.
            sb.append("tail -n ").append(max).append(" \"$ff\" 2>/tmp/__ferr_$$ || ")
              .append("echo \"###FILE_ERROR:$ff:::$(cat /tmp/__ferr_$$ 2>/dev/null)###\"; ")
              .append("rm -f /tmp/__ferr_$$; ");
        } else {
            // No time window, with keywords: grep fallback (exit 1 = no match, not an error).
            String pattern = request.getSearchKeywords().stream()
                    .map(k -> k.replace("'", "'\\''"))
                    .collect(Collectors.joining("|"));
            sb.append("_out=$(grep -i -E '").append(pattern).append("' \"$ff\" 2>/tmp/__ferr_$$); _ec=$?; ")
              .append("if [ $_ec -le 1 ]; then echo \"$_out\"; ")
              .append("else echo \"###FILE_ERROR:$ff:::$(cat /tmp/__ferr_$$ 2>/dev/null || echo exit\\ $_ec)###\"; fi; ")
              .append("rm -f /tmp/__ferr_$$; ");
        }
        sb.append("done; done");
        return sb.toString();
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

        for (String line : stdout.split("\n")) {
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
}
