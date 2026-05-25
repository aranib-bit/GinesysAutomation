package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatabaseConfig {
    private String type;
    private String name;
    private String host;
    private String port;
    private String username;
    private String password;
    private String poolName;
    private int maximumPoolSize;
    private int minimumIdle;
    private int idleTimeout;
    private int connectionTimeout;
    private int maxLifetime;
    private int leakDetectionThreshold;
}
