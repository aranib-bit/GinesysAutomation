package com.gsl.network;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.Network;
import org.openqa.selenium.bidi.network.Header;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebDriver BiDi-based {@link NetworkCapture} implementation for Firefox (and any other
 * BiDi-capable driver) — one of several {@link NetworkCapture} implementations, selected by
 * {@code NetworkServiceFactory} based on which protocol the driver supports (BiDi here, CDP for
 * {@link CdpNetworkCapture}, or neither for {@link NoOpNetworkCapture}).
 * One instance is created per browser session (via {@link NetworkCaptureManager}'s ThreadLocal).
 *
 * <p>Firefox must have webSocketUrl=true in its options (already set in BrowserOptions.firefox()).
 *
 * <p>Captures: URL, method, status, request headers, response headers.
 * Request/response body is NOT available via the Selenium 4.x BiDi Network API —
 * ResponseData.getContent() returns only the byte size, not the body text.
 * Body capture for BiDi requires a future Selenium version that exposes buffer data.
 *
 * <p>URL filtering: if network.capture.url.filter is set, only matching URLs are stored.
 */
public final class BiDiNetworkCapture implements NetworkCapture {

    private static final Logger LOG = LogManager.getLogger(BiDiNetworkCapture.class);

    private final WebDriver              driver;
    private final List<NetworkLogRecord> logs = new CopyOnWriteArrayList<>();
    private Network                      network;

    /** Wraps the given driver; capture does not begin until {@link #start()} is called. */
    public BiDiNetworkCapture(WebDriver driver) {
        this.driver = driver;
    }

    /** Registers the BiDi {@code Network} module's response/error listeners on {@link #driver}. */
    @Override
    public void start() {
        try {
            network = new Network(driver);

            // ── Capture completed responses ───────────────────────────────────
            network.onResponseCompleted(params -> {
                String url    = params.getRequest().getUrl();
                if (!matchesFilter(url)) return;

                String              method          = params.getRequest().getMethod();
                int                 status          = params.getResponseData().getStatus();
                Map<String, String> requestHeaders  = headersFromBiDi(params.getRequest().getHeaders());
                Map<String, String> responseHeaders = headersFromBiDi(params.getResponseData().getHeaders());

                if (!ConfigFactory.getNetworkConfig().networkCaptureFailedOnly() || status >= 400) {
                    logs.add(new NetworkLogRecord(
                            url, method, status,
                            requestHeaders, responseHeaders,
                            null, null,                          // body unavailable in Selenium 4.x BiDi
                            System.currentTimeMillis(), 0L,
                            status >= 400 ? NetworkLogType.FAILED : NetworkLogType.RESPONSE));
                }
            });

            // ── Capture failed requests ───────────────────────────────────────
            network.onFetchError(params -> {
                String url = params.getRequest().getUrl();
                if (!matchesFilter(url)) return;

                String              method         = params.getRequest().getMethod();
                Map<String, String> requestHeaders = headersFromBiDi(params.getRequest().getHeaders());

                logs.add(new NetworkLogRecord(
                        url, method, 0,
                        requestHeaders, Map.of(),
                        null, null,
                        System.currentTimeMillis(), 0L,
                        NetworkLogType.FAILED));
            });

            LOG.debug("[BiDiNetworkCapture] BiDi network capture started.");
            ReportLogger.network("[BiDiNetworkCapture] BiDi network capture started.");
        } catch (Exception ex) {
            LOG.warn("[BiDiNetworkCapture] Failed to start: {}", ex.getMessage());
            ReportLogger.networkError("[BiDiNetworkCapture] Failed to start: {}", ex.getMessage());
        }
    }

    /** Closes the BiDi {@code Network} module, if it was successfully created by {@link #start()}. */
    @Override
    public void stop() {
        try {
            if (network != null) network.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void clear() { logs.clear(); }

    @Override
    public List<NetworkLogRecord> getLogs() { return List.copyOf(logs); }

    @Override
    public String protocolName() { return "BiDi"; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true when no {@code network.capture.url.filter} is configured, or when {@code url} contains at least one configured pattern. */
    private static boolean matchesFilter(String url) {
        String raw = ConfigFactory.getNetworkConfig().networkCaptureUrlFilter();
        if (raw == null || raw.isBlank()) return true;
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(url::contains);
    }

    /** Flattens BiDi {@link Header} entries (name + nested value) into a plain name-to-value map. */
    private static Map<String, String> headersFromBiDi(List<Header> headers) {
        if (headers == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach(h -> out.put(h.getName(), h.getValue().getValue()));
        return Collections.unmodifiableMap(out);
    }
}
