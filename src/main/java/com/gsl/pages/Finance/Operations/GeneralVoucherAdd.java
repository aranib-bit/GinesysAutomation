package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.Finance.Operations.Common.FooterMode;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherFooter;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.PageStylingSpec;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;
import com.gsl.validation.ui.UiValidationScope;
import org.assertj.core.api.SoftAssertions;
import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level facade page for creating/viewing a General Voucher — mirrors
 * {@code ApVoucherAdd}/{@code ArVoucherAdd}: composes page components,
 * method-chaining, returns {@code this}.
 */
public class GeneralVoucherAdd implements CashBankParent, VerifiablePage {

    private final GeneralVoucherDocumentInformation  generalVoucherDocumentInformation;
    private final GeneralVoucherExpenseIncomeDetail   generalVoucherExpenseIncomeDetail;
    private final GeneralVoucherFooter                generalVoucherFooter;

    public GeneralVoucherAdd() {
        com.gsl.validation.ui.UiStyleScope.set(VoucherDomain.GV);   // style baseline: Common + GV sheet
        this.generalVoucherDocumentInformation = new GeneralVoucherDocumentInformation(this);
        this.generalVoucherExpenseIncomeDetail  = new GeneralVoucherExpenseIncomeDetail();
        this.generalVoucherFooter               = new GeneralVoucherFooter();
    }

