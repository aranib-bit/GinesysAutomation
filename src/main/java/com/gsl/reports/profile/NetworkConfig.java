package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetworkConfig {
    private String networkMode;
    private boolean enabled;
    private boolean captureFailedOnly;
    private boolean captureHeaders;
    private boolean captureBody;
    private String urlFilter;
    private boolean passShowAll;
    private String timingLogOn;
    private String cookiesLogOn;
    private String callstackLogOn;
    private boolean browserLogsEnabled;
    private boolean browserLogsCaptureOnFailure;
    private boolean browserLogsFailOnJsError;
    private boolean browserLogsFailOnCriticalJsError;
    private boolean browserLogsCaptureOnly;
    private String browserLogsLevel;
}
