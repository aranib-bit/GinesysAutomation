package com.gsl.popup;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.PopupOutcome;
import com.gsl.enums.PopupType;
import com.gsl.exceptions.LoaderStillInProgressException;
import com.gsl.exceptions.UnexpectedPopupException;
import com.gsl.reports.PopupTrailBuffer;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.WaitUtils;

/**
 * Single entry point for unexpected popup detection.
 * Called automatically by SeleniumUtils.click() after every click action.
 * SUCCESS and INFO popups are ignored — handled by DocumentMessageBox as before.
 */
public final class PopupMonitor {

    private PopupMonitor() {}

    /**
     * Probes for an unexpected popup after a business action.
     * Silently returns if no popup is detected or if detection is disabled via config.
     *
     * @param actionName human-readable description of the preceding action (used in failure message)
     * @throws UnexpectedPopupException if an unexpected popup is detected
     */
    public static void checkAfterAction(String actionName) {
        if (!ConfigFactory.getWaitConfig().popupDetectionEnabled()) return;

        PopupResult popup = PopupHandler.detectPopup();
        if (popup == null) return;

        // Record every popup (except SUCCESS/INFO) to the trail for diagnostics history
        if (popup.popupType() != PopupType.SUCCESS && popup.popupType() != PopupType.INFO) {
            String trailMsg = (popup.message() != null && !popup.message().isBlank())
                    ? popup.message() : popup.header();
            PopupTrailBuffer.record("[" + popup.popupType() + " after " + actionName + "] "
                    + popup.header() + " — " + trailMsg);
        }

        // SUCCESS and INFO are expected responses handled by DocumentMessageBox — ignore them
        if (popup.popupType() == PopupType.SUCCESS || popup.popupType() == PopupType.INFO) return;

        // CONFIRM dialogs are expected interaction flows (e.g. concurrent session, discard changes)
        // Not dismissed — page object handles the Yes/No. Just note it and continue.
        if (popup.popupType() == PopupType.CONFIRM) {
            String msg = (popup.message() != null && !popup.message().isBlank())
                    ? " | Message: \"" + popup.message() + "\"" : "";
            ReportLogger.popup(PopupOutcome.EXPECTED, "Confirm dialog detected after [" + actionName + "] — \""
                    + popup.header() + "\"" + msg + " — handled by page flow");
            return;
        }

        // WARNING is logged with full details but does not fail the test
        if (popup.popupType() == PopupType.WARNING) {
            ReportLogger.popup(PopupOutcome.EXPECTED, "Warning popup after [" + actionName + "] — " + popup.header());
            PopupReporter.reportHandledPopup(popup, actionName);
            return;
        }

        PopupReporter.reportUnexpectedPopup(popup, actionName);
        throw new UnexpectedPopupException(popup, actionName);
    }

    /**
     * Same as checkAfterAction but for handled scenarios — logs the popup as a warning
     * and returns without throwing. Use when the caller knows a popup may appear and
     * deals with it, but still wants it noted in the report.
     */
    public static void noteAfterAction(String actionName) {
        if (!ConfigFactory.getWaitConfig().popupDetectionEnabled()) return;

        PopupResult popup = PopupHandler.detectPopup();
        if (popup == null) return;

        if (popup.popupType() == PopupType.SUCCESS || popup.popupType() == PopupType.INFO) return;

        ReportLogger.popup(PopupOutcome.EXPECTED, "Handled popup on [" + actionName + "] — "
                + popup.popupType() + " | Header: " + popup.header());
        PopupReporter.reportHandledPopup(popup, actionName);
    }

