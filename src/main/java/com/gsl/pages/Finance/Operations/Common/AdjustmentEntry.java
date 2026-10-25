package com.gsl.pages.Finance.Operations.Common;

import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Operations.Common.AdjustmentPopups;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.popup.PopupMonitor;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AdjustmentEntry {

    private final AdjustmentEntryParent parent;
    private final VoucherDomain voucherDomain;

    public AdjustmentEntry(AdjustmentEntryParent parent) {
        this.parent = parent;
        this.voucherDomain = parent.voucherDomain();
    }

    // ===========================
    // Locators
    // ===========================

    // CANCEL_BUTTON → CommonButton.CANCEL.locator() (plain Cancel, no positional constraint)
    // Adjusted Document menu options → AdjustmentPopups.<X>.menuOptionLocator()
    private static final class Loc {
        static final By ADJUSTED_DOCUMENT_MENU =
                By.xpath("//div[text()='Adjusted Document']//following::*[contains(@class,'mdi mdi-20px mdi-dots-horizontal')]");

        // Scoped OK — the app's own shell is always `x-window-maximized`; any floating dialog
        // (the Adjustment Entry window itself for a plain FIFO Set-Off inline confirmation, or a
        // nested popup such as Reference Site wise FIFO set-off) renders as a non-maximized
        // x-window.
        //
        // @aria-hidden='false' is the load-bearing condition: Ext.Msg is a singleton ExtJS hides
        // (never destroys) between uses, so once any message box has appeared anywhere in the
        // session, its own "OK" button lingers in the DOM as a permanently-hidden match for this
        // xpath — with aria-hidden="true", vs. "false" on the real, currently-shown button.
        // (An earlier version of this locator tried excluding by ancestor CLASS instead —
        // `//div[contains(@class,'x-window') and not(contains(@class,'x-hidden-offsets'))]//a[...]`
        // — but contains(@class,'x-window') substring-matches inner structural wrapper divs like
        // `x-window-body` that don't carry x-hidden-offsets themselves even though their own outer
        // ancestor does, so the exclusion never actually reached the div that mattered. aria-hidden
        // is checked directly on the candidate <a>, so it isn't vulnerable to that same loophole.
        // ancestor:: (not the earlier //div[...]//a[...] structure) is used for the same reason —
        // it walks every real ancestor rather than whichever div an outer predicate binds to first.
        static final By OK_BUTTON = By.xpath(
                "//a[@aria-hidden='false' and contains(normalize-space(),'OK')" +
                " and not(ancestor::div[contains(@class,'x-window-maximized')])]");

        static final By SHOW_REF_SITE_SUMMARY =
                By.xpath("//span[normalize-space()='Show Reference Site Summary' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

        static final By TRANSACTION_AMOUNT =
                By.xpath("//span[text()='Transaction Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        static final By ADJUSTED_AMOUNT =
                By.xpath("//span[text()='Adjusted Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        static final By PENDING_TO_ADJUST =
                By.xpath("//span[text()='Pending to Adjust:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        // Shared row+column prefix: locates the <td> of the 'Adjusted Amount' column
        // in the grid row containing the given document number.
        static final String ADJUSTED_AMOUNT_CELL_BASE_XPATH =
                "//div[normalize-space()='%s']/ancestor::tr" +
                "//td[count(//div[contains(@class,'x-column-header')]" +
                "[.//span[normalize-space()='" + CommonVoucherGridColumnHeader.ADJUSTED_AMOUNT.headerText() + "']]" +
                "/preceding-sibling::div[contains(@class,'x-column-header')]) + 1]";

        static final String ADJUSTED_AMOUNT_BY_DOC_XPATH = ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//div";

        // Same row+column shape as ADJUSTED_AMOUNT_CELL_BASE_XPATH, for the per-document
        // "Pending Amount" column. NOTE: header text 'Pending Amount' is taken from the footer's
        // own label wording (the grid column itself was truncated in the user's screenshots) —
        // verify on first live run and adjust here if the column is named differently.
        static final String PENDING_AMOUNT_BY_DOC_XPATH =
                "//div[normalize-space()='%s']/ancestor::tr" +
                "//td[count(//div[contains(@class,'x-column-header')]" +
                "[.//span[normalize-space()='Pending Amount']]" +
                "/preceding-sibling::div[contains(@class,'x-column-header')]) + 1]//div";

        // The singleton ExtJS message box lingers hidden in the DOM with stale text — the
        // Confirmation is only "showing" when this element is displayed AND carries its text.
        static final By MESSAGE_BOX_TEXT = By.xpath("//*[@id='messagebox-1001-msg']");

        // After a double-click activates the ExtJS inline editor, the editor renders as a
        // floating x-editor overlay — not inside the cell's DOM subtree. Target it globally.
        static final By ACTIVE_EDITOR_INPUT = By.xpath(
                "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]//input"
        );
    }


    // ===========================
    // Actions
    // ===========================

    public AdjustmentEntry openAdjustedDocumentMenu() {
        SeleniumUtils.click(Loc.ADJUSTED_DOCUMENT_MENU, WaitType.CLICKABLE, "Adjusted Document Menu");
        return this;
    }

    public SelectDocumentPopup selectDocument() {
        SeleniumUtils.click(AdjustmentPopups.SELECT_DOCUMENT.menuOptionLocator(),
                WaitType.CLICKABLE, "Select Document Option");
        JavascriptExecutorUtils.waitForExtJS();
        Log.warn("AdjustmentEntry.selectDocument: waitForExtJS done — checking loader mask [{}]",
                FrameworkConstants.getSelectDocGridLoader());
        boolean loaderFoundInitially = !DriverManager.getDriver()
                .findElements(FrameworkConstants.getSelectDocGridLoader()).isEmpty();
        Log.warn("AdjustmentEntry.selectDocument: loader mask present={}", loaderFoundInitially);
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics BEFORE loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        int maxRetries = ConfigFactory.getWaitConfig().selectDocLoaderMaxRetries();
        long loaderWaitStartMs = System.currentTimeMillis();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                        FrameworkConstants.getSelectDocGridLoader()));
                long elapsedMs = System.currentTimeMillis() - loaderWaitStartMs;
                // Record into the wait buffer regardless: this loop calls WaitUtils.webDriverWait
                // directly, bypassing recordIfEnabled, so its time never reached Wait Analysis.
                com.gsl.reports.WaitDurationBuffer.record(
                        "SelectDoc grid loader (adjustment entry)", elapsedMs,
                        "AdjustmentEntry.selectDocument", attempt);
                if (attempt > 1 || elapsedMs > 1000) {
                    // Only worth a report line when the wait was non-trivial — otherwise this
                    // fires on every fast, healthy call and just adds noise.
                    ReportLogger.warn("[Select-Doc Loader] cleared at attempt " + attempt + "/"
                            + maxRetries + " after " + elapsedMs + "ms total.");
                }
                Log.warn("AdjustmentEntry.selectDocument: loader gone at attempt [{}]", attempt);
                break;
            } catch (TimeoutException te) {
                long elapsedMs = System.currentTimeMillis() - loaderWaitStartMs;
                // ReportLogger, not Log: Log.* is SLF4J-only and never reaches the Extent report,
                // so this loop's retries — worth up to maxRetries × extjsTimeout() (3 × 40s = 120s
                // by default) — were entirely invisible in the HTML report.
                ReportLogger.warn("[Select-Doc Loader Retry " + attempt + "/" + maxRetries
                        + "] loader still visible after " + elapsedMs + "ms total"
                        + (attempt == maxRetries ? " — giving up." : " — retrying."));
                if (attempt == maxRetries) throw te;
                Log.warn("AdjustmentEntry.selectDocument: loader still visible after attempt [{}], retrying...", attempt);
            }
        }
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics AFTER loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        return new SelectDocumentPopup();
    }

    public AdjustmentEntry fifoSetOff() {
        SeleniumUtils.click(AdjustmentPopups.FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "FIFO Set-Off Option");
        return this;
    }

    public AdjustmentEntry referenceSiteWiseFIFO() {
        SeleniumUtils.click(AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        return this;
    }

    /** Clicks the menu item, waits for the popup window to actually become visible,
     *  then returns a ready-to-use popup object. The visibility wait is load-bearing —
     *  without it, the first native action on the popup grid can race the dialog's
     *  render and fail to find the row-0 cell. */
    public ReferenceSiteWiseFIFOPopup openReferenceSiteWiseFIFOPopup() {
        SeleniumUtils.click(AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        JavascriptExecutorUtils.waitForExtJS();
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                .until(ExpectedConditions.visibilityOfElementLocated(
                        AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.popupWindowLocator(voucherDomain)));
        return new ReferenceSiteWiseFIFOPopup(voucherDomain);
    }

    /** Captures Transaction Amount, Adjusted Amount, Pending to Adjust from the Adjustment Entry footer. */
    public AdjustmentAmountSnapshot captureAmounts() {
        return new AdjustmentAmountSnapshot(
                getTransactionAmount(), getAdjustedAmount(), getPendingToAdjust());
    }

    public AdjustmentEntry showReferenceSiteSummary() {
        WaitUtils.fluentWaitBuilder().until(driver -> {
            driver.findElement(Loc.SHOW_REF_SITE_SUMMARY).click();
            return true;
        });
        ExtentLogger.info("Show Reference Site Summary is clicked successfully");
        WaitUtils.waitForExtMask("Show Reference Site Summary");
        PopupMonitor.checkAfterAction("Show Reference Site Summary");
        return this;
    }

    public AdjustmentEntry clickOK() {
        SeleniumUtils.clickAndNotePopup(Loc.OK_BUTTON, WaitType.CLICKABLE, "OK Button in Adjustment Entry");
        return this;
    }

    /**
     * Clicks OK when Grid Amount > total adjustment entry amount (Grid:GREATER).
     * Returns a DocumentPopup pre-wired with this entry's parent and this instance
     * so the caller can invoke clickProceedAnyway() or clickStay().
     */
    public DocumentPopup clickOKExpectingConfirmation() {
        SeleniumUtils.click(Loc.OK_BUTTON, WaitType.CLICKABLE, "OK Button");
        return new DocumentPopup(parent, this);
    }

    /**
     * Clicks OK and reports whether the amount-mismatch Confirmation
     * ({@link com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage#ADJUSTMENT_AMOUNT_MISMATCH_CONFIRMATION})
     * appeared — present-or-absent, since it only appears when the adjusted total differs from
     * the row's payment amount. Does not use {@code clickAndNotePopup}: this popup is an expected
     * part of the scenario, not an unexpected one to flag.
     */
    public boolean clickOkAndDetectMismatchConfirmation() {
        SeleniumUtils.click(Loc.OK_BUTTON, WaitType.CLICKABLE, "OK Button in Adjustment Entry");
        final String expected = com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage
                .ADJUSTMENT_AMOUNT_MISMATCH_CONFIRMATION.getText();
        try {
            WaitUtils.fluentWaitBuilder()
                    .withTimeout(Duration.ofSeconds(4))
                    .until(d -> {
                        java.util.List<org.openqa.selenium.WebElement> els = d.findElements(Loc.MESSAGE_BOX_TEXT);
                        if (els.isEmpty() || !els.get(0).isDisplayed()) return false;
                        String t = els.get(0).getText();
                        return t != null && t.contains(expected);
                    });
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** The mismatch Confirmation as a {@link DocumentPopup} wired back to this entry/parent —
     *  call after {@link #clickOkAndDetectMismatchConfirmation()} returned {@code true}. */
    public DocumentPopup mismatchConfirmation() {
        return new DocumentPopup(parent, this);
    }

    /** Per-document Pending Amount read off the Adjustment Entry grid (UI is the source of truth
     *  for the pending ceiling; callers may cross-check {@code v_pending_adj}). */
    public String getPendingAmountForDocument(String documentNumber) {
        By locator = By.xpath(String.format(Loc.PENDING_AMOUNT_BY_DOC_XPATH, documentNumber));
        return SeleniumUtils.getText(locator, "PendingAmount_" + documentNumber);
    }

    public AdjustmentEntry clickCancel() {
        SeleniumUtils.click(CommonButton.CANCEL.locator(), WaitType.CLICKABLE, "Cancel Button");
        return this;
    }

    // ===========================
    // Value Fetchers
    // ===========================

    public String getTransactionAmount() {
        return SeleniumUtils.getAttribute(Loc.TRANSACTION_AMOUNT, "value", "Transaction Amount");
    }

    public String getAdjustedAmount() {
        return SeleniumUtils.getAttribute(Loc.ADJUSTED_AMOUNT, "value", "Adjusted Amount");
    }

    public String getPendingToAdjust() {
        return SeleniumUtils.getAttribute(Loc.PENDING_TO_ADJUST, "value", "Pending To Adjust");
    }

    public String getAdjustedAmountForDocument(String documentNumber) {
        By locator = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        return SeleniumUtils.getText(locator, "AdjustedAmount_" + documentNumber);
    }

    public AdjustmentEntry setAdjustedAmountForDocument(String documentNumber, String amount) {
        By cellDiv = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        ActionUtils.doubleClick(cellDiv);
        By cellInput = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//input", documentNumber));
        String editorLabel = "AdjustedAmount_editor_" + documentNumber;
        System.out.println("EDITORLABEL"+editorLabel);
        SeleniumUtils.clear(cellInput, editorLabel);
        SeleniumUtils.sendKeys(cellInput, WaitType.CLICKABLE, amount, editorLabel);
        // Commit the edit like a user (Tab - same idiom as GV's narration edit). Left open, the value only lands when
        // OK is clicked, so anything reading the popup before that (amounts, UI checks) sees the old state.
        SeleniumUtils.pressTab(cellInput, editorLabel);
        JavascriptExecutorUtils.waitForExtJS();
        final java.math.BigDecimal want = new java.math.BigDecimal(amount.replace(",", "").trim());
        try {
            WaitUtils.fluentWaitBuilder()
                    .withMessage("Adjusted Amount cell of " + documentNumber + " to show " + amount)
                    .until(d -> {
                        try {
                            String shown = d.findElement(cellDiv).getText().replace(",", "").trim();
                            return !shown.isEmpty() && new java.math.BigDecimal(shown).compareTo(want) == 0;
                        } catch (RuntimeException e) {
                            return false;
                        }
                    });
        } catch (TimeoutException e) {
            String shown;
            try {
                shown = DriverManager.getDriver().findElement(cellDiv).getText();
            } catch (RuntimeException ex) {
                shown = "(cell not readable)";
            }
            ReportLogger.business("[Adjustment] Adjusted Amount for " + documentNumber + " typed " + amount
                    + " but the cell shows '" + shown + "' after Tab (edit not committed yet)");
            // A different, committed amount means the app refused the typed one (e.g. it is more than the
            // document's pending - run 20261009_152746: typed 20.00, kept 10.00). Continuing only ends in an
            // unanswered mismatch Confirmation and an unrelated blocked click minutes later, so stop here.
            java.math.BigDecimal kept = parseAmount(shown);
            if (kept != null && kept.compareTo(want) != 0) {
                throw new IllegalStateException("[Adjustment] Test data conflict on " + documentNumber
                        + ": typed Adjusted Amount " + amount + " but the app kept " + shown.trim()
                        + " (document Pending Amount in the popup: " + pendingAmountShown(documentNumber)
                        + "; v_pending_adj now: " + dbPendingShown(documentNumber)
                        + ").The document's pending amount is lower than the test data expected - most likely"
                        + " already adjusted by another voucher.");
            }
        }
        return this;
    }

    private static java.math.BigDecimal parseAmount(String text) {
        try {
            String t = text == null ? "" : text.replace(",", "").trim();
            return t.isEmpty() ? null : new java.math.BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The document's pending in the database right now, for diagnostics only - never throws. */
    private static String dbPendingShown(String documentNumber) {
        try {
            return com.gsl.database.query.PendingAdjQuery.livePendingForDiagnostics(
                    com.gsl.database.DatabaseManager.getJdbi(), documentNumber);
        } catch (RuntimeException e) {
            return "unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** The document row's Pending Amount cell, for diagnostics only - never throws. */
    private static String pendingAmountShown(String documentNumber) {
        try {
            return DriverManager.getDriver().findElements(
                            By.xpath(String.format(Loc.PENDING_AMOUNT_BY_DOC_XPATH, documentNumber)))
                    .stream().map(e -> e.getText().trim()).filter(t -> !t.isEmpty()).findFirst()
                    .orElse("(not readable)");
        } catch (RuntimeException e) {
            return "(not readable)";
        }
    }


    // ===========================
    // Business Flows
    // ===========================

    public AdjustmentEntry performFIFOAdjustment() {
        return openAdjustedDocumentMenu()
                .fifoSetOff()
                .clickOK();
    }

    public AdjustmentEntry performReferenceSiteFIFOAdjustment() {
        return openAdjustedDocumentMenu()
                .referenceSiteWiseFIFO()
                .showReferenceSiteSummary()
                .clickOK();
    }

    public SelectDocumentPopup openSelectDocumentFlow() {
        openAdjustedDocumentMenu();
        return selectDocument();
    }

    // ── UI-contract validation ────────────────────────────────────────────────
    // Label locators — UNVERIFIED (same "span text -> ancestor::div[x-form-item]" idiom
    // Loc.TRANSACTION_AMOUNT/ADJUSTED_AMOUNT/PENDING_TO_ADJUST already use, proven live by the
    // real captureAmounts() flow; the label <span> itself, used here for text+style checks, has
    // not been independently confirmed).
    private static final By TRANSACTION_AMOUNT_LABEL =
            By.xpath("//span[text()='Transaction Amount:']");
    private static final By ADJUSTED_AMOUNT_LABEL =
            By.xpath("//span[text()='Adjusted Amount:']");
    private static final By PENDING_TO_ADJUST_LABEL =
            By.xpath("//span[text()='Pending to Adjust:']");

    /** UI-contract check for the Transaction Amount / Adjusted Amount / Pending to Adjust footer
     *  fields — confirmed by the user: always read-only, no editable path, so each uses
     *  {@code validateReadonlyAmount} (same as GV's Balance), no {@code UiFieldState} branching
     *  needed. */
    public List<UiValidationFailure> validateAmountFooterUi(UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();

        failures.addAll(UiComponentValidator.validateLabel(TRANSACTION_AMOUNT_LABEL,
                "ADJUSTMENT_ENTRY_TRANSACTION_AMOUNT_LABEL", "Transaction Amount:",
                com.gsl.utils.ui.FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(UiComponentValidator.validateReadonlyAmount(Loc.TRANSACTION_AMOUNT,
                "ADJUSTMENT_ENTRY_TRANSACTION_AMOUNT", level));

        failures.addAll(UiComponentValidator.validateLabel(ADJUSTED_AMOUNT_LABEL,
                "ADJUSTMENT_ENTRY_ADJUSTED_AMOUNT_LABEL", "Adjusted Amount:",
                com.gsl.utils.ui.FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(UiComponentValidator.validateReadonlyAmount(Loc.ADJUSTED_AMOUNT,
                "ADJUSTMENT_ENTRY_ADJUSTED_AMOUNT", level));

        failures.addAll(UiComponentValidator.validateLabel(PENDING_TO_ADJUST_LABEL,
                "ADJUSTMENT_ENTRY_PENDING_TO_ADJUST_LABEL", "Pending to Adjust:",
                com.gsl.utils.ui.FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(UiComponentValidator.validateReadonlyAmount(Loc.PENDING_TO_ADJUST,
                "ADJUSTMENT_ENTRY_PENDING_TO_ADJUST", level));

        return failures;
    }

    /** Renders + soft-asserts {@link #validateAmountFooterUi}'s results via the same "UI
     *  Validation" step-tree/Actual-Results-merge every other page in this framework uses. */
    public void validateUiContract(UiValidationLevel level) {
        UiValidationReporter.renderAndAssert(validateAmountFooterUi(level));
    }
}
