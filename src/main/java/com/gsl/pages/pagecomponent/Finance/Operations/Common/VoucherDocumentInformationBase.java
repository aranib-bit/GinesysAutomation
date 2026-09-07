package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.driver.DriverManager;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Common XPaths and fill logic shared by AR, AP, General, and Contra Voucher
 * document-information page components.
 *
 * All field locators are built in the constructor from the VoucherDomain enum,
 * which implements VoucherDocumentLabels. Each domain constant overrides only
 * the label methods that differ from the application-wide defaults. To change a
 * label for a specific voucher type, override the relevant method in VoucherDomain.
 *
 * Subclasses call fillCommonFields() and may interleave voucher-specific steps
 * (e.g. On Account radio for AP) using the protected setters exposed here.
 */
public abstract class VoucherDocumentInformationBase {

    protected static final String SCHEME_DOC_SELECTION = "//span[text()='%s']";

    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Locators built from VoucherDomain labels ──────────────────────────────

    private final By docScheme;
    private final By documentDate;
    private final By reference;
    private final By cashBankSearch;
    private final By cashBankDisplay;
    private final By remarks;

    private static final By ownerSiteDisplay = By.xpath(
            "//span[contains(@class,'x-form-item-label-text') and normalize-space()='Owner Site:']" +
            "/ancestor::div[contains(@class,'x-form-item')]" +
            "//input[contains(@id,'-inputEl')]");

    private static final By NARRATION_POPUP_HEADER = By.xpath(
            "//div[contains(@class,'x-boundlist-floating')]" +
            "[.//div[@class='headerBoundList'][.//b[normalize-space(text())='Narration Text']]]");

    private final CashBankParent parentPage;

    protected VoucherDocumentInformationBase(CashBankParent parentPage, VoucherDomain domain) {
        this.parentPage     = parentPage;
        this.docScheme      = byLabel(domain.getLabelVoucherNo(),
                                 "//input[contains(@id,'inputEl') and contains(@id,'combo')]");
        this.documentDate   = byLabel(domain.getLabelDate(),
                                 "//input[contains(@id,'" + domain.getDateFieldId() + "')" +
                                 " and contains(@data-componentid,'datefield-')]");
        this.reference      = byLabel(domain.getLabelReference(),
                                 "//input[contains(@id,'textfield-')" +
                                 " and contains(@data-componentid,'textfield-')" +
                                 " and contains(@aria-disabled,'false')]");
        this.cashBankSearch = byLabel(domain.getLabelCashBank(),
                                 " //div[contains(@id,'gslpicklistfieldextended-')" +
                                 " and contains(@id,'-trigger-picker')]");
        this.cashBankDisplay = byLabel(domain.getLabelCashBank(),
                                 "//input[contains(@id,'gslpicklistfieldextended-')" +
                                 " and contains(@id,'-inputEl')]");
        this.remarks        = byLabel(domain.getLabelRemarks(),
                                 "//input[contains(@id,'narration')" +
                                 " and contains(@data-componentid,'combo-')]");
    }

    private static By byLabel(String label, String widgetXPath) {
        return By.xpath("//span[text()='" + label + "']/ancestor::label" +
                        "/following-sibling::div" + widgetXPath);
    }

    // ── Template method — subclasses call this for the common section ─────────

    protected final void fillCommonFields(VoucherDocumentHeader docInfo) {
        setDocScheme(docInfo.getVoucherno());
        setDocumentDate(formatDate(docInfo.getDocumentdate()));
        setReference(docInfo.getReference());
        setCashBankField(docInfo.getCashbank());
    }

    // ── Protected setters — available to subclasses for custom ordering ───────

    protected void setDocScheme(String docscheme) {
        // No override supplied (null/blank) — leave whatever scheme the app defaulted to.
        // Mirrors fillCommonFields' Cash/Bank blank-guard: positive scenarios normally
        // don't pass a Voucher No./scheme override, so this is a no-op for them.
        //
        // IMPORTANT for Edit flows: this blank-guard is the ONLY branch that never touches the
        // UI. Passing the voucher's EXISTING scheme_docno here (to hit the "already matches,
        // skip" branch below) still requires the getAttribute() read below to succeed first,
        // which waits for the field's visibility — and on a live General Voucher Edit run that
        // wait timed out (Voucher No. is disabled/rendered differently in Edit mode than in
        // Create mode). Edit data sources must pass null here, not the existing value, to
        // guarantee this method never interacts with the field at all — see
        // ExcelGeneralVoucherEditDataSource/DbGeneralVoucherEditDataSource's own comments on
        // their DocumentInformation construction for the concrete fix.
        if (docscheme == null || docscheme.isBlank()) {
            Log.info("DOC_SCHEME override not supplied — leaving default value in place");
            return;
        }
        String current = SeleniumUtils.getAttribute(docScheme, "value", "DOC_SCHEME");
        if (current != null && current.equalsIgnoreCase(docscheme)) {
            Log.info("DOC_SCHEME is already '{}' — skipping update", docscheme);
            return;
        }
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.clear(docScheme, "DOC_SCHEME");
        SeleniumUtils.sendKeys(docScheme, WaitType.CLICKABLE, docscheme, "DOC_SCHEME");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(By.xpath(String.format(SCHEME_DOC_SELECTION, docscheme)),
                WaitType.CLICKABLE, "SCHEME_DOC_SELECTION");
    }