    /**
     * Shared diagnosis for a wait/action that just failed (typically a Selenium
     * {@code TimeoutException} or {@code NoSuchElementException}) — called from the catch block of
     * every explicit wait in {@code WaitUtils}/{@code JavascriptExecutorUtils}, and after every raw
     * interaction in {@code ActionUtils}, on top of the click-based check above. Common to every
     * voucher (AP/AR/GV) and every operation mode (Create/Modify/Revert/Release/Delete), since they
     * all share those utility classes.
     *
     * <p>Before giving up with the original, often-cryptic Selenium exception, checks whether the
     * <em>real</em> cause is:
     * <ol>
     *   <li>An unexpected application popup actually being on screen — thrown as
     *       {@link UnexpectedPopupException} with the same rich diagnosis used after a click.
     *       SUCCESS/INFO/CONFIRM are expected outcomes and are not the cause of a failure; WARNING
     *       is logged/reported but, matching {@link #checkAfterAction}, does not itself explain the
     *       failure — the search continues to the loader check below.</li>
     *   <li>No popup, but the ExtJS loading mask is still visible — thrown as
     *       {@link LoaderStillInProgressException} instead of a bare "waiting for visibility of
     *       element ... x-mask" timeout.</li>
     *   <li>Neither — {@code original} is rethrown unchanged; this is a genuine, unrelated
     *       failure (e.g. an actually-broken locator) and must not be masked.</li>
     * </ol>
     *
     * @param original      the exception that aborted the wait/action
     * @param actionName    human-readable description of what was being waited for/acted on
     * @param timeoutSeconds the actual timeout the failed wait was configured with — reported
     *                       verbatim in {@link LoaderStillInProgressException}'s message, so it
     *                       must be the caller's real timeout (e.g. a deliberately short 3-second
     *                       probe), not a hardcoded global default.
     * @return never returns — always throws. Declared to return {@link RuntimeException} so callers
     *         can write {@code throw PopupMonitor.diagnoseFailure(e, "...", seconds);}
     */
    public static RuntimeException diagnoseFailure(RuntimeException original, String actionName, long timeoutSeconds) {
        return diagnoseFailure(original, actionName, timeoutSeconds, -1L);
    }

    /**
     * Overload that also carries the REAL elapsed wait time, so
     * {@link LoaderStillInProgressException}'s "Waited:" line reports what actually happened
     * rather than just the configured ceiling (a wait aborting at 6s used to report the full
     * 40s). Callers that can measure their own elapsed time should prefer this; the 3-arg form
     * above stays for those that genuinely can't.
     */
    public static RuntimeException diagnoseFailure(RuntimeException original, String actionName,
                                                   long timeoutSeconds, long elapsedMs) {
        return diagnoseFailure(original, actionName, timeoutSeconds, elapsedMs, 1);
    }

    /**
     * Overload that also carries the REAL attempt count (initial wait + retry polls) — Round 8's
     * mask-retry makes multi-attempt waits real ({@code WaitUtils.waitForExtMaskInternal}), and a
     * failure should say how many attempts were actually made, not just report one opaque ceiling.
     * Callers that retry should prefer this; the 4-arg form above stays for those that don't
     * (reported as a single attempt).
     */
    public static RuntimeException diagnoseFailure(RuntimeException original, String actionName,
                                                   long timeoutSeconds, long elapsedMs, int attempts) {
        if (ConfigFactory.getWaitConfig().popupDetectionEnabled()) {
            PopupResult popup = PopupHandler.detectPopup();
            if (popup != null) {
                if (popup.popupType() == PopupType.WARNING) {
                    ReportLogger.popup(PopupOutcome.EXPECTED, "Warning popup detected while diagnosing ["
                            + actionName + "] failure — " + popup.header());
                    PopupReporter.reportHandledPopup(popup, actionName);
                } else if (popup.popupType() != PopupType.SUCCESS
                        && popup.popupType() != PopupType.INFO
                        && popup.popupType() != PopupType.CONFIRM) {
                    PopupReporter.reportUnexpectedPopup(popup, actionName);
                    throw new UnexpectedPopupException(popup, actionName);
                }
            }
        }

        if (WaitUtils.isExtMaskPresent(DriverManager.getDriver())) {
            throw new LoaderStillInProgressException(actionName, timeoutSeconds, elapsedMs, attempts, original);
        }

        throw original;
    }
}