    /**
     * Confirms the browser is actually showing this voucher's Add/Edit form right now, via its
     * own window title-bar text — added (Round 23) after a confirmed live race where
     * {@code VoucherListPageBase}'s Actions→Edit click registered as successful while the app
     * silently stayed on the list page: the failure only surfaced 30+ seconds later, deep inside
     * the Edit form, as a confusing "General ledger column not found" error
     * (FIN-GV-EDIT-DETAIL-01, run 20260908_135507). {@code GeneralVoucherAdd} serves both the Add
     * flow ({@code clickAdd()}) and every Edit entry path, so both header shapes are accepted —
     * confirmed against a live screenshot of this exact page ("Add: General Voucher").
     * Deliberately checks for the colon-qualified header, not a bare
     * {@code contains("General Voucher")} — the list page's own header ("General Vouchers",
     * plural, no colon) would otherwise false-positive-match and defeat the whole check.
     *
     * <p><b>Correction (this session):</b> this originally read {@link PageHeaderUtils#getPageHeaderText()}
     * — the shared LIST-page toolbar text — which the Add/Edit form does not use at all, so the
     * check always matched the STALE underlying list-page header ("General Vouchers") instead,
     * confirmed live across many runs (e.g. `20260909_094016`, `20260909_112203`) even while the
     * Add form was genuinely, correctly open on screen. Now reads
     * {@link PageHeaderUtils#getWindowTitleText()} instead — the Add/Edit form's own
     * {@code winTitleType1} window title, confirmed live via DevTools inspection of this exact
     * page. See that method's Javadoc for the full mechanism.
     *
     * <p>Expected header text is centralized in {@link VoucherDomain#getAddFormHeader()}/
     * {@link VoucherDomain#getEditFormHeader()} (backed by {@code FinanceVoucherAddEditPageHeader}
     * — the single source of truth) rather than hardcoded here, mirroring
     * {@code VoucherListGridHeaderBase#verifyOnThisPage()}'s own delegation to
     * {@link VoucherDomain#getListPageHeader()}.
     */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getWindowTitleText();
        boolean onAddOrEditForm = actual != null
                && (actual.equalsIgnoreCase(VoucherDomain.GV.getAddFormHeader())
                    || actual.equalsIgnoreCase(VoucherDomain.GV.getEditFormHeader()));
        if (!onAddOrEditForm) {
            throw new IllegalStateException(
                    "Expected the General Voucher Add/Edit form to be open (page header '"
                    + VoucherDomain.GV.getAddFormHeader() + "' or '" + VoucherDomain.GV.getEditFormHeader()
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public GeneralVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        generalVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public GeneralVoucherDocumentInformation getDocumentInformation() {
        return generalVoucherDocumentInformation;
    }

    /**
     * Round 23 — confirms the Edit form has GENUINELY finished rendering before the caller
     * touches any field, not just that its window title flipped (see {@link #verifyOnThisPage()},
     * which only checks that — ExtJS flips the title before the form's own data-bound fields,
     * including "Voucher No.", have finished loading; a live run (FIN-GV-EDIT-PAYMENT-01, run
     * 20260926_231950) hit a {@code NoSuchElementException} filling a field on a form that LOOKED
     * open by title alone but hadn't actually bound its data yet).
     *
     * <p>Sequence: (1) waits out the brief ExtJS loading mask the app shows right after the
     * double-click/Actions→Edit click (confirmed live), (2) re-confirms the window title, (3)
     * confirms the "Voucher No." field shows the expected, already-saved document number — the
     * strongest of the signals, since it requires the server round trip that populates this
     * field to have completed, (4) separately confirms that same field is still read-only. Round
     * 24 split (3) and (4) apart deliberately: run `20260927_011136` showed the Edit form actually
     * renders THREE fields labeled "Voucher No.:" (an auto-combo and a manual-doc-scheme textfield,
     * both hidden depending on doc-numbering-scheme config, plus the real visible readonly field) —
     * {@link GeneralVoucherDocumentInformation#getSavedDocumentNo()} resolves whichever one is
     * genuinely visible, unconditional on its read-only state, so check (3) keeps confirming "the
     * correct document opened" even if a future regression makes the field editable; check (4)
     * then reports THAT regression with its own specific message, rather than either check masking
     * or being confused with the other. Deliberately does NOT check the footer Save button — its
     * enabled/visible state depends on unrelated factors (form validation, balance) and would risk
     * false failures unrelated to "has the form opened."
     *
     * <p>Also measures and asserts this round's page-styling regression checks (font-size/
     * font-family for the breadcrumb, window title, and Voucher No. field) once the page is
     * confirmed genuinely rendered — the correct moment to measure real styling. Values are
     * deposited into {@link ActualResultsHolder} for the "Actual Results" report regardless of
     * outcome, then soft-asserted (collecting all 3 mismatches into one failure, same convention
     * as this codebase's DB validators) so a style regression genuinely fails the test rather
     * than only showing a cosmetic badge in the report.
     *
     * @param expectedVoucherNo the {@code scheme_docno} this Edit form was opened for
     * @throws IllegalStateException if the title, Voucher No. value, or Voucher No. read-only
     *         check doesn't pass
     * @throws AssertionError if any of the 3 font-style checks mismatch
     */
    /** Voucher number of the Edit form that was opened - compared with the read-only Voucher No. display. */
    private String openedVoucherNo;

    public void verifyEditFormFullyLoaded(String expectedVoucherNo) {
        this.openedVoucherNo = expectedVoucherNo;
        WaitUtils.waitForExtMask("Open Voucher for Edit");

        String actualTitle = PageHeaderUtils.getWindowTitleText();
        boolean titleOk = actualTitle != null
                && actualTitle.equalsIgnoreCase(VoucherDomain.GV.getEditFormHeader());
        if (!titleOk) {
            throw new IllegalStateException(
                    "Edit form not fully loaded — expected window title '"
                    + VoucherDomain.GV.getEditFormHeader() + "' but got '" + actualTitle + "'.");
        }

        String actualVoucherNo = generalVoucherDocumentInformation.getSavedDocumentNo();
        String expectedTrimmed = expectedVoucherNo == null ? "" : expectedVoucherNo.trim();
        boolean voucherNoOk = actualVoucherNo != null
                && actualVoucherNo.trim().equalsIgnoreCase(expectedTrimmed);
        if (!voucherNoOk) {
            throw new IllegalStateException(
                    "Edit form not fully loaded — expected Voucher No. field to show '"
                    + expectedVoucherNo + "' but got '" + actualVoucherNo + "'.");
        }

        // Round 24 — separate, dedicated check: deliberately independent of the value check above,
        // so a regression that makes this field editable fails with its own clear message instead
        // of being masked by (or confused with) a "wrong document opened" failure.
        if (!generalVoucherDocumentInformation.isSavedDocumentNoReadOnly()) {
            throw new IllegalStateException(
                    "Edit form's Voucher No. field is editable — expected it to remain read-only "
                    + "(aria-readonly='true') while editing an existing General Voucher.");
        }

        // Round 23 continued — page-styling regression checks (report + assertion).
        String breadcrumbFont = PageHeaderUtils.getBreadcrumbFont();
        String titleFont      = PageHeaderUtils.getWindowTitleFont();
        String voucherNoFont  = generalVoucherDocumentInformation.getSavedDocumentNoFont();

        ActualResultsHolder holder = ActualResultsHolder.get();
        holder.setUiBreadcrumbFont(breadcrumbFont);
        holder.setUiWindowTitleFont(titleFont);
        holder.setUiSavedDocumentNoFont(voucherNoFont);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(breadcrumbFont).as("Breadcrumb font (size|family)")
                .isEqualTo(PageStylingSpec.BREADCRUMB.expectedFont());
        soft.assertThat(titleFont).as("Window title font (size|family)")
                .isEqualTo(PageStylingSpec.WINDOW_TITLE.expectedFont());
        soft.assertThat(voucherNoFont).as("Voucher No. field font (size|family)")
                .isEqualTo(PageStylingSpec.READONLY_DISPLAY_FIELD.expectedFont());
        soft.assertAll();
    }

    public GeneralVoucherAdd fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data) {
        generalVoucherExpenseIncomeDetail.fillExpenseIncomeRows(data);
        return this;
    }

    /** See {@code GeneralVoucherExpenseIncomeDetail#fillExpenseIncomeRows(MergedFinanceGeneralVoucherData,
     *  boolean)} — {@code expectSiteAllocationComplete=false} only for the two deliberately-incomplete
     *  negative scenarios driven through {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}. */
    public GeneralVoucherAdd fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data,
            boolean expectSiteAllocationComplete) {
        generalVoucherExpenseIncomeDetail.fillExpenseIncomeRows(data, expectSiteAllocationComplete);
        return this;
    }

    public GeneralVoucherExpenseIncomeDetail getExpenseIncomeDetail() {
        return generalVoucherExpenseIncomeDetail;
    }

