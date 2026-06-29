package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.Common.AdjustmentPopups;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Page object for the "Reference Site wise FIFO set-off" popup.
 *
 * <h3>UI flow (native Selenium — no ExtJS injection)</h3>
 * <ol>
 *   <li>Double-click row 0's <em>Reference Site Name</em> cell to activate the inline editor.</li>
 *   <li>Type the reference site name into the active editor input; the application
 *       filters the boundlist via its own combo handlers.</li>
 *   <li>Click the matching boundlist row by {@code data-qtip} (highlight-proof).</li>
 *   <li>Click a row in the sub-grid by site name.</li>
 *   <li>Click "Auto Adjust" — popup closes, Adjustment Entry footer shows updated amounts.</li>
 * </ol>
 *
 * <h3>Locator notes</h3>
 * <ul>
 *   <li>{@code @role='dialog'} scopes to exactly 1 match (ancestor divs lack this attribute).</li>
 *   <li>Title lives in {@code div.winTitleType2}, not a {@code <span>}.</li>
 *   <li>{@code x-column-header} and {@code x-grid-view} are siblings in ExtJS DOM —
 *       ancestor-navigation from column-header → grid-view fails; use {@code [1]}/{@code [last()]}.</li>
 *   <li>The inline editor is rendered as a floating {@code x-editor} overlay at document
 *       level (outside {@code WIN_SCOPE}) — same pattern as {@code DEDUCT_TDS_TRIGGER}.</li>
 * </ul>
 */
public class ReferenceSiteWiseFIFOPopup {

    // ── Popup title + window scope are owned by AdjustmentPopups so the same
    //    string drives both the visibility wait in AdjustmentEntry and all the
    //    locators inside this popup. Change the title once, in the enum.
    private static final String WIN_SCOPE =
            AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.windowScopeXpath();

    // ── Last-row cell resolved by column header text ─────────────────────────
    // Last data row in the popup grid (so newly added rows are targeted), td at
    // the position of the named column (counted by preceding x-column-header
    // siblings inside WIN_SCOPE). Same header-position pattern as
    // ADJUSTED_AMOUNT_CELL_BASE_XPATH in AdjustmentEntry. The popup opens with
    // 1 row, so last() == [1] for the first fill.
    private static final String LAST_ROW_CELL_BY_HEADER_XPATH =
            WIN_SCOPE + "//tr[contains(@class,'x-grid-row')][last()]" +
            "/td[count(" + WIN_SCOPE +
            "//div[contains(@class,'x-column-header')]" +
            "[.//span[normalize-space()='%s']]" +
            "/preceding-sibling::div[contains(@class,'x-column-header')]) + 1]";

    private static final By REF_SITE_NAME_CELL_LAST_ROW =
            By.xpath(String.format(LAST_ROW_CELL_BY_HEADER_XPATH, "Reference Site Name"));

    // ── Active inline editor input — floating x-editor overlay at document level,
    //    NOT inside WIN_SCOPE (ExtJS renders editors outside the host window DOM).
    private static final By ACTIVE_EDITOR_INPUT = By.xpath(
            "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]" +
            "//input[contains(@class,'x-form-field')]");

    // ── Combo option in the boundlist dropdown ────────────────────────────────
    // Boundlist is grid-style (Site Name | Site Short Name | Site Type | OU Name | Pending Amount).
    // Match the first td by data-qtip — highlight-proof; same pattern as subLedgerPickerRow.
    private static final String COMBO_OPTION_XPATH =
            "//div[contains(@class,'x-boundlist-floating') and not(contains(@class,'x-hide-display'))]" +
            "//div[contains(@class,'x-boundlist-item') and .//td[@data-qtip=\"%s\"]]";

    // ── Sub-grid row by site name ─────────────────────────────────────────────
    // [last()] = second x-grid-view in the popup = site sub-grid
    private static final String SUB_GRID_ROW_BY_SITE_XPATH =
            "(" + WIN_SCOPE + "//div[contains(@class,'x-grid-view')])[last()]" +
            "//div[contains(@class,'x-grid-item-container')]" +
            "//div[normalize-space()='%s']";

    // ── Auto Adjust button ────────────────────────────────────────────────────
    private static final By AUTO_ADJUST_BUTTON =
            By.xpath(WIN_SCOPE + "//a[.//span[normalize-space()='Auto Adjust']]");

    // ── Add Row button — mdi-playlist-plus icon inside the popup grid's
    //    vertical-docked left toolbar (id="toolbar-XXXX" with class
    //    "x-toolbar-vertical x-docked-left" — observed in the FIFO popup DOM).
    //    Scoping to WIN_SCOPE + the vertical toolbar prevents collision with:
    //      (a) the underlying voucher's sub-ledger Add Row (outside WIN_SCOPE),
    //      (b) any per-row mdi-playlist-plus that might appear in an action
    //          column — the grid-level Add Row lives only on this toolbar.
    private static final By ADD_ROW_BUTTON = By.xpath(
            WIN_SCOPE + "//div[contains(@class,'x-toolbar-vertical')]" +
            "//i[contains(@class,'mdi-playlist-plus')]" +
            "/parent::span[contains(@class,'x-btn-inner-IconButton-small')]");

