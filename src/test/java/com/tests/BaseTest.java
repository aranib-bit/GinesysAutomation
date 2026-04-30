package com.tests;

import com.gsl.config.FrameworkConfig;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.driver.Driver;
import com.gsl.utils.TestContextManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;
import java.net.MalformedURLException;

public class BaseTest {

    protected FrameworkConfig config;

    @BeforeMethod
    public void setUp(Method method) throws MalformedURLException {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();
        Driver.initDriver();
    }

    @AfterMethod
    public void tearDown() {
        DocumentReservationRegistry.releaseCurrentThread();
        Driver.quitDriver();
        TestContextManager.clear();
    }


}
