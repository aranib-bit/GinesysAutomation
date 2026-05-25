package com.gsl.network;

import com.gsl.config.FrameworkConfig;
import com.gsl.model.network.CallFrameEntry;
import com.gsl.model.network.NetworkTimingSnapshot;
import com.gsl.model.network.RequestCookieEntry;
import com.gsl.model.network.ServerTimingEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v147.network.Network;
import org.openqa.selenium.devtools.v147.network.model.Cookie;
import org.openqa.selenium.devtools.v147.network.model.Headers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Attaches CDP listeners to an already-open DevTools session (managed by CdpNetworkCapture)
 * to capture per-request timing breakdown, request cookies, and JS initiator call stack.
 *
 * Never calls createSession() — relies on CdpNetworkCapture to own the session lifecycle.
 */
public final class NetworkTimingCapture {

    private static final Logger LOG = LogManager.getLogger(NetworkTimingCapture.class);

    private NetworkTimingCapture() {}

    private static final ThreadLocal<Deque<NetworkTimingSnapshot>> snapshots =
            ThreadLocal.withInitial(ConcurrentLinkedDeque::new);

    private static final ThreadLocal<Map<String, RequestContext>> pending =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    private static final ThreadLocal<Map<String, Cookie>> allCookiesCache =
            ThreadLocal.withInitial(HashMap::new);

    private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    // ── Mutable accumulator (one per in-flight request) ──────────────────────

    static final class RequestContext {
        String requestId;
        String url;
        double wallTimeSec;
        double responseMonoTimeSec;
        int    status;
        String mimeType = "";
        final Map<String, Double>     timings       = new HashMap<>();
        final List<ServerTimingEntry>  serverTiming  = new ArrayList<>();
        final List<RequestCookieEntry> requestCookies = new ArrayList<>();
        final List<CallFrameEntry>     callFrames    = new ArrayList<>();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attaches the 3-event listeners to an existing DevTools session.
     * Called by CdpNetworkCapture.start() after its own session is established.
     */
    public static void attachListeners(DevTools devTools, FrameworkConfig config) {
        if (!config.performanceTimingEnabled() && !config.enableNetworkLogs()) {
            return;
        }
        try {
            snapshots.get().clear();
            pending.get().clear();
            allCookiesCache.get().clear();

            // Fetch full cookie jar once for metadata enrichment
            try {
                List<Cookie> cookies = devTools.send(Network.getAllCookies());
                Map<String, Cookie> cookieMap = allCookiesCache.get();
                cookies.forEach(c -> cookieMap.put(c.getName(), c));
            } catch (Exception ignored) {}

            registerListeners(devTools, config);
            LOG.debug("[NetworkTimingCapture] Timing listeners attached.");
        } catch (Exception ex) {
            LOG.warn("[NetworkTimingCapture] Failed to attach listeners: {}", ex.getMessage());
        }
    }

    public static List<NetworkTimingSnapshot> stopAndCollect() {
        // Flush pending contexts whose responseReceived fired but loadingFinished did not
        pending.get().values().stream()
                .filter(ctx -> ctx.status > 0)
                .forEach(ctx -> snapshots.get().addLast(buildSnapshot(ctx, 0.0, 0L)));
        pending.get().clear();
        allCookiesCache.get().clear();

        List<NetworkTimingSnapshot> result = new ArrayList<>(snapshots.get());
        snapshots.get().clear();
        return result;
    }

    // ── Listener registration ─────────────────────────────────────────────────

