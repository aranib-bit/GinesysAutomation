package com.gsl.exceptions;

import org.openqa.selenium.TimeoutException;

/**
 * Thrown when a wait aborts because the application's ExtJS loading mask
 * ({@code .x-mask}/{@code .x-mask-msg}) was still visible when the timeout expired — as opposed
 * to a genuine locator/DOM problem. Distinguishes "the app just hadn't finished loading yet" from
 * an actual defect so the failure reason is immediately visible in the test report, instead of a
 * raw Selenium {@code TimeoutException}/{@code NoSuchElementException} that reads like an
 * unrelated failure in whatever step happened to run next.
 *
 * <p><b>Deliberately extends {@link TimeoutException}</b> (not a plain {@code RuntimeException}):
 * this exception replaces exactly the {@code TimeoutException} that {@code WaitUtils}/
 * {@code JavascriptExecutorUtils} would otherwise have thrown from the same failed wait. Many
 * page objects across the framework (e.g. {@code VoucherDocumentInformationBase
 * .handleNarrationTextPopup}, the Sub-Ledger/On-Account/TDS-payable confirm handlers) use a
 * narrow {@code catch (TimeoutException e)} around a short, deliberately optional wait — "this
 * popup/row may or may not appear, absence is fine." Subclassing {@code TimeoutException} keeps
 * every one of those existing catches working exactly as before; only a caller with no such catch
 * (a genuine failure) ever sees the clearer message. {@code UnexpectedPopupException} is
 * intentionally NOT a {@code TimeoutException} subclass — a real application-bug popup appearing
 * during one of those "optional" waits is a new, more severe finding that should surface, not be
 * silently swallowed as "element absent."
 *
 * <p>Raised by {@code PopupMonitor.diagnoseFailure(...)}, the shared diagnosis helper called from
 * every wait's failure path ({@code WaitUtils}, {@code JavascriptExecutorUtils}) — common to every
 * voucher (AP/AR/GV) and every operation mode (Create/Modify/Revert/Release/Delete), since they
 * all share those utility classes.
 */
public final class LoaderStillInProgressException extends TimeoutException {

    /**
     * @param actionName      what was being waited for — pass the CALLER's own context label where
     *                        one exists, not a generic constant; a failure that only ever says
     *                        "Loader wait (ExtJS mask)" is indistinguishable between call sites.
     * @param timeoutSeconds  the configured ceiling for this wait
     * @param elapsedMs       how long the wait ACTUALLY ran before aborting, or a negative value if
     *                        the caller genuinely doesn't know
     */
    public LoaderStillInProgressException(String actionName, long timeoutSeconds, long elapsedMs,
                                          Throwable cause) {
        super(buildMessage(actionName, timeoutSeconds, elapsedMs), cause);
    }

    /**
     * Kept for callers that genuinely have no elapsed-time measurement. Prefer the 4-arg
     * constructor: this overload can only print the configured ceiling, which is misleading
     * whenever the wait aborted early (see {@link #buildMessage}).
     */
    public LoaderStillInProgressException(String actionName, long timeoutSeconds, Throwable cause) {
        this(actionName, timeoutSeconds, -1L, cause);
    }

    /**
     * Reports the REAL elapsed time, falling back to the configured ceiling only when the caller
     * couldn't measure it.
     *
     * <p>This previously printed {@code timeoutSeconds} unconditionally and labelled it
     * "Waited:" — so a wait that aborted after 6s still reported "Waited: 40 second(s)", because 40
     * is simply {@code extjs.timeout}. That made every mask failure look like it had exhausted the
     * full budget and made the number useless for judging whether the app was slow or the wait
     * aborted early.
     */
    private static String buildMessage(String actionName, long timeoutSeconds, long elapsedMs) {
        String waited = elapsedMs >= 0
                ? String.format("%.1f second(s) actual (ceiling %d s)", elapsedMs / 1000.0, timeoutSeconds)
                : String.format("unmeasured — configured ceiling was %d second(s)", timeoutSeconds);
        return String.format("""
                Loader Still In Progress

                Action:  %s
                Waited:  %s
                Detail:  An ExtJS loading mask (.x-mask / .x-mask-msg) was still visible when the \
                wait expired — the application had not finished loading/processing in time.
                """,
                actionName, waited);
    }
}
