package com.gsl.network;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.devtools.HasDevTools;

/**
 * Selects the correct NetworkCapture implementation for the given driver:
 *
 *   Chrome / Edge  →  CdpNetworkCapture  (HasDevTools)
 *   Firefox        →  BiDiNetworkCapture (HasBiDi, requires webSocketUrl=true)
 *   Other / off    →  NoOpNetworkCapture
 *
 * The returned instance is NOT started here — Driver.initDriver() calls start()
 * after storing it in NetworkCaptureManager so it is bound to the correct thread.
 */
public final class NetworkServiceFactory {

    private static final Logger LOG = LogManager.getLogger(NetworkServiceFactory.class);

    private NetworkServiceFactory() {}

    /**
     * Picks the {@link NetworkCapture} implementation matching {@code driver}'s capabilities.
     * Returns a {@link NoOpNetworkCapture} when {@code network.capture.enabled} is off, or when
     * {@code driver} supports neither {@link org.openqa.selenium.devtools.HasDevTools} nor
     * {@link HasBiDi}.
     *
     * @param driver the session's WebDriver, already created (not yet started)
     * @return a not-yet-started {@link NetworkCapture} — callers must still call {@link NetworkCapture#start()}
     */
    public static NetworkCapture create(WebDriver driver) {
        if (!ConfigFactory.getNetworkConfig().enableNetworkLogs()) {
            return new NoOpNetworkCapture();
        }
        if (driver instanceof HasDevTools) {
            LOG.debug("[NetworkServiceFactory] Using CDP network capture.");
            ReportLogger.network("[NetworkServiceFactory] Using CDP network capture.");
            return new CdpNetworkCapture(driver);
        }
        if (driver instanceof HasBiDi) {
            LOG.debug("[NetworkServiceFactory] Using BiDi network capture.");
            ReportLogger.network("[NetworkServiceFactory] Using BiDi network capture.");
            return new BiDiNetworkCapture(driver);
        }
        LOG.warn("[NetworkServiceFactory] Driver {} supports neither CDP nor BiDi — network capture disabled.",
                driver.getClass().getSimpleName());
        ReportLogger.networkWarn("[NetworkServiceFactory] Driver {} supports neither CDP nor BiDi — network capture disabled.",
                driver.getClass().getSimpleName());
        return new NoOpNetworkCapture();
    }
}
