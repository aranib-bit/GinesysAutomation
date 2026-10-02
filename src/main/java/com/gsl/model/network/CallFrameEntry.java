package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

/**
 * One JS call-stack frame from a CDP request's initiator stack, captured by
 * {@code NetworkTimingCapture} to show what script triggered a network request.
 * Getters/setters/builder are Lombok-generated ({@code @Data @Builder}).
 */
@Data
@Builder
public class CallFrameEntry {
    private String functionName;
    private String scriptUrl;
    private int    lineNumber;
    private int    columnNumber;
}
