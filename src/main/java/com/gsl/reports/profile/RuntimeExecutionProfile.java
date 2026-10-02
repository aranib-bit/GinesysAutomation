package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Top-level, immutable aggregate of every {@code com.gsl.reports.profile} section — one full
 * "Runtime Profile" snapshot of the suite's effective configuration, assembled once per suite by
 * {@code RuntimeProfileBuilder#build()}.
 *
 * <p>Consumed by {@code ReportConfigRenderer} two ways: {@code renderDashboard} pulls a handful of
 * headline fields into the ExtentReports dashboard system-info panel, and {@code
 * renderAllSections} renders every nested section as its own markup table on the current test
 * node. It can also be serialized as-is to {@code runtime-snapshot.json} via {@code
 * ReportConfigRenderer#generateJsonSnapshot} for offline inspection.
 *
 * <p>All credentials on the nested sections ({@link DatabaseConfig}, {@link RemoteServerConfig})
 * are already masked before this object is built — nothing downstream needs to remember to mask
 * them again. {@link #getAppLogSources()} is not itself a section DTO — just a
 * {@code "Configured"}/{@code "Not Configured"} status map, one entry per configured application
 * log source.
 */
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