    protected void setDocumentDate(String date) {
        String current = SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
        if (current != null && current.equalsIgnoreCase(date)) {
            Log.info("DOCUMENT_DATE is already '{}' — skipping update", date);
            return;
        }
        Log.info("DOCUMENT_DATE current='{}', setting to '{}'", current, date);
        SeleniumUtils.clear(documentDate, "DOCUMENT_DATE");
        SeleniumUtils.sendKeys(documentDate, WaitType.CLICKABLE, date, "DOCUMENT_DATE");
    }

    /** Sets Cash/Bank — equals-guard first (Edit flows pass the voucher's own EXISTING, unchanged
     *  Cash/Bank through here; it's never actually re-selected on Edit, so without this check
     *  every Edit run unconditionally re-opened the picker and re-clicked the same value for no
     *  reason), then {@link SeleniumUtils#fillAndVerifyCommit} to confirm the pick actually landed
     *  (retrying on mismatch), same protection now given to every other field in this class.
     *  Blank/null is a deliberate no-op — T-03's NEG_BLANK_CASHBANK scenario leaves Cash/Bank
     *  empty so Save raises "Cash/Bank is required"; positive scenarios always pass a non-blank
     *  value. */
    protected void setCashBankField(String cashbank) {
        if (cashbank == null || cashbank.isBlank()) return;
        String current = getCashBank();
        if (current != null && current.equalsIgnoreCase(cashbank)) {
            Log.info("CASH_BANK is already '{}' — skipping update", cashbank);
            return;
        }
        SeleniumUtils.fillAndVerifyCommit("CASH_BANK", cashbank,
                () -> {
                    clickCashBankSearch();
                    selectCashBank(cashbank);
                },
                this::getCashBank);
    }

    /** Sets the Reference field via {@link SeleniumUtils#fillAndVerifyCommit} (type → Tab to force
     *  the ExtJS textfield to commit its typed DOM value into the bound model → read back to
     *  confirm, retrying on mismatch). */
    protected void setReference(String referenceValue) {
        if (referenceValue == null || referenceValue.isBlank()) return;
        SeleniumUtils.fillAndVerifyCommit("REFERENCE", referenceValue,
                () -> {
                    SeleniumUtils.sendKeys(reference, WaitType.CLICKABLE, referenceValue, "REFERENCE");
                    WaitUtils.untilVisible(reference).sendKeys(Keys.TAB);
                },
                () -> SeleniumUtils.getAttribute(reference, "value", "REFERENCE"));
    }

    /** Sets the Remarks (narration) field via {@link SeleniumUtils#fillAndVerifyCommit} — {@link
     *  #handleNarrationTextPopup} is the "commit" half of each attempt (handles the Narration Text
     *  picker popup's three branches), with the readback/retry happening after it returns. */
    protected void setRemarks(String remarksValue) {
        if (remarksValue == null || remarksValue.isBlank()) return;
        SeleniumUtils.fillAndVerifyCommit("REMARKS", remarksValue,
                () -> {
                    SeleniumUtils.sendKeys(remarks, WaitType.CLICKABLE, remarksValue, "REMARKS");
                    handleNarrationTextPopup(remarksValue);
                },
                () -> SeleniumUtils.getAttribute(remarks, "value", "REMARKS"));
    }

