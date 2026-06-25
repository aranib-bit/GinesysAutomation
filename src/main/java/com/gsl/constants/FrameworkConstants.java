package com.gsl.constants;

import org.openqa.selenium.By;

import java.nio.file.Paths;

public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    private static String runId;
    private static String reportPath;

    private static final By BY_LOADING_WINDOW = By.xpath("//div[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");
    private static final By BY_LOADING_MASK = By.xpath("//div[contains(@class, 'x-mask-fixed')]");
    private static final By BY_SELECT_DOC_GRID_LOADER = By.xpath(
            "//div[contains(@class,'x-window') and not(contains(@class,'x-window-maximized'))]" +
            "//div[@role='progressbar' and @aria-valuetext='Loading...']");

    /** Stable run identifier (yyyyMMdd_HHmmss) — same value for the entire JVM run. */
    public static String getRunId() {
        if (runId == null) {
            runId = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }
        return runId;
    }

    /**
     * Returns the run-scoped report directory: {@code <project>/reports/runs/<runId>/}.
     * All per-test artifacts (screenshots, html-captures, har) land here so each run
     * is fully self-contained and old runs are never overwritten.
     */
    public static String getRunReportDir() {
        return System.getProperty("user.dir") + "/reports/runs/" + getRunId();
    }

    /** Absolute path to {@code index.html} inside the run-scoped directory. */
    public static String getReportPath() {
        if (reportPath == null) {
            reportPath = getRunReportDir() + "/index.html";
        }
        return reportPath;
    }

    public static String getReportDir() {
        return Paths.get(getReportPath()).getParent().toString();
    }

    /** Convenience path used by CI to always find the latest report. */
    public static String getLatestReportDir() {
        return System.getProperty("user.dir") + "/reports/latest";
    }

    public static By getLoadingWindow() {
        return BY_LOADING_WINDOW;
    }

    public static By getLoadingMask() {
        return BY_LOADING_MASK;
    }

    public static By getSelectDocGridLoader() {
        return BY_SELECT_DOC_GRID_LOADER;
    }
}
