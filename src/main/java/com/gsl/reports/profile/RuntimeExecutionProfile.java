package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuntimeExecutionProfile {
    private ExecutionConfig execution;
    private BrowserConfig browser;
    private DatabaseConfig database;
    private NetworkConfig network;
    private DiagnosticsConfig diagnostics;
    private RemoteLogConfig remoteLogs;
    private RemoteServerConfig remoteServer;
    private TestDataConfig testData;
    private PerformanceConfig performance;
    private Map<String, String> appLogSources;
}
