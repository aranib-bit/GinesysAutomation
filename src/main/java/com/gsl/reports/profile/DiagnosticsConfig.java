package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

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
