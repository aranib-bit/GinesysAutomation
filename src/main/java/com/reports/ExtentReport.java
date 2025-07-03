package com.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.constants.FrameworkConstants;

public final class ExtentReport {

    private ExtentReport(){}

    public static ExtentReports extent;
    public static ExtentTest extentTest; //extentTest is not thread safe now

    public static void initReports(){
        extent=new ExtentReports();
        ExtentSparkReporter sparkReporter=new ExtentSparkReporter(FrameworkConstants.getReportPath());
        extent.attachReporter(sparkReporter);
    }

    public static void flushReports(){
        extent.flush();
    }

    public static void createTest(String testCaseName){
        extentTest=extent.createTest(testCaseName);
        ExtentManager.setExtentTest(extentTest);
    }

    public static void assignAuthor (Enum author){
        ExtentManager.getExtentTest().assignAuthor(author.toString());
    }

}
