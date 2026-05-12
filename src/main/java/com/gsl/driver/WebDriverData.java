package com.gsl.driver;

import com.gsl.enums.Browser;
import com.gsl.enums.RemoteModeType;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable data carrier for WebDriver creation requirements.
 * Passed into RemoteDriverFactory so the factory needs no direct config access.
 *
 * Build via:
 *   WebDriverData data = WebDriverData.builder()
 *       .browserType(Browser.CHROME)
 *       .remoteModeType(RemoteModeType.GRID)
 *       .gridUrl("http://localhost:4444/wd/hub")
 *       .headless(false)
 *       .build();
 */
@Getter
@Builder
public final class WebDriverData {

    private final Browser        browserType;
    private final RemoteModeType remoteModeType;
    private final String         gridUrl;
    private final boolean        headless;
    private final boolean        incognito;
    private final boolean        acceptInsecureCerts;
}
