package com.driver;

import com.config.ConfigFactory;
import lombok.SneakyThrows;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;

public final class RemoteDriverFactory {
    private RemoteDriverFactory() {
    }

    @SneakyThrows
    public static WebDriver getRemoteDriver(String browser) {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        if (browser.equalsIgnoreCase("chrome")) {
            capabilities.setBrowserName("chrome");
        } else if (browser.equalsIgnoreCase("firefox")) {
            capabilities.setBrowserName("firefox");
        } else if (browser.equalsIgnoreCase("edge")) {
            capabilities.setBrowserName("edge");
        }
        return new RemoteWebDriver(new URL(ConfigFactory.getConfig().remoteurl()), capabilities);
    }
}
