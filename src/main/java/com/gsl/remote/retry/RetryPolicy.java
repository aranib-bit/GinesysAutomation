package com.gsl.remote.retry;

import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.utils.Log;
import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.transport.TransportException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * Exponential-backoff retry wrapper for remote log fetching.
 *
 * <p>Used by {@link com.gsl.remote.fetcher.SshLogFetcher} and
 * {@link com.gsl.remote.fetcher.WinRmLogFetcher} to wrap the actual remote command execution —
 * only transient connection failures are retried; a command that ran and failed is not.
 * <ul>
 *   <li>Retry-eligible: {@code TransportException}, {@code SSHException},
 *       {@code SocketTimeoutException}, {@code TimeoutException}, and transient winrm4j
 *       exceptions (detected by class name, see {@link #isWinRmTransient}).</li>
 *   <li>Not retried: {@link RemoteCommandException} (non-zero exit / bad command),
 *       {@link IllegalArgumentException} (validation failure).</li>
 * </ul>
 */
public final class RetryPolicy {

    private final int  maxAttempts;
    private final long initialDelayMs;

    /**
     * @param maxAttempts    maximum number of attempts, including the first (non-retry) call
     * @param initialDelayMs delay before the first retry; doubles on each subsequent attempt, see {@link #delayMs(int)}
     */
    public RetryPolicy(int maxAttempts, long initialDelayMs) {
        this.maxAttempts    = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    /**
     * Wraps an operation in this retry policy, returning the first successful result.
     *
     * @throws RemoteCommandException  immediately, without retrying (see class Javadoc)
     * @throws IllegalArgumentException immediately, without retrying (see class Javadoc)
     * @throws Exception the last failure, once a non-retryable exception is thrown or
     *                    {@code maxAttempts} is exhausted
     */
    public <T> T execute(String operationLabel, Callable<T> operation) throws Exception {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return operation.call();
            } catch (RemoteCommandException | IllegalArgumentException e) {
                // non-retryable
                throw e;
            } catch (Exception e) {
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    Log.warn("[RetryPolicy] {} — giving up after {} attempt(s): {}",
                            operationLabel, attempt, e.getMessage());
                    throw e;
                }
                long delay = delayMs(attempt);
                Log.warn("[RetryPolicy] {} — attempt {} failed ({}); retrying in {}ms",
                        operationLabel, attempt, e.getClass().getSimpleName(), delay);
                Thread.sleep(delay);
            }
        }
    }

    /** delay = initialDelayMs × 2^(attempt-1), capped at 30 s */
    public long delayMs(int attempt) {
        long delay = initialDelayMs * (1L << (attempt - 1));
        return Math.min(delay, 30_000L);
    }

    /** Configured maximum number of attempts, including the first (non-retry) call. */
    public int getMaxAttempts() { return maxAttempts; }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** True for transient transport/timeout exceptions (SSH or WinRM) that are worth retrying. */
    private static boolean isRetryable(Exception e) {
        return e instanceof TransportException
                || e instanceof SSHException
                || e instanceof SocketTimeoutException
                || e instanceof TimeoutException
                || isWinRmTransient(e);
    }

    // Avoid a hard compile-time dependency on winrm4j in this class — check by class name.
    /** True when {@code e} is a winrm4j transient/IO exception, matched by class name to avoid a hard dependency on winrm4j here. */
    private static boolean isWinRmTransient(Exception e) {
        String name = e.getClass().getName();
        return name.contains("WinRmClientException")
                || name.contains("WinRmRuntimeIoException")
                || (e.getCause() instanceof IOException && name.contains("winrm"));
    }
}
