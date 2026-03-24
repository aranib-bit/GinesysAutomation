package com.gsl.driver;

import com.gsl.config.ConfigFactory;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class Driver {

    private Driver() {
    }


    // Initializes WebDriver for the current thread
    public static void initDriver() throws MalformedURLException {
        String browser = ConfigFactory.getConfig().browser();
        String runmode = ConfigFactory.getConfig().runmode();
        if (DriverManager.getDriver() == null) {
            WebDriver driver = DriverFactory.getDriver(runmode, browser);
            DriverManager.setDriver(driver);

            // Logging for debugging
        System.out.println("[Thread " + Thread.currentThread().getId() +
                   "] Initialized driver with session ID: " +
                    ((org.openqa.selenium.remote.RemoteWebDriver) driver).getSessionId());
        }

        DriverManager.getDriver().get(ConfigFactory.getConfig().url());
        DriverManager.getDriver().manage().window().maximize();
      //  DriverManager.getDriver().manage().timeouts().implicitlyWait(
             //   Duration.ofSeconds (ConfigFactory.getConfig().timeout()));
    }


    // Quits and removes WebDriver for the current thread
    public static void quitDriver() {
        if (DriverManager.getDriver() != null) {
            DriverManager.getDriver().quit();
            DriverManager.unloadDriver();

            System.out.println("[Thread " + Thread.currentThread().getId() + "] Driver quit.");
        }
    }
}
