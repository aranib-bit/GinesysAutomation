package com.gsl.driver;

import com.gsl.driver.manager.ChromeManager;
import com.gsl.driver.manager.EdgeManager;
import com.gsl.driver.manager.FirefoxManager;
import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;

import java.util.EnumMap;
import java.util.function.Supplier;

/**
 * Local driver factory refactored with EnumMap<Browser, Supplier<WebDriver>>.
 * Manager method references replace the old if/else chain — lazy, SRP-compliant, extensible.
 * New code should prefer LocalExecutionProvider via MasterDriverFactory.
 */
public final class LocalDriverFactory {

    private LocalDriverFactory() {}

    private static final EnumMap<Browser, Supplier<WebDriver>> FACTORIES =
            new EnumMap<>(Browser.class);

    static {
        FACTORIES.put(Browser.CHROME,  ChromeManager::getDriver);
        FACTORIES.put(Browser.FIREFOX, FirefoxManager::getDriver);
        FACTORIES.put(Browser.EDGE,    EdgeManager::getDriver);
    }

    /** Returns a local WebDriver for the given Browser enum — lazy-evaluated via Supplier. */
    public static WebDriver getDriver(Browser browser) {
        Supplier<WebDriver> supplier = FACTORIES.get(browser);
        if (supplier == null) {
            throw new IllegalArgumentException("[LocalDriverFactory] Unsupported browser: " + browser);
        }
        return supplier.get();
    }

    /** Backward-compatible String overload — delegates to typed getDriver(Browser). */
    public static WebDriver getLocalDriver(String browser) {
        return getDriver(Browser.valueOf(browser.trim().toUpperCase()));
    }
}