    /** Edits an existing Expense/Income Detail row in place — see
     *  {@code GeneralVoucherExpenseIncomeDetail#editRow}'s Javadoc for scope/limitations. */
    public GeneralVoucherAdd editExpenseIncomeRow(int rowIndex, ExpenseIncomeDetail newValues) {
        generalVoucherExpenseIncomeDetail.editRow(rowIndex, newValues);
        return this;
    }

    /** Appends and fills a brand-new Expense/Income Detail row — plan §4B's "add a new line"
     *  shape. See {@code GeneralVoucherExpenseIncomeDetail#addRow}'s Javadoc for scope. */
    public GeneralVoucherAdd addExpenseIncomeRow(int rowIndex, ExpenseIncomeDetail newRow,
            List<SiteAllocation> allocations) {
        generalVoucherExpenseIncomeDetail.addRow(rowIndex, newRow, allocations);
        return this;
    }

    /** Fills a {@code TYPE1_NONE} row without the full combination-matrix fill pipeline — see
     *  {@code GeneralVoucherExpenseIncomeDetail#fillTypeOneNoneRow}'s Javadoc for scope. */
    public GeneralVoucherAdd fillTypeOneNoneRow(int rowIndex, ExpenseIncomeDetail row) {
        generalVoucherExpenseIncomeDetail.fillTypeOneNoneRow(rowIndex, row);
        return this;
    }

    /** Re-opens a row's Allocate Sites popup and applies a new split — plan §4B's
     *  "site-split-only" shape. See {@code GeneralVoucherExpenseIncomeDetail#reallocateSites}'s
     *  Javadoc, including its UNCONFIRMED live-DOM caveat. */
    public GeneralVoucherAdd reallocateExpenseIncomeRowSites(int rowIndex, List<SiteAllocation> newSiteAllocations) {
        generalVoucherExpenseIncomeDetail.reallocateSites(rowIndex, newSiteAllocations);
        return this;
    }

    /**
     * Fills the Cash/Bank counter-leg's Payment Window — see {@code GeneralVoucherExpenseIncomeDetail
     * #handlePaymentInformation} for the no-op cases (null / CASH) and what {@code debitLine}
     * (the line's own Dr/Cr side) selects between.
     */
    public GeneralVoucherAdd handlePaymentInformation(PaymentInformation payment, boolean debitLine) {
        generalVoucherExpenseIncomeDetail.handlePaymentInformation(payment, debitLine);
        return this;
    }

    /** Fills the Payment Window of every grid row that carries a payment — see
     *  {@code GeneralVoucherExpenseIncomeDetail#handleRowPayments}. */
    public GeneralVoucherAdd handleRowPayments(MergedFinanceGeneralVoucherData data) {
        generalVoucherExpenseIncomeDetail.handleRowPayments(data);
        return this;
    }

    /** See {@code VoucherFooterBase#waitUntilSaveEnabled} — polls for ExtJS to actually report
     *  Save as enabled before it's clicked, since a greyed-out (unbalanced-voucher) Save button
     *  still passes Selenium's own {@code elementToBeClickable} check. */
    public boolean waitUntilSaveEnabled(int timeoutSeconds) {
        return generalVoucherFooter.waitUntilSaveEnabled(timeoutSeconds);
    }

    @LogStep("Save General Voucher")
    public DocumentPopup clickSave() {
        return generalVoucherFooter.clickSave();
    }

