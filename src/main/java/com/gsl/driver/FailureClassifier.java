package com.gsl.driver;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;

/**
 * Classifies a test failure so {@link RecoveryManager} knows whether/how to react.
 *
 * The default, when uncertain, is {@link FailureCategory#TEST_FAILURE} — i.e. do nothing to the
 * session/browser. Only an affirmatively-detected session/browser/environment symptom escalates.
 * This is deliberate: a plain business-rule or DB-validation assertion (the overwhelmingly common
 * case) must NEVER cause the browser to be closed or the user re-logged-in — that would silently
 * corrupt any subsequent test relying on the shared session, for zero benefit (retrying/relogging
 * in does not make "Outstanding Amount expected 1250 actual NULL" pass).
 */
public final class FailureClassifier {

    public enum FailureCategory {
        /** Assertion / DB-validation / business-rule failure. No session/browser action — ever. */
        TEST_FAILURE,
        /** Menu not clickable, timeout mid-navigation, loader stuck — session still alive. */
        NAVIGATION_FAILURE,
        /** Server-side session expired / login page showing where an app page was expected. */
        SESSION_FAILURE,
        /** Driver crashed, window/session closed. */
        BROWSER_FAILURE,
        /** DB/API down, 500 Server Error page — retry the operation, not the session. */
        ENVIRONMENT_FAILURE
    }

    private FailureClassifier() {}

    /**
     * Classifies {@code t} into a {@link FailureCategory}, checked in priority order: assertion
     * failures always win (never touch the session); then affirmative browser/session-death
     * signals; then message-text environment hints; then known Selenium interaction exception
     * types (including ones buried a few levels down the cause chain); everything else defaults
     * to {@link FailureCategory#TEST_FAILURE}. A {@code null} throwable also classifies as
     * {@code TEST_FAILURE}.
     */
    public static FailureCategory classify(Throwable t) {
        if (t == null) return FailureCategory.TEST_FAILURE;

        // Plain assertion failures (Assert.*, SoftAssert.assertAll) — always a test/business failure.
        if (t instanceof AssertionError) {
            return FailureCategory.TEST_FAILURE;
        }

        // Affirmative browser-death signals take priority over anything else.
        if (t instanceof NoSuchSessionException || !HealthChecker.isBrowserAlive()) {
            return FailureCategory.BROWSER_FAILURE;
        }

        // Session died server-side (login page showing where an app page was expected).
        if (!HealthChecker.isSessionAlive()) {
            return FailureCategory.SESSION_FAILURE;
        }

        String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
        if (msg.contains("server error") || msg.contains("connection refused")
                || msg.contains("database") && msg.contains("down")) {
            return FailureCategory.ENVIRONMENT_FAILURE;
        }

        // Session and browser are confirmed alive at this point — a locator/timeout exception here
        // is a navigation/rendering hiccup, not a dead session, so Levels 1-3 (no re-login) apply.
        if (t instanceof TimeoutException || t instanceof NoSuchElementException
                || t instanceof WebDriverException) {
            return FailureCategory.NAVIGATION_FAILURE;
        }

        // Fallback: walk the FULL cause chain looking for a UI-interaction failure that got
        // wrapped along the way (e.g. StepExecutor/LoggingAspect rethrow a step-name-annotated
        // wrapper, or a page object catches-and-rethrows as a different type). Without this, an
        // ElementNotInteractableException/timeout buried a level or two down silently falls
        // through to TEST_FAILURE below and the broken session gets handed to the next test
        // instead of being closed — confirmed via a live run where a "Select Document" click
        // failed with "element not interactable" but the session was still returned to the pool.
        // Bounded to 6 levels — a runaway-loop guard, not a tuning knob (cause chains this deep
        // don't occur in practice).
        Throwable cursor = t;
        for (int depth = 0; cursor != null && depth < 6; depth++, cursor = cursor.getCause()) {
            if (isUiInteractionFailure(cursor)) {
                return FailureCategory.NAVIGATION_FAILURE;
            }
        }

        // Anything else (IllegalStateException from a page object's own business check,
        // RuntimeException wrapping a soft-assert failure, etc.) — default to no session action.
        return FailureCategory.TEST_FAILURE;
    }

    /** True if {@code t} itself (not its cause chain) is a recognized Selenium UI-interaction exception, by type or by message text. */
    private static boolean isUiInteractionFailure(Throwable t) {
        if (t instanceof NoSuchElementException || t instanceof TimeoutException
                || t instanceof StaleElementReferenceException
                || t instanceof ElementClickInterceptedException
                || t instanceof ElementNotInteractableException
                || t instanceof WebDriverException) {
            return true;
        }
        String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
        return msg.contains("not interactable") || msg.contains("no such element")
                || msg.contains("timed out") || msg.contains("timeout")
                || msg.contains("stale element") || msg.contains("click intercepted");
    }
}
