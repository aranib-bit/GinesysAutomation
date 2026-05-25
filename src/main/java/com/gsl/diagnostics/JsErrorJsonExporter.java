package com.gsl.diagnostics;

import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.JsErrorClassification;
import com.gsl.browser.JsErrorClassificationEngine;
import com.gsl.browser.LogSeverity;
import com.gsl.config.ConfigFactory;
import com.gsl.extjs.UiFrameworkAdapter;
import com.gsl.extjs.UiFrameworkAdapterFactory;
import com.gsl.network.NetworkLogRecord;
import com.gsl.network.NetworkLogType;
import com.gsl.reports.ActionTrailBuffer;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Writes a structured JSON artifact to reports/js-errors/ when a CRITICAL JS error occurs.
 * Uses manual StringBuilder JSON construction — no Jackson dependency required in the export path.
 * JSON format matches the Enterprise JS Error Observability spec.
 */
public final class JsErrorJsonExporter {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ISO_INSTANT;

    private JsErrorJsonExporter() {}

    /**
     * Exports a JSON artifact for the failing test if CRITICAL JS errors are present.
     * Silently skips when no CRITICAL errors exist or js.error.json.export.enabled=false.
     */
    public static void export(
            ITestResult result,
            List<BrowserLogRecord> jsLogs,
            List<NetworkLogRecord> networkLogs,
            Set<String> correlatedUrls,
            WebDriver driver) {

        if (!ConfigFactory.getConfig().jsErrorJsonExportEnabled()) return;

        List<BrowserLogRecord> criticals = jsLogs.stream()
                .filter(r -> r.level() == LogSeverity.CRITICAL)
                .toList();
        if (criticals.isEmpty()) return;

        try {
            Path dir = Paths.get("reports", "js-errors");
            Files.createDirectories(dir);

            String testName     = sanitize(result.getMethod().getMethodName());
            String correlId     = nvl(TestContextManager.getExecutionId(), "unknown");
            String timestamp    = TS_FMT.format(Instant.now());
            String filename     = testName + "_" + correlId + "_" + timestamp + ".json";
            Path   outputPath   = dir.resolve(filename);

            String json = buildJson(result, criticals, networkLogs, correlatedUrls, driver, correlId);
            Files.writeString(outputPath, json, StandardCharsets.UTF_8);
            Log.info("[JsErrorJsonExporter] Artifact written: {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            Log.warn("[JsErrorJsonExporter] Failed to write artifact: {}", e.getMessage());
        }
    }

    private static String buildJson(
            ITestResult result,
            List<BrowserLogRecord> criticals,
            List<NetworkLogRecord> networkLogs,
            Set<String> correlatedUrls,
            WebDriver driver,
            String correlId) {

        BrowserLogRecord first = criticals.get(0);
        JsErrorClassification cls = JsErrorClassificationEngine.classify(first);

        long failedCount     = networkLogs == null ? 0 : networkLogs.stream()
                .filter(n -> n.type() == NetworkLogType.FAILED || n.status() >= 400).count();
        int  correlatedCount = correlatedUrls == null ? 0 : correlatedUrls.size();

        String currentUrl   = safeDriverCall(() -> driver.getCurrentUrl());
        String pageTitle    = safeDriverCall(() -> driver.getTitle());
        String activeModule = "";
        String activeTab    = "";
        String activeRoute  = "";
        try {
            UiFrameworkAdapter adapter = UiFrameworkAdapterFactory.get();
            activeModule = nvl(adapter.getActiveModule(driver), "");
            activeTab    = nvl(adapter.getActiveTab(driver),    "");
            activeRoute  = nvl(adapter.getActiveRoute(driver),  "");
        } catch (Exception ignored) {}

        List<String> trail = ActionTrailBuffer.getAll();

        StringBuilder j = new StringBuilder();
        j.append("{\n");
        j.append("  \"eventType\": \"JS_RUNTIME_ERROR\",\n");
        j.append("  \"severity\": \"CRITICAL\",\n");
        j.append("  \"application\": \"GinesysWeb\",\n");
        j.append("  \"environment\": ").append(q(ConfigFactory.getConfig().env())).append(",\n");
        j.append("  \"correlationId\": ").append(q(correlId)).append(",\n");
        j.append("  \"testName\": ").append(q(result.getMethod().getMethodName())).append(",\n");
        j.append("  \"timestamp\": ").append(q(ISO_FMT.format(Instant.now()))).append(",\n");

        j.append("  \"error\": {\n");
        j.append("    \"type\": ").append(q(extractType(first.message()))).append(",\n");
        j.append("    \"message\": ").append(q(first.message())).append(",\n");
        j.append("    \"stack\": ").append(q(first.stack())).append(",\n");
        j.append("    \"sourceUrl\": ").append(q(first.url())).append(",\n");
        j.append("    \"lineNumber\": ").append(first.lineNumber()).append(",\n");
        j.append("    \"columnNumber\": ").append(first.columnNumber()).append(",\n");
        j.append("    \"capturedAtStep\": ").append(q(first.capturedAtStep())).append(",\n");
        j.append("    \"capturedAfterActionMs\": ").append(first.capturedAfterActionMs()).append(",\n");
        j.append("    \"pageReadyState\": ").append(q(first.pageReadyState())).append(",\n");
        j.append("    \"classification\": {\n");
        j.append("      \"category\": ").append(q(cls.category().name())).append(",\n");
        j.append("      \"probableCause\": ").append(q(cls.probableCause())).append(",\n");
        j.append("      \"confidence\": ").append(q(cls.confidence())).append("\n");
        j.append("    }\n");
        j.append("  },\n");

        j.append("  \"browser\": {\n");
        j.append("    \"name\": ").append(q(ConfigFactory.getConfig().browser())).append(",\n");
        j.append("    \"version\": \"unknown\"\n");
        j.append("  },\n");

        j.append("  \"uiContext\": {\n");
        j.append("    \"url\": ").append(q(currentUrl)).append(",\n");
        j.append("    \"title\": ").append(q(pageTitle)).append(",\n");
        j.append("    \"activeModule\": ").append(q(activeModule)).append(",\n");
        j.append("    \"activeTab\": ").append(q(activeTab)).append(",\n");
        j.append("    \"currentRoute\": ").append(q(activeRoute)).append("\n");
        j.append("  },\n");

        j.append("  \"network\": {\n");
        j.append("    \"failedRequests\": ").append(failedCount).append(",\n");
        j.append("    \"correlatedWithJsError\": ").append(correlatedCount).append("\n");
        j.append("  },\n");

        j.append("  \"actionTrail\": [\n");
        for (int i = 0; i < trail.size(); i++) {
            j.append("    ").append(q(trail.get(i)));
            if (i < trail.size() - 1) j.append(",");
            j.append("\n");
        }
        j.append("  ],\n");

        j.append("  \"artifacts\": {\n");
        j.append("    \"extentReport\": \"reports/index.html\",\n");
        j.append("    \"harDir\": \"reports/har/\",\n");
        j.append("    \"jsErrorsDir\": \"reports/js-errors/\"\n");
        j.append("  }\n");
        j.append("}\n");

        return j.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String extractType(String message) {
        if (message == null) return "Error";
        int idx = message.indexOf(':');
        return idx > 0 ? message.substring(0, idx).trim() : "Error";
    }

    @FunctionalInterface
    private interface StringSupplier { String get() throws Exception; }

    private static String safeDriverCall(StringSupplier s) {
        try { return nvl(s.get(), ""); } catch (Exception e) { return ""; }
    }
}
