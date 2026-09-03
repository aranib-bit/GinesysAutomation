package com.gsl.diagnostics;

import com.gsl.browser.AdvancedLogFetcher;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.JsErrorClassification;
import com.gsl.browser.JsErrorClassificationEngine;
import com.gsl.browser.LogSeverity;
import com.gsl.driver.DriverManager;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.network.NetworkLogRecord;
import com.gsl.reports.ActionTrailBuffer;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

import java.util.List;
import java.util.Optional;

/**
 * Lightweight, immutable evidence holder consumed by {@link RootCauseClassifier}.
 *
 * <p>Collected on the failing test thread from {@code TestListener.onTestFailure()} <em>before</em>
 * the full {@link FailureBundleOrchestrator} bundle runs. It gathers only what the classifier needs:
 * <ul>
 *   <li>the throwable and its message (the primary signal — most failures are distinguishable by
 *       exception type + message pattern);</li>
 *   <li>failed HTTP requests (status &ge; 400) — read from the in-memory network capture, free;</li>
 *   <li>critical JS errors — a single forced {@link AdvancedLogFetcher#fetchLogs()} round-trip
 *       (low-yield in this app, but requested) plus the reused {@link JsErrorClassificationEngine},
 *       filtered to only the one (if any) temporally correlated with this failure — see
 *       {@link #JS_ERROR_CORRELATION_WINDOW_MS};</li>
 *   <li>whether an ExtJS mask is currently visible;</li>
 *   <li>the last recorded action-trail step.</li>
 * </ul>
 * Every collection step is defensive — any failure degrades to an empty/false value, never throws.
 */
public final class RootCauseInputs {

    /**
     * Round 11 (§22): how close (before "now", the moment evidence is collected — right after the
     * failure) a CRITICAL JS error's own timestamp must be for it to be treated as a candidate
     * cause. Confirmed live (run 20260903_173520) that without this, a JS error from 68 seconds
     * earlier — recurring harmlessly in dozens of historical runs, including passing tests — was
     * mis-attributed as the cause of an unrelated `TimeoutException`. Not exposed as a wait
     * threshold (per feedback_no_threshold_masking) — this is a diagnostics-correlation window,
     * not a wait ceiling; it never makes a test wait longer or pass/fail differently.
     */
    private static final long JS_ERROR_CORRELATION_WINDOW_MS = 15_000;

    private final Throwable                 throwable;
    private final List<NetworkLogRecord>    failedRequests;
    private final List<BrowserLogRecord>    criticalJsLogs;
    private final BrowserLogRecord          correlatedJsError; // null if none within the recency window
    private final JsErrorClassification     jsClassification;
    private final boolean                   extMaskPresent;
    private final String                    failedStep;

    private RootCauseInputs(Throwable throwable,
                            List<NetworkLogRecord> failedRequests,
                            List<BrowserLogRecord> criticalJsLogs,
                            BrowserLogRecord correlatedJsError,
                            JsErrorClassification jsClassification,
                            boolean extMaskPresent,
                            String failedStep) {
        this.throwable         = throwable;
        this.failedRequests    = failedRequests;
        this.criticalJsLogs    = criticalJsLogs;
        this.correlatedJsError = correlatedJsError;
        this.jsClassification  = jsClassification;
        this.extMaskPresent    = extMaskPresent;
        this.failedStep        = failedStep;
    }

    public static RootCauseInputs collect(ITestResult result) {
        Throwable t      = (result != null) ? result.getThrowable() : null;
        WebDriver driver = safeDriver();

        // Failed HTTP requests — in-memory, cheap.
        List<NetworkLogRecord> failed = List.of();
        try {
            failed = NetworkCaptureManager.get().getLogs().stream()
                    .filter(r -> r.status() >= 400)
                    .toList();
        } catch (Exception e) {
            Log.warn("[RootCauseInputs] Network log read failed: {}", e.getMessage());
        }

        // Critical JS errors — one forced fetch on failure (guarded; empty on any error).
        List<BrowserLogRecord> critical = List.of();
        if (driver != null) {
            try {
                critical = AdvancedLogFetcher.fetchLogs().stream()
                        .filter(r -> r.level() == LogSeverity.CRITICAL)
                        .toList();
            } catch (Exception e) {
                Log.warn("[RootCauseInputs] Browser log fetch failed: {}", e.getMessage());
            }
        }

        // Round 11 (§22): only a JS error temporally correlated with THIS failure is a candidate
        // cause — see JS_ERROR_CORRELATION_WINDOW_MS's Javadoc. "Now" is captured right here,
        // immediately after the forced log fetch above, since collect() itself runs right after
        // the failure (TestListener.onTestFailure(), before the full diagnostics bundle).
        long collectedAtMs = System.currentTimeMillis();
        BrowserLogRecord correlated = JsErrorClassificationEngine
                .mostRecentWithin(critical, collectedAtMs, JS_ERROR_CORRELATION_WINDOW_MS)
                .orElse(null);
        JsErrorClassification jsClass = (correlated != null)
                ? JsErrorClassificationEngine.classify(correlated)
                : JsErrorClassification.unknown();
        boolean mask = WaitUtils.isExtMaskPresent(driver);

        List<String> trail = ActionTrailBuffer.getForDisplay();
        String step = (trail == null || trail.isEmpty()) ? "" : trail.get(trail.size() - 1);

        return new RootCauseInputs(t, failed, critical, correlated, jsClass, mask, step);
    }

    private static WebDriver safeDriver() {
        try {
            return DriverManager.getDriver();
        } catch (Exception e) {
            return null;
        }
    }

    // ── accessors ───────────────────────────────────────────────────────────

    public Throwable throwable() { return throwable; }

    public String message() {
        return (throwable != null && throwable.getMessage() != null) ? throwable.getMessage() : "";
    }

    /**
     * Round 11 (§22): {@code true} only when a CRITICAL JS error was found temporally correlated
     * with this failure (see {@link #JS_ERROR_CORRELATION_WINDOW_MS}) — NOT merely "were any
     * critical JS errors logged anywhere during this test." A stale, unrelated error earlier in
     * the test no longer causes {@link RootCauseClassifier} to attribute the failure to it.
     */
    public boolean hasCriticalJsError() { return correlatedJsError != null; }

    /** The message of the temporally-correlated JS error (see {@link #hasCriticalJsError()}) —
     *  empty when none was found within the correlation window, even if uncorrelated ones exist
     *  in {@link #criticalJsLogs}. */
    public String firstCriticalJsErrorMessage() {
        return correlatedJsError != null ? correlatedJsError.message() : "";
    }

    public JsErrorClassification jsClassification() { return jsClassification; }

    public boolean hasHttpError()     { return !failedRequests.isEmpty(); }
    public boolean hasNetworkErrors() { return !failedRequests.isEmpty(); }

    public int firstHttpErrorCode() {
        return failedRequests.isEmpty() ? -1 : failedRequests.get(0).status();
    }

    public String firstFailedRequestUrl() {
        return failedRequests.isEmpty() ? "" : failedRequests.get(0).url();
    }

    public boolean isExtMaskPresent() { return extMaskPresent; }

    public String failedStep() { return failedStep; }
}
