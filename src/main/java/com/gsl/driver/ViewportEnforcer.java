package com.gsl.driver;

import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.RunMode;
import com.gsl.utils.Log;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.emulation.Emulation;

import java.util.List;
import java.util.Optional;

/**
 * Forces the browser viewport to the configured width/height and fails fast if it cannot.
 *
 * On a Docker Selenium Grid node with no window manager, {@code manage().window().setSize(...)}
 * and {@code --window-size=...} are both silently ignored — the browser keeps whatever default
 * size the node handed it (observed: 800x600 window / 800x513 viewport) while every call site
 * that asked for a resize reports success. CDP's {@code Emulation.setDeviceMetricsOverride} sets
 * the layout viewport directly inside the renderer process, so it is not dependent on a window
 * manager being present — that is what actually pins the size on such a node. The follow-up
 * {@code innerWidth}/{@code innerHeight} read-back is the fail-fast check: if neither mechanism
 * took effect, the run stops here with a clear cause instead of surfacing as unrelated
 * {@code element click intercepted} / {@code TimeoutException} failures deep inside a test.
 */
public final class ViewportEnforcer {

    private ViewportEnforcer() {}

    /**
     * Forces {@code driver}'s viewport to the configured width/height, unless {@link #isEnabled}
     * says this run should skip enforcement entirely. Applies {@code window().setSize()} first,
     * then (for a {@link HasDevTools} driver) the CDP {@code Emulation.setDeviceMetricsOverride}
     * override, then polls {@code window.innerWidth}/{@code innerHeight} until they match or the
     * poll budget is exhausted.
     *
     * @throws IllegalStateException if neither mechanism produces the requested viewport size —
     *                                deliberately fail-fast here rather than let it surface later
     *                                as an unrelated click-intercepted/timeout failure mid-test
     */
    public static void enforce(WebDriver driver) {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        if (!isEnabled(cfg)) {
            Log.info("[ViewportEnforcer] Disabled for this run (runMode={}, viewport.enforcer.enabled "
                    + "override={}) — skipping.", cfg.runMode(), cfg.viewportEnforcerEnabled());
            return;
        }
        int width = cfg.windowWidth();
        int height = cfg.windowHeight();

        try {
            driver.manage().window().setSize(new Dimension(width, height));
        } catch (Exception e) {
            Log.warn("[ViewportEnforcer] window().setSize() failed — {}", e.getMessage());
        }
        long[] afterSetSize = readViewport(driver);
        Log.info("[ViewportEnforcer] After setSize() — outer={} viewport={}x{}",
                safeOuterSize(driver), afterSetSize[0], afterSetSize[1]);

        if (driver instanceof HasDevTools hasDevTools) {
            try {
                DevTools devTools = hasDevTools.getDevTools();
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
            } catch (Exception e) {
                Log.warn("[ViewportEnforcer] CDP setDeviceMetricsOverride failed — {}", e.getMessage(), e);
            }
        } else {
            Log.warn("[ViewportEnforcer] driver is not HasDevTools (actual class={}) — CDP step skipped",
                    driver.getClass().getName());
        }

        // Bounded poll rather than a single immediate read: an async race between the CDP
        // command's ack and the renderer actually repainting at the new size would otherwise be
        // indistinguishable from a genuine failure. Every attempt is logged so a real failure
        // (never converges) is still visible, not silently retried away.
        long[] actual = afterSetSize;
        for (int attempt = 1; attempt <= 10; attempt++) {
            actual = readViewport(driver);
            Log.info("[ViewportEnforcer] Poll attempt {} — viewport={}x{}", attempt, actual[0], actual[1]);
            if (actual[0] == width && actual[1] == height) break;
            try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        long actualWidth = actual[0];
        long actualHeight = actual[1];
        if (actualWidth != width || actualHeight != height) {
            throw new IllegalStateException(String.format(
                    "[ViewportEnforcer] Viewport mismatch — expected %dx%d but the browser reports %dx%d. "
                    + "Both window().setSize() and the CDP device-metrics override failed to take effect "
                    + "on this node; ExtJS dialogs taller than the real viewport will render off-screen "
                    + "and every subsequent click on them will fail.",
                    width, height, actualWidth, actualHeight));
        }
        Log.info("[ViewportEnforcer] Viewport confirmed at {}x{}", actualWidth, actualHeight);
    }

    /**
     * {@code viewport.enforcer.enabled} explicitly set (system property or browser.properties)
     * wins outright, either way. Unset (the common case) falls back to a run-mode default: enabled
     * for every remote/grid mode (this class's own original design intent — see class Javadoc:
     * built for "a Docker Selenium Grid node with no window manager"), disabled for {@code LOCAL}
     * — confirmed live (run 20260926_002605) that LOCAL's own real OS window manager only ever
     * misses by a small margin (1350x673 vs. requested 1366x768) during a brief
     * multi-session-launch contention window, not the severe "stuck at an 800x600 Docker default"
     * case this check exists to catch.
     */
    private static boolean isEnabled(BrowserConfig cfg) {
        Boolean override = cfg.viewportEnforcerEnabled();
        if (override != null) return override;
        return cfg.runMode() != RunMode.LOCAL;
    }

    private static String safeOuterSize(WebDriver driver) {
        try {
            return driver.manage().window().getSize().toString();
        } catch (Exception e) {
            return "unavailable (" + e.getMessage() + ")";
        }
    }

    /** Reads {@code [window.innerWidth, window.innerHeight]} from the page via JS, as a 2-element {@code [width, height]} array. */
    @SuppressWarnings("unchecked")
    private static long[] readViewport(WebDriver driver) {
        Object result = ((JavascriptExecutor) driver)
                .executeScript("return [window.innerWidth, window.innerHeight];");
        List<Number> dims = (List<Number>) result;
        return new long[] {dims.get(0).longValue(), dims.get(1).longValue()};
    }
}
