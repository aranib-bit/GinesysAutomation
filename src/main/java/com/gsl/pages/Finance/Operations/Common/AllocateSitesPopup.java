package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.math.BigDecimal;
import java.util.List;

/**
 * Page object for the "Allocate Sites" popup — opened from a General Voucher
 * Expense/Income Detail grid row's "Site" button (Type 2/Type 3 rows).
 *
 * <p>Modeled on {@link ReferenceSiteWiseFIFOPopup}'s combo idiom (type into a
 * floating {@code x-editor}/boundlist overlay rendered at document level, not
 * inside the popup window's own DOM scope — same insight documented there),
 * adapted to this popup's distinct shape:
 * <ul>
 *     <li><b>Quick Site Allocate</b>: Site search field + Amount field + Add button
 *         — a commit action (adds a new row to the grid below), unlike
 *         {@code ReferenceSiteWiseFIFOPopup.addRow()} which adds an empty row to
 *         type into.</li>
 *     <li><b>Allocated Site(s) grid</b>: Site Name | Amount, read back for assertions.</li>
 *     <li><b>Tagging Summary</b>: Actual Amount | Tagged Amount | Pending Amount,
 *         read-only fields.</li>
 * </ul>
 *
 * <p>Self-contained window-scope (not registered in {@code AdjustmentPopups} —
 * this popup opens directly from a grid-row button, not the Adjustment Entry
 * dropdown), same {@code x-window[@role='dialog']}/{@code winTitleType2} pattern.
 *
 * <p><b>UNVERIFIED — see plan's "Verification / Open items" §2:</b> the exact
 * window title text and every locator below are best-effort, built from the
 * screenshot's field labels, not confirmed against the live DOM.
 */
public class AllocateSitesPopup {

    /** TODO: confirm against the live UI — the screenshot doesn't show this dialog's actual title text. */
    private static final String WIN_TITLE = "Allocate Sites";

    private static final String WIN_SCOPE =
            "//div[contains(@class,'x-window') and @role='dialog' and " +
            ".//div[contains(@class,'winTitleType2') and normalize-space()='" + WIN_TITLE + "']]";

    private static final By POPUP_WINDOW = By.xpath(WIN_SCOPE);

    // ── Quick Site Allocate ────────────────────────────────────────────────────
    private static final By SITE_SEARCH_INPUT = By.xpath(WIN_SCOPE + byLabelSuffix("Site:"));
    private static final By AMOUNT_INPUT       = By.xpath(WIN_SCOPE + byLabelSuffix("Amount:"));
    private static final By ADD_BUTTON         = By.xpath(WIN_SCOPE + "//span[normalize-space()='Add']/ancestor::a");

    // ExtJS renders combo boundlists as floating overlays at document level, outside the
    // window's own DOM scope — same insight documented in ReferenceSiteWiseFIFOPopup.
    /**
     * The Site field's search trigger (the magnifier).
     *
     * <p><b>Site is a {@code gslpicklistfieldextended}, not a combo.</b> The first version of this
     * class typed into the field and waited for an inline {@code x-boundlist-floating} dropdown —
     * the combo idiom borrowed from {@code ReferenceSiteWiseFIFOPopup}. That dropdown does not
     * exist for this widget: the DOM captured at run 20260807_113508 shows
     * {@code <input id="gslpicklistfieldextended-1537-inputEl" role="textbox">} (no
     * {@code role="combobox"}, no {@code aria-haspopup}) paired with an
     * {@code x-form-search-trigger}. Clicking that trigger opens a <i>separate modal search
     * dialog</i> — the same widget and the same flow as Cash/Bank on the voucher header.
     */
    private static final By SITE_PICKER_TRIGGER = By.xpath(
            WIN_SCOPE + "//span[normalize-space()='Site:']/ancestor::label/following-sibling::div" +
            "//div[contains(@class,'x-form-search-trigger')]");

