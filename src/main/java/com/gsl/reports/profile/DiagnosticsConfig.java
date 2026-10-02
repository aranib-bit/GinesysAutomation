package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the diagnostics-capture flags shown in the Extent Report's Runtime
 * Profile "Diagnostics Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildDiagnostics}, rendered by
 * {@code ReportConfigRenderer#renderDiagnosticsSection}).
 *
 * <p>Despite sharing its simple name with {@link com.gsl.config.DiagnosticsConfig}, this is only a
 * small subset of that interface's many capture-mode/threshold settings — just the on/off flags
 * and server host/type fields listed below — plus one field, {@code jsErrorJsonExportEnabled},
 * pulled from the unrelated {@code ReportConfig} instead. It is not exhaustive: fields like
 * {@code stepTimelineEnabled} or the {@code frontend*} tracking flags on the live config have no
 * counterpart here.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnosticsConfig {
    private boolean htmlCaptureOnElementFailure;
    private boolean driverCapabilitiesEnabled;
    private boolean activePopupEnabled;
    private boolean dbStateEnabled;
    private boolean threadDumpEnabled;
    private boolean jsErrorJsonExportEnabled;
    private boolean uiDiagnosticsEnabled;
    private boolean appServerUtilEnabled;
    private String appServerHost;
    private String appServerType;
    private boolean dbServerUtilEnabled;
    private String dbServerHost;
    private String dbServerType;
    private boolean eventLogEnabled;
    private int eventLogLines;
}