    /**
     * After typing in the Remarks (narration) combo, the app shows a "Narration Text"
     * picker grid with previously saved narrations.
     *
     * <ul>
     *   <li>If a row matching the typed text exists — click it to select the narration.</li>
     *   <li>Otherwise — press Tab to commit the typed text as a new entry and dismiss.</li>
     * </ul>
     *
     * After either branch the method waits for the popup to be fully invisible before
     * returning. This prevents the lingering overlay from interfering with subsequent
     * DOM interactions (e.g. native Sub-Ledger combo editor cell click), which causes
     * ExtJS alignTo() to compute zero geometry and throw a layout exception.
     */
    private void handleNarrationTextPopup(String remarksValue) {
        JavascriptExecutorUtils.waitForExtJS();
        // Round 18: a single ~3s miss isn't proof the popup is genuinely absent — confirmed live
        // (run 20260903_120345) that the SAME never-before-typed remarks text missed this exact
        // wait on one attempt and was found moments later on fillAndVerifyCommit's own retry —
        // i.e. the popup was still rendering, not gated on prior narration history existing. This
        // is the same debounced-render race already diagnosed and fixed once for the sibling
        // GL/Sub-Ledger boundlist (SeleniumUtils.clickBoundlistOptionAcrossPages, Round 6 RC-5/
        // Round 12 §23) — re-confirm with the same shared act-and-confirm-stable idiom before
        // concluding genuine absence, instead of a single un-retried wait. Genuine absence remains
        // a legitimate, expected outcome for GV Edit's deliberately fresh Faker-generated remarks
        // (no real narration history to match) — this only catches the case where the popup was
        // simply still rendering, not the case where there's truly nothing to show.
        boolean popupAppeared = SeleniumUtils.isElementPresent(NARRATION_POPUP_HEADER, 3)
                || SeleniumUtils.actAndConfirmStable(() -> {},
                        () -> SeleniumUtils.isElementPresent(NARRATION_POPUP_HEADER, 2),
                        2000L, 2, "NARRATION_POPUP");

        if (!popupAppeared) {
            // No popup means no row is available to select — the same situation the "no match"
            // branch below handles by pressing Tab to force the combo to commit the typed text as
            // its real bound value. This branch used to just log and return without doing that,
            // which left the field showing the typed text but never actually committed it — the
            // combo's real submitted value stayed whatever it was before this call (confirmed live:
            // nartext intermittently saved as blank on Save even though the typed text was visible
            // on screen and identical to the field's own pre-existing value). Pressing Tab here
            // mirrors the no-match branch's already-established fix for exactly this situation.
            WaitUtils.untilVisible(remarks).sendKeys(Keys.TAB);
            ExtentLogger.info("[REMARKS] Narration Text popup did not appear — pressed Tab to commit typed value '" + remarksValue + "'");
            return;
        }

        boolean hasMatch = !DriverManager.getDriver()
                .findElements(matchedNarrationRow(remarksValue)).isEmpty();

        if (hasMatch) {
            SeleniumUtils.click(matchedNarrationRow(remarksValue), WaitType.CLICKABLE, "NARRATION_ROW");
            ExtentLogger.info("[REMARKS] Narration Text popup — clicked matched row for '" + remarksValue + "'");
        } else {
            WaitUtils.untilVisible(remarks).sendKeys(Keys.TAB);
            ExtentLogger.info("[REMARKS] Narration Text popup — no match, pressed Tab for '" + remarksValue + "'");
        }

        WaitUtils.untilInvisible(NARRATION_POPUP_HEADER);
        JavascriptExecutorUtils.waitForExtJS();
    }

    private static By matchedNarrationRow(String text) {
        return By.xpath(
                "//div[contains(@class,'x-boundlist-floating')]" +
                "[.//b[normalize-space(text())='Narration Text']]" +
                "//td[@data-qtip='" + text + "']");
    }

    protected void clickCashBankSearch() {
        SeleniumUtils.click(cashBankSearch, WaitType.CLICKABLE, "CASH_BANK_SEARCH");
    }

    protected void selectCashBank(String cashbankName) {
        new CashBankPopup(parentPage).selectCashBank(cashbankName);
    }

    protected static String formatDate(LocalDate date) {
        Objects.requireNonNull(date,
                "documentdate is null — check the Excel 'Date' column format or the DataProvider source");
        return date.format(UI_DATE_FORMAT);
    }

    public String getOwnerSite() {
        return SeleniumUtils.getAttribute(ownerSiteDisplay, "value", "OWNER_SITE_DISPLAY");
    }

    public String getDocumentDate() {
        return SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
    }

    public String getDocScheme() {
        return SeleniumUtils.getAttribute(docScheme, "value", "DOC_SCHEME");
    }

    public String getReference() {
        return SeleniumUtils.getAttribute(reference, "value", "REFERENCE");
    }

    public String getCashBank() {
        return SeleniumUtils.getAttribute(cashBankDisplay, "value", "CASH_BANK_DISPLAY");
    }

    public String getRemarks() {
        return SeleniumUtils.getAttribute(remarks, "value", "REMARKS");
    }
}
