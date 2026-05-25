package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RemoteLogConfig {
    private String captureMode;
    private long maxLines;
    private boolean debugEnabled;
    private int sshRetryCount;
    private int sshCommandTimeoutSeconds;
    private int winrmOperationTimeoutSeconds;
    private String serverTimezoneOffset;
    private String keywords;
}
