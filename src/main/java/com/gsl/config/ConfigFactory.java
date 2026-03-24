package com.gsl.config;

public final class ConfigFactory {

    private ConfigFactory() {
    }

    public static FrameworkConfig getConfig() {
        return org.aeonbits.owner.ConfigFactory.create(FrameworkConfig.class);
    }

    public static ExcelDataConfig getExcelDataConfig() {
        return org.aeonbits.owner.ConfigFactory.create(ExcelDataConfig.class);
    }

    public static DataBaseConfig getDataBaseConfig() {
        return org.aeonbits.owner.ConfigFactory.create(DataBaseConfig.class);
    }
}