    // ── The picklist's search dialog ───────────────────────────────────────────
    // Locators reused verbatim from CashBankPopup.Loc, which drives this exact widget on the
    // voucher header and is proven against the live app. NOT window-scoped: the dialog stacks
    // above the Allocate Sites window as its own layer, outside WIN_SCOPE's subtree.
    private static final By PICKER_SEARCH_INPUT = By.xpath(
            "//span[normalize-space()='Search:']/ancestor::label/following-sibling::div" +
            "//input[contains(@id,'textfield') and contains(@id,'-inputEl')]");

    private static final String PICKER_ROW_XPATH =
            "//div[contains(@class,'x-grid-item-container')]//tr" +
            "//td[count(//div[@role='columnheader'][.//span[text()='Name']]/preceding-sibling::div)+1]" +
            "//div[normalize-space()='%s']";

    // ExtJS truncated-text workaround, same as CashBankPopup: the button's inner span renders as
    // "K"/"ancel" rather than "OK"/"Cancel".
    private static final By PICKER_OK_BUTTON = By.xpath(
            "//span[contains(@id,'button') and contains(@id,'btnInnerEl') and text()='K']");

    // ── Allocated Site(s) grid ─────────────────────────────────────────────────
    private static final String ALLOCATED_ROW_BY_SITE_XPATH =
            WIN_SCOPE + "//tr[contains(@class,'x-grid-row')][.//div[normalize-space()='%s']]";
    private static final By ALLOCATED_ROWS = By.xpath(WIN_SCOPE + "//tr[contains(@class,'x-grid-row')]");

    // ── Tagging Summary ─────────────────────────────────────────────────────────
    private static final By ACTUAL_AMOUNT_FIELD  = By.xpath(WIN_SCOPE + byLabelSuffix("Actual Amount:"));
    private static final By TAGGED_AMOUNT_FIELD   = By.xpath(WIN_SCOPE + byLabelSuffix("Tagged Amount:"));
    private static final By PENDING_AMOUNT_FIELD  = By.xpath(WIN_SCOPE + byLabelSuffix("Pending Amount:"));

    // ── Footer ──────────────────────────────────────────────────────────────────
    private static final By OK_BUTTON     = By.xpath(WIN_SCOPE + "//span[normalize-space()='OK']/ancestor::a");
    private static final By CANCEL_BUTTON = By.xpath(WIN_SCOPE + "//span[normalize-space()='Cancel']/ancestor::a");

    private static String byLabelSuffix(String label) {
        return "//span[normalize-space()='" + label + "']/ancestor::label" +
               "/following-sibling::div//input[contains(@id,'inputEl')]";
    }

    // =========================================================================
    // Actions
    // =========================================================================

    /** Types into the Quick Site Allocate "Site" search field and clicks the matching boundlist option. */
    /**
     * Selects a site through the picklist's search dialog: click the magnifier, type the name into
     * the dialog's Search box, click the matching row in the Name column, confirm with OK.
     *
     * <p>Mirrors {@code VoucherDocumentInformationBase.clickCashBankSearch() + selectCashBank()} →
     * {@code CashBankPopup.selectCashBank}, which drives the identical widget on the voucher header.
     * See {@link #SITE_PICKER_TRIGGER} for why the previous combo/boundlist approach could never
     * have worked.
     */
    public AllocateSitesPopup searchSite(String siteName) {
        // clickWithStaleRetry/sendKeysWithStaleRetry, not the plain variants: this flow is the
        // most DOM-churning interaction in the whole GV UI (nested picker dialog opens, a
        // server-reloaded search grid, a result-row click, an OK click that closes the dialog and
        // updates the popup's own Site field) — see SeleniumUtils' own Javadoc on these methods.
        SeleniumUtils.clickWithStaleRetry(SITE_PICKER_TRIGGER, WaitType.CLICKABLE, "AllocateSites_SiteSearchTrigger");
        JavascriptExecutorUtils.waitForExtJS();

        SeleniumUtils.sendKeysWithStaleRetry(PICKER_SEARCH_INPUT, WaitType.VISIBLE, siteName, "AllocateSites_SitePickerSearch");
        JavascriptExecutorUtils.waitForExtJS();

        // popupWaitSeconds, not shortWait: the grid reloads from the server after each search.
        By row = By.xpath(String.format(PICKER_ROW_XPATH, siteName));
        WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().popupWaitSeconds())
                .until(ExpectedConditions.visibilityOfElementLocated(row));
        SeleniumUtils.clickWithStaleRetry(row, WaitType.CLICKABLE, "AllocateSites_SiteRow_" + siteName);

