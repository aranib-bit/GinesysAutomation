package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.WaitType;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import java.math.BigDecimal;
import java.util.List;

/**
 * TDS & Net Payable popup — AP Voucher (On Account = Yes, tdsProvided = true).
 *
 * Flow per TDS row:
 *   1. Find the sub-ledger row by matching TdsAndNetPayable.lineNo to SubLedgerInformation.lineNo
 *   2. Click the "TDS" button in that sub-ledger grid row to open the popup
 *   3. Set TDS Name (picker search + select)
 *   4. Optionally override Gross Payable / TDS Applicable Amount
 *   5. Optionally click "Populate TDS Exception Rate"
 *   6. Click OK
 *
 * Button label "TDS" is assumed — verify against the live DOM if different.
 */
public class APVoucherTdsAndNetPayable {

    // ── Sub-ledger grid TDS button (same row-matching pattern as Adjust / Cheque/RTGS) ──

    private static final String TDS_BUTTON_TEMPLATE =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s']" +
            " and td[@data-columnid=//span[normalize-space()='Reference Site']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]" +
            "//td[@data-columnid=//span[normalize-space()='Reference Site']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='TDS']";

    private static final String TDS_BUTTON_TEMPLATE_WITH_SLID =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s']" +
            " and td[@data-columnid=//span[normalize-space()='Reference Site']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']" +
            " and td[@data-columnid=//span[normalize-space()='Sub Ledger Id']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]" +
            "//td[@data-columnid=//span[normalize-space()='Reference Site']" +
            "/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='TDS']";

    // ── "Calculate TDS & Net Payable" popup fields ───────────────────────────

    // TDS Name picker (search-trigger picker field)
    private static final By TDS_NAME_INPUT =
            By.xpath("//span[normalize-space()='TDS Name:']/ancestor::div[contains(@class,'x-form-item')]" +
                     "//input[@data-ref='inputEl']");
    private static final By TDS_NAME_SEARCH_TRIGGER =
            By.xpath("//span[normalize-space()='TDS Name:']/ancestor::div[contains(@class,'x-form-item')]" +
                     "//div[contains(@class,'x-form-trigger')]");
    private static final String TDS_NAME_OPTION_TEMPLATE =
            "//li[@role='option' and normalize-space()='%s']";

    // Numeric inputs (spinner inputs — clear and type)
    private static final By GROSS_PAYABLE =
            By.xpath("//span[normalize-space()='Gross Payable:']/ancestor::div[contains(@class,'x-form-item')]" +
                     "//input[@data-ref='inputEl']");

    private static final By TDS_APPLICABLE_AMOUNT =
            By.xpath("//span[normalize-space()='TDS Applicable Amount:']/ancestor::div[contains(@class,'x-form-item')]" +
                     "//input[@data-ref='inputEl']");

    // "Populate TDS Exception Rate" link — click only when data says "Yes"
    private static final By POPULATE_TDS_EXCEPTION_RATE =
            By.xpath("//span[normalize-space()='Populate TDS Exception Rate'] | " +
                     "//a[normalize-space()='Populate TDS Exception Rate']");

    // OK button inside the popup
    private static final By OK_BUTTON =
            By.xpath("//div[contains(@class,'x-window') and .//span[normalize-space()='Calculate TDS']]" +
                     "//span[normalize-space()='OK']/ancestor::a[contains(@class,'x-btn') and @aria-hidden='false']");

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
            SubLedgerInformation sl = matchSubLedger(tds, sls);
            if (sl == null) {
                Log.warn("[TDS] No SubLedgerInformation found for lineNo='{}' — skipping", tds.getLineNo());
                continue;
            }

            Log.debug("[TDS] Opening TDS popup for SL='{}' lineNo='{}'", sl.getSubLedger(), tds.getLineNo());
            clickTdsButton(sl.getSubLedger(), sl.getReferenceSite(), sl.getId());

            setTdsName(tds.getTdsName());

            if (tds.getGrossPayable() != null)
                setNumericField(GROSS_PAYABLE, tds.getGrossPayable(), "GROSS_PAYABLE");

            if (tds.getTdsApplicableAmount() != null)
                setNumericField(TDS_APPLICABLE_AMOUNT, tds.getTdsApplicableAmount(), "TDS_APPLICABLE_AMOUNT");

            if ("Yes".equalsIgnoreCase(tds.getPopulateTdsExceptionRate())) {
                SeleniumUtils.click(POPULATE_TDS_EXCEPTION_RATE, WaitType.CLICKABLE, "POPULATE_TDS_EXCEPTION_RATE");
                JavascriptExecutorUtils.waitForExtJS();
            }

            clickOk();
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
        SeleniumUtils.click(TDS_NAME_SEARCH_TRIGGER, WaitType.CLICKABLE, "TDS_NAME_TRIGGER");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(TDS_NAME_INPUT, WaitType.VISIBLE, tdsName, "TDS_NAME_INPUT");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath(String.format(TDS_NAME_OPTION_TEMPLATE, tdsName));
        SeleniumUtils.click(option, WaitType.CLICKABLE, "TDS_NAME_OPTION_" + tdsName);
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void setNumericField(By locator, BigDecimal value, String fieldName) {
        SeleniumUtils.clear(locator, fieldName);
        SeleniumUtils.sendKeys(locator, WaitType.CLICKABLE, value.toPlainString(), fieldName);
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void clickOk() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "TDS_POPUP_OK");
        JavascriptExecutorUtils.waitForExtJS();
    }
}
