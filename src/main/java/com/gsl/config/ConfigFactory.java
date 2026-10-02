package com.gsl.config;

/**
 * Single entry point the rest of the framework uses to obtain any {@code @Config} instance —
 * one static getter per config interface, each backed by {@link ConfigCache} so the underlying
 * Owner proxy is created once and reused. Callers should always go through this class rather
 * than calling {@code org.aeonbits.owner.ConfigFactory} or {@link ConfigCache} directly.
 */
public final class ConfigFactory {

    private ConfigFactory() {
    }

    /** Returns the shared {@link FrameworkConfig} instance (core {@code config.properties}). */
    public static FrameworkConfig getConfig() {
        return ConfigCache.getOrCreate(FrameworkConfig.class);
    }

    /** Returns the shared {@link ExcelDataConfig} instance. */
    public static ExcelDataConfig getExcelDataConfig() {
        return ConfigCache.getOrCreate(ExcelDataConfig.class);
    }

    /** Returns the shared {@link DataBaseConfig} instance. */
    public static DataBaseConfig getDataBaseConfig() {
        return ConfigCache.getOrCreate(DataBaseConfig.class);
    }

    /** Returns the shared {@link AppLogPathsConfig} instance. */
    public static AppLogPathsConfig getAppLogPathsConfig() {
        return ConfigCache.getOrCreate(AppLogPathsConfig.class);
    }

    /** Returns the shared {@link BrowserConfig} instance. */
    public static BrowserConfig getBrowserConfig() {
        return ConfigCache.getOrCreate(BrowserConfig.class);
    }

    /** Returns the shared {@link WaitConfig} instance. */
    public static WaitConfig getWaitConfig() {
        return ConfigCache.getOrCreate(WaitConfig.class);
    }

    /** Returns the shared {@link ExecutionConfig} instance. */
    public static ExecutionConfig getExecutionConfig() {
        return ConfigCache.getOrCreate(ExecutionConfig.class);
    }

    /** Returns the shared {@link FinanceConfig} instance. */
    public static FinanceConfig getFinanceConfig() {
        return ConfigCache.getOrCreate(FinanceConfig.class);
    }

    /** Returns the shared {@link NetworkConfig} instance. */
    public static NetworkConfig getNetworkConfig() {
        return ConfigCache.getOrCreate(NetworkConfig.class);
    }

    /** Returns the shared {@link DiagnosticsConfig} instance. */
    public static DiagnosticsConfig getDiagnosticsConfig() {
        return ConfigCache.getOrCreate(DiagnosticsConfig.class);
    }

    /** Returns the shared {@link ReportConfig} instance. */
    public static ReportConfig getReportConfig() {
        return ConfigCache.getOrCreate(ReportConfig.class);
    }

    /** Returns the shared {@link RemoteLogConfig} instance. */
    public static RemoteLogConfig getRemoteLogConfig() {
        return ConfigCache.getOrCreate(RemoteLogConfig.class);
    }

    /** Returns the shared {@link ApiConfig} instance. */
    public static ApiConfig getApiConfig() {
        return ConfigCache.getOrCreate(ApiConfig.class);
    }
}
