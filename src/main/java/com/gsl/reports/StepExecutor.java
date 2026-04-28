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
        } finally {
            ExtentManager.setExtentTest(parent);
        }
    }

    private static void failNode(ExtentTest node, String stepName, Exception e) {
        Log.error("[STEP FAIL] {} — {}", stepName, e.getMessage());
        if (DriverManager.getDriver() != null) {
            node.fail("Step failed: " + e.getMessage(),
                    MediaEntityBuilder.createScreenCaptureFromBase64String(
                            ScreenshotUtils.getScreenshot()).build());
        } else {
            node.fail("Step failed: " + e.getMessage());
        }
    }
}
