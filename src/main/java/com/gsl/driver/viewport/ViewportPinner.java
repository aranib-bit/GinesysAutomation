package com.gsl.driver.viewport;

import org.openqa.selenium.WebDriver;

/**
 * Strategy interface for forcing a browser's layout viewport to a given size, selected per driver
 * by {@link ViewportPinnerFactory} — the same shape as {@code com.gsl.network.NetworkCapture} and
 * {@code NetworkServiceFactory}:
 *
 * <ul>
 *   <li>{@link CdpViewportPinner} — Chrome/Edge, via Chrome DevTools Protocol</li>
 *   <li>{@link BiDiViewportPinner} — Firefox, via the WebDriver BiDi browsingContext module</li>
 *   <li>{@link OuterSizeViewportPinner} — driver supports neither protocol</li>
 * </ul>
 *
 * Implementations only apply the size; verifying it took effect is
 * {@link com.gsl.driver.ViewportEnforcer}'s job.
 */
public interface ViewportPinner {

    /** Attempts to make the viewport {@code width}x{@code height}; may throw if the mechanism is unavailable. */
    void pin(WebDriver driver, int width, int height);

    /** Short label identifying the mechanism in use (e.g. {@code "CDP"}, {@code "BiDi"}, {@code "OUTER_SIZE"}), used in logs. */
    String protocolName();
}
