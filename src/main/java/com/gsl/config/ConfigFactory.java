package com.gsl.config;

public final class ConfigFactory {

    private ConfigFactory() {
    }

    public static FrameworkConfig getConfig() {
        return ConfigCache.getOrCreate(FrameworkConfig.class);
    }

    public static ExcelDataConfig getExcelDataConfig() {
        return ConfigCache.getOrCreate(ExcelDataConfig.class);
    }

    public static DataBaseConfig getDataBaseConfig() {
        return ConfigCache.getOrCreate(DataBaseConfig.class);
    }

    public static AppLogPathsConfig getAppLogPathsConfig() {
        return ConfigCache.getOrCreate(AppLogPathsConfig.class);
    }

    public static BrowserConfig getBrowserConfig() {
        return ConfigCache.getOrCreate(BrowserConfig.class);
    }

    public static WaitConfig getWaitConfig() {
        return ConfigCache.getOrCreate(WaitConfig.class);
    }

    public static ExecutionConfig getExecutionConfig() {
        return ConfigCache.getOrCreate(ExecutionConfig.class);
    }

    public static FinanceConfig getFinanceConfig() {
        return ConfigCache.getOrCreate(FinanceConfig.class);
    }

    public static NetworkConfig getNetworkConfig() {
        return ConfigCache.getOrCreate(NetworkConfig.class);
    }

    public static DiagnosticsConfig getDiagnosticsConfig() {
        return ConfigCache.getOrCreate(DiagnosticsConfig.class);
    }

    public static ReportConfig getReportConfig() {
        return ConfigCache.getOrCreate(ReportConfig.class);
    }

    public static RemoteLogConfig getRemoteLogConfig() {
        return ConfigCache.getOrCreate(RemoteLogConfig.class);
    }

    public static ApiConfig getApiConfig() {
        return ConfigCache.getOrCreate(ApiConfig.class);
    }
}
