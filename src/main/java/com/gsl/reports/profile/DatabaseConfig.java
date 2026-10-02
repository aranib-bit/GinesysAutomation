package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the database/connection-pool values shown in the Extent Report's Runtime
 * Profile "Database Configuration" section (built by {@code RuntimeProfileBuilder#buildDatabase},
 * rendered by {@code ReportConfigRenderer#renderDatabaseSection}).
 *
 * <p>Note the naming trap: this class's live counterpart is {@code com.gsl.config.DataBaseConfig}
 * (capital "B" in "DataBase"), not a same-named {@code com.gsl.config.DatabaseConfig} — no such
 * class exists. {@code username} and {@code password} are always replaced with a masked
 * placeholder by the builder before this object is ever populated; this class does no masking
 * itself, it only displays whatever it is given.
 */
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
