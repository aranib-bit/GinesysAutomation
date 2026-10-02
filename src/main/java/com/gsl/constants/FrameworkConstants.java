package com.gsl.constants;

import org.openqa.selenium.By;

import java.nio.file.Paths;

/**
 * Static, run-scoped constants shared across the framework: the JVM-wide run identifier, the
 * report directory layout derived from it, and the {@link By} locators for the app's various
 * boot-time loading overlays (splash screen, ExtJS loading window/mask, Select-Document grid
 * loader). Other utils and page objects look these up here instead of hardcoding the locator or
 * recomputing the run-scoped path themselves, so every artifact (report, screenshot, HAR) for a
 * given JVM run lands under the same {@link #getRunReportDir()}.
 */
public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    private static String runId;
    private static String reportPath;

    private static final By BY_LOADING_WINDOW = By.xpath("//div[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");
    private static final By BY_LOADING_MASK = By.xpath("//div[contains(@class, 'x-mask-fixed')]");
    private static final By BY_SPLASH_SCREEN = By.id("splashScreen");
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

    /** Parent directory of {@link #getReportPath()} — the run-scoped report directory as a plain path string. */
    public static String getReportDir() {
        return Paths.get(getReportPath()).getParent().toString();
    }

    /** Convenience path used by CI to always find the latest report. */
    public static String getLatestReportDir() {
        return System.getProperty("user.dir") + "/reports/latest";
    }

    /** Locator for the maximized ExtJS app window shown while the app boots ({@code x-window-maximized}). */
    public static By getLoadingWindow() {
        return BY_LOADING_WINDOW;
    }

    /** Locator for the fixed-position ExtJS loading mask ({@code .x-mask-fixed}) shown during page/route loads. */
    public static By getLoadingMask() {
        return BY_LOADING_MASK;
    }

    /** Locator for the app-level {@code #splashScreen} overlay, which clears via a CSS opacity fade rather than removal/hide. */
    public static By getSplashScreen() {
        return BY_SPLASH_SCREEN;
    }

    /** Locator for the Select-Document popup's grid loading progress bar ({@code aria-valuetext="Loading..."}). */
    public static By getSelectDocGridLoader() {
        return BY_SELECT_DOC_GRID_LOADER;
    }
}
