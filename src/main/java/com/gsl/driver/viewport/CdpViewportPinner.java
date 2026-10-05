package com.gsl.driver.viewport;

import com.gsl.utils.Log;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.emulation.Emulation;

import java.util.Optional;

/** Chrome/Edge: pins the layout viewport inside the renderer via CDP {@code Emulation.setDeviceMetricsOverride}, independent of any window manager. */
public final class CdpViewportPinner implements ViewportPinner {

    @Override
    public void pin(WebDriver driver, int width, int height) {
        DevTools devTools = ((HasDevTools) driver).getDevTools();
        Log.info("[ViewportEnforcer] DevTools impl={}, cdpSession before={}",
                devTools.getClass().getName(), devTools.getCdpSession());
        // Reuses CdpNetworkCapture's session when one is already open (network capture
        // enabled) instead of creating a second one — DevTools.createSession() attaches a
        // fresh CDP session unconditionally, which NetworkTimingCapture (see its class
        // Javadoc) deliberately avoids triggering a second time. createSessionIfThereIsNotOne()
        // is the session-agnostic entry point: safe whether or not one already exists.
        devTools.createSessionIfThereIsNotOne();
        Log.info("[ViewportEnforcer] cdpSession after createSessionIfThereIsNotOne={}",
                devTools.getCdpSession());
        devTools.send(Emulation.setDeviceMetricsOverride(
                width, height,
                1,      // deviceScaleFactor — keep 1:1 so screenshots stay CSS-pixel-accurate
                false,  // mobile — must stay false or ExtJS switches to touch event handling
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()));
        Log.info("[ViewportEnforcer] Emulation.setDeviceMetricsOverride sent without exception");
    }

    @Override
    public String protocolName() {
        return "CDP";
    }
}
