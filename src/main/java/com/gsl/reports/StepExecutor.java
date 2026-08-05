package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.browser.CustomMetricsObserverInjector;
import com.gsl.browser.injector.FrontendDiagnosticsInjectorFactory;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ScreenshotMetadata;
import com.gsl.utils.Selenium.ScreenshotUtils;
import org.openqa.selenium.JavascriptExecutor;
import com.gsl.reports.StepDurationBuffer;

public final class StepExecutor {

    private StepExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    @FunctionalInterface
    public interface StepAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface StepSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Creates a collapsible Extent child node under the CURRENT test node and returns it
     * WITHOUT running any action. Use it as a per-row (or per-section) grouping container
     * that later sub-steps attach to via {@link #stepUnder(ExtentTest, String, StepAction)}.
     *
     * <p>The node carries no pass/fail status of its own — Extent aggregates the status of
     * the children added to it, so the group turns red when any child step fails.
     */
    public static ExtentTest group(String name) {
        ActionTrailBuffer.record(name);
        return ExtentManager.getExtentTest().createNode(name);
    }

    /**
     * Runs a step as a child of an EXPLICITLY supplied parent node instead of the
     * thread-current node, then restores the previous current node. Same pass/fail/
     * screenshot/duration semantics as {@link #step(String, StepAction)} — it simply
     * re-points {@link ExtentManager}'s current node at {@code parentNode} for the
     * duration of the call so the new child attaches under the captured group.
     *
     * <p>This is how operations that execute in a later pass (reference-site re-apply,
     * adjustments, payment, AP TDS) report under the sub-ledger row group created in
     * Pass 1, even though they run after that pass has returned.
     */
    public static long stepUnder(ExtentTest parentNode, String stepName, StepAction action) {
        if (parentNode == null) {
            // Defensive: no captured group — fall back to a normal step under the current node.
            return step(stepName, action);
        }
        ExtentTest previous = ExtentManager.getExtentTest();
        ExtentManager.setExtentTest(parentNode);
        try {
            return step(stepName, action);
        } finally {
            ExtentManager.setExtentTest(previous);
        }
    }

    /**
     * Wraps a test action in a collapsible Extent child node.
     * On success: node is marked pass and the parent test is restored.
     * On failure: node captures a screenshot, marks fail, restores the parent,
     * then re-throws so TestNG records the test as failed.
     * All ReportLogger/ExtentLogger calls inside the action log to the child node
     * because ExtentManager's ThreadLocal is temporarily swapped.
     */
    public static long step(String stepName, StepAction action) {
        // Record step in action trail buffer for failure diagnostics
        ActionTrailBuffer.record(stepName);

        long startMs  = System.currentTimeMillis();

        // Push step context into the browser so AdvancedJsLogger timing correlation works,
        // and re-inject CustomMetrics observers in case a full-page navigation wiped them.
        // NOTE: executeScript can block for 1-4s when the browser JS engine is busy after
        // a post-save navigation — startMs is set first so that wait is included in the step
        // duration rather than creating a silent gap between steps.
        try {
            if (DriverManager.getDriver() != null) {
                ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                    "window._fwCurrentStep = arguments[0]; window._fwStepStartMs = Date.now();",
                    stepName);
                if (ConfigFactory.getDiagnosticsConfig().customMetricsEnabled()) {
                    CustomMetricsObserverInjector.inject(DriverManager.getDriver());
                }
                if (ConfigFactory.getDiagnosticsConfig().frontendDiagnosticsEnabled()) {
                    FrontendDiagnosticsInjectorFactory.get().inject(DriverManager.getDriver());
                }
            }
        } catch (Exception ignored) {}

        ExtentTest parent = ExtentManager.getExtentTest();
        ExtentTest node   = parent.createNode(stepName);
        ExtentManager.setExtentTest(node);
        Log.info("[STEP] {}", stepName);
        long durationMs = 0;
        try {
            action.run();
            durationMs = System.currentTimeMillis() - startMs;
            node.getModel().setName(stepName + " (" + formatDuration(durationMs) + ")");
            ExtentLogger.passCategorized(LogCategory.TEST_STEP_STATUS, "Step passed");
            Log.info("[STEP PASS] {} ({}ms)", stepName, durationMs);
            StepDurationBuffer.record(stepName, durationMs, true);
            return durationMs;
        } catch (RuntimeException e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, e);
            throw e;
        } catch (Exception e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, e);
            throw new RuntimeException("Step failed: " + stepName, e);
        } catch (Throwable t) {
            // Catches Errors (e.g. AssertionError, SoftAssertionError) that are not Exception subclasses
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, t);
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException("Step failed: " + stepName, t);
        } finally {
            ExtentManager.setExtentTest(parent);
        }
    }

    /**
     * Same nested-node/pass/fail/duration semantics as {@link #step(String, StepAction)}, but for
     * an action that returns a value — e.g. an individual API call inside a larger orchestration
     * method, so each call gets its own collapsible report node (per-call sub-section) instead of
     * every call logging flat into the enclosing step. Reuses {@code step(...)} itself via a
     * one-element result holder rather than duplicating its create-node/pass/fail logic.
     *
     * <p>Falls back to running {@code action} directly (no node) when there is no active
     * {@link ExtentTest} to attach to — mirrors the {@link ExtentManager#hasTest()} guard already
     * used by {@code WebApiSessionClient} for the same reason: some callers (e.g. session bootstrap
     * outside a TestNG test) run before any test node exists.
     */
    public static <T> T call(String stepName, StepSupplier<T> action) {
        if (!ExtentManager.hasTest()) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(stepName + " failed", e);
            }
        }
        Object[] holder = new Object[1];
        step(stepName, () -> holder[0] = action.get());
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }

    static String formatDuration(long ms) {
        if (ms < 1_000) return ms + "ms";
        long s = ms / 1_000, rem = ms % 1_000;
        if (s < 60) return s + "s " + rem + "ms";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    /**
     * Logs exception details inside the child node so they appear inline with the step,
     * then attaches a screenshot if a live browser session is available.
     * Accepts Throwable to also handle AssertionError / SoftAssertionError (not Exception subclasses).
     */
    private static void failNode(ExtentTest node, String stepName, long durationMs, Throwable e) {
        node.getModel().setName(stepName + " (" + formatDuration(durationMs) + ")");
        Log.error("[STEP FAIL] {} — {}", stepName, e.getMessage());
        node.fail("Exception  : " + e.getClass().getSimpleName());
        node.fail("Message    : " + e.getMessage());
        if (DriverManager.getDriver() != null) {
            try {
                // Capture screenshot + page-context metadata in a single pass.
                // Metadata (URL, scroll, viewport) aids reproduction without needing a re-run.
                ScreenshotMetadata meta = ScreenshotUtils.saveScreenshotWithMetadata(stepName);
                node.fail("Screenshot at failure",
                        MediaEntityBuilder.createScreenCaptureFromPath(meta.filePath()).build());
                String annotation = meta.toAnnotation();
                if (!annotation.isBlank()) {
                    node.info("Page context: " + annotation);
                }
            } catch (Exception screenshotEx) {
                node.fail("Screenshot unavailable: " + screenshotEx.getMessage());
            }
        }
    }
}