    private static void registerListeners(DevTools devTools, FrameworkConfig config) {
        String urlFilter = config.networkCaptureUrlFilter();

        // ── Listener 1: requestWillBeSent ─────────────────────────────────────
        devTools.addListener(Network.requestWillBeSent(), event -> {
            String url = event.getRequest().getUrl();
            if (urlFilter != null && !urlFilter.isBlank() && !url.contains(urlFilter)) {
                return;
            }

            RequestContext ctx = new RequestContext();
            ctx.requestId   = event.getRequestId().toString();
            ctx.url         = url;
            ctx.wallTimeSec = event.getWallTime().toJson().doubleValue();

            if (config.networkCaptureHeaders()) {
                extractCookieHeader(event.getRequest(),
                        ctx.requestCookies, allCookiesCache.get());
            }

            // Call stack from initiator
            event.getInitiator().getStack().ifPresent(stack ->
                stack.getCallFrames().stream().limit(15).forEach(cf -> {
                    String fn = cf.getFunctionName();
                    ctx.callFrames.add(CallFrameEntry.builder()
                            .functionName(fn == null || fn.isBlank() ? "(anonymous)" : fn)
                            .scriptUrl(filenameOf(cf.getUrl()))
                            .lineNumber(cf.getLineNumber())
                            .columnNumber(cf.getColumnNumber())
                            .build());
                })
            );

            pending.get().put(ctx.requestId, ctx);
        });

        // ── Listener 2: responseReceived ──────────────────────────────────────
        devTools.addListener(Network.responseReceived(), event -> {
            RequestContext ctx = pending.get().get(event.getRequestId().toString());
            if (ctx == null) return;

            ctx.responseMonoTimeSec = event.getTimestamp().toJson().doubleValue();
            ctx.status   = event.getResponse().getStatus();
            ctx.mimeType = event.getResponse().getMimeType();

            event.getResponse().getTiming().ifPresent(timing -> {
                ctx.timings.put("requestTime",         timing.getRequestTime().doubleValue());
                ctx.timings.put("proxyStart",          timing.getProxyStart().doubleValue());
                ctx.timings.put("proxyEnd",            timing.getProxyEnd().doubleValue());
                ctx.timings.put("dnsStart",            timing.getDnsStart().doubleValue());
                ctx.timings.put("dnsEnd",              timing.getDnsEnd().doubleValue());
                ctx.timings.put("connectStart",        timing.getConnectStart().doubleValue());
                ctx.timings.put("sslStart",            timing.getSslStart().doubleValue());
                ctx.timings.put("sslEnd",              timing.getSslEnd().doubleValue());
                ctx.timings.put("connectEnd",          timing.getConnectEnd().doubleValue());
                ctx.timings.put("sendStart",           timing.getSendStart().doubleValue());
                ctx.timings.put("sendEnd",             timing.getSendEnd().doubleValue());
                ctx.timings.put("receiveHeadersStart", timing.getReceiveHeadersStart().doubleValue());
                ctx.timings.put("receiveHeadersEnd",   timing.getReceiveHeadersEnd().doubleValue());
            });

            if (config.networkCaptureHeaders()) {
                extractServerTiming(event.getResponse().getHeaders(), ctx.serverTiming);
            }
        });

        // ── Listener 3: loadingFinished ───────────────────────────────────────
        devTools.addListener(Network.loadingFinished(), event -> {
            RequestContext ctx = pending.get().remove(event.getRequestId().toString());
            if (ctx == null) return;

            double loadedMono = event.getTimestamp().toJson().doubleValue();
            long   bytes      = event.getEncodedDataLength().longValue();

            snapshots.get().addLast(buildSnapshot(ctx, loadedMono, bytes));
        });
    }

    // ── Snapshot builder ──────────────────────────────────────────────────────

    private static NetworkTimingSnapshot buildSnapshot(RequestContext ctx,
                                                        double loadedMono, long bytes) {
        double contentDl = (ctx.responseMonoTimeSec > 0 && loadedMono > 0)
                ? Math.max(0, (loadedMono - ctx.responseMonoTimeSec) * 1000.0) : 0.0;

        double reqTimeSec = ctx.timings.getOrDefault("requestTime", 0.0);
        double queueing   = (reqTimeSec > 0 && ctx.wallTimeSec > 0)
                ? Math.max(0, (reqTimeSec - ctx.wallTimeSec) * 1000.0) : 0.0;

        double sendStart  = ctx.timings.getOrDefault("sendStart",  -1.0);
        double connectEnd = ctx.timings.getOrDefault("connectEnd", -1.0);
        double stalled    = sendStart >= 0
                ? Math.max(0, sendStart - Math.max(connectEnd, 0)) : 0.0;

        return NetworkTimingSnapshot.builder()
                .url(ctx.url)
                .statusCode(ctx.status)
                .mimeType(ctx.mimeType)
                .timings(Collections.unmodifiableMap(new HashMap<>(ctx.timings)))
                .serverTiming(Collections.unmodifiableList(new ArrayList<>(ctx.serverTiming)))
                .requestCookies(Collections.unmodifiableList(new ArrayList<>(ctx.requestCookies)))
                .callStack(Collections.unmodifiableList(new ArrayList<>(ctx.callFrames)))
                .queueingMs(queueing)
                .stalledMs(stalled)
                .contentDownloadMs(contentDl)
                .encodedDataBytes(bytes)
                .capturedAtMs(System.currentTimeMillis())
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void extractCookieHeader(
            org.openqa.selenium.devtools.v147.network.model.Request request,
            List<RequestCookieEntry> out, Map<String, Cookie> cache) {
        if (request == null) return;
        request.getHeaders().toJson().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("cookie"))
                .map(e -> e.getValue().toString())
                .findFirst()
                .ifPresent(header -> parseCookieHeader(header, out, cache));
    }

