package com.gsl.driver.viewport;

import com.gsl.utils.Log;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.browsingcontext.BrowsingContext;

/**
 * Firefox (and any other BiDi-only driver): pins the viewport via the WebDriver BiDi
 * {@code browsingContext.setViewport} command — the BiDi counterpart of CDP's device-metrics
 * override. Requires {@code webSocketUrl=true} (already set in {@code BrowserOptions.firefox()}).
 */
public final class BiDiViewportPinner implements ViewportPinner {

    @Override
    public void pin(WebDriver driver, int width, int height) {
        // devicePixelRatio 1.0 — keep screenshots CSS-pixel-accurate
        new BrowsingContext(driver, driver.getWindowHandle()).setViewport(width, height, 1.0);
        Log.info("[ViewportEnforcer] BiDi browsingContext.setViewport({}x{}) sent without exception", width, height);
    }

    @Override
    public String protocolName() {
        return "BiDi";
    }
}
