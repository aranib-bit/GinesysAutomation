package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Selenium.ScreenshotUtils;

public final class ExtentLogger {

    private ExtentLogger() {
    }

    public static void pass(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().pass(message);
    }

    public static void fail(String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (DriverManager.getDriver() != null) {
            try {
                ExtentManager.getExtentTest().fail(message,
                        MediaEntityBuilder.createScreenCaptureFromBase64String(ScreenshotUtils.getScreenshot()).build());
            } catch (org.openqa.selenium.WebDriverException wde) {
                // Session is dead — fall back to text-only to prevent AOP infinite loop
                ExtentManager.getExtentTest().fail(message + " [screenshot unavailable: " + wde.getMessage().split("\n")[0] + "]");
            }
        } else {
            ExtentManager.getExtentTest().fail(message);
        }
    }

    public static void info(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().info(message);
    }

    public static void warning(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().warning(message);
    }

    public static void failWithBase64Screenshot(String message, String base64) {
        if (ExtentManager.getExtentTest() == null) return;
        try {
            ExtentManager.getExtentTest().fail(message,
                    MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
        } catch (Exception e) {
            ExtentManager.getExtentTest().fail(message);
        }
    }

    /** Attaches raw HTML as an info entry on the current test. No-op if no active test. */
    public static void infoHtml(String html) {
        if (ExtentManager.getExtentTest() == null) return;
        ExtentManager.getExtentTest().info((Markup) () -> html);
    }

    /**
     * P8: Creates a named child node on the current ExtentTest and renders the given HTML
     * inside it. The child node appears inline in the test step list (same style as
     * StepExecutor nodes), ensuring it always renders after the preceding steps.
     *
     * @param nodeName  display name for the child node (shown as the collapsible header)
     * @param innerHtml HTML content to render inside the node
     */
    public static void infoHtmlOnChildNode(String nodeName, String innerHtml) {
        ExtentTest parent = ExtentManager.getExtentTest();
        if (parent == null) return;
        ExtentTest child = parent.createNode(nodeName);
        child.info((Markup) () -> innerHtml);
    }

    public static void infoTable(String[][] table) {
        ExtentManager.getExtentTest().info(buildScrollableTable(table));
    }

    private static Markup buildScrollableTable(String[][] table) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;width:100%'>")
          .append("<table style='border-collapse:collapse;font-size:12px;white-space:nowrap'>");
        for (int r = 0; r < table.length; r++) {
            sb.append("<tr>");
            for (String cell : table[r]) {
                if (r == 0) {
                    sb.append("<th style='border:1px solid #ccc;padding:4px 8px;background:#f2f2f2;font-weight:bold'>")
                      .append(cell).append("</th>");
                } else {
                    sb.append("<td style='border:1px solid #ccc;padding:4px 8px'>")
                      .append(cell == null ? "—" : cell).append("</td>");
                }
            }
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        String html = sb.toString();
        return () -> html;
    }
}

