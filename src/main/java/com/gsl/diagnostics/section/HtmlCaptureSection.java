package com.gsl.diagnostics.section;

import com.gsl.constants.FrameworkConstants;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.utils.Log;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Captures the full page DOM at the moment of a test failure and writes it to
 * reports/html-captures/<testName>_<timestamp>.html.
 *
 * The section in the Diagnostic Report shows:
 *   - The file path (absolute) for opening locally in a browser
 *   - A 4 KB inline preview so you can see which page was captured
 *
 * This lets you evaluate the failing XPath directly in Chrome DevTools
 * (Elements → Ctrl+F → paste the XPath) without having to re-run the test.
 */
public final class HtmlCaptureSection implements DiagnosticSection {

    private static final int PREVIEW_CHARS = 512;

    private static String outputDir() {
        return FrameworkConstants.getRunReportDir() + "/html-captures";
    }

    private final String capturedPath;
    private final String pageSource;
    private final String testName;

    public HtmlCaptureSection(WebDriver driver, String testName) {
        this.testName = testName;
        String src  = null;
        String path = null;
        if (driver != null) {
            try {
                src  = driver.getPageSource();
                path = writeToFile(src, testName);
            } catch (Exception e) {
                Log.warn("[HtmlCaptureSection] Failed to capture page source: {}", e.getMessage());
            }
        }
        this.pageSource   = src;
        this.capturedPath = path;
    }

    @Override
    public String sectionTitle() {
        return "HTML Capture — " + testName;
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();

        if (capturedPath != null) {
            html.append("<div style='font-size:11px;margin-bottom:6px;'>")
                .append("<b>File:</b> <span style='font-family:monospace;color:#0066cc;'>")
                .append(esc(capturedPath))
                .append("</span> &nbsp; <span style='color:#888;font-size:10px;'>")
                .append("Open in browser → DevTools (F12) → Elements → Ctrl+F → paste XPath</span>")
                .append("</div>");
        }

        if (pageSource != null) {
            String preview = pageSource.length() > PREVIEW_CHARS
                    ? pageSource.substring(0, PREVIEW_CHARS) + "\n... [preview truncated — full source in file]"
                    : pageSource;
            html.append("<details><summary style='cursor:pointer;font-size:11px;color:#555;'>")
                .append("Page Source Preview (first ").append(PREVIEW_CHARS).append(" chars)")
                .append("</summary>")
                .append("<pre style='font-size:10px;background:#f8f8f8;padding:6px;")
                .append("border-left:3px solid #ccc;white-space:pre-wrap;overflow:auto;max-height:300px;'>")
                .append(esc(preview))
                .append("</pre></details>");
        }

        return html.toString();
    }

    @Override
    public boolean hasData() {
        return pageSource != null && !pageSource.isBlank();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String writeToFile(String source, String testName) {
        try {
            Path dir = Paths.get(outputDir());
            Files.createDirectories(dir);
            String fileName = sanitize(testName) + "_" + System.currentTimeMillis() + ".html";
            Path   file     = dir.resolve(fileName);
            Files.writeString(file, source, StandardCharsets.UTF_8);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            Log.warn("[HtmlCaptureSection] Could not write HTML capture file: {}", e.getMessage());
            return null;
        }
    }

    private static String sanitize(String name) {
        return name == null ? "unknown" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
