package com.reports;

import com.aventstack.extentreports.MediaEntityBuilder;
import com.utils.Selenium.ScreenshotUtils;

public final class ExtentLogger {

    private ExtentLogger(){}

    public static void pass(String message){
        ExtentManager.getExtentTest().pass(message);
    }

    public static void fail(String message){
        ExtentManager.getExtentTest().fail(message, MediaEntityBuilder.createScreenCaptureFromBase64String(ScreenshotUtils.getScreenshot()).build());
    }

    public static void info(String message){
        ExtentManager.getExtentTest().info(message);
    }




}
