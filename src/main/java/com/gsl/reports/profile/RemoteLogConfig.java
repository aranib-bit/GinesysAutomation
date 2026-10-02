package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the remote log-collection values shown in the Extent Report's Runtime
 * Profile "Remote Log Collection Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildRemoteLogs}, rendered by
 * {@code ReportConfigRenderer#renderRemoteLogSection}).
 *
 * <p>A close, renamed mirror of the log-capture-related fields on
 * {@link com.gsl.config.RemoteLogConfig} (e.g. its {@code remoteLogsCaptureMode()} becomes
 * {@code captureMode} here). The <em>same</em> live class also supplies the host/port/credential
 * fields on the sibling {@link RemoteServerConfig} DTO — see that class's Javadoc — so together
 * these two report-profile classes split one live config interface into two display sections
 * rather than each mirroring a same-named counterpart of their own.
 */
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
