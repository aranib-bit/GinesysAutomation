package com.gsl.remote.fetcher;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.filter.LogFilter;
import com.gsl.remote.model.*;
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
import java.util.List;
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

            List<LogEntry> entries = parseAndFilter(stdout, request);
            return LogFetchResponse.builder()
                    .entries(entries)
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
        String paths = request.getLogPaths().stream()
                .map(p -> "'" + p + "'")
                .collect(Collectors.joining(" "));

        if (request.getSearchKeywords().isEmpty()) {
            return "cat " + paths + " | tail -n " + request.getMaxResultLines();
        }

        String pattern = request.getSearchKeywords().stream()
                .map(k -> k.replace("'", "'\\''"))
                .collect(Collectors.joining("|"));

        return "grep -h -i -E '" + pattern + "' " + paths
                + " | tail -n " + request.getMaxResultLines();
    }

    private List<LogEntry> parseAndFilter(String stdout, LogFetchRequest request) {
        if (stdout == null || stdout.isBlank()) return List.of();

        List<LogEntry> result = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            parser.parse(line).ifPresent(entry -> {
                if (filter.matches(entry)) result.add(entry);
            });
        }
        return result;
    }
}