    private static void parseCookieHeader(String header, List<RequestCookieEntry> out,
                                           Map<String, Cookie> cache) {
        for (String pair : header.split(";")) {
            String[] kv  = pair.strip().split("=", 2);
            String name  = kv[0].strip();
            String value = kv.length > 1 ? kv[1].strip() : "";
            Cookie full  = cache.get(name);

            out.add(RequestCookieEntry.builder()
                    .name(name)
                    .value(value)
                    .domain(full != null ? full.getDomain() : "")
                    .path(full != null ? full.getPath() : "")
                    .expires(full != null ? formatExpiry(full) : "Session")
                    .size(full != null ? full.getSize() : (name + "=" + value).length())
                    .httpOnly(full != null && full.getHttpOnly())
                    .secure(full != null && full.getSecure())
                    .sameSite(full != null
                            ? full.getSameSite().map(Enum::name).orElse("")
                            : "")
                    .priority(full != null ? full.getPriority().toString() : "")
                    .build());
        }
    }

    private static String formatExpiry(Cookie c) {
        if (Boolean.TRUE.equals(c.getSession())) return "Session";
        double epochSec = c.getExpires().doubleValue();
        if (epochSec <= 0) return "Session";
        try {
            return EXPIRY_FMT.format(Instant.ofEpochSecond((long) epochSec));
        } catch (Exception e) {
            return String.valueOf((long) epochSec);
        }
    }

    private static void extractServerTiming(Headers headers, List<ServerTimingEntry> out) {
        headers.toJson().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("server-timing"))
                .map(e -> e.getValue().toString())
                .findFirst()
                .ifPresent(value -> out.addAll(parseServerTiming(value)));
    }

    static List<ServerTimingEntry> parseServerTiming(String headerValue) {
        List<ServerTimingEntry> entries = new ArrayList<>();
        if (headerValue == null || headerValue.isBlank()) return entries;
        for (String metric : headerValue.split(",")) {
            metric = metric.strip();
            if (metric.isBlank()) continue;
            String[] parts = metric.split(";");
            String metricName  = parts[0].strip();
            String description = null;
            double duration    = 0.0;
            for (int i = 1; i < parts.length; i++) {
                String param = parts[i].strip();
                if (param.toLowerCase().startsWith("dur=")) {
                    try { duration = Double.parseDouble(param.substring(4).strip()); }
                    catch (NumberFormatException ignored) {}
                } else if (param.toLowerCase().startsWith("desc=")) {
                    description = param.substring(5).strip().replaceAll("^\"|\"$", "");
                }
            }
            entries.add(ServerTimingEntry.builder()
                    .metricName(metricName).description(description).duration(duration).build());
        }
        return entries;
    }

    private static String filenameOf(String url) {
        if (url == null || url.isBlank()) return "(unknown)";
        int colon = url.lastIndexOf(':');
        if (colon > 0 && url.indexOf('/', colon) < 0) url = url.substring(0, colon);
        int slash = url.lastIndexOf('/');
        String file = slash >= 0 ? url.substring(slash + 1) : url;
        int q = file.indexOf('?');
        return q >= 0 ? file.substring(0, q) : file;
    }
}
