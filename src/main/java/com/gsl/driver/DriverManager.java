package com.gsl.driver;

import com.gsl.enums.PlatformType;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.events.EventFiringDecorator;

import java.util.EnumMap;

/**
 * Thread-safe driver and credential manager for parallel test execution.
 *
 * Driver context:
 *   Each thread maintains one WebDriver per PlatformType (WEB, ANDROID, IOS).
 *   The active context defaults to WEB and can be switched via switchContext(PlatformType).
 *   All existing getDriver()/setDriver()/unloadDriver() calls operate on the active context,
 *   so existing tests require no changes.
 *
 * Credential context:
 *   Two ThreadLocals hold the current UI user and API user assigned per thread.
 */
public final class DriverManager {

    // ── Driver context ────────────────────────────────────────────────────────

    private static final EnumMap<PlatformType, ThreadLocal<WebDriver>> CONTEXT_DRIVERS =
            buildContextMap();

    private static final ThreadLocal<PlatformType> activeContext =
            ThreadLocal.withInitial(() -> PlatformType.WEB);

    private static EnumMap<PlatformType, ThreadLocal<WebDriver>> buildContextMap() {
        EnumMap<PlatformType, ThreadLocal<WebDriver>> map = new EnumMap<>(PlatformType.class);
        for (PlatformType pt : PlatformType.values()) {
            map.put(pt, new ThreadLocal<>());
        }
        return map;
    }

    // ── Credential context ────────────────────────────────────────────────────

    private static final ThreadLocal<UserCredential> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<UserCredential> apiUser     = new ThreadLocal<>();

    private DriverManager() {}

    // ── Driver lifecycle ──────────────────────────────────────────────────────

    /** Returns the WebDriver for the current thread's active context (default: WEB). */
    public static WebDriver getDriver() {
        return CONTEXT_DRIVERS.get(activeContext.get()).get();
    }

    /**
     * Stores a WebDriver in the current thread's active context, wrapped with
     * a {@link HeartbeatListener} so every browser interaction feeds
     * {@code UserPool.heartbeat}. The wrap is the chokepoint for starvation
     * detection — all provider factories (Chrome/Firefox/Edge/Remote/Grid/etc.)
     * funnel through here, so one hook covers them all.
     */
    public static void setDriver(WebDriver driver) {
        WebDriver instrumented = (driver == null)
                ? null
                : new EventFiringDecorator<>(new HeartbeatListener()).decorate(driver);
        CONTEXT_DRIVERS.get(activeContext.get()).set(instrumented);
        if (driver != null) {
            ReportLogger.framework("WebDriver initialized — context={}", activeContext.get());
        }
    }

    /** Removes the WebDriver for the current thread's active context. Prevents memory leaks. */
    public static void unloadDriver() {
        ReportLogger.framework("WebDriver unloaded — context={}", activeContext.get());
        CONTEXT_DRIVERS.get(activeContext.get()).remove();
    }

    /**
     * Removes WebDrivers for ALL contexts on the current thread and resets the active context.
     * Call in tearDown when a test uses multiple platforms (web + mobile in the same test).
     */
    public static void unloadAllContexts() {
        CONTEXT_DRIVERS.values().forEach(ThreadLocal::remove);
        activeContext.remove();
    }

    // ── Context switching ─────────────────────────────────────────────────────

    /**
     * Switches the active driver context for the current thread.
     * After this call, getDriver()/setDriver()/unloadDriver() operate on the given platform.
     *
     * Example:
     *   DriverManager.switchContext(PlatformType.ANDROID);
     *   DriverManager.setDriver(AndroidManager.getDriver(mobileData));
     *   // ... mobile actions ...
     *   DriverManager.switchContext(PlatformType.WEB);  // back to browser
     */
    public static void switchContext(PlatformType platform) {
        activeContext.set(platform);
    }

    /** Returns the active PlatformType for the current thread. */
    public static PlatformType getActiveContext() {
        return activeContext.get();
    }

    /** Returns the WebDriver bound to a specific context without changing the active context. */
    public static WebDriver getDriverForContext(PlatformType platform) {
        return CONTEXT_DRIVERS.get(platform).get();
    }

    // ── Credential management ─────────────────────────────────────────────────

    public static UserCredential getCurrentUser()            { return currentUser.get(); }
    public static void setCurrentUser(UserCredential cred)   { currentUser.set(cred); }
    public static void unloadCurrentUser()                   { currentUser.remove(); }

    public static UserCredential getApiUser()                { return apiUser.get(); }
    public static void setApiUser(UserCredential cred)       { apiUser.set(cred); }
    public static void unloadApiUser()                       { apiUser.remove(); }
}
