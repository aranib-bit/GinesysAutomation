package com.tests;

import com.gsl.config.FrameworkConfig;
import com.gsl.driver.Driver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.net.MalformedURLException;

public class BaseTest {

    protected FrameworkConfig config;

    @BeforeMethod
    public void setUp() throws MalformedURLException {
        Driver.initDriver();
    }

    @AfterMethod
    public void tearDown() {
        Driver.quitDriver();
    }


}
