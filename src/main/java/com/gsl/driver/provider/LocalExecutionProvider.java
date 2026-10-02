package com.gsl.driver.provider;

import com.gsl.driver.manager.ChromeManager;
import com.gsl.driver.manager.EdgeManager;
import com.gsl.driver.manager.FirefoxManager;
import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;

import java.util.EnumMap;
import java.util.function.Supplier;

/**
 * Creates a local WebDriver using atomic Manager classes via method references.
 * EnumMap<Browser, Supplier<WebDriver>> provides lazy evaluation and eliminates if/else.
 * Adding a new browser requires one line in the static block and a new Manager class.
 */
public final class LocalExecutionProvider implements ExecutionProvider {

    private static final EnumMap<Browser, Supplier<WebDriver>> FACTORIES =
            new EnumMap<>(Browser.class);

    static {
        FACTORIES.put(Browser.CHROME,  ChromeManager::getDriver);
        FACTORIES.put(Browser.FIREFOX, FirefoxManager::getDriver);
        FACTORIES.put(Browser.EDGE,    EdgeManager::getDriver);
    }

    /**
     * Looks up {@code browser}'s factory in {@link #FACTORIES} and invokes it to create a local
     * WebDriver.
     *
     * @throws IllegalArgumentException if {@code browser} has no registered local factory
     */
    @Override
    public WebDriver createDriver(Browser browser) {
        Supplier<WebDriver> supplier = FACTORIES.get(browser);
        if (supplier == null) {
            throw new IllegalArgumentException("[LocalExecutionProvider] Unsupported browser: " + browser);
        }
        return supplier.get();
    }
}
