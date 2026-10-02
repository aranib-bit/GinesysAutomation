package com.gsl.diagnostics;

import com.gsl.browser.JsErrorClassification;
import com.gsl.exceptions.LoaderStillInProgressException;
import com.gsl.exceptions.UnexpectedPopupException;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

/**
 * Deterministic root-cause classifier (R-04).
 *
 * <p>Maps a failed test's throwable plus collected evidence ({@link RootCauseInputs}) to a single
 * {@link RootCause} verdict. Rules are <strong>exception-type + message-pattern</strong> based and
 * ordered by the failure frequency actually observed in this application's reports — the first
 * matching rule wins. Pattern matching is case-insensitive on both the throwable message and the
 * last action-trail step.
 *
 * <p>Note: {@code ElementNotInteractableException} is intentionally not a primary rule — it does not
 * occur in the scanned report corpus; it falls through to {@link RootCauseCategory#UNKNOWN}.
 */
public final class RootCauseClassifier {

    private RootCauseClassifier() {}

    public static RootCause classify(RootCauseInputs in) {
        Throwable t = in.throwable();
        if (t == null) {
            return RootCause.of(RootCauseCategory.UNKNOWN, "No throwable on the test result.", "LOW");
        }
        String msg = in.message();

        // 0a–0b — PopupMonitor.diagnoseFailure() already confirmed these live, at the moment of
        // failure, by querying the DOM — not a message-pattern guess like the rules below. Always
        // wins over the heuristics further down.
        if (t instanceof UnexpectedPopupException) {
            return RootCause.of(RootCauseCategory.UNEXPECTED_POPUP,
                    "An unexpected application popup was confirmed present at the moment of failure. "
                    + firstLine(msg, 240), "HIGH");
        }
        if (t instanceof LoaderStillInProgressException) {
            return RootCause.of(RootCauseCategory.EXTJS_MASK_BLOCKING,
                    "An ExtJS loading mask was confirmed still visible at the moment of failure — the "
                    + "application had not finished loading/processing in time. " + firstLine(msg, 240), "HIGH");
        }

        // 0c — App-side business-rejection dialogs that happen to intercept a later click must not
        // be misclassified as a generic loading-mask timing race just because the underlying
        // exception is ElementClickInterceptedException — confirmed live (run 20260925_234705,
        // FIN-GV-ADD-COMBO-04): the intercepting element was the app's own "This cheque no is
        // already in use" error dialog, not a loadmask. Message-pattern check, independent of
        // exception type (same idea as 0a/0b), checked before the generic x-mask rule below so a
        // real app rejection always wins over that shallower match.
        if (contains(msg, "cheque no is already in use")) {
            return RootCause.of(RootCauseCategory.APP_SAVE_REJECTED,
                    "Application rejected the save — the resolved cheque number was already in use "
                    + "by another document. " + firstLine(msg, 240), "HIGH");
        }

        // 1–2 — Click intercepted (very common). x-mask overlay is the dominant cause here.
        if (t instanceof ElementClickInterceptedException) {
            if (contains(msg, "x-mask")) {
                return RootCause.of(RootCauseCategory.EXTJS_MASK_BLOCKING,
                        "An ExtJS x-mask overlay intercepted the click — a loadmask/async operation was "
                        + "still active when the interaction was attempted.", "HIGH");
            }
            return RootCause.of(RootCauseCategory.CLICK_INTERCEPTED,
                    "Another element intercepted the click. " + firstLine(msg, 200), "HIGH");
        }

        // 3 — Grid row / document not found (proportional-scroll fallback exhausted).
        if (t instanceof NoSuchElementException
                && contains(msg, "not found after", "scroll attempts", "checkbox for")) {
            return RootCause.of(RootCauseCategory.GRID_ROW_NOT_FOUND,
                    "Target row/document not found in the grid after scroll/jump fallbacks — the document "
                    + "may be absent or the grid did not render it. " + firstLine(msg, 220), "HIGH");
        }

        // 4–6 — IllegalState: data prerequisite vs. application save rejection.
        if (t instanceof IllegalStateException) {
            if (contains(msg, "no sub-ledger", "v_pending_adj", "ensure open", "pending")) {
                return RootCause.of(RootCauseCategory.TEST_DATA_PREREQUISITE,
                        "Required test data is missing (no eligible pending document/sub-ledger for this site). "
                        + "This is a data-availability problem, not a product defect. " + firstLine(msg, 240), "HIGH");
            }
            if (contains(msg, "cannot save", "document number", "not found in adjusted", "not found in pending")) {
                return RootCause.of(RootCauseCategory.APP_SAVE_REJECTED,
                        "Application rejected the save / business-rule failure. " + firstLine(msg, 240), "HIGH");
            }
            return RootCause.of(RootCauseCategory.APP_SAVE_REJECTED,
                    "Illegal application state during the flow. " + firstLine(msg, 240), "MEDIUM");
        }

        // 7–11 — Timeout variants.
        if (t instanceof TimeoutException) {
            // JS_ERROR_BLOCKED_RENDER only when the JS error actually prevented a render.
            // When the inner cause is NoSuchElementException the element was never found —
            // the JS error is likely background noise and the real cause is a locator mismatch.
            if (in.hasCriticalJsError() && !elementNeverFound(msg)) {
                return jsBlocked(in);
            }
            if (in.hasHttpError()) {
                return RootCause.of(RootCauseCategory.SERVER_ERROR,
                        "Server returned HTTP " + in.firstHttpErrorCode() + " during the test: "
                        + in.firstFailedRequestUrl(), "HIGH");
            }
            if (in.isExtMaskPresent() || contains(msg, "x-mask")) {
                return RootCause.of(RootCauseCategory.EXTJS_MASK_BLOCKING,
                        "An ExtJS x-mask overlay was still present when the wait expired.", "MEDIUM");
            }
            if (contains(msg, "presence of element", "clickable", "visibility of")) {
                return RootCause.of(RootCauseCategory.ELEMENT_NEVER_APPEARED,
                        "Element never reached the expected condition before timeout — slow render or a "
                        + "stale/changed locator. " + firstLine(msg, 240), "HIGH");
            }
            return RootCause.of(RootCauseCategory.UI_TIMEOUT,
                    "Wait timed out. " + firstLine(msg, 240), "MEDIUM");
        }

        // 12 — NoSuchElement (other than grid-row): JS-blocked render or locator mismatch.
        // Only classify as JS_ERROR_BLOCKED_RENDER when there is evidence the element existed
        // before the JS error (e.g. a StaleElement or a prior step found it). A plain
        // NoSuchElementException means the element was never in the DOM — prefer LOCATOR_MISMATCH.
        if (t instanceof NoSuchElementException) {
            if (in.hasCriticalJsError() && !elementNeverFound(msg)) {
                return jsBlocked(in);
            }
            if (!in.hasNetworkErrors()) {
                return RootCause.of(RootCauseCategory.LOCATOR_MISMATCH,
                        "Element not found and no network errors detected — the DOM structure likely changed "
                        + "(ExtJS upgrade or UI change). " + firstLine(msg, 200), "HIGH");
            }
            return RootCause.of(RootCauseCategory.LOCATOR_MISMATCH,
                    "Element not found. " + firstLine(msg, 200), "MEDIUM");
        }

        // 13 — Stale element: DOM rebuilt between find and interact.
        if (t instanceof StaleElementReferenceException) {
            return RootCause.of(RootCauseCategory.DOM_REBUILT,
                    "Element went stale — the page re-rendered between findElement() and interaction "
                    + "(AJAX refresh or ExtJS layout recalculation).", "MEDIUM");
        }

        // 14a — Combined validation failure (UI contract / database / snapshot areas, all of which ran).
        if (t instanceof com.gsl.validation.CombinedValidationError cve) {
            String areas = cve.areas().stream()
                    .map(a -> a.title() + " (" + a.lines().size() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            if (cve.onlyUi()) {
                return RootCause.of(RootCauseCategory.UI_CONTRACT_MISMATCH,
                        "Only the UI contract failed — the flow itself (Save, DB and snapshot validation) "
                        + "completed. Failed: " + areas, "HIGH");
            }
            if (cve.hasKind(com.gsl.validation.CombinedValidationError.Kind.DATABASE)
                    || cve.hasKind(com.gsl.validation.CombinedValidationError.Kind.SNAPSHOT)) {
                return RootCause.of(RootCauseCategory.DB_VALIDATION_FAILURE,
                        "Database state does not match expected after save. Failed areas: " + areas, "HIGH");
            }
            return RootCause.of(RootCauseCategory.ASSERTION_FAILURE, "Failed areas: " + areas, "HIGH");
        }

        // 14–15 — Assertions: DB validation vs. generic.
        if (t instanceof AssertionError) {
            if (contains(in.failedStep(), "database validation", "finvchmain", "finvchdet", "finpost", "db validat")
                    || contains(msg, "finvchmain", "finvchdet", "finpost", "db validat")) {
                return RootCause.of(RootCauseCategory.DB_VALIDATION_FAILURE,
                        "Database state does not match expected after save — likely a backend data-mapping "
                        + "defect. " + firstLine(msg, 220), "HIGH");
            }
            return RootCause.of(RootCauseCategory.ASSERTION_FAILURE,
                    "Assertion failed. " + firstLine(msg, 240), "HIGH");
        }

        // 16 — NPE: framework/null-data defect.
        if (t instanceof NullPointerException) {
            return RootCause.of(RootCauseCategory.FRAMEWORK_NPE,
                    "Null reference during execution — likely a framework or null-data defect. "
                    + firstLine(msg, 200), "MEDIUM");
        }

        // 17 — Environment / connectivity (DNS, dead session, generic WebDriver transport).
        if (contains(msg, "no such host", "connection refused", "unreachable", "session deleted", "session id")
                || isType(t, "NoSuchSessionException", "WebDriverException", "SocketException", "UnreachableBrowserException")) {
            return RootCause.of(RootCauseCategory.ENVIRONMENT,
                    "Environment/connectivity problem (host, browser session, or network). " + firstLine(msg, 200), "MEDIUM");
        }

        // 18 — Fallback.
        return RootCause.of(RootCauseCategory.UNKNOWN,
                t.getClass().getSimpleName() + ": " + firstLine(msg, 240), "LOW");
    }

    private static RootCause jsBlocked(RootCauseInputs in) {
        JsErrorClassification jc = in.jsClassification();
        String detail = "A critical JS error blocked the render"
                + (jc != null ? " [" + jc.category() + "]: " + jc.probableCause() : "")
                + " — " + firstLine(in.firstCriticalJsErrorMessage(), 200);
        String confidence = (jc != null) ? jc.confidence() : "MEDIUM";
        return RootCause.of(RootCauseCategory.JS_ERROR_BLOCKED_RENDER, detail, confidence);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns true when the timeout/element message indicates the element was never located
     * (i.e. the underlying cause is NoSuchElementException, not a stale/intercepted element).
     * Used to distinguish a locator mismatch from a JS-destroyed render.
     */
    private static boolean elementNeverFound(String msg) {
        return contains(msg, "nosuchelementexception", "no such element");
    }

    private static boolean contains(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank()) return false;
        String lc = haystack.toLowerCase();
        for (String n : needles) {
            if (n != null && lc.contains(n.toLowerCase())) return true;
        }
        return false;
    }

    /** Walks the cause chain checking simple class names (covers wrapped/subtype causes). */
    private static boolean isType(Throwable t, String... simpleNames) {
        for (Throwable cur = t; cur != null; ) {
            String sn = cur.getClass().getSimpleName();
            for (String name : simpleNames) {
                if (sn.equals(name)) return true;
            }
            Throwable next = cur.getCause();
            if (next == cur) break;   // guard against self-referential cause chains
            cur = next;
        }
        return false;
    }

    private static String firstLine(String s, int maxLen) {
        if (s == null || s.isBlank()) return "";
        int nl = s.indexOf('\n');
        String line = (nl >= 0) ? s.substring(0, nl).stripTrailing() : s;
        return line.length() > maxLen ? line.substring(0, maxLen) + "…" : line;
    }
}
