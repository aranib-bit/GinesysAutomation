package com.gsl.network;

import com.gsl.browser.AdvancedJsLogger;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.network.Network;
import org.openqa.selenium.devtools.v153.network.model.RequestId;
import org.openqa.selenium.devtools.v153.page.Page;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CDP-based {@link NetworkCapture} implementation for Chrome and Edge — one of several
 * {@link NetworkCapture} implementations, selected by {@code NetworkServiceFactory} based on
 * which protocol the driver supports (CDP here, BiDi for {@link BiDiNetworkCapture}, or neither
 * for {@link NoOpNetworkCapture}).
 * One instance is created per browser session (via {@link NetworkCaptureManager}'s ThreadLocal).
 * Uses a pending-request map keyed by CDP requestId to correlate request headers/body
 * with the corresponding response event and calculate duration.
 *
 * <p>URL filtering: if network.capture.url.filter is set, only URLs containing at least
 * one of the comma-separated patterns are stored. Unmatched requests are discarded.
 */
public final class CdpNetworkCapture implements NetworkCapture {

    private static final Logger LOG = LogManager.getLogger(CdpNetworkCapture.class);

    private final DevTools devTools;
    private final List<NetworkLogRecord>     logs    = new CopyOnWriteArrayList<>();
    private final Map<String, PendingEntry>  pending = new ConcurrentHashMap<>();
    private volatile boolean                 started = false;

    /** In-flight request accumulator, keyed by CDP requestId, until its response/failure event arrives. */
    private static final class PendingEntry {
        String              url;
        String              method;
        Map<String, String> requestHeaders;
        String              requestBody;
        long                startTime;
    }

    /** Wraps the given driver's existing {@link DevTools} session; capture does not begin until {@link #start()} is called. */
    public CdpNetworkCapture(WebDriver driver) {
        this.devTools = ((HasDevTools) driver).getDevTools();
    }

    /** Enables the CDP {@code Network} domain and registers request/response/failure listeners. */
    @Override
    public void start() {
        try {
            // createSessionIfThereIsNotOne() rather than createSession(): ViewportEnforcer may
            // already have attached a CDP session on this driver (to apply the device-metrics
            // override before navigation) — reattaching here would discard that session instead
            // of reusing it. Behaviour is identical to createSession() when this is the first
            // CDP use on the driver, since cdpSession is null at that point either way.
            devTools.createSessionIfThereIsNotOne();
            devTools.send(Network.enable(
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty()));

            // ── Persist JS logger shim across page navigations (login redirects, etc.) ──
            if (ConfigFactory.getNetworkConfig().browserLogsEnabled()) {
                devTools.send(Page.addScriptToEvaluateOnNewDocument(
                        AdvancedJsLogger.getScript(),
                        Optional.empty(), Optional.empty(), Optional.empty()));
            }

            // ── Capture outgoing request ──────────────────────────────────────
            devTools.addListener(Network.requestWillBeSent(), req -> {
                PendingEntry e = new PendingEntry();
                e.url           = req.getRequest().getUrl();
                e.method        = req.getRequest().getMethod();
                e.startTime     = System.currentTimeMillis();
                e.requestHeaders = headersFromMap(req.getRequest().getHeaders().toJson());
                if (ConfigFactory.getNetworkConfig().networkCaptureBody()) {
                    e.requestBody = req.getRequest().getPostData().orElse(null);
                }
                pending.put(req.getRequestId().toString(), e);
            });

            // ── Capture response ──────────────────────────────────────────────
            devTools.addListener(Network.responseReceived(), res -> {
                String     reqId = res.getRequestId().toString();
                PendingEntry e   = pending.remove(reqId);
                if (e == null || !matchesFilter(e.url)) return;

                long               duration        = System.currentTimeMillis() - e.startTime;
                Map<String, String> responseHeaders = headersFromMap(res.getResponse().getHeaders().toJson());
                int                status          = res.getResponse().getStatus();
                String             responseBody    = null;

                if (ConfigFactory.getNetworkConfig().networkCaptureBody()) {
                    try {
                        Network.GetResponseBodyResponse body =
                                devTools.send(Network.getResponseBody(new RequestId(reqId)));
                        responseBody = body.getBase64Encoded()
                                ? "[base64-encoded binary body]"
                                : body.getBody();
                    } catch (Exception ignored) {
                        // Binary, streaming, or already-evicted responses — skip body
                    }
                }

                if (!ConfigFactory.getNetworkConfig().networkCaptureFailedOnly() || status >= 400) {
                    logs.add(new NetworkLogRecord(
                            e.url, e.method, status,
                            e.requestHeaders, responseHeaders,
                            e.requestBody, responseBody,
                            e.startTime, duration,
                            status >= 400 ? NetworkLogType.FAILED : NetworkLogType.RESPONSE));
                }
            });

            // ── Capture failed requests ───────────────────────────────────────
            devTools.addListener(Network.loadingFailed(), fail -> {
                String     reqId = fail.getRequestId().toString();
                PendingEntry e   = pending.remove(reqId);
                if (e == null || !matchesFilter(e.url)) return;

                long duration = System.currentTimeMillis() - e.startTime;
                logs.add(new NetworkLogRecord(
                        e.url, e.method, 0,
                        e.requestHeaders, Map.of(),
                        e.requestBody, null,
                        e.startTime, duration,
                        NetworkLogType.FAILED));
            });

            // Attach timing/cookies/call-stack listeners to the same session
            NetworkTimingCapture.attachListeners(devTools);

            started = true;
            LOG.debug("[CdpNetworkCapture] CDP network capture started.");
            ReportLogger.network("[CdpNetworkCapture] CDP network capture started.");
        } catch (Exception ex) {
            LOG.warn("[CdpNetworkCapture] Failed to start: {}", ex.getMessage());
            ReportLogger.networkError("[CdpNetworkCapture] Failed to start: {}", ex.getMessage());
        }
    }

    /** Drops any in-flight (not yet responded/failed) request entries; already-captured logs are kept. */
    @Override
    public void stop() {
        pending.clear();
    }

    @Override
    public void clear() {
        logs.clear();
        pending.clear();
    }

    @Override
    public List<NetworkLogRecord> getLogs() { return List.copyOf(logs); }

    /** {@code "CDP"} once {@link #start()} has completed successfully, otherwise a diagnostic string noting the failed session. */
    @Override
    public String protocolName() { return started ? "CDP" : "CDP (session failed — check logs)"; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true when no URL filter is configured, or when the URL matches at least one pattern. */
    private static boolean matchesFilter(String url) {
        String raw = ConfigFactory.getNetworkConfig().networkCaptureUrlFilter();
        if (raw == null || raw.isBlank()) return true;
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(url::contains);
    }

    /** Stringifies a raw CDP headers map's values (as returned by {@code Headers.toJson()}) into a plain name-to-value map. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> headersFromMap(Map<String, Object> raw) {
        if (raw == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return Collections.unmodifiableMap(out);
    }
}
