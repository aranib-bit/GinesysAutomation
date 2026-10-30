package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.SubLedgerRowIdentity;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherSubLedgerInformation;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherTdsAndNetPayable;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.driver.DriverManager;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;

import java.util.ArrayList;
import java.util.List;

public class ApVoucherAdd implements CashBankParent, VerifiablePage {

    private final APVoucherDocumentInformation  apVoucherDocumentInformation;
    private final APVoucherSubLedgerInformation apVoucherSubLedgerInformation;
    private final APVoucherTdsAndNetPayable      apVoucherTdsAndNetPayable;
    private final APVoucherFooter                apVoucherFooter;

    public ApVoucherAdd() {
        com.gsl.validation.ui.UiStyleScope.set(VoucherDomain.AP);   // style baseline: Common + AP sheet
        this.apVoucherDocumentInformation  = new APVoucherDocumentInformation(this);
        this.apVoucherSubLedgerInformation = new APVoucherSubLedgerInformation();
        this.apVoucherTdsAndNetPayable     = new APVoucherTdsAndNetPayable(VoucherDomain.AP);
        this.apVoucherFooter               = new APVoucherFooter();
    }

    /** Mirrors {@code GeneralVoucherAdd#verifyOnThisPage()} — accepts either the Add or Edit
     *  window title, since this facade serves both flows. */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getWindowTitleText();
        boolean onAddOrEditForm = actual != null
                && (actual.equalsIgnoreCase(VoucherDomain.AP.getAddFormHeader())
                    || actual.equalsIgnoreCase(VoucherDomain.AP.getEditFormHeader()));
        if (!onAddOrEditForm) {
            throw new IllegalStateException(
                    "Expected the AP Voucher Add/Edit form to be open (page header '"
                    + VoucherDomain.AP.getAddFormHeader() + "' or '" + VoucherDomain.AP.getEditFormHeader()
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    /** Reduced-scope relative to {@code GeneralVoucherAdd#verifyEditFormFullyLoaded} — confirms
     *  the Edit window title and waits out the post-open ExtJS mask, but does NOT round-trip a
     *  Voucher No. field read-back: {@code APVoucherDocumentInformation} has no
     *  {@code getSavedDocumentNo()}-style getter today (GV-only addition). */
    public void verifyEditFormFullyLoaded(String expectedVoucherNo) {
        uiStage().expectingVoucherNo(expectedVoucherNo);
        WaitUtils.waitForExtMask("Open Voucher for Edit");
        String actualTitle = PageHeaderUtils.getWindowTitleText();
        boolean titleOk = actualTitle != null
                && actualTitle.equalsIgnoreCase(VoucherDomain.AP.getEditFormHeader());
        if (!titleOk) {
            throw new IllegalStateException(
                    "Edit form not fully loaded — expected window title '"
                    + VoucherDomain.AP.getEditFormHeader() + "' but got '" + actualTitle + "'.");
        }
    }

    /** Full UI-contract sweep — Document Information fields (Owner Site/Cash-Bank/Date/Remarks,
     *  plus AP's own On Account radio), the Sub-Ledger Information grid's headers, and the footer
     *  Save button — collecting every {@link UiValidationFailure} and soft-asserting them all at
     *  once (same convention as {@code GeneralVoucherAdd#validateUiContract}). The conditional
     *  TDS/Net Payable popup ({@code APVoucherTdsAndNetPayable}) is deliberately out of scope for
     *  this pass — see the framework plan's AP section. No per-row grid-cell validation either
     *  (see the plan's "Scope note common to both" for AR/AP).
     *
     *  <p>{@code isEditMode} is the same Owner-Site/Remarks state-divergence override
     *  {@code GeneralVoucherAdd#validateUiContract} documents — see that Javadoc for why both rules
     *  are codebase-wide facts, not GV-specific. Confirmed live for AP too (user-supplied
     *  screenshot + DOM, 2026-10-01): AP's Remarks IS visually disabled/non-interactable on a blank
     *  Add form, same as GV/AR — an earlier live run's {@code aria-disabled='false'} reading was a
     *  false negative, since AP's Remarks combo sets the native {@code disabled} attribute without
     *  syncing its ARIA mirror; {@code UiComponentValidator#validateFieldState} now checks both,
     *  not a genuine AP business difference after all. */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        uiStage().blankForm(level, state, isEditMode).finish();
    }

    /** Same sweep but FAILs are parked for the flow's final assertion instead of throwing. */
    public void validateUiContractDeferred(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        uiStage().blankForm(level, state, isEditMode).finishDeferred();
    }

    /** Blank-form sweep BEFORE a business fill: held, then reported together with the after-fill results
     *  by {@link #endFillUiDeferred} as ONE "UI Full Validation Details". */
    public void validateUiContractBeforeFill(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        uiStage().blankForm(level, state, isEditMode).holdForFill();
    }

    private com.gsl.validation.ui.voucher.VoucherUiValidationStage uiStage;

    /** The common reporting stage for this voucher — see {@code VoucherUiValidationStage}. One instance
     *  per page so the "before fill" snapshots taken by the blank-form sweep are still there for
     *  {@link #validateAfterFillDeferred}. */
    public com.gsl.validation.ui.voucher.VoucherUiValidationStage uiStage() {
        if (uiStage == null) {
            uiStage = com.gsl.validation.ui.voucher.VoucherUiValidationStage.forSpec(
                    new com.gsl.validation.ui.voucher.ApVoucherUiSpec(apVoucherDocumentInformation, apVoucherFooter, apVoucherSubLedgerInformation));
        }
        return uiStage;
    }

    /** Starts observing the sub-ledger fill that follows (each row, its Adjustment Entry / Payment Information
     *  popups and the footer after the row are validated while they exist). Pair with {@link #endFillUiDeferred}. */
    public void beginFillUi(UiValidationLevel level) {
        uiStage().beginFill(level);
    }

    /** Ends the observation and renders everything as ONE deferred (soft) "UI Validation" result. */
    public void endFillUiDeferred(UiValidationLevel level) {
        uiStage().endFill(level).finishDeferred();
    }

    /** After the fill, before Save: Document Information before/after, grid footer before/after (Record Count
     *  grows by {@code rowsFilled}), each filled row identified by its business keys. Deferred (soft). */
    public void validateAfterFillDeferred(UiValidationLevel level, int rowsFilled) {
        uiStage().afterFill(level, rowsFilled).finishDeferred();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ApVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        apVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public APVoucherDocumentInformation getDocumentInformation() {
        return apVoucherDocumentInformation;
    }

    /** Minimal header (Date → Voucher No → On Account) for the Add-Row-without-Cash/Bank validation. */
    public ApVoucherAdd fillHeaderForAddRowValidation(DocumentInformation docInfo) {
        apVoucherDocumentInformation.fillHeaderForAddRowValidation(docInfo);
        return this;
    }

    /** Whether the Sub Ledger picker lists {@code name} (types it, selects nothing) - see the component's Javadoc. */
    public boolean isSubLedgerOfferedByPicker(String name, String id) {
        return apVoucherSubLedgerInformation.isSubLedgerOfferedByPicker(name, id);
    }

    public ApVoucherAdd fillSubLedgerRowsForAdvance(MergedFinanceApVoucherData data, boolean includeRetry) {
        apVoucherSubLedgerInformation.fillSubLedgerRowsForAdvance(data, includeRetry);
        return this;
    }

    public ApVoucherAdd fillSubLedgerRowsForDocument(MergedFinanceApVoucherData data, boolean includeRetry) {
        apVoucherSubLedgerInformation.fillSubLedgerRowsForDocument(data, includeRetry);
        return this;
    }

    /** Called only when {@code data.isTdsProvided() = true} (On Account = Yes scenario). */
    public ApVoucherAdd fillTdsAndNetPayable(MergedFinanceApVoucherData data) {
        apVoucherTdsAndNetPayable.fillTdsRows(data);
        return this;
    }

    /**
     * Clicks the Add Row button in the sub-ledger grid.
     * When Cash/Bank is not yet selected, the app raises "Please select cash/bank ledger first."
     * Returns a {@link DocumentPopup} so the caller can assert the popup header and body.
     */
    public DocumentPopup clickAddSubLedgerRow() {
        apVoucherSubLedgerInformation.clickAddRow();
        return new DocumentPopup();
    }

    /** Fills only the Cash/Bank field — used after dismissing a Cash/Bank validation popup. */
    public ApVoucherAdd fillCashBankOnly(String cashbank) {
        apVoucherDocumentInformation.fillCashBankOnly(cashbank);
        return this;
    }

    @LogStep("Save AP Voucher")
    public DocumentPopup clickSave() {
        JavascriptExecutorUtils.setSubLedgerVerify("apvch_grid");
        JavascriptExecutorUtils.restoreAllGlFieldsFromCapture(DriverManager.getDriver(), "apvch_grid");
        apVoucherFooter.clickSave();
        return new DocumentPopup();
    }

    /**
     * Clicks the Amount (Dr) cell for the given sub-ledger row.
     * When the row has an adjusted document, the app raises a read-only Information dialog;
     * the returned {@link DocumentPopup} wraps that dialog for assertion.
     */
    public DocumentPopup clickAmountCell(String slName, String slId) {
        return apVoucherSubLedgerInformation.clickAmountCell(slName, slId);
    }

    /** Reads the "Deduct TDS" cell value for the given sub-ledger row from the live UI grid. */
    public String readDeductTds(String slName, String refSite, String slId) {
        return apVoucherSubLedgerInformation.readDeductTds(slName, refSite, slId);
    }

    /**
     * Reads the "TDS Info" cell for the given sub-ledger row — "TDS not appl." or "TDS: X.XX".
     * Returns null if the column is absent (document-adjustment mode) or not yet populated.
     * TODO: Call after sub-ledger fill and store in ActualResultsHolder.getUiTdsInfoMap()
     *       to activate the TDS Info column in the Sub Ledger Information report section.
     */
    public String readTdsInfo(String slName, String refSite, String slId) {
        return apVoucherSubLedgerInformation.readTdsInfo(slName, refSite, slId);
    }

    /** Clicks the X to close the AP Voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox close() {
        return apVoucherFooter.clickClose();
    }

    /** Reads row {@code rowNumber}'s (1-based) own Sub-Ledger name/Reference Site/Sub Ledger Id
     *  straight off the rendered grid — see {@code APVoucherSubLedgerInformation
     *  #readSubLedgerRowIdentity} for why this is a UI read, not a DB join. */
    public SubLedgerRowIdentity readSubLedgerRowIdentity(int rowNumber) {
        return apVoucherSubLedgerInformation.readSubLedgerRowIdentity(rowNumber);
    }

    /** Opens the Adjustment Entry window (Adjust → Adjusted Document menu) for an existing,
     *  already-populated Sub-Ledger row — see {@code APVoucherSubLedgerInformation
     *  #openAdjustmentEntryForExistingRow} for why this doesn't re-fill the row's other fields. */
    public AdjustmentEntry openAdjustmentEntryForExistingRow(String subLedgerName, String referenceSite, String slId) {
        return apVoucherSubLedgerInformation.openAdjustmentEntryForExistingRow(subLedgerName, referenceSite, slId);
    }

    /** Opens the Cheque/RTGS payment popup for an existing Sub-Ledger row and submits
     *  {@code payment} — see {@code APVoucherSubLedgerInformation
     *  #setPaymentInformationForExistingRow} for the before/after cross-check this enables. */
    public com.gsl.pages.Finance.Operations.Common.ApPaymentInformationPopup setPaymentInformationForExistingRow(
            String subLedgerName, String referenceSite, String slId,
            com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo payment) {
        return apVoucherSubLedgerInformation.setPaymentInformationForExistingRow(subLedgerName, referenceSite, slId, payment);
    }

    /** Edits an existing Advance-mode row's Amount (Dr) cell directly — see
     *  {@code APVoucherSubLedgerInformation#setAmountForExistingRow} for why this only applies to
     *  Advance mode (Document mode's Amount cell is read-only). */
    public ApVoucherAdd setAmountForExistingRow(int rowIndex, String newAmount) {
        apVoucherSubLedgerInformation.setAmountForExistingRow(rowIndex, newAmount);
        return this;
    }
}
