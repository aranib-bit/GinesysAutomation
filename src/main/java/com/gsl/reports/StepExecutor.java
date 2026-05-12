package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ScreenshotUtils;

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
        ExtentTest parent = ExtentManager.getExtentTest();
        ExtentTest node   = parent.createNode(stepName);
        ExtentManager.setExtentTest(node);
        Log.info("[STEP] {}", stepName);

        try {
            action.run();
            node.pass("Step passed");
            Log.info("[STEP PASS] {}", stepName);
        } catch (RuntimeException e) {
            failNode(node, stepName, e);
            throw e;
        } catch (Exception e) {
            failNode(node, stepName, e);
            throw new RuntimeException("Step failed: " + stepName, e);
        } catch (Throwable t) {
            // Catches Errors (e.g. AssertionError, SoftAssertionError) that are not Exception subclasses
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
        // Log exception type and message as separate fail entries inside the step node
        // so they appear at the correct position (not at the top of the test report)
        node.fail("Exception  : " + e.getClass().getSimpleName());
        node.fail("Message    : " + e.getMessage());
        if (DriverManager.getDriver() != null) {
            try {
                node.fail("Screenshot at failure",
                        MediaEntityBuilder.createScreenCaptureFromBase64String(
                                ScreenshotUtils.getScreenshot()).build());
            } catch (Exception screenshotEx) {
                node.fail("Screenshot unavailable: " + screenshotEx.getMessage());
            }
        }
    }
}
