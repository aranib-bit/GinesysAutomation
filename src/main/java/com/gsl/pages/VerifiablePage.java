package com.gsl.pages;

/**
 * Implemented by page objects that can confirm, via a live DOM signal (not URL, not menu-highlight
 * state), that the browser is actually showing them right now. {@code NavigationUtils} checks for
 * this after a navigation call lands — if the returned page implements it, the check runs before
 * the caller is handed a page object it might otherwise trust blindly (e.g. before clicking a
 * generic, page-agnostic "Add" button that exists on many list pages).
 */
public interface VerifiablePage {

    /** @throws IllegalStateException if the live page doesn't match what this page object expects. */
    void verifyOnThisPage();

    /**
     * Bounded-retry wrapper around {@link #verifyOnThisPage()} — polls it every 300ms until it
     * stops throwing or {@code timeoutSeconds} elapses, then rethrows the LAST failure only if it
     * never resolved.
     *
     * <p>Added to bridge a genuine render-completion race: a view swap's header text can be read
     * an instant before the new page's own text is actually written in, so a single-shot
     * {@code verifyOnThisPage()} call can throw even though the page is correct (or about to be).
     * Confirmed live in run {@code 20260909_094016}: this exact race on General Voucher's Add-form
     * page header caused a needless "reopen" click, which then genuinely failed (the Add form was
     * already open, covering the original button) — masking the real, upstream cause. Callers that
     * used to call {@code verifyOnThisPage()} once and treat any failure as "definitely not on this
     * page yet" should call this instead, reserving a genuine reopen/retry action for when the page
     * STILL doesn't match after this window.
     *
     * @param timeoutSeconds total time to keep retrying before giving up — a real page-header
     *                        write is near-instant once the view swap completes, so a short window
     *                        (a few seconds) is enough to bridge the race without masking a
     *                        genuinely-stuck page for long.
     * @throws RuntimeException whatever {@link #verifyOnThisPage()} itself throws, if it never
     *         passes within {@code timeoutSeconds}
     */
    default void waitUntilVerified(long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (true) {
            try {
                verifyOnThisPage();
                return;
            } catch (RuntimeException e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw e;
                }
                sleep(300);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
