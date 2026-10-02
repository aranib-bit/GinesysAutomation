package com.gsl.network;

import com.gsl.utils.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P9: Generates a HAR 1.2 (HTTP Archive) JSON file from captured network traffic.
 *
 * <p>The file is written to {@code reports/har/<testName>-<epochMs>.har} and the
 * absolute path is returned so callers can embed a download link in the report.
 *
 * <p>HAR spec reference: http://www.softwareishard.com/blog/har-12-spec/
 */
public final class HarFileGenerator {

    private HarFileGenerator() {}

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Converts the given log records to HAR 1.2 JSON and saves the file.
     *
     * @param logs     captured network log records for this test
     * @param testName used as the HAR file name prefix (unsafe characters replaced)
     * @return absolute path of the written file, or {@code null} if generation failed
     */
    public static Path generate(List<NetworkLogRecord> logs, String testName) {
        if (logs == null || logs.isEmpty()) return null;

        try {
            Path harDir = Paths.get("reports", "har");
            Files.createDirectories(harDir);

            String safeName = testName == null ? "test"
                    : testName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String fileName = safeName + "-" + System.currentTimeMillis() + ".har";
            Path harPath = harDir.resolve(fileName);

            String json = buildHar(logs);
            Files.writeString(harPath, json, StandardCharsets.UTF_8);

            Log.info("[HAR] Written: {}", harPath.toAbsolutePath());
            return harPath;

        } catch (IOException e) {
            Log.warn("[HAR] Failed to write HAR file: {}", e.getMessage());
            return null;
        }
    }

    // ── HAR builder ───────────────────────────────────────────────────────────

    /** Assembles the top-level HAR JSON object (log/version/creator/entries) from all log records. */
    private static String buildHar(List<NetworkLogRecord> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
          .append("  \"log\": {\n")
          .append("    \"version\": \"1.2\",\n")
          .append("    \"creator\": { \"name\": \"SeleniumFramework\", \"version\": \"1.0\" },\n")
          .append("    \"entries\": [\n");

        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(buildEntry(logs.get(i)));
        }

        sb.append("\n    ]\n")
          .append("  }\n")
          .append("}\n");
        return sb.toString();
    }

    /** Builds one HAR {@code entries[]} object (startedDateTime/time/request/response/timings) for a single log record. */
    private static String buildEntry(NetworkLogRecord r) {
        String startedAt = r.timestamp() > 0
                ? ISO_FMT.format(Instant.ofEpochMilli(r.timestamp()))
                : ISO_FMT.format(Instant.now());

        return "    {\n"
             + "      \"startedDateTime\": " + jsonStr(startedAt) + ",\n"
             + "      \"time\": " + r.durationMs() + ",\n"
             + "      \"request\": " + buildRequest(r) + ",\n"
             + "      \"response\": " + buildResponse(r) + ",\n"
             + "      \"timings\": { \"send\": 0, \"wait\": " + r.durationMs() + ", \"receive\": 0 }\n"
             + "    }";
    }

    /** Builds a HAR {@code request} object, adding a {@code postData} block only when a request body was captured. */
    private static String buildRequest(NetworkLogRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
          .append("        \"method\": ").append(jsonStr(r.method())).append(",\n")
          .append("        \"url\": ").append(jsonStr(r.url())).append(",\n")
          .append("        \"httpVersion\": \"HTTP/1.1\",\n")
          .append("        \"headers\": ").append(headersJson(r.requestHeaders())).append(",\n")
          .append("        \"queryString\": [],\n")
          .append("        \"cookies\": [],\n")
          .append("        \"headersSize\": -1,\n")
          .append("        \"bodySize\": -1");
        if (hasValue(r.requestBody())) {
            String reqMime = Optional.ofNullable(r.requestHeaders())
                    .map(h -> h.getOrDefault("content-type", "application/json"))
                    .map(ct -> ct.contains(";") ? ct.substring(0, ct.indexOf(';')).trim() : ct)
                    .orElse("application/json");
            int reqBodySize = r.requestBody().getBytes(StandardCharsets.UTF_8).length;
            sb.append(",\n        \"postData\": { \"mimeType\": ").append(jsonStr(reqMime))
              .append(", \"text\": ").append(jsonStr(r.requestBody())).append(" }");
            // patch bodySize now that we know it
            int bsIdx = sb.indexOf("\"bodySize\": -1");
            if (bsIdx >= 0) sb.replace(bsIdx, bsIdx + 14, "\"bodySize\": " + reqBodySize);
        }
        return sb.append("\n      }").toString();
    }

    /** Builds a HAR {@code response} object; a {@code FAILED} record gets status/statusText {@code 0}/"Network Error" instead of an HTTP status. */
    private static String buildResponse(NetworkLogRecord r) {
        int status = r.status() == 0 ? 0 : r.status();
        String statusText = r.type() == NetworkLogType.FAILED ? "Network Error" : httpStatusText(status);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
          .append("        \"status\": ").append(status).append(",\n")
          .append("        \"statusText\": ").append(jsonStr(statusText)).append(",\n")
          .append("        \"httpVersion\": \"HTTP/1.1\",\n")
          .append("        \"headers\": ").append(headersJson(r.responseHeaders())).append(",\n")
          .append("        \"cookies\": [],\n");
        String respMime = Optional.ofNullable(r.responseHeaders())
                .map(h -> h.getOrDefault("content-type", "application/json"))
                .map(ct -> ct.contains(";") ? ct.substring(0, ct.indexOf(';')).trim() : ct)
                .orElse("application/json");
        int respBodySize = hasValue(r.responseBody())
                ? r.responseBody().getBytes(StandardCharsets.UTF_8).length : -1;
        sb.append("        \"content\": {\n")
          .append("          \"size\": ").append(respBodySize).append(",\n")
          .append("          \"mimeType\": ").append(jsonStr(respMime));
        if (hasValue(r.responseBody())) {
            sb.append(",\n          \"text\": ").append(jsonStr(r.responseBody()));
        }
        sb.append("\n        },\n")
          .append("        \"redirectURL\": \"\",\n")
          .append("        \"headersSize\": -1,\n")
          .append("        \"bodySize\": ").append(respBodySize).append("\n")
          .append("      }");
        return sb.toString();
    }

    /** Renders a headers map as a HAR {@code [{name, value}, ...]} array, or {@code "[]"} when empty/null. */
    private static String headersJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("{ \"name\": ").append(jsonStr(e.getKey()))
              .append(", \"value\": ").append(jsonStr(e.getValue())).append(" }");
            first = false;
        }
        return sb.append("]").toString();
    }

    /** Maps a subset of common HTTP status codes to their reason phrase; falls back to {@code "Unknown"}. */
    private static String httpStatusText(int code) {
        return switch (code) {
            case 200 -> "OK"; case 201 -> "Created"; case 204 -> "No Content";
            case 301 -> "Moved Permanently"; case 302 -> "Found"; case 304 -> "Not Modified";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 403 -> "Forbidden";
            case 404 -> "Not Found"; case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway"; case 503 -> "Service Unavailable";
            default  -> "Unknown";
        };
    }

    /** True when {@code s} is non-null, non-blank, and not the literal string {@code "null"}. */
    private static boolean hasValue(String s) {
        return s != null && !s.isBlank() && !"null".equalsIgnoreCase(s);
    }

    /** Emits a JSON string literal, escaping special characters. */
    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
