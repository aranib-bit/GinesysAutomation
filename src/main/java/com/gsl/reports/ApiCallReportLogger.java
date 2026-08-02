package com.gsl.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

/**
 * Logs every API call made through {@code ApiClient} to the Extent Report as a bordered
 * Field|Value table (reusing {@link ReportLogger#infoLabeledTable}, the same renderer "Actual
 * Results" already uses) — Method/URL/Payload/Response, with a "(NNNms)" duration suffix on the
 * label, same style as the existing "Actual Results (360ms)" business-data blocks.
 *
 * <p>Calls made during backfill (see {@code PendingArDocumentBackfillStrategy} /
 * {@code PendingApDocumentBackfillStrategy}) additionally get a bold "PreRequisites — Data
 * Creation" header immediately above their table — set via {@link #setSection(String)} around
 * the backfill loop, so a WEB test's own direct API-assisted setup or a plain {@code APITest}
 * call (no section set) is unaffected and just gets the plain per-call table.
 */
public final class ApiCallReportLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadLocal<String> section = new ThreadLocal<>();

    private ApiCallReportLogger() {}

    /** Sets the section header every subsequent logCall() on this thread renders under, until cleared. */
    public static void setSection(String label) {
        section.set(label);
    }

    public static void clearSection() {
        section.remove();
    }

    public static void logCall(String method, String url, Object requestBody, Response response, long durationMs) {
        String current = section.get();
        if (current != null) {
            ReportLogger.infoHtml("<b>" + current + "</b>");
        }

        String[][] table = {
            {"Field", "Value"},
            {"Method", method},
            {"URL", url},
        };
        ReportLogger.infoLabeledTable(method + " " + url + " (" + durationMs + "ms)", table);

        String payloadBlock = requestBody != null
                ? NetworkExtentLogger.jsonBlock(toJsonString(requestBody))
                : "<i>(none)</i>";
        String responseBlock = NetworkExtentLogger.jsonBlock(response.getBody().asString());
        ReportLogger.infoHtml(
                NetworkExtentLogger.collapsible("Payload", payloadBlock, "#555")
                        + NetworkExtentLogger.collapsible("Response", responseBlock, "#555"));
    }

    private static String toJsonString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
