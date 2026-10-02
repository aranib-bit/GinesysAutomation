package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Per-request timing/metadata breakdown captured by {@code NetworkTimingCapture} — request
 * cookies, JS initiator call stack, {@code Server-Timing} entries, and derived queueing/stalled/
 * content-download durations — one instance per CDP request, collected into the list returned
 * by {@code NetworkTimingCapture.stopAndCollect()}.
 * Getters/setters/builder are Lombok-generated ({@code @Data @Builder}).
 */
@Data
@Builder
public class NetworkTimingSnapshot {
    private String url;
    private int    statusCode;
    private String mimeType;
    private Map<String, Double>      timings;
    private List<ServerTimingEntry>  serverTiming;
    private List<RequestCookieEntry> requestCookies;
    private List<CallFrameEntry>     callStack;
    private double queueingMs;
    private double stalledMs;
    private double contentDownloadMs;
    private long   encodedDataBytes;
    private long   capturedAtMs;
}