        SeleniumUtils.clickWithStaleRetry(PICKER_OK_BUTTON, WaitType.CLICKABLE, "AllocateSites_SitePickerOK");
        JavascriptExecutorUtils.waitForExtJS();

        verifySiteCommitted(siteName);
        return this;
    }

    /**
     * Confirms the Site field actually holds {@code siteName} after selection.
     *
     * <p><b>Why this exists.</b> The original {@code searchSite} caught every exception from the
     * option click, pressed TAB, and logged "committed via TAB (boundlist auto-collapsed)" — an
     * unconditional success message for what was often a silent miss. When the combo has
     * {@code forceSelection}, blurring on text that matched no option makes ExtJS <i>clear the
     * field</i>, so the run continued with an empty Site, set the Amount, clicked Add, and surfaced
     * as the app's own "Site selection is mandatory." error two steps downstream — whose modal mask
     * then blocked the popup's OK button and burned the full overlay-retry budget. Run
     * 20260807_110215 is that failure end to end; the report cheerfully logged
     * "AllocateSites_Option_CNSStore committed via TAB" while the field sat empty.
     *
     * <p>The site was never the problem there — {@code admsite} has {@code CNSStore} (code 1102).
     * Failing here, at the point of the miss, with the options that were actually on offer, is the
     * difference between a one-line diagnosis and a three-step reconstruction.
     */
    private void verifySiteCommitted(String siteName) {
        String actual = SeleniumUtils.getAttribute(SITE_SEARCH_INPUT, "value", "AllocateSites_SiteSearch");
        if (actual != null && actual.trim().equalsIgnoreCase(siteName.trim())) {
            return;
        }
        throw new IllegalStateException(
                "Allocate Sites: failed to select site '" + siteName + "' — the Site field holds "
                + (actual == null || actual.isBlank() ? "nothing" : "'" + actual + "'")
                + " after the picklist dialog closed. Check that the site exists and is selectable "
                + "in this voucher's context (admsite.name), and that the picker's Name column still "
                + "matches PICKER_ROW_XPATH. Continuing would hit the app's own "
                + "\"Site selection is mandatory.\" error at Add, whose modal mask then blocks this "
                + "popup's OK button — so the failure would surface two steps away from its cause.");
    }

    /** Types into the Quick Site Allocate "Amount" field. */
    public AllocateSitesPopup setAmount(BigDecimal amount) {
        SeleniumUtils.clear(AMOUNT_INPUT, "AllocateSites_Amount");
        SeleniumUtils.sendKeys(AMOUNT_INPUT, WaitType.VISIBLE, amount.toPlainString(), "AllocateSites_Amount");
        return this;
    }

    /** Clicks the Quick Site Allocate "Add" button, committing the current Site+Amount into the grid below. */
    public AllocateSitesPopup clickAdd() {
        SeleniumUtils.clickWithStaleRetry(ADD_BUTTON, WaitType.CLICKABLE, "AllocateSites_Add");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /** Composed convenience — the one method the fill loop calls per {@code SiteAllocation} row. */
    public AllocateSitesPopup addSiteAllocation(String siteName, BigDecimal amount) {
        searchSite(siteName);
        setAmount(amount);
        return clickAdd();
    }

    // ── Tagging Summary read-back ──────────────────────────────────────────────

    public BigDecimal getActualAmount() {
        return BigDecimalUtils.safeParse(SeleniumUtils.getAttribute(ACTUAL_AMOUNT_FIELD, "value", "ACTUAL_AMOUNT"));
    }

    public BigDecimal getTaggedAmount() {
        return BigDecimalUtils.safeParse(SeleniumUtils.getAttribute(TAGGED_AMOUNT_FIELD, "value", "TAGGED_AMOUNT"));
    }

    public BigDecimal getPendingAmount() {
        return BigDecimalUtils.safeParse(SeleniumUtils.getAttribute(PENDING_AMOUNT_FIELD, "value", "PENDING_AMOUNT"));
    }

    // ── Allocated Site(s) grid read-back ───────────────────────────────────────

    public List<String> getAllocatedSiteNames() {
        return com.gsl.driver.DriverManager.getDriver().findElements(ALLOCATED_ROWS).stream()
                .map(row -> row.getText())
                .toList();
    }

    public BigDecimal getAllocatedAmountForSite(String siteName) {
        By row = By.xpath(String.format(ALLOCATED_ROW_BY_SITE_XPATH, siteName));
        String text = SeleniumUtils.getAttribute(row, "textContent", "ALLOCATED_ROW_" + siteName);
        return BigDecimalUtils.safeParse(text);
    }

    // ── Footer ──────────────────────────────────────────────────────────────────

    /** Commits the allocation and waits for the popup to close. */
    public void clickOk() {
        SeleniumUtils.clickWithStaleRetry(OK_BUTTON, WaitType.CLICKABLE, "AllocateSites_OK");
        JavascriptExecutorUtils.waitForExtJS();
        try {
            WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                    .until(ExpectedConditions.invisibilityOfElementLocated(POPUP_WINDOW));
        } catch (TimeoutException stillOpen) {
            // The app refuses OK while Pending Amount != 0 and keeps the popup up. Reporting that
            // as a bare "window never became invisible" timeout hides the app's own explanation —
            // which is exactly the text the negative scenarios need to assert against, and which
            // the NegativeValidation constants currently only guess at. Surface it.
            throw new IllegalStateException(
                    "Allocate Sites: OK did not close the popup — the app rejected the allocation. "
                    + "App message: [" + currentDialogMessage() + "]. Tagging Summary — actual="
                    + safeRead(this::getActualAmount) + ", tagged=" + safeRead(this::getTaggedAmount)
                    + ", pending=" + safeRead(this::getPendingAmount)
                    + ". A scenario that intends to leave an allocation incomplete should assert this "
                    + "message here and then clickCancel(), rather than expecting OK to succeed.",
                    stillOpen);
        }
    }

    /** Text of whatever message box is on screen, for the diagnostic above. Never throws. */
    private static String currentDialogMessage() {
        try {
            List<WebElement> msg = com.gsl.driver.DriverManager.getDriver()
                    .findElements(By.xpath("//*[@id='messagebox-1001-msg']"));
            if (!msg.isEmpty() && msg.get(0).isDisplayed()) {
                String t = msg.get(0).getText();
                if (t != null && !t.isBlank()) return t;
            }
            return "<no message box visible>";
        } catch (RuntimeException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }

    private static String safeRead(java.util.function.Supplier<BigDecimal> read) {
        try {
            BigDecimal v = read.get();
            return v == null ? "?" : v.toPlainString();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Discards the allocation and waits for the popup to close. */
    public void clickCancel() {
        SeleniumUtils.clickWithStaleRetry(CANCEL_BUTTON, WaitType.CLICKABLE, "AllocateSites_Cancel");
        JavascriptExecutorUtils.waitForExtJS();
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                .until(ExpectedConditions.invisibilityOfElementLocated(POPUP_WINDOW));
    }
}
