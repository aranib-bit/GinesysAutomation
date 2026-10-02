package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.Markup;
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

/**
 * Wraps a unit of test logic in its own collapsible Extent Report node, giving it independent
 * pass/fail status, a duration suffix on its title, and (on failure) an attached screenshot —
 * the framework's standard way to break a test body into named, individually-reportable steps.
 *
 * <p>Use {@link #step(String, StepAction)} for a void action and {@link #call(String, StepSupplier)}
 * for one that returns a value (e.g. a single API call). Use {@link #group(String)} to create a
 * grouping node up front (e.g. one per data row) that later steps attach under via
 * {@link #stepUnder(ExtentTest, String, StepAction)} — needed when those later steps run in a
 * separate pass after the group's own code has already returned. Steps that want a synthesized
 * "what happened" recap at the end of their node should record fragments via
 * {@link StepSummaryBuffer#record(String)} during their own logic; {@code step(...)} renders them
 * automatically, or call {@link #renderSummaryAsChildNode(String)} explicitly when the summary
 * must appear after the step's own child nodes rather than before them.
 */
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
        // * NOTE: executeScript can block for 1-4s when the browser JS engine is busy after
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
            renderStepSummary(node, stepName);
            ExtentLogger.passCategorized(LogCategory.TEST_STEP_STATUS, "Step passed");
            Log.info("[STEP PASS] {} ({}ms)", stepName, durationMs);
            StepDurationBuffer.record(stepName, durationMs, true);
            return durationMs;
        } catch (RuntimeException e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepSummaryBuffer.flush(); // discard — a step that didn't finish shouldn't render a summary
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, e);
            throw e;
        } catch (Exception e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepSummaryBuffer.flush();
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, e);
            throw new RuntimeException("Step failed: " + stepName, e);
        } catch (Throwable t) {
            // Catches Errors (e.g. AssertionError, SoftAssertionError) that are not Exception subclasses
            durationMs = System.currentTimeMillis() - startMs;
            StepSummaryBuffer.flush();
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, durationMs, t);
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException("Step failed: " + stepName, t);
        } finally {
            ExtentManager.setExtentTest(parent);
        }
    }

    /**
     * Same node/pass/fail/duration/screenshot behavior as {@link #step(String, StepAction)}, but a
     * failure does NOT propagate: the step's node is marked failed exactly as usual, and the
     * {@link Throwable} is RETURNED (null when the step passed) so the caller can record it and let
     * later independent steps still run. The caller MUST surface the returned failure at its flow's
     * final assertion point, otherwise the failure would be silently swallowed — see
     * {@code VoucherSaveFlowSteps#softStep}/{@code finalizeAsserts}.
     */
    public static Throwable stepSoft(String stepName, StepAction action) {
        try {
            step(stepName, action);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /**
     * Drains {@link StepSummaryBuffer} and, if anything was recorded during this step, renders one
     * consolidated "Summary" box as the last thing in the node — the TL;DR a reader sees right
     * before the green "Step passed" marker, instead of piecing the outcome together from the
     * individual lines logged above it. No-op (renders nothing) when the step recorded no
     * fragments — most steps that don't opt in stay exactly as they were before this existed.
     *
     * <p>Reuses the report's existing green checkmark-summary house style (same
     * background/border/font as {@code FinVchMainBaseValidator#buildLevel1Summary}'s
     * "Level 1 — finvchmain field checks" box — e.g. run 20260909_114004) rather than the
     * previously-introduced grey/blue-grey box, which looked visually inconsistent with every
     * other summary box already in the report. Plain one-line text fragments get the same
     * "&#10003;&nbsp;" checkmark bullet that style uses; a fragment that is already its own HTML
     * block (e.g. a table built by one of the Point 9 call sites) renders as-is, unprefixed.
     */
    private static void renderStepSummary(ExtentTest node, String stepName) {
        java.util.List<StepSummaryBuffer.Fragment> fragments = StepSummaryBuffer.flush();
        if (fragments.isEmpty()) return;
        String html = buildSummaryHtml(stepName, fragments);
        node.info((Markup) () -> html);
    }

    /** Shared box-builder behind {@link #renderStepSummary}/{@link #renderSummaryAsChildNode} —
     *  Item Q's 13px font (matches DocumentMessageBox's "VERIFY SAVE RESULT" box) and Item AF's
     *  step-named heading (e.g. "Fill Expense/Income Detail — Summary" instead of a bare, repeated
     *  "Summary"). */
    private static String buildSummaryHtml(String heading, java.util.List<StepSummaryBuffer.Fragment> fragments) {
        StringBuilder body = new StringBuilder();
        for (StepSummaryBuffer.Fragment fragment : fragments) {
            String text = fragment.text();
            boolean isHtmlBlock = text.stripLeading().startsWith("<");
            body.append("<div style='margin-bottom:4px;'>");
            if (!isHtmlBlock) {
                body.append(fragment.ok() ? "&#10003;&nbsp; " : "&#10007;&nbsp; ");
            }
            body.append(text).append("</div>");
        }
        return "<div style='font-family:monospace;font-size:13px;line-height:1.8;"
                + "padding:8px 12px;background:#f0fff4;border-left:4px solid #38a169;"
                + "border-radius:2px;margin:4px 0;'>"
                + "<b>" + heading + " &#8212; Summary</b><br/>" + body + "</div>";
    }

    /**
     * Renders whatever is currently buffered in {@link StepSummaryBuffer} as a NEW, explicit
     * child node under the current step — instead of the step's own final log entry, which the
     * ExtentReports Spark template always renders ABOVE every child node regardless of the real
     * chronological call order (confirmed against the rendered HTML: a node's own {@code <table>}
     * of log rows always precedes its nested {@code accordion}/child-node cards). Call this
     * explicitly, as the LAST thing in a step's body, when the step also creates its own child
     * nodes (e.g. one {@link #group}/{@link #stepUnder} per row) and the summary needs to appear
     * visually AFTER them rather than above — e.g. "Fill Expense/Income Detail"'s per-line "Line
     * N" nodes, where the consolidated Summary must read as the final word, not the first.
     *
     * <p>Safe to combine with the ordinary end-of-step auto-render: this drains the buffer
     * immediately, so by the time the enclosing {@link #step} finishes and calls
     * {@link #renderStepSummary}, the buffer is already empty and that call is a no-op — no
     * duplicate box. No-op (creates nothing) when nothing was recorded.
     */
    public static void renderSummaryAsChildNode(String heading) {
        java.util.List<StepSummaryBuffer.Fragment> fragments = StepSummaryBuffer.flush();
        if (fragments.isEmpty()) return;
        ExtentTest parent = ExtentManager.getExtentTest();
        ExtentTest child = parent.createNode(heading);
        String html = buildSummaryHtml(heading, fragments);
        child.info((Markup) () -> html);
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

    /** Formats a millisecond duration for a step's title suffix — {@code "Nms"} under a second,
     *  {@code "Ns Nms"} under a minute, else {@code "Nm Ns"}. */
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
        // What is actually on screen right now (masks, dialogs, open lists, editors, login error, Ajax)
        // — explains an intermittent failure without lengthening any wait.
        com.gsl.utils.Selenium.PageStateDiagnostics.captureOnce(e, "Step '" + stepName + "' failed", node);
        if (DriverManager.getDriver() != null) {
            try {
                // Capture screenshot + page-context metadata in a single pass.
                // Metadata (URL, scroll, viewport) aids reproduction without needing a re-run.
                // Captured now (the page state exists only now) but attached at test end by
                // ScreenshotPolicy: report.screenshots.mode=LAST keeps just the final one.
                ScreenshotMetadata meta = ScreenshotUtils.saveScreenshotWithMetadata(stepName);
                ScreenshotPolicy.defer(java.util.List.of(node), stepName, meta.filePath(), meta.toAnnotation());
            } catch (Exception screenshotEx) {
                node.fail("Screenshot unavailable: " + screenshotEx.getMessage());
            }
        }
    }
}
