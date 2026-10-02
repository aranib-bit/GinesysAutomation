package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.SubLedgerRowIdentity;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;

import java.util.List;

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

    /**
     * Reads row {@code rowNumber}'s (1-based) own Sub-Ledger name/Reference Site/Sub Ledger Id
     * text straight off the rendered grid — the inverse of {@link #clickAdjust}'s own
     * {@code data-columnid}-scoped locating shape: that method LOCATES a row given these three
     * values; this method READS them, for a caller that only knows the row's position (e.g.
     * re-tagging an already-populated Edit-mode row, which has no test-data-supplied identity to
     * locate it by). Safe to call regardless of the row's current tag state — Sub-Ledger name/
     * Reference Site/Sub Ledger Id are a persistent part of the document (finvchdet/finsl),
     * unaffected by whether the row is currently tagged.
     */
    protected SubLedgerRowIdentity readRowIdentity(int rowNumber) {
        String subLedgerName = SeleniumUtils.getAttribute(
                By.xpath("(//tr[td[@data-columnid=" + colSubLedger + "]])[" + rowNumber
                        + "]//td[@data-columnid=" + colSubLedger + "]/div"),
                "textContent", "SUBLEDGER_NAME_ROW" + rowNumber);
        String referenceSite = SeleniumUtils.getAttribute(
                By.xpath("(//tr[td[@data-columnid=" + colRefSite + "]])[" + rowNumber
                        + "]//td[@data-columnid=" + colRefSite + "]/div"),
                "textContent", "REFERENCE_SITE_ROW" + rowNumber);
        String slId = slIdColumnAvailable
                ? SeleniumUtils.getAttribute(
                        By.xpath("(//tr[td[@data-columnid=" + colSlId + "]])[" + rowNumber
                                + "]//td[@data-columnid=" + colSlId + "]/div"),
                        "textContent", "SL_ID_ROW" + rowNumber)
                : null;
        return new SubLedgerRowIdentity(
                subLedgerName == null ? null : subLedgerName.trim(),
                referenceSite == null ? null : referenceSite.trim(),
                slId == null ? null : slId.trim());
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

    // ── UI-contract validation — grid header ─────────────────────────────────────────────────
    //
    // Placed here (not per-domain) so AR/AP/GV all inherit it for the columns common to every
    // voucher's sub-ledger grid — same "base-class placement delivers free AR/AP reuse" reasoning
    // as VoucherDocumentInformationBase's own validate*Ui methods.

    /** Validates the Sub Ledger column header's text+style (resolved per-domain, e.g. AR/GV "Sub
     *  Ledger" vs. AP "Sub-Ledger"). */
    public List<UiValidationFailure> validateSubLedgerGridHeaderUi(UiValidationLevel level) {
        IVoucherGridColumnHeader col = domain.getSubLedgerColumnHeader();
        return UiComponentValidator.validateGridHeader(col.headerLocator(), "GRID_HEADER_SUB_LEDGER",
                col.headerText(), level);
    }

    /** Validates the Reference Site column header's text+style — {@code CONTRA}/AR-excluded
     *  domains should not call this (AR has no Reference Site column; see
     *  {@link VoucherDomain#excludesCommonColumn}). */
    public List<UiValidationFailure> validateReferenceSiteGridHeaderUi(UiValidationLevel level) {
        IVoucherGridColumnHeader col = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(domain);
        return UiComponentValidator.validateGridHeader(col.headerLocator(), "GRID_HEADER_REFERENCE_SITE",
                col.headerText(), level);
    }
}
