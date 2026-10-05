package com.gsl.driver.viewport;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.devtools.HasDevTools;

/**
 * Selects the {@link ViewportPinner} for a driver, mirroring {@code NetworkServiceFactory}:
 * {@code HasDevTools} (Chrome/Edge) → CDP, {@code HasBiDi} (Firefox) → BiDi, otherwise outer-size compensation.
 */
public final class ViewportPinnerFactory {

    private ViewportPinnerFactory() {}

    /** Picks the pinner matching {@code driver}'s capabilities. */
    public static ViewportPinner create(WebDriver driver) {
        if (driver instanceof HasDevTools) return new CdpViewportPinner();
        if (driver instanceof HasBiDi)     return new BiDiViewportPinner();
        return new OuterSizeViewportPinner();
    }
}
