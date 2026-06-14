package com.gsl.network;

import com.gsl.browser.AdvancedJsLogger;
import com.gsl.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v147.network.Network;
import org.openqa.selenium.devtools.v147.network.model.RequestId;
import org.openqa.selenium.devtools.v147.page.Page;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CDP-based network capture for Chrome and Edge.
 * One instance is created per browser session (via NetworkCaptureManager ThreadLocal).
 * Uses a pending-request map keyed by CDP requestId to correlate request headers/body
 * with the corresponding response event and calculate duration.
 *
 * URL filtering: if network.capture.url.filter is set, only URLs containing at least
 * one of the comma-separated patterns are stored. Unmatched requests are discarded.
 */
public final class CdpNetworkCapture implements NetworkCapture {

    private static final Logger LOG = LogManager.getLogger(CdpNetworkCapture.class);

    private final DevTools devTools;
    private final List<NetworkLogRecord>     logs    = new CopyOnWriteArrayList<>();
    private final Map<String, PendingEntry>  pending = new ConcurrentHashMap<>();
    private volatile boolean                 started = false;

    private static final class PendingEntry {
        String              url;
        String              method;
        Map<String, String> requestHeaders;
        String              requestBody;
        long                startTime;
    }

    public CdpNetworkCapture(WebDriver driver) {
        this.devTools = ((HasDevTools) driver).getDevTools();
    }

    @Override
    public void start() {
        try {
            devTools.createSession();
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
        } catch (Exception ex) {
            LOG.warn("[CdpNetworkCapture] Failed to start: {}", ex.getMessage());
        }
    }

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

    @SuppressWarnings("unchecked")
    private static Map<String, String> headersFromMap(Map<String, Object> raw) {
        if (raw == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return Collections.unmodifiableMap(out);
    }
}
