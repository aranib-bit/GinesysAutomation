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
}
