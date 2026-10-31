package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /**
     * Option row of the Voucher No. (doc scheme) dropdown — a grid-style boundlist
     * ({@code x-boundlist-floating → x-boundlist-item → td per column: Scheme Name | Sample No.}).
     * Scoped to the visible floating list: a bare {@code //span[text()='General Voucher']} also matched
     * the left-nav tree node of the same name on qaprod (scheme name == menu label) and the click was
     * intercepted. Same row shape/convention as {@code GeneralVoucherExpenseIncomeDetail.gridBoundlistOption}.
     */
    protected static final String SCHEME_DOC_SELECTION =
            "//div[contains(@class,'x-boundlist-floating') and not(contains(@style,'display: none'))]"
            + "//div[contains(@class,'x-boundlist-item') and .//td[@data-qtip=\"%1$s\" or normalize-space(.)=\"%1$s\"]]";

    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Locators built from VoucherDomain labels ──────────────────────────────

    private final By docScheme;
    private final By documentDate;
    private final By reference;
    private final By cashBankSearch;
    private final By cashBankDisplay;
    private final By remarks;

    // ── Label text kept around for UI-contract validation (validate*Ui methods below) ─────────
    private static final String OWNER_SITE_LABEL_TEXT = "Owner Site:";
    private final String cashBankLabelText;
    private final String dateLabelText;
    private final String remarksLabelText;

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
        this.cashBankLabelText = domain.getLabelCashBank();
        this.dateLabelText     = domain.getLabelDate();
        this.remarksLabelText  = domain.getLabelRemarks();
        this.voucherNoLabelText = domain.getLabelVoucherNo();
        this.referenceLabelText = domain.getLabelReference();
    }

    private static By byLabel(String label, String widgetXPath) {
        return By.xpath("//span[text()='" + label + "']/ancestor::label" +
                        "/following-sibling::div" + widgetXPath);
    }

    /** The label span itself, e.g. for {@link #OWNER_SITE_LABEL_TEXT}/{@link #cashBankLabelText}. */
    private static By labelLocator(String label) {
        return By.xpath("//span[text()='" + label + "']");
    }

    /** UNVERIFIED — placeholder locator, pending live DOM confirmation (same convention already
     *  used by {@code GeneralVoucherDocumentInformation}'s own UNVERIFIED locators): assumes the
     *  required-field marker renders as a sibling span immediately after the label span. Confirm
     *  against the live app and adjust once the true DOM shape is known. */
    private static By requiredMarkerLocator(String label) {
        return By.xpath("//span[text()='" + label + "']/following-sibling::span[normalize-space()='*']");
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
        // ! IMPORTANT for Edit flows: this blank-guard is the ONLY branch that never touches the
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
        // waitForExtJS() before interacting — same guard setDocScheme (above) already applies
        // before its own clear()/sendKeys() pair. Added after a 3rd live "element not interactable"
        // hit on this exact field (runs 20260927_190439, 20260928_111303, 20260929_125107), always
        // within ~2s of the Edit window opening — the field passes Selenium's own visibility check
        // (waitUntilElementVisible, used by getAttribute() just above) but isn't yet truly
        // interactable, consistent with the Add/Edit window still settling. Not 100%-confirmed to
        // close the gap (waitForExtJS checks Ext.isReady/Ajax/masks, not a CSS slide-in animation
        // specifically) — if it recurs, capture a screenshot/DOM at the exact failure moment next.
        JavascriptExecutorUtils.waitForExtJS();
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
                    failIfUnexpectedPostDatedCashDialog(cashbank);
                },
                this::getCashBank);
    }

    private static final String POST_DATED_CASH_FRAGMENT = "post dated entries are not allowed for cash";

    /**
     * Stops at once, with the app's own words and a screenshot of the open dialog, when selecting a Cash
     * ledger raises "Post dated entries are not allowed for cash transactions" although the Document Date
     * is NOT in the future. Left alone, the dialog's mask blocks the Cash/Bank picker click on the retry
     * below and the test dies ~2 minutes later as an {@code ElementClickInterceptedException} that hides
     * the real reason (runs 20261031: AP/AR/GV cash scenarios between 01:39 and 05:09 IST).
     *
     * <p>Deliberately no date workaround: in those runs the app's own default Document Date already read
     * the test's date, yet its post-dated check still fired, so the inconsistency is the app's to explain.
     * A genuinely future Document Date (the NEG_CASH_POST_DATED scenario) is left to its own flow.
     */
    private void failIfUnexpectedPostDatedCashDialog(String cashbank) {
        com.gsl.popup.PopupResult popup = com.gsl.popup.PopupHandler.detectPopup();
        if (popup == null || popup.message() == null
                || !popup.message().toLowerCase().contains(POST_DATED_CASH_FRAGMENT)) {
            return;
        }
        String docDate = SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
        try {
            LocalDate parsed = LocalDate.parse(docDate.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (parsed.isAfter(LocalDate.now())) return;
        } catch (RuntimeException unparsable) {
            // Cannot tell whether the date is in the future — treat as the unexplained case below.
        }
        String msg = "App rejected Cash/Bank '" + cashbank + "' as post-dated although Document Date is '"
                + docDate + "' (test machine date " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ", " + java.time.ZonedDateTime.now().getZone() + ", " + java.time.LocalTime.now().withNano(0)
                + "). App message: \"" + popup.message() + "\". Not retried: the dialog's mask would block the "
                + "next click. If this only happens shortly after midnight IST, the app's post-dated check and "
                + "its default Document Date use different clocks.";
        com.gsl.reports.ExtentLogger.fail(msg);   // screenshot is taken now, dialog still open
        throw new IllegalStateException(msg);
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
                    settleTypedRemarks(remarksValue);
                    handleNarrationTextPopup(remarksValue);
                },
                () -> SeleniumUtils.getAttribute(remarks, "value", "REMARKS"));
    }

    /**
     * Makes sure the Remarks input still holds exactly {@code remarksValue} once the narration
     * combo's per-keystroke query traffic has gone idle — BEFORE {@link #handleNarrationTextPopup}
     * presses Tab and commits whatever is in the input. Confirmed live (FIN-GV-EDIT-HEADER-02, run
     * 20261012_142236; HEADER-01, run 20261008_121133): with a slow query the input read back
     * 'Synergizeebsite', then 'Syner', then 'S' — characters lost mid-typing and a late response
     * overwriting the input — and Tab committed the damaged value each time. Retypes (native
     * clear + sendKeys) while the value is wrong; logs a read-only probe of the combo's config
     * and loading state the first time it is, so the mechanism is visible in the logs. Never
     * throws: {@code fillAndVerifyCommit}'s readback stays the outer guard.
     */
    private void settleTypedRemarks(String remarksValue) {
        int maxAttempts = ConfigFactory.getWaitConfig().comboTypeSettleMaxAttempts();
        long recheckMs = ConfigFactory.getWaitConfig().selectDocStabilityRecheckDelayMs();
        boolean probed = false;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            JavascriptExecutorUtils.waitForExtJS();
            boolean held = SeleniumUtils.actAndConfirmStable(() -> {},
                    () -> holdsTypedText(readRemarksQuietly(), remarksValue), recheckMs, 1, "REMARKS_TYPED_VALUE");
            if (held) {
                if (attempt > 1) {
                    ReportLogger.fieldEntry("REMARKS held '" + remarksValue + "' after retype " + (attempt - 1));
                }
                return;
            }
            if (!probed) {
                Log.warn("[VoucherDocumentInformationBase] REMARKS shows '{}' after typing '{}' and "
                        + "query idle — combo state: {}", readRemarksQuietly(), remarksValue, probeRemarksCombo());
                probed = true;
            }
            if (attempt < maxAttempts) {
                Log.warn("[VoucherDocumentInformationBase] REMARKS typed value lost (attempt {}/{}) — retyping",
                        attempt, maxAttempts);
                SeleniumUtils.sendKeys(remarks, WaitType.CLICKABLE, remarksValue, "REMARKS");
            }
        }
        Log.warn("[VoucherDocumentInformationBase] REMARKS still not holding '{}' after {} type attempts "
                + "(shows '{}') — continuing to commit; the commit readback decides", remarksValue,
                maxAttempts, readRemarksQuietly());
    }

    /** A typeAhead completion may append a suffix after the typed text (the popup branch handles
     *  that) — only lost or overwritten typed characters count as "not held". */
    private static boolean holdsTypedText(String actual, String typed) {
        return actual != null && actual.startsWith(typed);
    }

    private String readRemarksQuietly() {
        try {
            return DriverManager.getDriver().findElement(remarks).getAttribute("value");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Read-only snapshot of the Remarks combo component (no state is changed). */
    private String probeRemarksCombo() {
        try {
            WebElement input = DriverManager.getDriver().findElement(remarks);
            Object result = ((org.openqa.selenium.JavascriptExecutor) DriverManager.getDriver()).executeScript(
                    "try {"
                    // The input's own id isn't always '<cmp>-inputEl' (live: plain 'narration'),
                    // so resolve the owning component from the DOM node instead.
                    + " var el = arguments[0];"
                    + " var c = window.Ext && ((Ext.Component.from && Ext.Component.from(el))"
                    + "   || (Ext.Component.fromElement && Ext.Component.fromElement(el))"
                    + "   || Ext.getCmp((el.id || '').replace(/-inputEl$/, '')));"
                    + " if (!c) return 'component not found for ' + el.id;"
                    + " var s = c.getStore ? c.getStore() : null;"
                    + " return JSON.stringify({xtype: c.xtype, queryMode: c.queryMode, minChars: c.minChars,"
                    + "  queryDelay: c.queryDelay, typeAhead: c.typeAhead, selectOnTab: c.selectOnTab,"
                    + "  forceSelection: c.forceSelection, autoSelect: c.autoSelect,"
                    + "  rawValue: c.getRawValue ? c.getRawValue() : null, lastQuery: c.lastQuery,"
                    + "  storeLoading: s ? !!s.isLoading() : null, storeCount: s ? s.getCount() : null,"
                    + "  ajaxLoading: Ext.Ajax ? Ext.Ajax.isLoading() : null,"
                    + "  pickerVisible: c.picker ? c.picker.isVisible() : false});"
                    + "} catch (e) { return 'probe-error: ' + e.message; }", input);
            return String.valueOf(result);
        } catch (RuntimeException e) {
            return "probe-unavailable: " + e.getClass().getSimpleName();
        }
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
            ReportLogger.fieldEntry("Narration Text popup did not appear — pressed Tab to commit typed value '" + remarksValue + "'");
            return;
        }

        boolean hasMatch = !DriverManager.getDriver()
                .findElements(matchedNarrationRow(remarksValue)).isEmpty();

        if (hasMatch) {
            SeleniumUtils.click(matchedNarrationRow(remarksValue), WaitType.CLICKABLE, "NARRATION_ROW");
            ReportLogger.fieldEntry("Narration Text popup — clicked matched row for '" + remarksValue + "'");
        } else {
            WaitUtils.untilVisible(remarks).sendKeys(Keys.TAB);
            ReportLogger.fieldEntry("Narration Text popup — no match, pressed Tab for '" + remarksValue + "'");
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

    // ── UI-contract validation ──────────────────────────────────────────────────────────────
    //
    // Placed on this BASE class, not on GeneralVoucherDocumentInformation, because the locators
    // above are already shared by every VoucherDocumentInformationBase subclass (AR/AP/GV) — that
    // placement is what makes AR/AP inherit working UI validation for these fields with no new
    // code once they wire up their own UI-validation test class (see the framework plan's "Reuse
    // path for AR/AP/Contra" section).

    /** Confirmed live (2026-10-01) as a {@code gsl_TitlePnlType1} readonly displayfield div — the
     *  same component shape already documented on {@code GeneralVoucherDocumentInformation
     *  #SAVED_DOCUMENT_NO_DISPLAY} ("Voucher No." field). Identical text across GV/AR/AP's own
     *  Add/Edit forms (common section, not a per-domain business difference) — kept here, not on
     *  any one domain's subclass, for the same "AR/AP inherit it for free" reason as the rest of
     *  this class. Each domain's OWN second section ("Expense/Income Detail" for GV, "Sub Ledger
     *  Information" for AR/AP) has different text and stays on that domain's own Add page class. */
    private static final By DOCUMENT_INFORMATION_SECTION_HEADER = By.xpath(
            "//div[contains(@class,'gsl_TitlePnlType1') and normalize-space(.)='" + FormSection.DOCUMENT_INFORMATION.title() + "']");

    /** UI-contract check for the common "Document Information" section header. */
    public List<UiValidationFailure> validateDocumentInformationSectionHeaderUi(UiValidationLevel level) {
        return UiComponentValidator.validateSectionHeader(
                DOCUMENT_INFORMATION_SECTION_HEADER, "SECTION_HEADER_DOCUMENT_INFORMATION",
                FormSection.DOCUMENT_INFORMATION.title(), level);
    }

    /** Full UI contract for Owner Site: label text+style, required-marker, input style+state. */
    public List<UiValidationFailure> validateOwnerSiteUi(UiValidationLevel level, UiFieldState state, boolean required) {
        return UiComponentValidator.validateField("OWNER_SITE",
                labelLocator(OWNER_SITE_LABEL_TEXT), OWNER_SITE_LABEL_TEXT,
                requiredMarkerLocator(OWNER_SITE_LABEL_TEXT), required,
                ownerSiteDisplay, FieldStyleSpec.LOOKUP_INPUT,
                state, level);
    }

    /** Owner Site is EDITABLE only on Add, {@code DISABLED} on Edit — there is no setter for it
     *  anywhere in this codebase ({@link #getOwnerSite()} is the only accessor), confirming it's
     *  permanently non-interactive once a voucher exists. Common across GV/AR/AP (same shared
     *  locator/component above), not a per-domain business difference — confirmed live for GV
     *  (2026-10-01); AR/AP inherit this rule automatically rather than each needing their own
     *  override logic. */
    public List<UiValidationFailure> validateOwnerSiteUi(UiValidationLevel level, boolean isEditMode, boolean required) {
        return validateOwnerSiteUi(level, isEditMode ? UiFieldState.DISABLED : UiFieldState.EDITABLE, required);
    }

    /** Full UI contract for Cash/Bank: label text+style, required-marker, input style+state. */
    public List<UiValidationFailure> validateCashBankUi(UiValidationLevel level, UiFieldState state, boolean required) {
        return UiComponentValidator.validateField("CASH_BANK",
                labelLocator(cashBankLabelText), cashBankLabelText,
                requiredMarkerLocator(cashBankLabelText), required,
                cashBankDisplay, FieldStyleSpec.LOOKUP_INPUT,
                state, level);
    }

    /** Full UI contract for Date: label text+style, required-marker, input style+state. */
    public List<UiValidationFailure> validateDateUi(UiValidationLevel level, UiFieldState state, boolean required) {
        return UiComponentValidator.validateField("DATE",
                labelLocator(dateLabelText), dateLabelText,
                requiredMarkerLocator(dateLabelText), required,
                documentDate, FieldStyleSpec.DATE_INPUT,
                state, level);
    }

    /** Full UI contract for Remarks: label text+style, required-marker, input style+state. */
    public List<UiValidationFailure> validateRemarksUi(UiValidationLevel level, UiFieldState state, boolean required) {
        return UiComponentValidator.validateField("REMARKS",
                labelLocator(remarksLabelText), remarksLabelText,
                requiredMarkerLocator(remarksLabelText), required,
                remarks, FieldStyleSpec.TEXT_INPUT,
                state, level);
    }

    /** Remarks is {@code DISABLED} on a freshly-opened, nothing-yet-filled Add form (ExtJS enables
     *  it only once Owner Site/Cash-Bank are chosen) and follows {@code state} once real data
     *  exists (Edit, or Add after prerequisites are filled) — confirmed live for GV (2026-10-01,
     *  user-confirmed: "Remarks in add mode enabled after cash/bank is selected"), for AR
     *  (2026-10-01 live run, 0 failures), and for AP (2026-10-01, user-supplied screenshot + DOM —
     *  see {@code UiComponentValidator#validateFieldState}'s Javadoc for why an earlier AP live run
     *  misread this as EDITABLE). Common to all three — AR/AP inherit this rule automatically
     *  rather than each needing their own override logic. */
    public List<UiValidationFailure> validateRemarksUi(UiValidationLevel level, UiFieldState state,
            boolean isEditMode, boolean required) {
        return validateRemarksUi(level, isEditMode ? state : UiFieldState.DISABLED, required);
    }

    // ── Voucher No. / Reference (common to every voucher) ───────────────────────────────────

    private String voucherNoLabelText;
    private String referenceLabelText;

    /** Voucher No.: label + element type + state. On Add it is the scheme picker (EDITABLE); on Edit the
     *  saved number is a read-only display. */
    public List<UiValidationFailure> validateVoucherNoUi(UiValidationLevel level, boolean isEditMode, boolean required) {
        return validateVoucherNoUi(level, isEditMode, required, null);
    }

    /**
     * As above; on Edit {@code expectedSavedNo} (the voucher that was opened) is compared with the number the
     * read-only display shows. An Edit form carries THREE "Voucher No.:" elements - two permanently hidden (lookup
     * combo, manual-scheme textfield) and the visible read-only display - so every lookup here resolves to the
     * first displayed one (see {@code UiComponentValidator}). The saved number is a read-only display, so it shows
     * no required marker and is reported as {@code VOUCHER_NO_SAVED} (its own baseline rows).
     */
    public List<UiValidationFailure> validateVoucherNoUi(UiValidationLevel level, boolean isEditMode, boolean required,
            String expectedSavedNo) {
        String label = voucherNoLabelText;
        if (!isEditMode) {
            return UiComponentValidator.validateField("VOUCHER_NO",
                    labelLocator(label), label, requiredMarkerLocator(label), required,
                    docScheme, FieldStyleSpec.LOOKUP_INPUT, UiFieldState.EDITABLE, level);
        }
        By display = By.xpath("//span[contains(@class,'x-form-item-label-text') and normalize-space()='" + label
                + "']/ancestor::div[contains(@class,'x-form-item')]//*[contains(@id,'-inputEl')]");
        List<UiValidationFailure> r = new java.util.ArrayList<>(UiComponentValidator.validateLabel(
                labelLocator(label), "VOUCHER_NO_SAVED", label, FieldStyleSpec.FIELD_LABEL, level));
        r.addAll(UiComponentValidator.validateRequiredMarker(requiredMarkerLocator(label), "VOUCHER_NO_SAVED", false, level));
        r.addAll(UiComponentValidator.validateElementTypeBaselined(display, "VOUCHER_NO_SAVED"));
        r.addAll(UiComponentValidator.validateNotEditable(display, "VOUCHER_NO_SAVED"));
        String shown = UiComponentValidator.readValue(display);
        r.addAll(expectedSavedNo == null
                ? List.of(new UiValidationFailure("VOUCHER_NO_SAVED", "value", "(recorded)", shown.isEmpty() ? "(empty)" : shown,
                        UiValidationFailure.Status.PASS))
                : UiComponentValidator.validateEquals("VOUCHER_NO_SAVED", "value (opened voucher)", expectedSavedNo, shown));
        r.addAll(UiComponentValidator.validateInputStyle(display, "VOUCHER_NO_SAVED", FieldStyleSpec.READONLY_AMOUNT,
                UiFieldState.READONLY, level));
        return r;
    }

    /** Reference: label + required marker + element type + style + state (a free-text field, EDITABLE on Add). */
    public List<UiValidationFailure> validateReferenceUi(UiValidationLevel level, UiFieldState state, boolean required) {
        String label = referenceLabelText;
        return UiComponentValidator.validateField("REFERENCE",
                labelLocator(label), label, requiredMarkerLocator(label), required,
                reference, FieldStyleSpec.TEXT_INPUT, state, level);
    }

    /** Snapshot of every Document Information field's state/value — call once before and once after the
     *  header is filled, then pass both to {@link #validateFieldsBeforeAfterUi}. */
    public java.util.Map<String, UiComponentValidator.FieldSnapshot> captureFieldSnapshots() {
        java.util.Map<String, UiComponentValidator.FieldSnapshot> m = new java.util.LinkedHashMap<>();
        m.put("VOUCHER_NO", UiComponentValidator.captureField(docScheme));
        m.put("REFERENCE", UiComponentValidator.captureField(reference));
        m.put("OWNER_SITE", UiComponentValidator.captureField(ownerSiteDisplay));
        m.put("CASH_BANK", UiComponentValidator.captureField(cashBankDisplay));
        m.put("DATE", UiComponentValidator.captureField(documentDate));
        m.put("REMARKS", UiComponentValidator.captureField(remarks));
        return m;
    }

    /** Before/after rows for each captured field. Expected states come from the Excel baseline
     *  ({@code EnabledBeforeFill}/{@code EnabledAfterFill}); values are recorded either way. */
    public List<UiValidationFailure> validateFieldsBeforeAfterUi(
            java.util.Map<String, UiComponentValidator.FieldSnapshot> before,
            java.util.Map<String, UiComponentValidator.FieldSnapshot> after) {
        List<UiValidationFailure> r = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, UiComponentValidator.FieldSnapshot> e : before.entrySet()) {
            UiComponentValidator.FieldSnapshot a = after.get(e.getKey());
            if (a == null) continue;
            if (!e.getValue().present() && !a.present()) {
                r.addAll(UiComponentValidator.skip(e.getKey(), "element presence", "validated if shown", "not displayed"));
                continue;
            }
            r.addAll(UiComponentValidator.validateFieldBeforeAfterBaselined(e.getKey(), e.getValue(), a, null, null));
        }
        return r;
    }
}
