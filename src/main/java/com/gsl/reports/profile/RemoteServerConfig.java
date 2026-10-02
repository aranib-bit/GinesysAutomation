package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the remote app-/DB-server host, port, and credential values shown in the
 * Extent Report's Runtime Profile "Remote Server Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildRemoteServer}, rendered by
 * {@code ReportConfigRenderer#renderRemoteServerSection}).
 *
 * <p>There is no same-named {@code com.gsl.config.RemoteServerConfig} — every field here is
 * sourced from {@link com.gsl.config.RemoteLogConfig}'s {@code remoteAppServer*}/
 * {@code remoteDbServer*} accessors, the same live class that backs the sibling
 * {@link RemoteLogConfig} DTO's log-capture fields (see that class's Javadoc). All SSH/WinRM
 * usernames and passwords are already masked by the builder before this object is populated —
 * this class only displays what it is given.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RemoteServerConfig {
    // Application server
    private String appHost;
    private int appSshPort;
    private String appSshUsername;
    private String appSshPassword;
    private int appWinrmPort;
    private boolean appWinrmUseHttps;
    private String appWinrmUsername;
    private String appWinrmPassword;
    // Database server
    private String dbHost;
    private int dbSshPort;
    private String dbSshUsername;
    private String dbSshPassword;
    private int dbWinrmPort;
    private boolean dbWinrmUseHttps;
    private String dbWinrmUsername;
    private String dbWinrmPassword;
}
