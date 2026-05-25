package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

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
