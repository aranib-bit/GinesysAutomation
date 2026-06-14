package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page object for the "Ref. Site-wise FIFO Set-Off" popup.
 *
 * <h3>UI flow</h3>
 * <ol>
 *   <li>Double-click empty upper grid to activate the inline row editor.</li>
 *   <li>Click ▼ combo trigger (floating {@code x-editor} overlay outside window DOM —
 *       same pattern as {@code DEDUCT_TDS_TRIGGER} in APVoucherSubLedgerInformation).</li>
 *   <li>Select reference site from the boundlist dropdown.</li>
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
 *   <li>Combo trigger is a floating {@code x-editor} rendered outside the popup's DOM.</li>
 * </ul>
 */
public class ReferenceSiteWiseFIFOPopup {

    // ── Popup title constant — change here to affect all locators ─────────────
    private static final String POPUP_TITLE = "Ref. Site-wise FIFO Set-Off";

    // ── Shared window scope (1 of 1, confirmed via DevTools with @role='dialog') ──
    private static final String WIN_SCOPE =
            "//div[contains(@class,'x-window') and @role='dialog' and " +
            ".//div[contains(@class,'winTitleType2') and normalize-space()='" + POPUP_TITLE + "']]";

    // ── Combo ▼ trigger — scoped inside WIN_SCOPE ────────────────────────────
    // Popup opens with the editor row pre-shown (Record Count: 1 already visible).
    // No double-click needed. The arrow is the only x-form-arrow-trigger inside
    // this dialog, so WIN_SCOPE scope uniquely targets it.
    private static final By COMBO_TRIGGER =
            By.xpath(WIN_SCOPE + "//div[contains(@class,'x-form-arrow-trigger')]");

    // ── Combo text input — inside WIN_SCOPE, for send-keys ───────────────────
    private static final By COMBO_INPUT =
            By.xpath(WIN_SCOPE + "//div[contains(@class,'x-grid-view')]" +
                     "//input[contains(@class,'x-form-field')]");

    // ── Combo option in the boundlist dropdown ────────────────────────────────
    // Boundlist is grid-style (Site Name | Site Short Name | Site Type | OU Name | Pending Amount).
    // Match the row whose first td contains the site name — same pattern as subLedgerPickerRow.
    private static final String COMBO_OPTION_XPATH =
            "//div[contains(@class,'x-boundlist-floating') and not(contains(@class,'x-hide-display'))]" +
            "//div[contains(@class,'x-boundlist-item') and .//td[normalize-space()='%s']]";

    // ── Sub-grid row by site name ─────────────────────────────────────────────
    // [last()] = second x-grid-view in the popup = site sub-grid
    private static final String SUB_GRID_ROW_BY_SITE_XPATH =
            "(" + WIN_SCOPE + "//div[contains(@class,'x-grid-view')])[last()]" +
            "//div[contains(@class,'x-grid-item-container')]" +
            "//div[normalize-space()='%s']";

    // ── Auto Adjust button ────────────────────────────────────────────────────
    private static final By AUTO_ADJUST_BUTTON =
            By.xpath(WIN_SCOPE + "//a[.//span[normalize-space()='Auto Adjust']]");

    // ── Used for invisibility wait after Auto Adjust closes the popup ─────────
    private static final By POPUP_WINDOW =
            By.xpath(WIN_SCOPE);

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Clicks the ▼ trigger arrow to open the Reference Site Name boundlist,
     * then clicks the matching option.
     * The popup opens with the editor row pre-shown — no double-click needed.
     */
    public ReferenceSiteWiseFIFOPopup selectReferenceSiteInCombo(String referenceSiteName) {
        SeleniumUtils.click(COMBO_TRIGGER, WaitType.VISIBLE, "RefSiteFIFO_ComboTrigger");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath(String.format(COMBO_OPTION_XPATH, referenceSiteName));
        SeleniumUtils.click(option, WaitType.CLICKABLE, "RefSiteFIFO_ComboOption_" + referenceSiteName);
        // Enter commits the selected value into the grid cell editor
        DriverManager.getDriver().findElement(COMBO_INPUT).sendKeys(Keys.ENTER);
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

    /**
     * Full single-site flow: double-click upper grid → select reference site in combo →
     * click sub-grid row → Auto Adjust.
     *
     * @param referenceSiteName name to select in the upper grid combo (from v_pending_adj.ref_admsite_name)
     * @param subSiteName       name of the row to click in the sub-grid (may equal referenceSiteName)
     */
    public void selectAndAutoAdjust(String referenceSiteName, String subSiteName) {
        selectReferenceSiteInCombo(referenceSiteName)
                .selectSubSiteRow(subSiteName)
                .clickAutoAdjust();
    }
}
