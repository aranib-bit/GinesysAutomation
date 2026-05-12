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
 * Retry-eligible: TransportException, SSHException, SocketTimeoutException, TimeoutException.
 * Not retried:    RemoteCommandException (non-zero exit / bad command),
 *                 IllegalArgumentException (validation failure).
 */
public final class RetryPolicy {

    private final int  maxAttempts;
    private final long initialDelayMs;

    public RetryPolicy(int maxAttempts, long initialDelayMs) {
        this.maxAttempts    = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    /** Wraps an operation in this retry policy, returning the first successful result. */
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

    public int getMaxAttempts() { return maxAttempts; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean isRetryable(Exception e) {
        return e instanceof TransportException
                || e instanceof SSHException
                || e instanceof SocketTimeoutException
                || e instanceof TimeoutException
                || isWinRmTransient(e);
    }

    // Avoid a hard compile-time dependency on winrm4j in this class — check by class name.
    private static boolean isWinRmTransient(Exception e) {
        String name = e.getClass().getName();
        return name.contains("WinRmClientException")
                || name.contains("WinRmRuntimeIoException")
                || (e.getCause() instanceof IOException && name.contains("winrm"));
    }
}
