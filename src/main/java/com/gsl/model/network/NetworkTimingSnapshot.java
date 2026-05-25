package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

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
