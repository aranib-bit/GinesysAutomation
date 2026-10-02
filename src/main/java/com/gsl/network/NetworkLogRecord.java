package com.gsl.network;

import java.util.Map;

/**
 * One captured network request/response (or failure), produced by any {@link NetworkCapture}
 * implementation and consumed by {@code NetworkExtentLogger}, {@link HarFileGenerator}, and
 * {@link NetworkJsErrorCorrelator}.
 *
 * @param status     HTTP status code; {@code 0} for a request that never received a response (e.g. a fetch error)
 * @param requestBody  captured request body, or {@code null} when body capture is disabled/unavailable for this protocol
 * @param responseBody captured response body, or {@code null} when body capture is disabled/unavailable for this protocol
 * @param timestamp  epoch millis when the request started
 * @param durationMs elapsed time from request start to response/failure; {@code 0} when not measured (e.g. BiDi)
 * @param type       {@link NetworkLogType#RESPONSE} or {@link NetworkLogType#FAILED}
 */
public record NetworkLogRecord(
        String              url,
        String              method,
        int                 status,
        Map<String, String> requestHeaders,
        Map<String, String> responseHeaders,
        String              requestBody,
        String              responseBody,
        long                timestamp,
        long                durationMs,
        NetworkLogType      type
) {}
