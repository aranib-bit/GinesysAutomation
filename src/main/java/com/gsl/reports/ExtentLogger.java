package com.gsl.reports;

import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Selenium.ScreenshotUtils;

public final class ExtentLogger {

    private ExtentLogger() {
    }

    public static void pass(String message) {
        ExtentManager.getExtentTest().pass(message);
    }

    public static void fail(String message) {
        if (DriverManager.getDriver() != null) {
            ExtentManager.getExtentTest().fail(message, MediaEntityBuilder.createScreenCaptureFromBase64String(ScreenshotUtils.getScreenshot()).build());
        } else {
            ExtentManager.getExtentTest().fail(message);
        }
    }

    public static void info(String message) {
        ExtentManager.getExtentTest().info(message);
    }
}

