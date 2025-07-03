package com.tests;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.config.FrameworkConfig;
import com.driver.Driver;
import com.reports.ExtentReport;
import org.aeonbits.owner.ConfigFactory;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.lang.reflect.Method;

public class BaseTest {


    @BeforeSuite
    public void beforeSuite(){
        ExtentReport.initReports();
    }

    @AfterSuite
    public void afterSuite(){
        ExtentReport.flushReports();
    }


    protected FrameworkConfig config;

    @AfterMethod
    public void tearDown(){
        Driver.quitDriver();
    }

    @BeforeMethod
    public void setUp(Method method ){
        ExtentReport.createTest(method.getName());
        Driver.initDriver();
    }
}
