package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the performance-diagnostics values shown in the Extent Report's Runtime
 * Profile "Performance Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildPerformance}, rendered by
 * {@code ReportConfigRenderer#renderPerformanceSection}) and echoed into the dashboard system-info
 * panel by {@code ReportConfigRenderer#renderDashboard}.
 *
 * <p>Unlike its siblings in this package, there is no same-named {@code com.gsl.config} class —
 * this DTO is assembled from a mix of fields on {@link com.gsl.config.DiagnosticsConfig}
 * ({@code actionTrailBufferSize}, {@code actionTrailDisplayLimit}, the {@code customMetrics*}
 * fields) and the unrelated {@code ReportConfig} ({@code timingEnabled}).
 */
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
