package com.gsl.utils.Selenium;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ScreenshotUtils {

    /** Resolved lazily so the run ID from FrameworkConstants is already set. */
    private static Path screenshotDir() {
        return Paths.get(FrameworkConstants.getRunReportDir(), "screenshots");
    }

    private ScreenshotUtils() {
    }

    public static String getScreenshot() {
        return ((TakesScreenshot) DriverManager.getDriver()).getScreenshotAs(OutputType.BASE64);
    }

    /**
     * Captures the current browser screen to a PNG on disk under reports/runs/{runId}/screenshots/
     * and returns the absolute file path. Prefer this over {@link #getScreenshot()} for
     * Extent attachments — file-path references are ~80 bytes vs a base64 PNG which is
     * ~1.3× the raw bytes held inside the live ExtentReports model until suite flush.
     */
    public static String saveScreenshot(String stepLabel) {
        try {
            byte[] bytes = ((TakesScreenshot) DriverManager.getDriver())
                    .getScreenshotAs(OutputType.BYTES);
            Path dir = screenshotDir();
            Files.createDirectories(dir);
            String safeLabel = (stepLabel == null ? "step" : stepLabel)
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            if (safeLabel.length() > 60) safeLabel = safeLabel.substring(0, 60);
            String fileName = safeLabel + "_" + System.currentTimeMillis()
                    + "_T" + Thread.currentThread().getId() + ".png";
            Path file = dir.resolve(fileName);
            Files.write(file, bytes);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write screenshot file", e);
        }
    }

    /**
     * Captures the screenshot to disk (same as {@link #saveScreenshot}) and additionally
     * collects page-context metadata (URL, scroll position, viewport) via lightweight JS
     * calls.  Metadata capture is best-effort — failures are silently swallowed so that a
     * dead or slow browser session never prevents the screenshot from being attached.
     */
    public static ScreenshotMetadata saveScreenshotWithMetadata(String stepLabel) {
        String filePath = saveScreenshot(stepLabel);  // throws on real IO failure
        String pageUrl  = "";
        long   scrollY  = 0;
        String viewport = "";
        try {
            org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
            pageUrl = driver.getCurrentUrl();
            // window.innerWidth/innerHeight — the actual CSS-pixel viewport the page renders into —
            // not manage().window().getSize(), which is the OUTER window and includes browser chrome
            // (tab strip, omnibox) that isn't part of what ExtJS lays out against. The two can differ
            // by 80-100+ px; conflating them is what let an 800x513 Docker viewport get reported as
            // "800x600" for a full day of runs before anyone noticed the real problem.
            Object dims = ((JavascriptExecutor) driver)
                    .executeScript("return [window.innerWidth, window.innerHeight, window.pageYOffset];");
            if (dims instanceof java.util.List<?> list && list.size() == 3) {
                if (list.get(0) instanceof Number w && list.get(1) instanceof Number h) {
                    viewport = w.longValue() + "x" + h.longValue();
                }
                if (list.get(2) instanceof Number y) scrollY = y.longValue();
            }
        } catch (Exception ignored) {}
        String locator = CurrentLocatorHolder.get();
        CurrentLocatorHolder.clear();
        return new ScreenshotMetadata(filePath, pageUrl, scrollY, viewport,
                stepLabel != null ? stepLabel : "", locator);
    }
}
