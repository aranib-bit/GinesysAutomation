package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

/**
 * One metric parsed from a response's {@code Server-Timing} header (name, optional description,
 * duration), per the Server-Timing spec. Parsed by {@code NetworkTimingCapture.parseServerTiming}.
 * Getters/setters/builder are Lombok-generated ({@code @Data @Builder}).
 */
@Data
@Builder
public class ServerTimingEntry {
    private String metricName;
    private String description;
    private double duration;
}
