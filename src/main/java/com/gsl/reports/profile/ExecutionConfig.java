package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

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
