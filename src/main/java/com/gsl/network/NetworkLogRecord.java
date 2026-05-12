package com.gsl.network;

import java.util.Map;

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
