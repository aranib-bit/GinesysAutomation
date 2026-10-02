package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherSubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;

import java.util.ArrayList;
import java.util.List;

public class ArVoucherAdd implements CashBankParent, VerifiablePage {

    private final ARVoucherDocumentInformation  arVoucherDocumentInformation;
    private final ARVoucherSubLedgerInformation arVoucherSubLedgerInformation;
    private final ARVoucherFooter               arVoucherFooter;

    public ArVoucherAdd() {
        com.gsl.validation.ui.UiStyleScope.set(VoucherDomain.AR);   // style baseline: Common + AR sheet
        this.arVoucherDocumentInformation = new ARVoucherDocumentInformation(this);
        this.arVoucherSubLedgerInformation = new ARVoucherSubLedgerInformation();
        this.arVoucherFooter = new ARVoucherFooter();
    }

    /** Mirrors {@code GeneralVoucherAdd#verifyOnThisPage()} — accepts either the Add or Edit
     *  window title, since this facade serves both flows. */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getWindowTitleText();
        boolean onAddOrEditForm = actual != null
                && (actual.equalsIgnoreCase(VoucherDomain.AR.getAddFormHeader())
                    || actual.equalsIgnoreCase(VoucherDomain.AR.getEditFormHeader()));
        if (!onAddOrEditForm) {
            throw new IllegalStateException(
                    "Expected the AR Voucher Add/Edit form to be open (page header '"
                    + VoucherDomain.AR.getAddFormHeader() + "' or '" + VoucherDomain.AR.getEditFormHeader()
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    /** Reduced-scope relative to {@code GeneralVoucherAdd#verifyEditFormFullyLoaded} — confirms
     *  the Edit window title and waits out the post-open ExtJS mask, but does NOT round-trip a
     *  Voucher No. field read-back: {@code ARVoucherDocumentInformation} has no
     *  {@code getSavedDocumentNo()}-style getter today (GV-only addition). */
    public void verifyEditFormFullyLoaded(String expectedVoucherNo) {
        WaitUtils.waitForExtMask("Open Voucher for Edit");
        String actualTitle = PageHeaderUtils.getWindowTitleText();
        boolean titleOk = actualTitle != null
                && actualTitle.equalsIgnoreCase(VoucherDomain.AR.getEditFormHeader());
        if (!titleOk) {
            throw new IllegalStateException(
                    "Edit form not fully loaded — expected window title '"
                    + VoucherDomain.AR.getEditFormHeader() + "' but got '" + actualTitle + "'.");
        }
    }

    /** Full UI-contract sweep — Document Information fields (Owner Site/Cash-Bank/Date/Remarks),
     *  the Sub-Ledger Information grid's headers, and the footer Save button — collecting every
     *  {@link UiValidationFailure} and soft-asserting them all at once (same convention as
     *  {@code GeneralVoucherAdd#validateUiContract}). No AR-only Document Information fields exist
     *  (unlike GV's Balance/Net Amount/Released Status), and no per-row grid-cell validation is
     *  included in this pass (see the framework plan's "Scope note common to both" for AR/AP).
     *
     *  <p>{@code isEditMode} is the same Owner-Site/Remarks state-divergence override
     *  {@code GeneralVoucherAdd#validateUiContract} documents — both rules are codebase-wide facts
     *  (no Owner Site setter anywhere; Remarks' ExtJS conditional-enable), not GV-specific, so they
     *  apply identically here via the same common base-class overloads. */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        List<UiValidationFailure> failures = new ArrayList<>();

        failures.addAll(arVoucherDocumentInformation.validateDocumentInformationSectionHeaderUi(level));
        failures.addAll(arVoucherDocumentInformation.validateOwnerSiteUi(level, isEditMode, true));
        failures.addAll(arVoucherDocumentInformation.validateCashBankUi(level, state, true));
        failures.addAll(arVoucherDocumentInformation.validateDateUi(level, state, true));
        failures.addAll(arVoucherDocumentInformation.validateRemarksUi(level, state, isEditMode, false));

        failures.addAll(arVoucherSubLedgerInformation.validateSubLedgerGridHeaderUi(level));
        failures.addAll(arVoucherSubLedgerInformation.validateReferenceSiteGridHeaderUi(level));

        boolean saveExpectedEnabled = state == UiFieldState.EDITABLE;
        failures.addAll(arVoucherFooter.validateSaveButtonUi(level, saveExpectedEnabled));

        UiValidationReporter.renderAndAssert(failures);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ArVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        arVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public ARVoucherDocumentInformation getDocumentInformation() {
        return arVoucherDocumentInformation;
    }

    public ArVoucherAdd fillSubLedgerRows(MergedFinanceArVoucherData data, boolean includeRetry) {
        arVoucherSubLedgerInformation.fillSubLedgerRows(data, includeRetry);
        return this;
    }

    @LogStep("Save AR Voucher")
    public DocumentPopup clickSave() {
        JavascriptExecutorUtils.setSubLedgerVerify("arvch_grid");
        arVoucherFooter.clickSave();
        return new DocumentPopup();
    }

    /** Clicks the X to close the AR Voucher form; returns the discard-confirmation dialog */
    public DocumentMessageBox close() {
        return arVoucherFooter.clickClose();
    }
}
