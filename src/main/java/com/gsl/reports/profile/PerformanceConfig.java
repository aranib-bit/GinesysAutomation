package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceConfig {
    private boolean timingEnabled;
    private int actionTrailBufferSize;
    private int actionTrailDisplayLimit;
    private boolean customMetricsEnabled;
    private String customMetricsCaptureMode;
    private long slowResourceThresholdMs;
    private long userTimingWarnMs;
    private int failOnLongTaskCount;
}
