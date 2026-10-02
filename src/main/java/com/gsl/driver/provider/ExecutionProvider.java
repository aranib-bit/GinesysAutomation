package com.gsl.driver.provider;

import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;

/**
 * Pluggable driver creation contract.
 * Each execution environment (Local, SeleniumGrid, DockerGrid, BrowserStack, LambdaTest, Appium)
 * implements this interface. ExecutionProviderRegistry maps RunMode → ExecutionProvider.
 */
public interface ExecutionProvider {

    /**
     * Creates and returns a new WebDriver session configured for this provider's execution
     * environment (local, grid, cloud, or mobile).
     *
     * @param browser the browser to launch; mobile providers may ignore this
     * @return a ready-to-use WebDriver
     */
    WebDriver createDriver(Browser browser);

    /** Human-readable provider name for logs/reports; defaults to the implementing class's simple name. */
    default String getProviderName() {
        return getClass().getSimpleName();
    }
}
