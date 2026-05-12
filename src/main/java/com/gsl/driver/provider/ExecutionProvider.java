package com.gsl.driver.provider;

import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;

/**
 * Pluggable driver creation contract.
 * Each execution environment (Local, SeleniumGrid, DockerGrid, BrowserStack, LambdaTest, Appium)
 * implements this interface. ExecutionProviderRegistry maps RunMode → ExecutionProvider.
 */
public interface ExecutionProvider {

    WebDriver createDriver(Browser browser);

    default String getProviderName() {
        return getClass().getSimpleName();
    }
}
