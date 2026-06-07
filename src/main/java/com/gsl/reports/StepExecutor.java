package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.browser.CustomMetricsObserverInjector;
import com.gsl.browser.injector.FrontendDiagnosticsInjectorFactory;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
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

    /**
     * Wraps a test action in a collapsible Extent child node.
     * On success: node is marked pass and the parent test is restored.
     * On failure: node captures a screenshot, marks fail, restores the parent,
     * then re-throws so TestNG records the test as failed.
     * All ReportLogger/ExtentLogger calls inside the action log to the child node
     * because ExtentManager's ThreadLocal is temporarily swapped.
     */
    public static void step(String stepName, StepAction action) {
        // Record step in action trail buffer for failure diagnostics
        ActionTrailBuffer.record(stepName);

        // Push step context into the browser so AdvancedJsLogger timing correlation works,
        // and re-inject CustomMetrics observers in case a full-page navigation wiped them.
        try {
            if (DriverManager.getDriver() != null) {
                ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                    "window._fwCurrentStep = arguments[0]; window._fwStepStartMs = Date.now();",
                    stepName);
                if (ConfigFactory.getConfig().customMetricsEnabled()) {
                    CustomMetricsObserverInjector.inject(DriverManager.getDriver());
                }
                if (ConfigFactory.getConfig().frontendDiagnosticsEnabled()) {
                    FrontendDiagnosticsInjectorFactory.get().inject(DriverManager.getDriver());
                }
            }
        } catch (Exception ignored) {}

        ExtentTest parent = ExtentManager.getExtentTest();
        ExtentTest node   = parent.createNode(stepName);
        ExtentManager.setExtentTest(node);
        Log.info("[STEP] {}", stepName);

        long startMs  = System.currentTimeMillis();
        long durationMs = 0;
        try {
            action.run();
            durationMs = System.currentTimeMillis() - startMs;
            node.pass("Step passed — " + durationMs + "ms");
            Log.info("[STEP PASS] {} ({}ms)", stepName, durationMs);
            StepDurationBuffer.record(stepName, durationMs, true);
        } catch (RuntimeException e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, e);
            throw e;
        } catch (Exception e) {
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, e);
            throw new RuntimeException("Step failed: " + stepName, e);
        } catch (Throwable t) {
            // Catches Errors (e.g. AssertionError, SoftAssertionError) that are not Exception subclasses
            durationMs = System.currentTimeMillis() - startMs;
            StepDurationBuffer.record(stepName, durationMs, false);
            failNode(node, stepName, t);
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException("Step failed: " + stepName, t);
        } finally {
            ExtentManager.setExtentTest(parent);
        }
    }

    /**
     * Logs exception details inside the child node so they appear inline with the step,
     * then attaches a screenshot if a live browser session is available.
     * Accepts Throwable to also handle AssertionError / SoftAssertionError (not Exception subclasses).
     */
    private static void failNode(ExtentTest node, String stepName, Throwable e) {
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
