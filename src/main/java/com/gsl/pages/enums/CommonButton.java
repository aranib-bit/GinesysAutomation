package com.gsl.pages.enums;

import org.openqa.selenium.By;

/**
 * Centralised registry for common UI button labels that appear across multiple modules.
 *
 * Usage — call locator() at the use-site, never store the result in a field:
 * <pre>
 *     SeleniumUtils.click(CommonButton.OK.locator(), WaitType.CLICKABLE, "OK");
 *     SeleniumUtils.click(CommonButton.YES.scopedLocator("//div[contains(@class,'x-message-box')]"), ...);
 * </pre>
 *
 * Notes:
 * - locator() uses normalize-space()= to tolerate leading/trailing whitespace that ExtJS
 *   injects into button span nodes (confirmed failure with text()= for SELECT button).
 * - Page-specific buttons (Login, Logout, Create Item, etc.) do NOT belong here —
 *   keep them as static final By fields inside the owning page's Loc class.
 * - Context-specific Finance popup buttons (Ok Go Ahead, Ignore & Proceed, Proceed Anyway,
 *   Stay) are owned by DocumentPopup.ButtonText — do not duplicate them here.
 * - Truncated ExtJS workaround xpaths ('ave', 'ancel', 'K') are owned by their
 *   respective Loc classes — do not move them here.
 */
public enum CommonButton {

    // ── Dialog / message-box buttons ─────────────────────────────────────────
    OK      ("OK"),
    YES     ("Yes"),
    NO      ("No"),
    CANCEL  ("Cancel"),
    PROCEED ("Proceed"),
    CONTINUE("Continue"),

    // ── Popup action buttons ──────────────────────────────────────────────────
    ACCEPT  ("Accept"),
    SELECT  ("Select"),

    // ── List / grid toolbar buttons ───────────────────────────────────────────
    ADD     ("Add");

    private final String label;

    CommonButton(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Standard full-page locator — matches the button anywhere on the page.
     * Uses normalize-space()= to tolerate whitespace padding that ExtJS injects into span nodes.
     */
    public By locator() {
        return By.xpath("//span[normalize-space()='" + label + "']");
    }

    /**
     * Scoped locator — restricts search to elements inside the given container xpath.
     * Use when the same button text appears in multiple contexts simultaneously.
     * Example:
     * <pre>
     *     CommonButton.YES.scopedLocator("//div[contains(@class,'x-message-box')]")
     * </pre>
     */
    public By scopedLocator(String containerXpath) {
        return By.xpath(containerXpath + "//span[normalize-space()='" + label + "']");
    }
}
