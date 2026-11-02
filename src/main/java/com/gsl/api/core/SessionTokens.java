package com.gsl.api.core;

import com.gsl.driver.DriverManager;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * WEBAPI tokens captured from a browser session's own {@code /token} login response (see
 * {@code com.gsl.network.SessionTokenCapture}), keyed by the browser. A data backfill running inside a UI test reuses
 * them instead of logging a second WEBAPI user in - which costs a pooled user and, when that user is logged in
 * elsewhere, a code-012 "KillUser" round trip - and acts as the SAME user the test (and its site/OU data resolution)
 * runs as. Held in memory only; never logged or written to a report.
 */
public final class SessionTokens {

    /** The token pair a login returned, plus the user id read from the JWT. */
    public record Captured(String authorizationToken, String ginesysAuthToken, String userId) {
        @Override public String toString() { return "Captured[userId=" + userId + ", tokens=***]"; }
    }

    private static final Map<WebDriver, Captured> BY_DRIVER = Collections.synchronizedMap(new WeakHashMap<>());

    private SessionTokens() {}

    /** Remembers (replacing any earlier pair - a re-login or refresh produces a new one) the tokens of {@code driver}'s session. */
    public static void put(WebDriver driver, Captured tokens) {
        if (driver != null && tokens != null) BY_DRIVER.put(driver, tokens);
    }

    /** Forgets the tokens of {@code driver}'s session (its login ended). */
    public static void clear(WebDriver driver) {
        if (driver != null) BY_DRIVER.remove(driver);
    }

    /** The tokens of the browser bound to the calling thread, or {@code null} when there is no browser or it has captured none. */
    public static Captured forCurrentThread() {
        try {
            return BY_DRIVER.get(DriverManager.getDriver());
        } catch (RuntimeException noDriverBound) {
            return null;
        }
    }
}
