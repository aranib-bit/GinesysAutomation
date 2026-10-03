package com.gsl.reports;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What one browser session actually ran on: browser name/version, the OS the node reported, and the
 * driver version, read from the session capabilities at the moment the session is created. Linux and
 * Windows nodes ship different Chrome/Edge builds, so a report difference between them is only
 * interpretable with these values in the report.
 *
 * <p>Sessions are recorded in a JVM-wide registry ({@link #register}) and summarised at the end of the
 * run ({@link #browserVersions()}). Everything is best-effort: a capability the node does not report is
 * blank, and no method throws.
 */
public final class BrowserFingerprint {

    private static final Map<String, BrowserFingerprint> BY_SESSION = new ConcurrentHashMap<>();

    private final String sessionId;
    private final String browserName;
    private final String browserVersion;
    private final String platform;
    private final String driverVersion;

    private BrowserFingerprint(String sessionId, String browserName, String browserVersion,
                               String platform, String driverVersion) {
        this.sessionId = sessionId;
        this.browserName = browserName;
        this.browserVersion = browserVersion;
        this.platform = platform;
        this.driverVersion = driverVersion;
    }

    public String sessionId()      { return sessionId; }
    public String browserName()    { return browserName; }
    public String browserVersion() { return browserVersion; }
    public String platform()       { return platform; }
    public String driverVersion()  { return driverVersion; }

    /** Leading numeric component of {@link #browserVersion()} (e.g. {@code 141} for {@code 141.0.7390.76}); blank when unknown. */
    public String majorVersion() {
        int dot = browserVersion.indexOf('.');
        return dot < 0 ? browserVersion : browserVersion.substring(0, dot);
    }

    /** {@code chrome 141.0.7390.76 / windows (driver 141.0.7390.78)}; the driver part is omitted when unknown. */
    public String describe() {
        String d = browserName + " " + browserVersion + " / " + platform;
        return driverVersion.isBlank() ? d : d + " (driver " + driverVersion + ")";
    }

    /**
     * Reads {@code driver}'s capabilities and stores the result under {@code sessionId}. Never throws;
     * a driver without capabilities is ignored.
     */
    public static void register(String sessionId, WebDriver driver) {
        try {
            WebDriver unwrapped = driver;
            while (unwrapped instanceof WrapsDriver w) {
                unwrapped = w.getWrappedDriver();
            }
            if (!(unwrapped instanceof HasCapabilities hc)) {
                return;
            }
            Capabilities c = hc.getCapabilities();
            BrowserFingerprint f = new BrowserFingerprint(
                    nz(sessionId),
                    nz(c.getBrowserName()),
                    nz(c.getBrowserVersion()),
                    nz(String.valueOf(c.getPlatformName())),
                    driverVersion(c));
            BY_SESSION.put(f.sessionId.isBlank() ? "session-" + BY_SESSION.size() : f.sessionId, f);
        } catch (Throwable ignored) {
            // best-effort: never affect session creation
        }
    }

    /** Distinct {@code browser version / platform} combinations seen this run, sorted; empty before any session exists. */
    public static List<String> browserVersions() {
        Set<String> out = new LinkedHashSet<>();
        all().stream().map(BrowserFingerprint::describe).sorted().forEach(out::add);
        return new ArrayList<>(out);
    }

    /** Distinct browser majors seen this run per browser, e.g. {@code chrome=[141], edge=[141, 140]}; used for the drift warning. */
    public static Map<String, Set<String>> majorsByBrowser() {
        Map<String, Set<String>> m = new java.util.TreeMap<>();
        for (BrowserFingerprint f : all()) {
            if (!f.browserName.isBlank() && !f.majorVersion().isBlank()) {
                m.computeIfAbsent(f.browserName, k -> new java.util.TreeSet<>()).add(f.majorVersion());
            }
        }
        return m;
    }

    /** Every recorded session, ordered by session id. */
    public static List<BrowserFingerprint> all() {
        List<BrowserFingerprint> l = new ArrayList<>(BY_SESSION.values());
        l.sort(Comparator.comparing(BrowserFingerprint::sessionId));
        return l;
    }

    /** The fingerprint recorded for {@code sessionId}, or {@code null}. */
    public static BrowserFingerprint forSession(String sessionId) {
        return sessionId == null ? null : BY_SESSION.get(sessionId);
    }

    /** Clears the registry (tests only). */
    static void clear() {
        BY_SESSION.clear();
    }

    private static String driverVersion(Capabilities c) {
        String v = nested(c, "chrome", "chromedriverVersion");
        if (v.isBlank()) v = nested(c, "msedge", "msedgedriverVersion");
        if (v.isBlank()) v = nz(c.getCapability("moz:geckodriverVersion"));
        // chromedriver reports "141.0.7390.78 (hash-refs/branch-heads/...)"; keep only the version number
        int sp = v.indexOf(' ');
        return sp < 0 ? v : v.substring(0, sp);
    }

    private static String nested(Capabilities c, String key, String inner) {
        Object o = c.getCapability(key);
        return o instanceof Map<?, ?> m ? nz(m.get(inner)) : "";
    }

    private static String nz(Object o) {
        if (o == null) return "";
        String s = o.toString();
        return s.equalsIgnoreCase("null") ? "" : s;
    }
}
