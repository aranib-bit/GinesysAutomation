package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
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

    // ── Per-domain fields — resolved once in constructor ──────────────────────

    private final VoucherDomain domain;
    private final String colSubLedger;
    private final String colRefSite;
    private final String colSlId;
    /** False when {@code domain}'s Sub-Ledger Information grid has no "Sub Ledger Id" column
     *  (e.g. AR — see {@link VoucherDomain#excludesCommonColumn}) — the WITH_SLID templates
     *  below would then never match a real row, so callers must fall back to name+refSite. */
    private final boolean slIdColumnAvailable;

    // ── XPath templates — built once in constructor ───────────────────────────

    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE;
    private final String SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID;
    private final String PAYMENT_BUTTON_TEMPLATE;
    private final String PAYMENT_BUTTON_TEMPLATE_WITH_SLID;

    protected VoucherSubLedgerInformationBase(VoucherDomain domain) {
        this.domain = domain;
        this.colSubLedger = domain.getSubLedgerColumnHeader().columnIdXPath();
        this.colRefSite   = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(domain).columnIdXPath();
        this.colSlId      = CommonVoucherGridColumnHeader.SUB_LEDGER_ID.resolveFor(domain).columnIdXPath();
        this.slIdColumnAvailable = !domain.excludesCommonColumn(CommonVoucherGridColumnHeader.SUB_LEDGER_ID);

        SUBLEDGERADJUSTBUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.ADJUST.label() + "']";

        SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + colSlId + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.ADJUST.label() + "']";

        PAYMENT_BUTTON_TEMPLATE =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']";

        PAYMENT_BUTTON_TEMPLATE_WITH_SLID =
                "//tr[td[@data-columnid=" + colSubLedger + "]/div[text()='%s']" +
                " and td[@data-columnid=" + colRefSite + "]/div[normalize-space()='%s']" +
                " and td[@data-columnid=" + colSlId + "]/div[normalize-space()='%s']]" +
                "//td[@data-columnid=" + colRefSite + "]/following-sibling::td/div//button[normalize-space()='" +
                SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']";
    }

    // ── Protected methods — inherited by AR and AP subclasses ─────────────────

    @Override
    public VoucherDomain voucherDomain() {
        return domain;
    }

    protected void clickAdjust(String subLedgerName, String referenceSite, String slid) {
        String xpath = (slIdColumnAvailable && slid != null && !slid.isBlank())
                ? String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID, subLedgerName, referenceSite, slid)
                : String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE, subLedgerName, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "SUBLEDGERADJUSTBUTTON");
    }

    protected PaymentInformationPopup clickPaymentButton(String subLedger, String referenceSite, String slid) {
        String xpath = (slIdColumnAvailable && slid != null && !slid.isBlank())
                ? String.format(PAYMENT_BUTTON_TEMPLATE_WITH_SLID, subLedger, referenceSite, slid)
                : String.format(PAYMENT_BUTTON_TEMPLATE, subLedger, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "PAYMENT_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
        return createPaymentPopup();
    }

    /**
     * Clicks the native Add Row button (mdi-playlist-plus icon) in the sub-ledger grid.
     * Replaces the JS {@code store.add({})} injection.
     */
    protected void clickAddRowButton() {
        SeleniumUtils.click(
                By.xpath("//i[contains(@class,'mdi-playlist-plus')]" +
                         "/parent::span[contains(@class,'x-btn-inner-IconButton-small')]"),
                WaitType.CLICKABLE, "ADD_SUBLEDGER_ROW");
    }

    protected abstract PaymentInformationPopup createPaymentPopup();
}
