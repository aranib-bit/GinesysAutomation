package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the network-capture and browser-log values shown in the Extent Report's
 * Runtime Profile "Network &amp; Browser Logging Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildNetwork}, rendered by
 * {@code ReportConfigRenderer#renderNetworkSection}).
 *
 * <p>Almost a 1:1 mirror of {@link com.gsl.config.NetworkConfig}'s own capture/browser-log
 * fields — the one exception is {@code networkMode}, which is the capture protocol that applies to
 * the configured browser (CDP for Chromium, BiDi for Firefox), not a configured value.
 */
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
