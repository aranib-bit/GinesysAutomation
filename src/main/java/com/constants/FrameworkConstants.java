package com.constants;

public final class FrameworkConstants {

    private FrameworkConstants(){}

    private final static String REPORT_PATH=System.getProperty("user.dir")+"/index.html";

    public static String getReportPath(){
        return REPORT_PATH;
    }
}
