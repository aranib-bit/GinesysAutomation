package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherGridColumnHeader;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Common XPath templates and button-click logic shared by AR and AP Voucher
 * sub-ledger page components.
 *
 * XPaths identical across both voucher types are declared as static final constants.
 * The only difference is the Sub Ledger column header text (AR: "Sub Ledger",
 * AP: "Sub-Ledger"), which is resolved from VoucherDomain — the same pattern as
 * resolveDocumentDate(VoucherDomain) in VoucherDocumentInformationBase.
 */
public abstract class VoucherSubLedgerInformationBase implements AdjustmentEntryParent {

    // ── Static constants — identical for AR and AP ────────────────────────────

    private static final String COL_REF_SITE =
            "//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id";

    private static final String COL_SL_ID =
            "//span[normalize-space()='Sub Ledger Id']/ancestor::div[contains(@id,'gridcolumn')]/@id";

    // ── Per-type field — resolved from VoucherDomain ─────────────────────────

    private final String colSubLedger;

    // ── XPath templates — built once in constructor ───────────────────────────

    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE;
    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID;
    private final String PAYMENT_BUTTON_TEMPLATE;
    private final String PAYMENT_BUTTON_TEMPLATE_WITH_SLID;

    protected VoucherSubLedgerInformationBase(VoucherDomain domain) {
        this.colSubLedger = resolveSubLedgerColumn(domain);

        SUBLEDGERADJUSTBUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + COL_REF_SITE + "]/following-sibling::td/div//button[normalize-space()='Adjust']";

        SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + COL_SL_ID + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + COL_REF_SITE + "]/following-sibling::td/div//button[normalize-space()='Adjust']";

        PAYMENT_BUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + COL_REF_SITE + "]/following-sibling::td/div//button[normalize-space()='Cheque/RTGS']";

        PAYMENT_BUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + COL_SL_ID + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + COL_REF_SITE + "]/following-sibling::td/div//button[normalize-space()='Cheque/RTGS']";
    }

    /**
     * Resolves the Sub Ledger column header XPath for the given voucher domain.
     * AR grid: "Sub Ledger" | AP grid: "Sub-Ledger"
     * Mirrors resolveDocumentDate(VoucherDomain) in VoucherDocumentInformationBase.
     */
    private static String resolveSubLedgerColumn(VoucherDomain domain) {
        switch (domain) {
            case AR: return ARVoucherGridColumnHeader.SUB_LEDGER.columnIdXPath();
            case AP: return APVoucherGridColumnHeader.SUB_LEDGER.columnIdXPath();
            default: throw new IllegalArgumentException(
                    "No Sub Ledger column header defined for voucher domain: " + domain);
        }
    }

    // ── Protected methods — inherited by AR and AP subclasses ─────────────────

    protected void clickAdjust(String subLedgerName, String referenceSite, String slid) {
        String xpath = (slid != null && !slid.isBlank())
                ? String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID, subLedgerName, referenceSite, slid)
                : String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE, subLedgerName, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "SUBLEDGERADJUSTBUTTON");
    }

    protected PaymentInformationPopup clickPaymentButton(String subLedger, String referenceSite, String slid) {
        String xpath = (slid != null && !slid.isBlank())
                ? String.format(PAYMENT_BUTTON_TEMPLATE_WITH_SLID, subLedger, referenceSite, slid)
                : String.format(PAYMENT_BUTTON_TEMPLATE, subLedger, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "PAYMENT_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
        return createPaymentPopup();
    }

    protected abstract PaymentInformationPopup createPaymentPopup();
}
