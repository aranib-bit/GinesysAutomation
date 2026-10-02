package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.reports.ExtentLogger;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    /** Confirmed live via user screenshot (2026-10-01) — the rendered title bar reads
     *  "Allocate Sites" exactly. */
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

    // Round 16 — same truncated-text idiom as PICKER_OK_BUTTON, for the picker's OWN Cancel
    // button (no picker-scoped Cancel locator existed before this — only the outer window's
    // CANCEL_BUTTON below, which closes the whole Allocate Sites dialog, not just this sub-picker).
    // Needed so a negative "confirm site not found" check can close the picker without selecting
    // anything.
    private static final By PICKER_CANCEL_BUTTON = By.xpath(
            "//span[contains(@id,'button') and contains(@id,'btnInnerEl') and text()='ancel']");

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

    // ── UI-contract validation ────────────────────────────────────────────────
    // Quick Site Allocate field labels — UNVERIFIED label-element locators (the existing
    // SITE_SEARCH_INPUT/AMOUNT_INPUT locators above anchor off the SAME "Site:"/"Amount:" span
    // text via byLabelSuffix, already proven live by the real business flow; the <label> ancestor
    // itself, used here for text+style checks, has not been independently confirmed).
    // Measured on the caption SPAN (like the main form's labels): the surrounding <label> is 11px,
    // the visible caption span is the 12px the spec expects.
    private static final By SITE_LABEL   = By.xpath(WIN_SCOPE + "//span[normalize-space()='Site:']");
    private static final By AMOUNT_LABEL = By.xpath(WIN_SCOPE + "//span[normalize-space()='Amount:']");

    // Allocated Site(s) grid column headers — UNVERIFIED, same ExtJS x-column-header idiom used
    // elsewhere in this codebase (e.g. AdjustmentEntry's own column-position xpaths), not yet
    // independently confirmed for this specific grid.
    // Located on the header TEXT span (span.x-column-header-text-inner: 12px, #333, transparent) — the
    // same element the main grid's headers are checked on; the outer header div is 11px / #000 /
    // #fafafa and is not what the spec describes.
    private static final By SITE_NAME_COLUMN_HEADER = By.xpath(
            WIN_SCOPE + "//div[contains(@class,'x-column-header')]//span[contains(@class,'x-column-header-text-inner')"
                    + " and normalize-space()='Site Name']");
    private static final By AMOUNT_COLUMN_HEADER = By.xpath(
            WIN_SCOPE + "//div[contains(@class,'x-column-header')]//span[contains(@class,'x-column-header-text-inner')"
                    + " and normalize-space()='Amount']");

    /** Full UI-contract sweep for this popup — Quick Site Allocate fields, the Allocated Site(s)
     *  grid's column headers, Tagging Summary's three readonly amount fields, and the OK/Cancel
     *  footer buttons. Composed into one call, same shape as {@code GeneralVoucherAdd
     *  #validateUiContract} — collects every {@link UiValidationFailure} and renders+asserts via
     *  {@link UiValidationReporter}, same "UI Validation" step-tree/Actual-Results-merge every
     *  other page in this framework already gets. */
    public void validateUiContract(UiValidationLevel level) {
        UiValidationReporter.renderAndAssert(collectUiContract(level));
    }

    // Popup title / section headers / footer values — UNVERIFIED placeholders (no live DOM yet);
    // a wrong locator reports one clean "NOT FOUND" row via UiComponentValidator's guard.
    private static final By WINDOW_TITLE_LOCATOR = By.xpath(
            WIN_SCOPE + "//div[contains(@class,'winTitleType2') and normalize-space()='" + WIN_TITLE + "']");
    // Grid footer is a summary ROW (not a toolbar): "Record Count: <b>N</b>" in the first text cell, the
    // total in the row's last cell. Record Count confirmed from live DOM; the total cell's inner
    // markup was cut off in the capture, so its text is read from the cell itself.
    private static final By GRID_FOOTER_RECORD_COUNT_VALUE = By.xpath(
            WIN_SCOPE + "//tr[contains(@class,'x-grid-row-summary')]"
                    + "//div[contains(@class,'x-grid-cell-inner')][contains(.,'Record Count')]//b");
    private static final By GRID_FOOTER_TOTAL_VALUE = By.xpath(
            WIN_SCOPE + "//tr[contains(@class,'x-grid-row-summary')]//td[contains(@class,'x-grid-cell-last')]");

    /** The popup's own chrome (labels, buttons, headers, section state, tagging field styles) —
     *  the original {@link #validateUiContract} body, returned instead of rendered so callers can
     *  nest it under a report node (see {@code GeneralVoucherAdd#validateRowUi}). */
    public List<UiValidationFailure> collectUiContract(UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();

        failures.addAll(UiComponentValidator.validateValue(WINDOW_TITLE_LOCATOR, "ALLOCATE_SITES_WINDOW_TITLE", WIN_TITLE));
        for (FormSection section : new FormSection[]{FormSection.ALLOCATE_SITES_QUICK_SITE_ALLOCATE,
                FormSection.ALLOCATE_SITES_ALLOCATED_SITES, FormSection.ALLOCATE_SITES_TAGGING_SUMMARY}) {
            failures.addAll(UiComponentValidator.validateSectionState(section, WIN_SCOPE, true));
        }

        failures.addAll(UiComponentValidator.validateLabel(SITE_LABEL, "ALLOCATE_SITES_SITE_LABEL",
                "Site:", FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(UiComponentValidator.validateLabel(AMOUNT_LABEL, "ALLOCATE_SITES_AMOUNT_LABEL",
                "Amount:", FieldStyleSpec.FIELD_LABEL, level));
        // The Add button's colours change on hover: park the mouse on the popup title so the RESTING
        // state is what gets measured.
        try {
            org.openqa.selenium.WebDriver driver = com.gsl.driver.DriverManager.getDriver();
            new org.openqa.selenium.interactions.Actions(driver)
                    .moveToElement(driver.findElement(WINDOW_TITLE_LOCATOR)).perform();
        } catch (RuntimeException ignored) {
            // best effort — worst case the hover colours are measured and reported as such
        }
        failures.addAll(UiComponentValidator.validateButton(ADD_BUTTON, "ALLOCATE_SITES_ADD_BUTTON",
                "Add", true, FieldStyleSpec.POPUP_ADD_BUTTON, level));

        failures.addAll(UiComponentValidator.validateGridHeader(SITE_NAME_COLUMN_HEADER,
                "ALLOCATE_SITES_GRID_HEADER_SITE_NAME", "Site Name", level));
        failures.addAll(UiComponentValidator.validateGridHeader(AMOUNT_COLUMN_HEADER,
                "ALLOCATE_SITES_GRID_HEADER_AMOUNT", "Amount", level));

        failures.addAll(UiComponentValidator.validateReadonlyAmount(ACTUAL_AMOUNT_FIELD,
                "ALLOCATE_SITES_ACTUAL_AMOUNT", level));
        failures.addAll(UiComponentValidator.validateReadonlyAmount(TAGGED_AMOUNT_FIELD,
                "ALLOCATE_SITES_TAGGED_AMOUNT", level));
        failures.addAll(UiComponentValidator.validateReadonlyAmount(PENDING_AMOUNT_FIELD,
                "ALLOCATE_SITES_PENDING_AMOUNT", level));

        failures.addAll(UiComponentValidator.validateButton(OK_BUTTON, "ALLOCATE_SITES_OK_BUTTON",
                "OK", true, level));
        // Cancel is the light/secondary button: live values (bg rgb(248,248,248), border
        // rgb(226,222,222), black 11px text) are identical to the page footer's Close button, so it
        // shares that spec; OK keeps the default (dark primary) spec, which it matches live.
        failures.addAll(UiComponentValidator.validateButton(CANCEL_BUTTON, "ALLOCATE_SITES_CANCEL_BUTTON",
                "Cancel", true, FieldStyleSpec.FOOTER_LIGHT_BUTTON, level));

        return failures;
    }

    /**
     * Value checks for one fill state of this popup, computed from the expected data (never read
     * back to derive expectations): every expected allocation appears in the grid with its amount,
     * the grid footer Record Count/total, and the three read-only Tagging Summary amounts.
     * {@code allocations} empty = the "before fill" state (Tagged 0.00, Pending = Actual).
     *
     * @param actualAmount the row amount being allocated (Tagging Summary "Actual Amount")
     */
    public List<UiValidationFailure> collectValuesUi(List<SiteAllocation> allocations, BigDecimal actualAmount,
            UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();
        BigDecimal tagged = BigDecimal.ZERO;
        int i = 1;
        for (SiteAllocation a : allocations) {
            tagged = tagged.add(a.getAmount());
            By row = By.xpath(String.format(ALLOCATED_ROW_BY_SITE_XPATH, a.getSiteName()));
            String component = "ALLOCATE_SITES_ALLOCATED_SITE_" + i;
            String actualText = SeleniumUtils.isElementPresent(row, 3)
                    ? SeleniumUtils.getAttribute(row, "textContent", component) : null;
            boolean nameOk = actualText != null;
            failures.add(new UiValidationFailure(component + "_NAME", "value", a.getSiteName(),
                    nameOk ? a.getSiteName() : "NOT FOUND in Allocated Site(s) grid",
                    nameOk ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            String expectedAmount = a.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            boolean amountOk = nameOk && actualText.replace(",", "").contains(expectedAmount);
            failures.add(new UiValidationFailure(component + "_AMOUNT", "value", expectedAmount,
                    nameOk ? (amountOk ? expectedAmount : actualText.trim()) : "—",
                    amountOk ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            i++;
        }
        failures.addAll(UiComponentValidator.validateAmountValue(GRID_FOOTER_RECORD_COUNT_VALUE,
                "ALLOCATE_SITES_GRID_FOOTER_RECORD_COUNT", String.valueOf(allocations.size()), null, null, level));
        failures.addAll(UiComponentValidator.validateAmountValue(GRID_FOOTER_TOTAL_VALUE,
                "ALLOCATE_SITES_GRID_FOOTER_TOTAL", tagged.toPlainString(), null, null, level));

        BigDecimal pending = actualAmount.subtract(tagged);
        failures.addAll(UiComponentValidator.validateAmountValue(ACTUAL_AMOUNT_FIELD, "ALLOCATE_SITES_ACTUAL_AMOUNT",
                actualAmount.toPlainString(), FieldStyleSpec.READONLY_AMOUNT, null, level));
        failures.addAll(UiComponentValidator.validateAmountValue(TAGGED_AMOUNT_FIELD, "ALLOCATE_SITES_TAGGED_AMOUNT",
                tagged.toPlainString(), FieldStyleSpec.READONLY_AMOUNT, null, level));
        failures.addAll(UiComponentValidator.validateAmountValue(PENDING_AMOUNT_FIELD, "ALLOCATE_SITES_PENDING_AMOUNT",
                pending.toPlainString(), FieldStyleSpec.READONLY_AMOUNT, null, level));
        for (By field : new By[]{ACTUAL_AMOUNT_FIELD, TAGGED_AMOUNT_FIELD, PENDING_AMOUNT_FIELD}) {
            String name = field == ACTUAL_AMOUNT_FIELD ? "ACTUAL" : field == TAGGED_AMOUNT_FIELD ? "TAGGED" : "PENDING";
            failures.addAll(UiComponentValidator.validateFieldState(field,
                    "ALLOCATE_SITES_" + name + "_AMOUNT", com.gsl.utils.ui.UiFieldState.DISABLED));
        }
        return failures;
    }

    private static String byLabelSuffix(String label) {
        return "//span[normalize-space()='" + label + "']/ancestor::label" +
               "/following-sibling::div//input[contains(@id,'inputEl')]";
    }

    /**
     * Returns the longest leading run of letters/digits/spaces in {@code siteName}, trimmed — the
     * text actually typed into the picker's Search field (see {@link #searchSite}'s Round 14
     * note). Falls back to the full name when that leading run is under 2 characters (e.g. a name
     * starting with a special character), since searching on an empty/near-empty term risks
     * matching everything (or, depending on the app's own minimum-length rule, nothing) — better
     * to risk the original failure mode there than trade it for a different one.
     */
    private static String searchSafeTerm(String siteName) {
        if (siteName == null) return "";
        int cut = 0;
        while (cut < siteName.length()
                && (Character.isLetterOrDigit(siteName.charAt(cut)) || siteName.charAt(cut) == ' ')) {
            cut++;
        }
        String safe = siteName.substring(0, cut).trim();
        return safe.length() >= 2 ? safe : siteName;
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
    /** Round 12 (§25): gap before re-confirming the Site field's committed value is STILL correct
     *  after it was already read as correct once — see {@link #searchSite} for why. */
    private static final long SITE_COMMIT_STABILITY_DELAY_MS = 400;

    /** Round 12 (§24): bounded attempts to (re-)drive the whole picker sequence before giving up —
     *  see {@link #searchSite} for why a longer poll alone cannot recover this failure. */
    private static final int MAX_SITE_SELECT_ATTEMPTS = 3;

    public AllocateSitesPopup searchSite(String siteName) {
        // REVERTED (Round 7): a previous version cleared this popup's own Site field here first,
        // and probed the picker row for `x-grid-item-selected` before pressing OK, on the theory
        // that a stale value from the PREVIOUS allocation on the same reused popup instance was
        // what verifySiteCommitted read back. Both additions were removed because the live
        // evidence refuted the theory and the change regressed two tests:
        //   * FIN-GV-ADD-COMBO-07 and -08 both PASSED in run 20260903_100559 (pre-change) and
        //     both FAILED in run 20260903_120345 (post-change) with the site-selection error.
        //   * The pre-clear's own read-back disproved its premise: the field was cleared and STILL
        //     read 'HAPPY SPROUTS-KP Road' across 17 polls. So the value is not a leftover this
        //     code can clear — either the app rewrites it, or (most likely) SeleniumUtils.clear()'s
        //     Ctrl+A+DELETE is a no-op against a read-only ExtJS picklist input, leaving only the
        //     extra interaction's side effects behind. Do not re-add a pre-clear here.
        //
        // Round 12 (§24): confirmed a DIFFERENT mechanism than the above — not a reused-instance
        // stale carry-over (this method's own caller, GeneralVoucherExpenseIncomeDetail
        // .clickSiteButton, always constructs a FRESH AllocateSitesPopup per row; a run with
        // exactly one row/one allocation still hit this). The wrong value was stable from the
        // very first read (not slowly converging), while the identical flow with the identical
        // data succeeded ~8 times immediately before/after in the same session — the same
        // async-reload-after-a-search-action race already proven real for the main voucher grid
        // (RC-11): this class's own comment below already documents "the grid reloads from the
        // server after each search," and a click that lands just ahead of that reload landing can
        // have its selection silently overwritten before OK commits it. Since a wrong value here
        // is stable, not converging, no amount of extra polling inside one attempt can recover it
        // (Round 1's poll-until-match is structurally unable to catch this class of failure) — only
        // redoing the whole selection after the race has already resolved can, exactly the
        // recovery `VoucherMainGridBase.clickCheckbox` already uses for the analogous grid-selection
        // race. Routed through the shared `SeleniumUtils.actAndConfirmStable` (§25) instead of a
        // bespoke retry loop here.
        //
        // clickWithStaleRetry/sendKeysWithStaleRetry, not the plain variants: this flow is the
        // most DOM-churning interaction in the whole GV UI (nested picker dialog opens, a
        // server-reloaded search grid, a result-row click, an OK click that closes the dialog and
        // updates the popup's own Site field) — see SeleniumUtils' own Javadoc on these methods.
        Runnable selectSite = () -> {
            SeleniumUtils.clickWithStaleRetry(SITE_PICKER_TRIGGER, WaitType.CLICKABLE, "AllocateSites_SiteSearchTrigger");
            JavascriptExecutorUtils.waitForExtJS();

            // Round 14 — bug fix: typing a site name containing '&' (e.g. "S&CM") into this Search
            // field returns ZERO results — confirmed live via screenshot (report run
            // 20260913_025254, FIN-GV-ADD-COMBO-08): the popup's own grid showed "No data to
            // display"/"Page 0 of 0" for the literal search term "S&CM". This is NOT an XPath
            // escaping problem — '&' has no special meaning inside an XPath string literal, and
            // the eventual failure was a clean "no such element" after the full wait, not a
            // selector-syntax error — the row never existed in the DOM because the APP's own
            // search request never returned it (almost certainly '&' being misread as a
            // query-string separator client-side, upstream of anything Selenium controls). Typing
            // only the longest safe leading run of letters/digits/spaces (stopping at the first
            // special character) sidesteps the broken search path entirely; the row-match xpath
            // below still uses the FULL, EXACT `siteName` — that part was always correct, since
            // it's matched against whatever the grid actually renders once the row exists at all.
            SeleniumUtils.sendKeysWithStaleRetry(PICKER_SEARCH_INPUT, WaitType.VISIBLE,
                    searchSafeTerm(siteName), "AllocateSites_SitePickerSearch");
            JavascriptExecutorUtils.waitForExtJS();

            // popupWaitSeconds, not shortWait: the grid reloads from the server after each search.
            By row = By.xpath(String.format(PICKER_ROW_XPATH, siteName));
            WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().popupWaitSeconds())
                    .until(ExpectedConditions.visibilityOfElementLocated(row));

            // Round 14 (§26): confirm the row is actually SELECTED in the grid's selection model
            // before trusting the click enough to press OK — measured root cause (see this
            // method's Javadoc history above): the picker opens on the FULL unfiltered list, which
            // can already contain a text match for `siteName`, so the visibility wait above can be
            // satisfied BEFORE the picker's own async server-reload (fired by typing, no
            // Enter/Tab) lands and replaces the grid — silently discarding a click that landed on
            // the pre-reload row. Measured across 22 passing vs 6 failing flows: the sendKeys-to-
            // click gap was 2-7s when it worked, 0-1s when it didn't — deterministic, not a rare
            // fluke. Re-click (fresh-resolved every attempt, not the same possibly-stale
            // reference) until the row is confirmed selected, instead of gambling on a longer wait.
            SeleniumUtils.actAndConfirmStable(
                    () -> SeleniumUtils.clickWithStaleRetry(row, WaitType.CLICKABLE, "AllocateSites_SiteRow_" + siteName),
                    () -> SeleniumUtils.isRowSelected(row),
                    150L, 3, "AllocateSites_SiteRowSelected_" + siteName);

            SeleniumUtils.clickWithStaleRetry(PICKER_OK_BUTTON, WaitType.CLICKABLE, "AllocateSites_SitePickerOK");
            JavascriptExecutorUtils.waitForExtJS();
        };
        BooleanSupplier siteCommitted = () -> {
            String actual = SeleniumUtils.getAttribute(SITE_SEARCH_INPUT, "value", "AllocateSites_SiteSearch");
            return actual != null && actual.trim().equalsIgnoreCase(siteName.trim());
        };

        boolean committed = SeleniumUtils.actAndConfirmStable(selectSite, siteCommitted,
                SITE_COMMIT_STABILITY_DELAY_MS, MAX_SITE_SELECT_ATTEMPTS, "AllocateSites_Site_" + siteName);

        if (!committed) {
            String actual = SeleniumUtils.getAttribute(SITE_SEARCH_INPUT, "value", "AllocateSites_SiteSearch");
            throw new IllegalStateException(
                    "Allocate Sites: failed to select site '" + siteName + "' — the Site field holds "
                    + (actual == null || actual.isBlank() ? "nothing" : "'" + actual + "'")
                    + " after " + MAX_SITE_SELECT_ATTEMPTS + " attempt(s). Check that the site exists "
                    + "and is selectable in this voucher's context (admsite.name), and that the "
                    + "picker's Name column still matches PICKER_ROW_XPATH. Continuing would hit the "
                    + "app's own \"Site selection is mandatory.\" error at Add, whose modal mask then "
                    + "blocks this popup's OK button — so the failure would surface two steps away "
                    + "from its cause.");
        }
        return this;
    }

    /**
     * Round 16 — negative-scenario counterpart to {@link #searchSite(String)}: opens the Site
     * picker, searches for {@code siteName}, and asserts NO matching row appears. Used by
     * {@code NEG_SITE_ALLOCATION_CROSS_OU} to confirm the app's Allocate Sites picker genuinely
     * returns zero results for a site belonging to a DIFFERENT Organizational Unit than the
     * voucher's own Owner Site (see {@code DbGeneralVoucherDataSource}'s Round 15/16 site
     * resolution) — i.e. that the app's own OU-scoping stays enforced, not just that our test
     * data avoids relying on it.
     *
     * <p>Uses a SHORT wait ({@link ConfigFactory#getWaitConfig()}'s {@code shortWaitSeconds()},
     * default 3s) rather than {@code popupWaitSeconds()} (30s, used by {@link #searchSite}'s own
     * "wait until the row appears" check) — a row that is never going to appear shouldn't block
     * the test for the full popup timeout.
     *
     * <p>Fails via {@code Assert.assertFalse} if a matching row unexpectedly DOES appear — "if
     * coming then it fails," per the scenario's own requirement — since that would mean either
     * the app's cross-OU scoping regressed, or this site is no longer genuinely cross-OU in the
     * current environment.
     *
     * <p>Closes the picker afterward via {@link #PICKER_CANCEL_BUTTON} (mirrors
     * {@link #PICKER_OK_BUTTON}'s truncated-text idiom) — leaves nothing selected, so the caller's
     * Expense/Income Detail row still has no Site allocated, matching the "leave the form
     * incomplete" convention {@code NEG_SUBLEDGER_REQUIRED_BEHAVIOR} already establishes for its
     * own negative scenario.
     */
    public AllocateSitesPopup assertSiteNotFound(String siteName) {
        SeleniumUtils.clickWithStaleRetry(SITE_PICKER_TRIGGER, WaitType.CLICKABLE, "AllocateSites_SiteSearchTrigger");
        JavascriptExecutorUtils.waitForExtJS();

        SeleniumUtils.sendKeysWithStaleRetry(PICKER_SEARCH_INPUT, WaitType.VISIBLE, siteName,
                "AllocateSites_SitePickerSearch_Negative");
        JavascriptExecutorUtils.waitForExtJS();

        By row = By.xpath(String.format(PICKER_ROW_XPATH, siteName));
        boolean rowAppeared = SeleniumUtils.isElementPresent(row,
                (int) ConfigFactory.getWaitConfig().shortWaitSeconds());
        org.testng.Assert.assertFalse(rowAppeared,
                "Allocate Sites: expected NO row for site '" + siteName + "' (belongs to a different "
                        + "Organizational Unit than the voucher's Owner Site), but the picker's grid "
                        + "returned a matching row — either the app's cross-OU site scoping has changed, "
                        + "or this site is no longer cross-OU in this environment.");

        SeleniumUtils.clickWithStaleRetry(PICKER_CANCEL_BUTTON, WaitType.CLICKABLE, "AllocateSites_SitePickerCancel");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /**
     * Why {@link #searchSite} verifies the committed value at all (history, kept for context):
     * the original version caught every exception from the option click, pressed TAB, and logged
     * "committed via TAB (boundlist auto-collapsed)" — an unconditional success message for what
     * was often a silent miss. When the combo has {@code forceSelection}, blurring on text that
     * matched no option makes ExtJS <i>clear the field</i>, so the run continued with an empty
     * Site, set the Amount, clicked Add, and surfaced as the app's own "Site selection is
     * mandatory." error two steps downstream — whose modal mask then blocked the popup's OK
     * button and burned the full overlay-retry budget. Run 20260807_110215 is that failure end to
     * end; the report cheerfully logged "AllocateSites_Option_CNSStore committed via TAB" while
     * the field sat empty. The site was never the problem there — {@code admsite} has
     * {@code CNSStore} (code 1102). Failing at the point of the miss, with the options that were
     * actually on offer, is the difference between a one-line diagnosis and a three-step
     * reconstruction. (Round 12: the verification itself, and its retry-on-failure, now lives
     * inline in {@link #searchSite} via {@code SeleniumUtils.actAndConfirmStable} — see that
     * method's Javadoc for why a longer poll alone can't recover the newer race it was extended
     * to cover.)
     */

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
