package com.gsl.driver;

import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;

public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver getDriver(String runmode, String browserName) throws MalformedURLException {
        WebDriver driver;
        if (runmode.equalsIgnoreCase("local")) {
            driver = LocalDriverFactory.getLocalDriver(browserName);
        } else if (runmode.equalsIgnoreCase("remote")) {
            driver = RemoteDriverFactory.getRemoteDriver(browserName);
        } else {
            throw new IllegalArgumentException("Unsupported runmode: " + runmode);
        }
        return driver;
    }

}
