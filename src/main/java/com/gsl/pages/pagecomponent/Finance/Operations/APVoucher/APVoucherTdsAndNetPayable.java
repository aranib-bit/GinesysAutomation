package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.flow.Finance.Operations.APVoucher.TdsApplicableAmountFlow;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.pages.pagecomponent.Finance.Common.TdsNameParent;
import com.gsl.pages.pagecomponent.Finance.Common.TdsNamePopup;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.pages.enums.Finance.Operations.Common.TdsNetPayableFieldAttribute;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.aventstack.extentreports.ExtentTest;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.math.BigDecimal;
import java.util.List;

/**
 * TDS & Net Payable popup — AP Voucher (On Account = Yes, tdsProvided = true).
 *
 * Flow per TDS row:
 *   1. Find the sub-ledger row by matching TdsAndNetPayable.lineNo to SubLedgerInformation.lineNo
 *   2. Click the "Calc. Amount" button in that sub-ledger grid row to open the popup
 *   3. Set TDS Name (picker search + select)
 *   4. Optionally override Gross Payable / TDS Applicable Amount
 *   5. Optionally click "Populate TDS Exception Rate"
 *   6. Click OK
 *
 * Button label resolved from {@link com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel#CALC_AMOUNT}.
 */
public class APVoucherTdsAndNetPayable implements TdsNameParent {

    // ── Instance fields — built in constructor using domain-resolved column headers ──

    private final String TDS_BUTTON_TEMPLATE;
    private final String TDS_BUTTON_TEMPLATE_WITH_SLID;

    public APVoucherTdsAndNetPayable(VoucherDomain domain) {
        String colSubLedger = domain.getSubLedgerColumnHeader().columnIdXPath();
        String colRefSite   = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(domain).columnIdXPath();
        String colSlId      = CommonVoucherGridColumnHeader.SUB_LEDGER_ID.resolveFor(domain).columnIdXPath();

        TDS_BUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite +
                "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CALC_AMOUNT.label() + "']";

        TDS_BUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + colSlId + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite +
                "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CALC_AMOUNT.label() + "']";

        // Popup field locators — built from domain labels (same pattern as cashBankSearch)
        tdsNameSearchTrigger = By.xpath(
                "//span[normalize-space()='" + domain.getLabelTdsName() + "']/ancestor::label" +
                "/following-sibling::div" +
                "//div[contains(@id,'gslpicklistfieldextended-') and contains(@id,'-trigger-picker')]");

        grossPayable = By.xpath(
                "//span[normalize-space()='" + domain.getLabelGrossPayable() + "']/ancestor::label" +
                "/following-sibling::div//input[@data-ref='inputEl']");

        tdsApplicableAmount = By.xpath(
                "//span[normalize-space()='" + domain.getLabelTdsApplicableAmount() + "']/ancestor::label" +
                "/following-sibling::div//input[@data-ref='inputEl']");

        tdsApplicableAmountSpinnerDown = By.xpath(
                "//span[normalize-space()='" + domain.getLabelTdsApplicableAmount() + "']/ancestor::label" +
                "/following-sibling::div//div[contains(@class,'x-form-spinner-down')]");

        tdsAmount = By.xpath(
                "//span[normalize-space()='" + domain.getLabelTdsAmount() + "']/ancestor::label" +
                "/following-sibling::div//input[@data-ref='inputEl']");

        netPayable = By.xpath(
                "//span[normalize-space()='" + domain.getLabelNetPayable() + "']/ancestor::label" +
                "/following-sibling::div//input[@data-ref='inputEl']");

        populateTdsExceptionRate = By.xpath(
                "//span[normalize-space()='" + domain.getLabelPopulateTdsException() + "'] | " +
                "//a[normalize-space()='" + domain.getLabelPopulateTdsException() + "']");
    }

    // ── "Calculate TDS & Net Payable" popup fields ───────────────────────────
    // Built in constructor from domain labels (mirrors cashBankSearch pattern in
    // VoucherDocumentInformationBase — label text via VoucherDocumentLabels, not hardcoded).

    // TDS Name search icon — opens the TDS Name grid popup
    private final By tdsNameSearchTrigger;

    // Numeric inputs (spinner inputs — clear and type)
    private final By grossPayable;
    private final By tdsApplicableAmount;
    private final By tdsApplicableAmountSpinnerDown;

    // Read-only computed fields — populated via async XHR; read via aria-valuenow
    private By tdsAmount;
    private By netPayable;

    // "Populate TDS Exception Rate" link — click only when data says "Yes"
    private final By populateTdsExceptionRate;

    // OK button inside the popup
    private static final By OK_BUTTON =
            By.xpath("//div[normalize-space()='Calculate TDS & Net Payable'] /ancestor::div[contains(@class,'x-window')] //span[@id[contains(.,'-btnInnerEl')] and normalize-space()='OK']");

