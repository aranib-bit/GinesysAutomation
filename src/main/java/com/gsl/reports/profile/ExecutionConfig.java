package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the suite-execution values shown in the Extent Report's Runtime Profile
 * "Environment &amp; Execution Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildExecution}, rendered by
 * {@code ReportConfigRenderer#renderEnvironmentSection}) and echoed into the dashboard system-info
 * panel by {@code ReportConfigRenderer#renderDashboard}.
 *
 * <p>Despite sharing its simple name with {@link com.gsl.config.ExecutionConfig}, this is NOT a
 * 1:1 mirror of it — that interface contributes only {@code executionType}, {@code threadCount},
 * and {@code concurrentUserCount}. The remaining fields come from three other live config sources:
 * {@code FrameworkConfig} ({@code environment}, {@code browser}, {@code applicationUrl}),
 * {@link com.gsl.config.BrowserConfig} ({@code runMode}, {@code applicationType},
 * {@code gridUrl}, {@code dockerGridUrl}), and {@code RemoteLogConfig}
 * ({@code remoteAppServerHost}).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionConfig {
    private String environment;
    private String browser;
    private String runMode;
    private String executionType;
    private int threadCount;
    private int concurrentUserCount;
    private String applicationType;
    private String applicationUrl;
    private String gridUrl;
    private String dockerGridUrl;
    private String remoteAppServerHost;
}
