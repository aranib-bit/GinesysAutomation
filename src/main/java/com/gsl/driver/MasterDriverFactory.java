package com.gsl.driver;

import com.gsl.driver.provider.ExecutionProviderRegistry;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import org.openqa.selenium.WebDriver;

/**
 * Single entry point for driver creation.
 * Delegates to ExecutionProviderRegistry — callers need only supply RunMode + Browser.
 */
public final class MasterDriverFactory {

    private MasterDriverFactory() {}

    /** Creates a WebDriver for {@code mode}/{@code browser} via the registered {@link ExecutionProviderRegistry} provider for that {@link RunMode}. */
    public static WebDriver create(RunMode mode, Browser browser) {
        return ExecutionProviderRegistry.get(mode).createDriver(browser);
    }
}