    // Validation error popup OK button — fired when TDS Applicable Amount is fully cleared
    // (error: "minimum value should be not less than 1")
    private static final By VALIDATION_ERROR_OK =
            By.xpath("//a[contains(@class,'x-btn-default-small')" +
                     " and contains(@class,'x-toolbar-item')" +
                     " and @aria-hidden='false']" +
                     "[.//span[normalize-space()='OK']]");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * For each TDS row: open the TDS popup from the matching sub-ledger row, fill fields, click OK.
     * Only called when {@code data.isTdsProvided() == true}.
     */
    public void fillTdsRows(MergedFinanceApVoucherData data) {
        List<TdsAndNetPayable> tdsList = data.getTdsAndNetPayableList();
        List<SubLedgerInformation> sls  = data.getSubLedgerInformationList();

        if (tdsList == null || tdsList.isEmpty()) return;

        for (TdsAndNetPayable tds : tdsList) {
            final SubLedgerInformation sl = matchSubLedger(tds, sls);
            if (sl == null) {
                Log.warn("[TDS] No SubLedgerInformation found for lineNo='{}' — skipping", tds.getLineNo());
                continue;
            }

            // Report the TDS calculation under the same SL row group created during the
            // sub-ledger fill, so "Calculate TDS & Net Payable" reads as part of that row.
            // stepUnder tolerates a null node (falls back to a plain step) if the row was
            // not registered for any reason.
            ExtentTest rowNode = SubLedgerRowStepRegistry.get(tds.getLineNo());
            StepExecutor.stepUnder(rowNode,
                    "Calculate TDS & Net Payable — " + tds.getTdsName(), () -> {

                StepExecutor.step("TDS Name and Section", () -> {
                    Log.debug("[TDS] Opening TDS popup for SL='{}' lineNo='{}'", sl.getSubLedger(), tds.getLineNo());
                    clickTdsButton(sl.getSubLedger(), sl.getReferenceSite(), sl.getId());
                    setTdsName(tds.getTdsName());
                });

                StepExecutor.step("Account Information", () -> {
                    // Delegate amount-filling to the layered architecture:
                    // Page Object (raw string) → Flow → BusinessLogic (WHAT) → Transaction (HOW)
                    TdsApplicableAmountFlow.execute(
                            tds.getTdsApplicableAmountBehaviour(),
                            this,
                            tds.getGrossPayable(),
                            tds.getTdsApplicableAmount()
                    );

                    if ("Yes".equalsIgnoreCase(tds.getPopulateTdsExceptionRate())) {
                        SeleniumUtils.click(populateTdsExceptionRate, WaitType.CLICKABLE, "POPULATE_TDS_EXCEPTION_RATE");
                        JavascriptExecutorUtils.waitForExtJS();
                    }

                    BigDecimal tdsAmt = readTdsAmount();
                    data.getCapturedTdsAmounts().put(tds.getLineNo(), tdsAmt);
                    Log.debug("[TDS] TdsAmount captured={} for lineNo='{}'", tdsAmt, tds.getLineNo());

                    BigDecimal netPay = readNetPayable(tds);
                    data.getCapturedNetPayables().put(tds.getLineNo(), netPay);
                    Log.debug("[TDS] NetPayable captured={} for lineNo='{}'", netPay, tds.getLineNo());

                    clickOk();
                });
            });
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SubLedgerInformation matchSubLedger(TdsAndNetPayable tds,
                                                  List<SubLedgerInformation> sls) {
        if (sls == null || tds.getLineNo() == null) return null;
        return sls.stream()
                .filter(s -> tds.getLineNo().equalsIgnoreCase(s.getLineNo()))
                .findFirst()
                .orElse(null);
    }

    private void clickTdsButton(String subLedger, String referenceSite, String slid) {
        String xpath = (slid != null && !slid.isBlank())
                ? String.format(TDS_BUTTON_TEMPLATE_WITH_SLID, subLedger,
                                referenceSite != null ? referenceSite : "", slid)
                : String.format(TDS_BUTTON_TEMPLATE, subLedger,
                                referenceSite != null ? referenceSite : "");
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "TDS_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void setTdsName(String tdsName) {
        if (tdsName == null || tdsName.isBlank()) return;
        SeleniumUtils.click(tdsNameSearchTrigger, WaitType.CLICKABLE, "TDS_NAME_SEARCH_ICON");
        JavascriptExecutorUtils.waitForExtJS();
        new TdsNamePopup(this).selectTdsName(tdsName);
        JavascriptExecutorUtils.waitForExtJS();
    }

    // ── Public UI primitives — called only by TdsApplicableAmountTransaction ──
    // No business logic here. Each method performs exactly one raw UI operation.

    /**
     * Types a value into the Gross Payable field (clear then send keys).
     * After this, the application auto-populates TDS Applicable Amount with the same value.
     */
    public void typeGrossPayable(BigDecimal value) {
        SeleniumUtils.clear(grossPayable, "GROSS_PAYABLE");
        SeleniumUtils.sendKeys(grossPayable, WaitType.CLICKABLE, value.toPlainString(), "GROSS_PAYABLE");
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Types a value into the TDS Applicable Amount field (clear then send keys).
     * Used to override the auto-filled value or to reset after dismissing a validation error.
     */
    public void typeTdsApplicableAmount(BigDecimal value) {
        SeleniumUtils.clear(tdsApplicableAmount, "TDS_APPLICABLE_AMOUNT");
        SeleniumUtils.sendKeys(tdsApplicableAmount, WaitType.CLICKABLE, value.toPlainString(), "TDS_APPLICABLE_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Reduces TDS Applicable Amount from {@code fromValue} to {@code toValue}
     * by clicking the spinner-down button (each click = −1 integer step).
     * If {@code toValue} has a fractional part, moves cursor to end and appends it
     * (e.g. fromValue=100, toValue=99.55 → 1 click down → END → type ".55").
     *
     * Precondition: field currently shows {@code fromValue} (auto-filled from gross).
     */
    public void adjustTdsApplicableAmountViaSpinner(BigDecimal fromValue, BigDecimal toValue) {
        int steps = fromValue.intValue() - toValue.intValue();
        for (int i = 0; i < steps; i++) {
            SeleniumUtils.click(tdsApplicableAmountSpinnerDown, WaitType.CLICKABLE, "TDS_SPINNER_DOWN");
        }
        BigDecimal fractional = toValue.subtract(new BigDecimal(toValue.intValue()));
        if (fractional.compareTo(BigDecimal.ZERO) != 0) {
            String decimalSuffix = toValue.toPlainString()
                    .substring(toValue.toPlainString().indexOf('.'));
            SeleniumUtils.click(tdsApplicableAmount, WaitType.CLICKABLE, "TDS_APPLICABLE_AMOUNT");
            WebElement field = DriverManager.getDriver().findElement(tdsApplicableAmount);
            field.sendKeys(Keys.END);
            field.sendKeys(decimalSuffix);
        }
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Clears the TDS Applicable Amount field entirely.
     * This triggers the application's validation error popup
     * ("minimum value should be not less than 1").
     */
    public void clearTdsApplicableAmount() {
        SeleniumUtils.clear(tdsApplicableAmount, "TDS_APPLICABLE_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Dismisses the validation error popup by clicking its OK button.
     * Called immediately after {@link #clearTdsApplicableAmount()} in the
     * CLEAR_ERROR_THEN_PARTIAL_EDIT scenario.
     */
    public void dismissValidationError() {
        SeleniumUtils.click(VALIDATION_ERROR_OK, WaitType.CLICKABLE, "VALIDATION_ERROR_OK");
        JavascriptExecutorUtils.waitForExtJS();
        Log.info("[TDS] Validation error popup dismissed");
    }

    /** Reads TDS Amount — disabled field, value in aria-valuenow. Returns ZERO on failure. */
    private BigDecimal readTdsAmount() {
        JavascriptExecutorUtils.waitForExtJS();
        try {
            String raw = SeleniumUtils.getAttribute(tdsAmount, TdsNetPayableFieldAttribute.ARIA_VALUE_NOW.attribute(), "TDS_AMOUNT");
            if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (Exception e) {
            Log.warn("[TDS] Could not read TDS Amount from popup: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Reads Net Payable via the DOM *property* (not the HTML attribute).
     * ExtJS number fields update input.value via JS after async computation,
     * leaving the original HTML attribute stuck at "0". getDomProperty reads
     * the live JS property and reflects what the user sees on screen.
     *
     * Fails fast if the value is zero and a non-zero gross payable was supplied —
     * this means the app did not compute Net Payable, which is a real bug and
     * must not silently propagate into captured data or subsequent API calls.
     */
    private BigDecimal readNetPayable(TdsAndNetPayable tds) {
        JavascriptExecutorUtils.waitForExtJS();
        // The Net Payable field is disabled — ExtJS never writes to input.value.
        // The computed value is stored in aria-valuenow (e.g. aria-valuenow="1900").
        String raw;
        try {
            raw = SeleniumUtils.getAttribute(netPayable, TdsNetPayableFieldAttribute.ARIA_VALUE_NOW.attribute(), "NET_PAYABLE");
        } catch (Exception e) {
            throw new AssertionError("[TDS] Could not read Net Payable field from popup", e);
        }

        BigDecimal value = (raw == null || raw.isBlank())
                ? BigDecimal.ZERO
                : new BigDecimal(raw.replace(",", "").trim());

        boolean grossIsNonZero = tds.getGrossPayable() != null
                && tds.getGrossPayable().compareTo(BigDecimal.ZERO) != 0;

        if (value.compareTo(BigDecimal.ZERO) == 0 && grossIsNonZero) {
            throw new AssertionError(
                    "[TDS] Net Payable is 0 after async settle for lineNo='" + tds.getLineNo() +
                    "' with Gross Payable=" + tds.getGrossPayable() +
                    ". App did not compute the value — check TDS Name selection or async response.");
        }

        return value;
    }

    private void clickOk() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "TDS_POPUP_OK");
        JavascriptExecutorUtils.waitForExtJS();
    }
}