    // ── Used for visibility/invisibility waits before driving and after Auto Adjust ─
    static final By POPUP_WINDOW =
            AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.popupWindowLocator();

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Adds a new empty row to the popup grid by clicking the native Add Row
     * button (mdi-playlist-plus icon). Subsequent {@link #selectReferenceSiteInCombo}
     * calls then target the newly added row (the locator uses {@code last()}).
     */
    public ReferenceSiteWiseFIFOPopup addRow() {
        SeleniumUtils.click(ADD_ROW_BUTTON, WaitType.CLICKABLE, "RefSiteFIFO_AddRow");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /**
     * Native UI flow on the last data row's <em>Reference Site Name</em> cell:
     * real double-click to activate the inline editor, type the name, click the
     * matching boundlist row. Mirrors the SubLedger row-add interaction shape.
     *
     * <p>Targets the {@code last()} row so the same method drives both the
     * pre-existing row 0 and any rows added via {@link #addRow()}.
     */
    public ReferenceSiteWiseFIFOPopup selectReferenceSiteInCombo(String referenceSiteName) {
        // Step 1 — Real browser double-click on the last row's Reference Site Name cell.
        WaitUtils.fluentWaitBuilder().until(driver -> {
            new Actions(driver).doubleClick(driver.findElement(REF_SITE_NAME_CELL_LAST_ROW)).perform();
            return true;
        });
        ExtentLogger.info("[RefSiteFIFO] Last row Reference Site Name cell double-clicked");
        JavascriptExecutorUtils.waitForExtJS();

        // Step 2 — Clear + type into the active editor input (floating x-editor overlay).
        SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "RefSiteFIFO_ComboInput");
        SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, referenceSiteName, "RefSiteFIFO_ComboInput");
        JavascriptExecutorUtils.waitForExtJS();

        // Step 3 — Commit the value. ExtJS keeps the boundlist open when several
        // options match the typed prefix (multi-row datasets like SubLedger), and
        // auto-collapses it after an exact-match auto-complete on a single-/few-
        // option dataset (this popup typically lists 1–2 reference sites). Try
        // the boundlist click first; if the option is in the DOM but not
        // interactable (boundlist auto-collapsed), TAB the editor input to commit
        // the highlighted auto-completed value — the same key a user would press.
        By option = By.xpath(String.format(COMBO_OPTION_XPATH, referenceSiteName));
        try {
            WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                    .until(ExpectedConditions.elementToBeClickable(option))
                    .click();
            ExtentLogger.info("RefSiteFIFO_Option_" + referenceSiteName + " is clicked successfully");
        } catch (Exception clickFailed) {
            SeleniumUtils.pressTab(ACTIVE_EDITOR_INPUT, "RefSiteFIFO_ComboInput");
            ExtentLogger.info("RefSiteFIFO_Option_" + referenceSiteName
                    + " committed via TAB (boundlist auto-collapsed)");
        }
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        return this;
    }

    /** Single-clicks a row in the sub-grid to select it by exact site name. */
    public ReferenceSiteWiseFIFOPopup selectSubSiteRow(String siteName) {
        By locator = By.xpath(String.format(SUB_GRID_ROW_BY_SITE_XPATH, siteName));
        SeleniumUtils.click(locator, WaitType.CLICKABLE, "RefSiteFIFO_SubSiteRow_" + siteName);
        return this;
    }

    /**
     * Clicks Auto Adjust and waits for the popup to disappear.
     * If the "pending amount" warning appears (grid total > adjusted total),
     * automatically clicks "Proceed Anyway" before waiting for popup close.
     */
    public void clickAutoAdjust() {
        SeleniumUtils.click(AUTO_ADJUST_BUTTON, WaitType.CLICKABLE, "RefSiteFIFO_AutoAdjust");
        JavascriptExecutorUtils.waitForExtJS();
        handlePendingWarningIfPresent();
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                .until(ExpectedConditions.invisibilityOfElementLocated(POPUP_WINDOW));
    }

    /**
     * Checks (with a short 3-second timeout) whether the "There is still Rs. X pending
     * to be adjusted" warning is present and clicks "Proceed Anyway" if so.
     * No-op when the warning is absent — normal flow continues.
     */
    private void handlePendingWarningIfPresent() {
        By proceedAnyway = DocumentPopup.ButtonText.PROCEED_ANYWAY.locator();
        try {
            WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                    .until(ExpectedConditions.elementToBeClickable(proceedAnyway));
            SeleniumUtils.click(proceedAnyway, WaitType.CLICKABLE, "RefSiteFIFO_ProceedAnyway");
            JavascriptExecutorUtils.waitForExtJS();
        } catch (Exception ignored) {
            // Warning popup not present — continue to popup-close wait
        }
    }

}
