package com.driver;

import lombok.SneakyThrows;
import org.openqa.selenium.WebDriver;

public final class DriverFactory {

    private DriverFactory() {
    }

    @SneakyThrows
    public static WebDriver getDriver(String runmode, String browserName) {
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
