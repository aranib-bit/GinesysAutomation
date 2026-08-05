package com.gsl.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

/**
 * Logs every API call made through {@code ApiClient} to the Extent Report as a bordered
 * Field|Value table (reusing {@link ReportLogger#infoLabeledTable}, the same renderer "Actual
 * Results" already uses) — Method/URL/Payload/Response, with a "(NNNms)" duration suffix on the
 * label, same style as the existing "Actual Results (360ms)" business-data blocks.
 *
 * <p>Calls made during backfill (see {@code ArVoucherApiClient}, {@code ItemApiClient},
 * {@code GrcApiClient}, {@code DcApiClient}, {@code ServiceInvoiceAdhocApiClient}) each wrap their
 * call to this logger in a {@link StepExecutor#call(String, StepExecutor.StepSupplier)} node named
 * for that specific call (e.g. "Get Sub Ledger Details for AR Voucher") — the node itself is the
 * per-call sub-section, so this class only renders the Method/URL table and Payload/Response
 * collapsibles, with no heading of its own.
 */
public final class ApiCallReportLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiCallReportLogger() {}

    public static void logCall(String method, String url, Object requestBody, Response response, long durationMs) {
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