    /** Clicks the X to close the General Voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox close() {
        return generalVoucherFooter.clickClose();
    }

    // ── UI-contract validation orchestrator ──────────────────────────────────────────────────
    //
    // "Document Information" is now validated via the common
    // VoucherDocumentInformationBase#validateDocumentInformationSectionHeaderUi (identical text/
    // component across GV/AR/AP) — only GV's OWN second section keeps its own locator here, since
    // its text ("Expense/Income Detail") is GV-specific (AR/AP's own second section is "Sub Ledger
    // Information" instead). Same gsl_TitlePnlType1 displayfield-div component confirmed live
    // (2026-10-01, user's own DevTools inspection) for "Document Information"; assumed (not yet
    // independently confirmed) for this one too.
    private static final By EXPENSE_INCOME_SECTION_HEADER =
            By.xpath("//div[contains(@class,'gsl_TitlePnlType1') and normalize-space(.)='Expense/Income Detail']");

    /** {@link #validateUiContract(UiValidationLevel, UiFieldState, boolean, int)} against the
     *  grid's first row (index 0) — the common case for a single-line-item voucher. */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        validateUiContract(level, state, isEditMode, 0);
    }

    /** Same sweep for a document whose status is known: pass {@link FooterMode#POSTED} for a Posted
     *  voucher (Save not shown, fields {@link UiFieldState#READONLY}). */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode,
            FooterMode footerMode) {
        validateUiContract(level, state, isEditMode, 0, footerMode);
    }

    /**
     * Full UI-contract sweep — page header/section headers, Document Information fields (Owner
     * Site/Cash-Bank/Date/Remarks/Balance/Net Amount), the Expense/Income Detail grid's headers
     * and one representative row, and the footer Save button — collecting every
     * {@link UiValidationFailure} from every component validator and soft-asserting them all at
     * once (same collect-all-then-assert convention as {@link #verifyEditFormFullyLoaded}).
     *
     * <p>{@code state} is how "View Mode" is expressed per the framework's design (Ginesys has no
     * separate View page — see {@code UiFieldState}'s own Javadoc): pass
     * {@link UiFieldState#READONLY} for a Posted voucher, {@link UiFieldState#EDITABLE} for an
     * in-progress Add/Edit. The Save button's expected enabled state is derived from {@code state}
     * (editable ⇒ enabled, read-only ⇒ disabled) rather than taken as a separate parameter.
     *
     * <p>{@code isEditMode} overrides {@code state} for two fields confirmed live (run
     * 2026-10-01, user-confirmed both are correct/intended app behavior, not bugs) to NOT follow
     * the general editable/read-only rule:
     * <ul>
     *   <li><b>Owner Site</b> is EDITABLE only on Add ({@code isEditMode=false}) — there is no
     *       setter for it anywhere in this codebase, confirming it's permanently non-interactive
     *       once a voucher exists; always {@code DISABLED} when {@code isEditMode=true},
     *       regardless of {@code state}.</li>
     *   <li><b>Remarks</b> is {@code DISABLED} on a freshly-opened, nothing-yet-filled Add form
     *       ({@code isEditMode=false}) — ExtJS only enables it once a prerequisite field (Owner
     *       Site/Cash-Bank) is chosen, which this UI-chrome-only check never fills in — and follows
     *       the normal {@code state} rule once real data exists, i.e. on Edit
     *       ({@code isEditMode=true}).</li>
     * </ul>
     *
     * <p>Required-ness assumed here (Owner Site/Cash-Bank/Date required, Remarks optional) and the
     * two section-header locators above are both best-effort, pending live confirmation — see the
     * framework plan's "Open items" section. {@link GeneralVoucherDocumentInformation#validateReleasedStatusUi}
     * is deliberately NOT part of this sweep (it needs the document's actual expected status text,
     * which this generic orchestrator doesn't have) — callers that know it can invoke that method
     * directly.
     *
     * @param gridRowIndexToCheck which Expense/Income Detail row to validate cell-level style on
     */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode,
            int gridRowIndexToCheck) {
        validateUiContract(level, state, isEditMode, gridRowIndexToCheck, FooterMode.of(isEditMode));
    }

    /** Full sweep with an explicit {@link FooterMode} (see {@link FooterMode}: Copy is Add-only, Save is
     *  shown only for an Unposted document). */
    public void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode,
            int gridRowIndexToCheck, FooterMode footerMode) {
        validateUiContract(level, state, isEditMode, gridRowIndexToCheck, footerMode, false, false);
    }

    /** As {@link #validateUiContract(UiValidationLevel, UiFieldState, boolean)} but DEFERRED: the
     *  report is rendered, and failures are parked in {@code UiFailureCollector} instead of thrown,
     *  so the flow's Save / DB validation / snapshot validation still run. The flow must end with
     *  {@code VoucherSaveFlowSteps.finalizeAsserts} (Create) or {@code UiFailureCollector
     *  .assertNoPending()} to report them. */
    public void validateUiContractDeferred(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        validateUiContract(level, state, isEditMode, 0, FooterMode.of(isEditMode), true, false);
    }

    /** Blank-form sweep taken BEFORE a business fill: results are HELD (not rendered) and merged into the single
     *  report that {@link #endFilledFormUiDeferred} renders after the fill. The grid row is left to the fill
     *  observer's per-row "Before fill" node, so rows are not duplicated. */
    public void validateUiContractBeforeFill(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        validateUiContract(level, state, isEditMode, 0, FooterMode.of(isEditMode), true, true);
    }

    private void validateUiContract(UiValidationLevel level, UiFieldState state, boolean isEditMode,
            int gridRowIndexToCheck, FooterMode footerMode, boolean deferred, boolean beforeFill) {
        List<UiValidationFailure> failures = new ArrayList<>();

        failures.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(), () -> {
            List<UiValidationFailure> doc = new ArrayList<>();
            doc.addAll(generalVoucherDocumentInformation.validateDocumentInformationSectionHeaderUi(level));
            doc.addAll(sectionState(FormSection.DOCUMENT_INFORMATION));
            // Same sub-section grouping as VoucherUiValidationStage (the common stage AP/AR use).
            doc.addAll(UiValidationScope.node("Voucher No.", () ->
                    generalVoucherDocumentInformation.validateVoucherNoUi(level, isEditMode, true, openedVoucherNo)));
            doc.addAll(UiValidationScope.node("Reference Details", () ->
                    generalVoucherDocumentInformation.validateReferenceUi(level, isEditMode ? UiFieldState.EDITABLE : state, false)));
            doc.addAll(UiValidationScope.node("Owner Site Details", () ->
                    generalVoucherDocumentInformation.validateOwnerSiteUi(level, isEditMode, true)));
            doc.addAll(UiValidationScope.node("Cash/Bank Details", () -> {
                List<UiValidationFailure> r = new ArrayList<>();
                r.addAll(generalVoucherDocumentInformation.validateCashBankUi(level, state, true));
                r.addAll(generalVoucherDocumentInformation.validateBalanceUi(level, isEditMode));
                return r;
            }));
            doc.addAll(UiValidationScope.node("Date Details", () ->
                    generalVoucherDocumentInformation.validateDateUi(level, state, true)));
            doc.addAll(UiValidationScope.node("Remarks", () ->
                    generalVoucherDocumentInformation.validateRemarksUi(level, state, isEditMode, false)));
            doc.addAll(UiValidationScope.node("Net Amount", () -> {
                List<UiValidationFailure> r = new ArrayList<>();
                r.addAll(generalVoucherDocumentInformation.validateNetAmountUi(level, isEditMode));
                r.addAll(generalVoucherDocumentInformation.validateNetAmountLabelUi(level));
                return r;
            }));
            return doc;
        }));

        failures.addAll(UiValidationScope.node(EXPENSE_INCOME_NODE, () -> {
            List<UiValidationFailure> grid = new ArrayList<>();
            grid.addAll(UiComponentValidator.validateSectionHeader(
                    EXPENSE_INCOME_SECTION_HEADER, "SECTION_HEADER_EXPENSE_INCOME_DETAIL",
                    FormSection.EXPENSE_INCOME_DETAIL.title(), level));
            grid.addAll(sectionState(FormSection.EXPENSE_INCOME_DETAIL));
            grid.addAll(beforeFill
                    ? generalVoucherExpenseIncomeDetail.validateGridHeaderAndToolbarUi(level)
                    : generalVoucherExpenseIncomeDetail.validateExpenseIncomeGridUi(gridRowIndexToCheck, level));
            // Grid footer: label text only here — this generic sweep doesn't know the expected
            // sums (see validateAddFormFlowUi for the value checks before/after each row).
            grid.addAll(UiValidationScope.node("Grid footer", () -> beforeFill
                    ? UiValidationScope.node("Before fill", () -> generalVoucherExpenseIncomeDetail.validateGridFooterUi())
                    : generalVoucherExpenseIncomeDetail.validateGridFooterUi()));
            return grid;
        }));

        // The Payment Information section is shown only when the voucher carries a (non-cash)
        // payment — an Edit of a Cash voucher legitimately has no such section: SKIP, not a missing-
        // element FAIL. (Always shown on a blank Add form.)
        failures.addAll(UiValidationScope.node(FormSection.PAYMENT_INFORMATION.title(),
                () -> UiComponentValidator.quickPresent(FormSection.PAYMENT_INFORMATION.titleLocator(
                        FormSection.voucherWindowScope(VoucherDomain.GV)))
                        ? sectionState(FormSection.PAYMENT_INFORMATION)
                        : UiComponentValidator.skip("SECTION_PAYMENT_INFORMATION", "section state",
                                "shown when the voucher has a payment", "not shown")));

        failures.addAll(UiValidationScope.node("Page Footer", () -> {
            List<UiValidationFailure> footer = new ArrayList<>(
                    generalVoucherFooter.validateFooterButtonsUi(level, footerMode));
            if (isEditMode) {
                footer.addAll(generalVoucherDocumentInformation
                        .validateReleaseStatusConsistency(footerMode == FooterMode.POSTED));
            }
            return footer;
        }));

        if (beforeFill) {
            UiValidationReporter.holdForFill(failures);
        } else if (deferred) {
            UiValidationReporter.renderDeferred(failures);
        } else {
            UiValidationReporter.renderAndAssert(failures);
        }
    }

    private static final String EXPENSE_INCOME_NODE = FormSection.EXPENSE_INCOME_DETAIL.title();

    /** Section open/closed check — asserted OPEN, read only; state comes from the section's chevron
     *  icon (see {@link FormSection#chevronLocator}), scoped to this voucher's Add/Edit window. */
    private static List<UiValidationFailure> sectionState(FormSection section) {
        return UiComponentValidator.validateSectionState(
                section, FormSection.voucherWindowScope(VoucherDomain.GV), true);
    }

    /**
     * Full phased UI validation of the Add form, filling it as it goes: blank form → Cash/Bank
     * selected → every planned grid row filled (with its own Allocate Sites / Payment Information
     * popups where the plan says they apply) → page footer. Results nest per section and per grid
     * row (see {@link GeneralVoucherExpenseIncomeDetail#fillAndValidateRowUi}); after each row a
     * checkpoint validates Net Amount (under that row) and the grid footer values (under "Grid
     * footer / After fill"). Every expected value is computed here from {@code plans}, never read
     * back from the page. Renders and soft-asserts once at the end, like {@link #validateUiContract}.
     *
     * @param cashBank the Cash/Bank value to select once the blank-form phase is validated
     */
    public void validateAddFormFlowUi(UiValidationLevel level, String cashBank,
            List<GeneralVoucherExpenseIncomeDetail.RowUiPlan> plans) {
        List<UiValidationFailure> failures = new ArrayList<>();
        final UiFieldState editable = UiFieldState.EDITABLE;

        // Phase A — blank form
        failures.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(), () -> {
            List<UiValidationFailure> doc = new ArrayList<>();
            doc.addAll(generalVoucherDocumentInformation.validateDocumentInformationSectionHeaderUi(level));
            doc.addAll(sectionState(FormSection.DOCUMENT_INFORMATION));
            doc.addAll(UiValidationScope.node("Before Cash/Bank selection", () -> {
                List<UiValidationFailure> before = new ArrayList<>();
                before.addAll(UiValidationScope.node("Voucher No.", () ->
                        generalVoucherDocumentInformation.validateVoucherNoUi(level, false, true)));
                before.addAll(UiValidationScope.node("Reference Details", () ->
                        generalVoucherDocumentInformation.validateReferenceUi(level, editable, false)));
                before.addAll(UiValidationScope.node("Owner Site Details", () ->
                        generalVoucherDocumentInformation.validateOwnerSiteUi(level, false, true)));
                before.addAll(UiValidationScope.node("Cash/Bank Details", () -> {
                    List<UiValidationFailure> r = new ArrayList<>(
                            generalVoucherDocumentInformation.validateCashBankUi(level, editable, true));
                    r.addAll(generalVoucherDocumentInformation.validateBalanceUi(level, false));
                    return r;
                }));
                before.addAll(UiValidationScope.node("Date Details", () ->
                        generalVoucherDocumentInformation.validateDateUi(level, editable, true)));
                before.addAll(UiValidationScope.node("Remarks", () ->
                        generalVoucherDocumentInformation.validateRemarksUi(level, editable, false, false)));
                before.addAll(UiValidationScope.node("Net Amount", () -> {
                    List<UiValidationFailure> r = new ArrayList<>(
                            generalVoucherDocumentInformation.validateNetAmountLabelUi(level));
                    r.addAll(generalVoucherDocumentInformation.validateNetAmountUi(level, false));
                    return r;
                }));
                return before;
            }));
            return doc;
        }));
        final java.util.Map<String, com.gsl.validation.ui.UiComponentValidator.FieldSnapshot> headerBefore =
                generalVoucherDocumentInformation.captureFieldSnapshots();
        final java.util.Map<String, String> footerBlank = new com.gsl.validation.ui.voucher.VoucherGridChrome(
                VoucherDomain.GV).readFooter();
        failures.addAll(UiValidationScope.node(FormSection.PAYMENT_INFORMATION.title(), () -> {
            List<UiValidationFailure> pay = new ArrayList<>(sectionState(FormSection.PAYMENT_INFORMATION));
            pay.addAll(UiValidationScope.node("Blank form",
                    () -> generalVoucherExpenseIncomeDetail.validatePaymentPanelUi(null, level)));
            return pay;
        }));
        failures.addAll(UiValidationScope.node(EXPENSE_INCOME_NODE, () -> {
            List<UiValidationFailure> grid = new ArrayList<>();
            grid.addAll(sectionState(FormSection.EXPENSE_INCOME_DETAIL));
            grid.addAll(generalVoucherExpenseIncomeDetail.validateGridHeaderAndToolbarUi(level, false));
            grid.addAll(UiValidationScope.node("Grid footer", () -> UiValidationScope.node("Before fill",
                    () -> generalVoucherExpenseIncomeDetail.validateGridFooterUi(
                            "blank grid", 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO))));
            return grid;
        }));

        // Phase B — Cash/Bank selected (the grid is not genuinely editable until it is)
        fillDocumentHeader(new DocumentInformation(null, null, java.time.LocalDate.now(), null, cashBank, null));
        failures.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(), () -> UiValidationScope.node(
                "After Cash/Bank selection", () -> {
                    // Same sub-section shape as "Before Cash/Bank selection": Cash/Bank (with its Balance) and Remarks.
                    List<UiValidationFailure> after = new ArrayList<>();
                    after.addAll(UiValidationScope.node("Cash/Bank Details", () -> {
                        List<UiValidationFailure> r = new ArrayList<>(
                                generalVoucherDocumentInformation.validateCashBankUi(level, editable, true));
                        r.addAll(generalVoucherDocumentInformation.validateBalanceUi(level, true));
                        return r;
                    }));
                    after.addAll(UiValidationScope.node("Remarks", () ->
                            generalVoucherDocumentInformation.validateRemarksUi(level, editable, true, false)));
                    return after;
                })));
        final java.util.Map<String, com.gsl.validation.ui.UiComponentValidator.FieldSnapshot> headerAfter =
                generalVoucherDocumentInformation.captureFieldSnapshots();
        failures.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(), () -> UiValidationScope.node(
                "Before / after Cash/Bank selection",
                () -> generalVoucherDocumentInformation.validateFieldsBeforeAfterUi(headerBefore, headerAfter))));
        final com.gsl.validation.ui.voucher.VoucherGridChrome footerChrome =
                new com.gsl.validation.ui.voucher.VoucherGridChrome(VoucherDomain.GV);
        final java.util.Map<String, String>[] footerPrev = new java.util.Map[]{footerBlank};

        // Phase C — rows, one at a time, with running-total checkpoints
        java.math.BigDecimal sumDr = java.math.BigDecimal.ZERO;
        java.math.BigDecimal sumCr = java.math.BigDecimal.ZERO;
        for (int i = 0; i < plans.size(); i++) {
            final int rowIndex = i;
            final GeneralVoucherExpenseIncomeDetail.RowUiPlan plan = plans.get(i);
            if (plan.row().getExpenseDr() != null) sumDr = sumDr.add(plan.row().getExpenseDr());
            if (plan.row().getIncomeCr() != null) sumCr = sumCr.add(plan.row().getIncomeCr());
            final java.math.BigDecimal dr = sumDr;
            final java.math.BigDecimal cr = sumCr;
            final String checkpoint = "after Row " + (rowIndex + 1);

            failures.addAll(UiValidationScope.node(EXPENSE_INCOME_NODE, () -> {
                List<UiValidationFailure> grid = new ArrayList<>(
                        generalVoucherExpenseIncomeDetail.fillAndValidateRowUi(rowIndex, plan, level));
                // Net Amount checkpoint, nested under this row's own node (same-name nodes merge).
                java.math.BigDecimal net = dr.subtract(cr);
                if (net.signum() != 0) {
                    grid.addAll(UiValidationScope.node(
                            GeneralVoucherExpenseIncomeDetail.rowNodeLabel(rowIndex, plan), () ->
                            UiValidationScope.node("Checkpoint " + checkpoint + " · Net Amount",
                                    () -> generalVoucherDocumentInformation.validateNetAmountAfterRowUi(
                                            level, net.abs(), net.signum() > 0 ? "Payment" : "Receipt", checkpoint))));
                }
                grid.addAll(UiValidationScope.node("Grid footer", () -> UiValidationScope.node("After fill",
                        () -> generalVoucherExpenseIncomeDetail.validateGridFooterUi(
                                checkpoint, rowIndex + 1, dr, cr))));
                // Record Count delta (+1) against the footer after the previous row, per row.
                final java.util.Map<String, String> now = footerChrome.readFooter();
                final java.util.Map<String, String> prev = footerPrev[0];
                grid.addAll(UiValidationScope.node("Grid footer", () -> UiValidationScope.node(
                        "Change " + checkpoint, () -> footerChrome.validateFooterChange(
                                "row " + (rowIndex + 1) + " added", prev, now, 1, null))));
                footerPrev[0] = now;
                return grid;
            }));
        }

        failures.addAll(UiValidationScope.node("Page Footer",
                () -> generalVoucherFooter.validateFooterButtonsUi(level, FooterMode.ADD)));

        UiValidationReporter.renderAndAssert(failures);
    }

    // ── UI validation of the REAL (business) fill — Create flow ───────────────────────────────────
    // The Create flow fills the form through the normal business code. These two calls bracket that
    // fill: begin installs an observer that validates each row once it is filled and each popup while
    // it is already open (no extra clicks, no reopening); end renders everything as ONE deferred
    // result (see UiValidationReporter#renderDeferred), so the flow still reaches Save and the DB /
    // snapshot validation whatever the UI findings are.

    private FilledFormUiRecorder filledFormRecorder;

    /** Starts recording UI checks of the fill that follows. Pair with {@link #endFilledFormUiDeferred}. */
    public void beginFilledFormUi(MergedFinanceGeneralVoucherData data, UiValidationLevel level) {
        filledFormRecorder = new FilledFormUiRecorder(data, level);
        generalVoucherExpenseIncomeDetail.setFillUiListener(filledFormRecorder);
    }

    /** Stops recording and renders the collected checks as a deferred "UI Validation" result. */
    public void endFilledFormUiDeferred() {
        generalVoucherExpenseIncomeDetail.setFillUiListener(null);
        FilledFormUiRecorder recorder = filledFormRecorder;
        filledFormRecorder = null;
        if (recorder != null) {
            UiValidationReporter.renderDeferred(recorder.failures());
        }
    }

    private final class FilledFormUiRecorder implements GeneralVoucherExpenseIncomeDetail.FillUiListener {
        private final MergedFinanceGeneralVoucherData data;
        private final UiValidationLevel level;
        private final List<UiValidationFailure> failures = new ArrayList<>();
        private java.math.BigDecimal dr = java.math.BigDecimal.ZERO;
        private java.math.BigDecimal cr = java.math.BigDecimal.ZERO;
        FilledFormUiRecorder(MergedFinanceGeneralVoucherData data, UiValidationLevel level) {
            this.data = data;
            this.level = level;
        }

        List<UiValidationFailure> failures() { return failures; }

        /** Row {@code i} as the test data defines it, with that row's OWN payment (if any) and Dr/Cr side. */
        private GeneralVoucherExpenseIncomeDetail.RowUiPlan plan(int i) {
            ExpenseIncomeDetail row = data.getExpenseIncomeDetailList().get(i);
            return new GeneralVoucherExpenseIncomeDetail.RowUiPlan(row,
                    data.getSiteAllocationsForLine(row.getLineNo()),
                    data.paymentForLine(row.getLineNo()), data.isPaymentDebit(row.getLineNo()));
        }

        /** Adds {@code body}'s results under Expense/Income Detail > the row's node. */
        private void underRow(int i, java.util.function.Supplier<List<UiValidationFailure>> body) {
            failures.addAll(UiValidationScope.node(EXPENSE_INCOME_NODE, () ->
                    UiValidationScope.node(GeneralVoucherExpenseIncomeDetail.rowNodeLabel(i, plan(i)), body)));
        }

        private String siteHeader(int i) {
            ExpenseIncomeDetail row = plan(i).row();
            boolean hasSl = row.getSubLedger() != null && !row.getSubLedger().isBlank();
            return "Allocate Sites · GL: " + row.getGeneralLedger() + (hasSl ? " · SL: " + row.getSubLedger() : "");
        }

        private String paymentHeader(PaymentInformation payment) {
            return "Payment Information · " + payment.getPaymentMode()
                    + (payment.getInstrumentNo() == null ? "" : " · " + payment.getInstrumentNo());
        }

        @Override
        public void beforeRowFilled(int i, ExpenseIncomeDetail row) {
            underRow(i, () -> UiValidationScope.node("Before fill", () -> UiValidationScope.node("Cells and row buttons",
                    () -> generalVoucherExpenseIncomeDetail.validateRowCellsUi(i, null, level))));
        }

        @Override
        public void afterRowFilled(int i, ExpenseIncomeDetail row) {
            GeneralVoucherExpenseIncomeDetail.RowUiPlan plan = plan(i);
            if (row.getExpenseDr() != null) dr = dr.add(row.getExpenseDr());
            if (row.getIncomeCr() != null) cr = cr.add(row.getIncomeCr());
            final java.math.BigDecimal runDr = dr;
            final java.math.BigDecimal runCr = cr;
            final String checkpoint = "after Row " + (i + 1);
            final int n = i + 1;

            for (com.gsl.validation.ui.PickerUi.Observation o : generalVoucherExpenseIncomeDetail.drainPickerObservations(i)) {
                underRow(i, () -> UiValidationScope.node(o.label(), () -> com.gsl.validation.ui.PickerUi.validate(o)));
            }
            underRow(i, () -> {
                List<UiValidationFailure> f = new ArrayList<>(UiValidationScope.node("After fill",
                        () -> UiValidationScope.node("Cells and row buttons",
                                () -> generalVoucherExpenseIncomeDetail.validateRowCellsUi(i, plan, level))));
                if (!plan.hasSite()) {
                    f.addAll(UiComponentValidator.skip("ALLOCATE_SITES_ROW_" + n, "applicability",
                            "not applicable (no Site on this row)", "not applicable"));
                }
                if (!plan.hasPayment()) {
                    f.addAll(UiComponentValidator.skip("PAYMENT_INFO_ROW_" + n, "applicability",
                            "not applicable (no Cheque/RTGS data)", "not applicable"));
                }
                return f;
            });

            java.math.BigDecimal net = runDr.subtract(runCr);
            if (net.signum() != 0) {
                underRow(i, () -> UiValidationScope.node("Checkpoint " + checkpoint + " · Net Amount",
                        () -> generalVoucherDocumentInformation.validateNetAmountAfterRowUi(
                                level, net.abs(), net.signum() > 0 ? "Payment" : "Receipt", checkpoint)));
            }
            failures.addAll(UiValidationScope.node(EXPENSE_INCOME_NODE, () -> UiValidationScope.node("Grid footer",
                    () -> UiValidationScope.node("After fill",
                            () -> generalVoucherExpenseIncomeDetail.validateGridFooterUi(checkpoint, n, runDr, runCr)))));
        }

        @Override
        public void beforeSiteAllocation(int i, com.gsl.pages.Finance.Operations.Common.AllocateSitesPopup popup) {
            underRow(i, () -> UiValidationScope.node(siteHeader(i), () -> UiValidationScope.node("Before fill", () -> {
                List<UiValidationFailure> before = new ArrayList<>(popup.collectUiContract(level));
                before.addAll(popup.collectValuesUi(List.of(), plan(i).amount(), level));
                return before;
            })));
        }

        @Override
        public void afterSiteAllocation(int i, com.gsl.pages.Finance.Operations.Common.AllocateSitesPopup popup,
                List<SiteAllocation> allocations) {
            underRow(i, () -> UiValidationScope.node(siteHeader(i), () -> UiValidationScope.node("After fill",
                    () -> popup.collectValuesUi(allocations, plan(i).amount(), level))));
        }

        @Override
        public void beforePaymentFill(int paymentRow, com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup popup) {
            PaymentInformation payment = plan(paymentRow).payment();
            underRow(paymentRow, () -> UiValidationScope.node(paymentHeader(payment), () ->
                    UiValidationScope.node("Before fill", () -> popup.collectUiContract(level))));
        }

        @Override
        public void afterPaymentFilled(int paymentRow,
                com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup popup, PaymentInformation payment) {
            underRow(paymentRow, () -> UiValidationScope.node(paymentHeader(payment), () ->
                    UiValidationScope.node("After fill", () -> popup.collectValuesUi(payment))));
        }

        @Override
        public void afterPaymentSubmitted(int paymentRow, PaymentInformation payment) {
            generalVoucherExpenseIncomeDetail.selectRow(paymentRow);
            underRow(paymentRow, () -> UiValidationScope.node(paymentHeader(payment), () ->
                    UiValidationScope.node("Payment Information section · after selecting Row " + (paymentRow + 1),
                            () -> generalVoucherExpenseIncomeDetail.validatePaymentPanelUi(payment, level))));
        }
    }
}
