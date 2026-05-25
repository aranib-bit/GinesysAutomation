package com.gsl.network;

import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.LogSeverity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Correlates failed network requests with CRITICAL JS errors by timestamp proximity.
 * A network request is considered "near" a JS error when it failed (status >= 400 or type=FAILED)
 * within ±2000 ms of any CRITICAL JS error timestamp.
 *
 * Returns a Set of URLs for correlated requests so NetworkLogSection can highlight them.
 * NetworkLogRecord has no requestId field — URL is used as the correlation key.
 */
public final class NetworkJsErrorCorrelator {

    private static final long WINDOW_MS = 2000L;

    private NetworkJsErrorCorrelator() {}

    /**
     * @return set of URLs of network requests that failed within ±2 s of a CRITICAL JS error.
     *         Empty set when no CRITICAL errors or no network failures are present.
     */
    public static Set<String> correlatedUrls(
            List<NetworkLogRecord> networkLogs,
            List<BrowserLogRecord> jsLogs) {

        if (networkLogs == null || networkLogs.isEmpty()
                || jsLogs == null || jsLogs.isEmpty()) {
            return Set.of();
        }

        List<Long> criticalTimestamps = jsLogs.stream()
                .filter(r -> r.level() == LogSeverity.CRITICAL)
                .map(BrowserLogRecord::timestamp)
                .toList();

        if (criticalTimestamps.isEmpty()) return Set.of();

        return networkLogs.stream()
                .filter(n -> isFailed(n) && isNearAnyError(n.timestamp(), criticalTimestamps))
                .map(NetworkLogRecord::url)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isFailed(NetworkLogRecord r) {
        return r.type() == NetworkLogType.FAILED || r.status() >= 400;
    }

    private static boolean isNearAnyError(long networkTs, List<Long> criticalTs) {
        return criticalTs.stream().anyMatch(ts -> Math.abs(networkTs - ts) <= WINDOW_MS);
    }
}
