package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.gsl.constants.FrameworkConstants;

public final class ExtentReport {

    private ExtentReport() {
    }

    public static ExtentReports extent;

    public static void initReports() {
        extent = new ExtentReports();
        ExtentSparkReporter sparkReporter = new ExtentSparkReporter(FrameworkConstants.getReportPath());
        // P4: widen the built-in timestamp column so timestamps never wrap
        sparkReporter.config().setCss(
            ".col-timestamp, td.timestamp, th.timestamp, " +
            ".step-time, .test-time-info, td:first-child.timestamp-cell {" +
            "  min-width: 160px !important;" +
            "  white-space: nowrap !important;" +
            "}"
        );
        extent.attachReporter(sparkReporter);
    }

    /**
     * P2: Sets the descriptive sentence on the current thread's ExtentTest node.
     * Called immediately after createTest() so the description appears under the test title.
     */
    public static void setTestDescription(String description) {
        if (description == null || description.isBlank()) return;
        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) {
            test.getModel().setDescription(description);
        }
    }

    public static void flushReports() {
        extent.flush();
    }

    public static void createTest(String testCaseName) {
        ExtentTest test = extent.createTest(testCaseName);
        ExtentManager.setExtentTest(test);
    }

    public static void assignAuthor(Enum author) {
        ExtentManager.getExtentTest().assignAuthor(author.toString());
    }

}
