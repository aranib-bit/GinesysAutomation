package com.gsl.validation.ui.voucher;

import com.gsl.pages.enums.Finance.Operations.Common.FooterMode;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiComponentValidator.FieldSnapshot;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;
import com.gsl.validation.ui.UiValidationScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * THE common reporting stage: every voucher's UI validation (GV, AP, AR, future ones) goes through
 * this one class. It decides what is validated, in what order, under which report nodes, and how the
 * result is rendered. A voucher contributes only a {@link VoucherUiSpec}.
 *
 * <p>Usage: {@code VoucherUiValidationStage.forSpec(spec).blankForm(level, state, edit).finish();}
 * For the phased flow, chain {@link #headerFill}, {@link #rowFlow}, then {@link #finish()} (throws on FAIL)
 * or {@link #finishDeferred()} (parks FAILs in the collector so the flow continues).
 *
 * <p>Generic mechanics live in {@link UiComponentValidator} and {@link VoucherGridChrome}; nothing here
 * knows a business rule.
 */
public final class VoucherUiValidationStage {

    private final VoucherUiSpec spec;
    private final VoucherGridChrome chrome;
    private final List<UiValidationFailure> results = new ArrayList<>();
    /** Snapshots taken by {@link #blankForm}, compared by {@link #afterFill}. */
    private Map<String, FieldSnapshot> headerBeforeFill;
    private Map<String, String> footerBeforeFill;
    /** Voucher number of the opened Edit form, compared with the read-only Voucher No. display. */
    private String expectedVoucherNo;

    public VoucherUiValidationStage expectingVoucherNo(String voucherNo) {
        this.expectedVoucherNo = voucherNo;
        return this;
    }

    private VoucherUiValidationStage(VoucherUiSpec spec) {
        this.spec = spec;
        this.chrome = new VoucherGridChrome(spec.domain());
    }

    public static VoucherUiValidationStage forSpec(VoucherUiSpec spec) {
        return new VoucherUiValidationStage(spec);
    }

    // ── Phase 1: the blank form ─────────────────────────────────────────────────────────────

    /** Contract of the whole form as it stands: Document Information (grouped by sub-section), the grid
     *  section (header + toolbar, columns, Narration / Sub Ledger Balance, Adjustment / Payment Information,
     *  footer) and the page footer. */
    public VoucherUiValidationStage blankForm(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        results.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(),
                () -> documentInformation(level, state, isEditMode)));
        results.addAll(UiValidationScope.node(spec.gridSection().title(), () -> gridSection(level, isEditMode)));
        results.addAll(UiValidationScope.node("Page Footer", () -> pageFooter(level, state, isEditMode)));
        return this;
    }

    private List<UiValidationFailure> documentInformation(UiValidationLevel level, UiFieldState state, boolean edit) {
        VoucherDocumentInformationBase doc = spec.documentInformation();
        headerBeforeFill = doc.captureFieldSnapshots();
        List<UiValidationFailure> f = new ArrayList<>();
        f.addAll(doc.validateDocumentInformationSectionHeaderUi(level));
        f.addAll(sectionState(FormSection.DOCUMENT_INFORMATION, "initial"));
        f.addAll(UiValidationScope.node("Voucher No.",
                () -> doc.validateVoucherNoUi(level, edit, spec.required("VOUCHER_NO"), expectedVoucherNo)));
        f.addAll(UiValidationScope.node("Reference Details",
                () -> doc.validateReferenceUi(level, edit ? UiFieldState.EDITABLE : state, spec.required("REFERENCE"))));
        f.addAll(UiValidationScope.node("Owner Site Details",
                () -> doc.validateOwnerSiteUi(level, edit, spec.required("OWNER_SITE"))));
        f.addAll(UiValidationScope.node("Cash/Bank Details",
                () -> doc.validateCashBankUi(level, state, spec.required("CASH_BANK"))));
        f.addAll(UiValidationScope.node("Date Details", () -> doc.validateDateUi(level, state, spec.required("DATE"))));
        f.addAll(UiValidationScope.node("Remarks",
                () -> doc.validateRemarksUi(level, state, edit, spec.required("REMARKS"))));
        List<UiValidationFailure> extra = spec.extraDocumentInformation(level, state, edit);
        if (!extra.isEmpty()) f.addAll(UiValidationScope.node("Other Details", () -> extra));
        return f;
    }

    private List<UiValidationFailure> gridSection(UiValidationLevel level, boolean edit) {
        FormSection grid = spec.gridSection();
        List<UiValidationFailure> f = new ArrayList<>();
        f.addAll(UiComponentValidator.validateSectionHeader(grid.titleLocator(chrome.scope()),
                "SECTION_HEADER_" + grid.key(), grid.title(), level));
        f.addAll(sectionState(grid, "initial"));
        f.addAll(UiValidationScope.node("Grid header and toolbar", () -> {
            List<UiValidationFailure> r = new ArrayList<>();
            for (IVoucherGridColumnHeader col : spec.gridColumns()) {
                String name = "GRID_HEADER_" + columnKey(col);
                r.addAll(col.kind() == GridColumnKind.USER
                        ? UiComponentValidator.validateGridHeaderOptional(col.headerLocator(), name, col.headerText(), level)
                        : UiComponentValidator.validateGridHeader(col.headerLocator(), name, col.headerText(), level));
                if (col.kind() == GridColumnKind.DEFAULT || UiComponentValidator.quickPresent(col.headerLocator())) {
                    r.addAll(UiComponentValidator.validateGridColumnMandatoryBaselined(col.headerLocator(), name));
                }
            }
            r.addAll(chrome.validateToolbar(edit ? null : Boolean.FALSE));
            return r;
        }));
        if (spec.hasNarrationAndSubLedgerBalance()) {
            f.addAll(UiValidationScope.node("Narration and Sub Ledger Balance · initial state", this::narrationAndBalanceInitial));
        }
        f.addAll(UiValidationScope.node(FormSection.ADJUSTMENT_INFORMATION.title() + " · before fill",
                () -> chrome.validateBlockPresentAndState(FormSection.ADJUSTMENT_INFORMATION,
                        spec.expectedSectionOpen(FormSection.ADJUSTMENT_INFORMATION, "initial"), UiValidationLevel.CONTRACT)));
        f.addAll(UiValidationScope.node(FormSection.PAYMENT_INFORMATION.title() + " · before fill",
                () -> chrome.validateBlockPresentAndState(FormSection.PAYMENT_INFORMATION,
                        spec.expectedSectionOpen(FormSection.PAYMENT_INFORMATION, "initial"), UiValidationLevel.CONTRACT)));
        footerBeforeFill = chrome.readFooter();
        f.addAll(UiValidationScope.node("Grid footer · before fill",
                () -> chrome.validateFooter("before fill", footerBeforeFill)));
        return f;
    }

    private List<UiValidationFailure> narrationAndBalanceInitial() {
        List<UiValidationFailure> r = new ArrayList<>();
        r.addAll(chrome.validateLabelledField("NARRATION", "Narration", "at start of entry", "enabled-before-fill"));
        r.addAll(chrome.validateLabelledField("SUB_LEDGER_BALANCE_FIELD", "Sub Ledger Balance", "at start of entry",
                "enabled-before-fill"));
        return r;
    }

    private List<UiValidationFailure> pageFooter(UiValidationLevel level, UiFieldState state, boolean edit) {
        // Legacy contract kept for non-editable checks: only the Save button's (disabled) state is asserted.
        return state == UiFieldState.EDITABLE
                ? spec.footer().validateFooterButtonsUi(level, FooterMode.of(edit))
                : spec.footer().validateSaveButtonUi(level, false);
    }

    private List<UiValidationFailure> sectionState(FormSection section, String phase) {
        Boolean expectedOpen = spec.expectedSectionOpen(section, phase);
        return expectedOpen == null
                ? UiComponentValidator.recordSectionState(section, chrome.scope())
                : UiComponentValidator.validateSectionState(section, chrome.scope(), expectedOpen);
    }

    // ── Phase 1b: after the real fill, before Save (business flows) ───────────────────────

    /**
     * Compares the form with the snapshots {@link #blankForm} took: every Document Information field
     * (state/value before -> after, changed as expected), the grid footer (Record Count grows by
     * {@code rowsFilled}; every total shown before -> after) and each filled row, titled by its business
     * keys (Sub Ledger / Reference Site) with its row buttons.
     */
    public VoucherUiValidationStage afterFill(UiValidationLevel level, int rowsFilled) {
        VoucherDocumentInformationBase doc = spec.documentInformation();
        if (headerBeforeFill != null) {
            Map<String, FieldSnapshot> after = doc.captureFieldSnapshots();
            results.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(),
                    () -> UiValidationScope.node("Before / after fill",
                            () -> doc.validateFieldsBeforeAfterUi(headerBeforeFill, after))));
        }
        Map<String, String> footerAfter = chrome.readFooter();
        Map<String, String> before = footerBeforeFill == null ? Map.of() : footerBeforeFill;
        results.addAll(UiValidationScope.node(spec.gridSection().title(), () -> {
            List<UiValidationFailure> f = new ArrayList<>();
            f.addAll(UiValidationScope.node("Grid footer · before and after fill", () -> {
                List<UiValidationFailure> r = new ArrayList<>();
                r.addAll(chrome.validateFooter("before fill", before));
                r.addAll(chrome.validateFooter("after fill", footerAfter));
                r.addAll(chrome.validateFooterChange("fill of " + rowsFilled + " row(s)", before, footerAfter, rowsFilled, null));
                return r;
            }));
            for (int i = 0; i < rowsFilled; i++) {
                final int row = i;
                f.addAll(UiValidationScope.node("Row " + (row + 1) + " · " + spec.rowIdentity(row + 1),
                        () -> UiValidationScope.node("After fill", () -> chrome.validateRowButtons(row, level))));
            }
            return f;
        }));
        return this;
    }

    // ── Phase 1c: observed business fill (per row, popups nested under the row) ─────────────

    private VoucherFillUiRecorder fillRecorder;

    /** Starts observing the business fill of the sub-ledger grid. Pair with {@link #endFill}. */
    public VoucherUiValidationStage beginFill(UiValidationLevel level) {
        if (spec.subLedger() == null) return this;
        fillRecorder = new VoucherFillUiRecorder(spec, chrome, level, footerBeforeFill);
        spec.subLedger().setSubLedgerFillListener(fillRecorder);
        return this;
    }

    /** Stops observing; adds the header before/after and the whole-fill footer summary after the per-row results. */
    public VoucherUiValidationStage endFill(UiValidationLevel level) {
        if (fillRecorder == null) return afterFill(level, 0);
        spec.subLedger().setSubLedgerFillListener(null);
        results.addAll(fillRecorder.results());
        int rows = fillRecorder.rowsFilled();
        fillRecorder = null;
        return afterFillSummary(rows);
    }

    private VoucherUiValidationStage afterFillSummary(int rows) {
        VoucherDocumentInformationBase doc = spec.documentInformation();
        if (headerBeforeFill != null) {
            Map<String, FieldSnapshot> after = doc.captureFieldSnapshots();
            results.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(),
                    () -> UiValidationScope.node("Before / after fill",
                            () -> doc.validateFieldsBeforeAfterUi(headerBeforeFill, after))));
        }
        Map<String, String> footerAfter = chrome.readFooter();
        Map<String, String> before = footerBeforeFill == null ? Map.of() : footerBeforeFill;
        results.addAll(UiValidationScope.node(spec.gridSection().title(), () ->
                UiValidationScope.node("Grid footer · summary",
                        () -> chrome.validateFooterChange("fill of " + rows + " row(s)", before, footerAfter, rows, null))));
        return this;
    }

    // ── Phase 2: header fill (before / after) ───────────────────────────────────────────────

    /** Runs {@code fillHeader} between two snapshots of every Document Information field and reports, per
     *  field, state/value before and after and whether the change was the expected one. */
    public VoucherUiValidationStage headerFill(Runnable fillHeader) {
        VoucherDocumentInformationBase doc = spec.documentInformation();
        Map<String, FieldSnapshot> before = doc.captureFieldSnapshots();
        fillHeader.run();
        Map<String, FieldSnapshot> after = doc.captureFieldSnapshots();
        results.addAll(UiValidationScope.node(FormSection.DOCUMENT_INFORMATION.title(),
                () -> UiValidationScope.node("Before / after header fill",
                        () -> doc.validateFieldsBeforeAfterUi(before, after))));
        return this;
    }

    // ── Phase 3: one grid row at a time ─────────────────────────────────────────────────────

    /**
     * For each row index 0..rowCount-1: footer before, {@code fillRow}, footer after (Record Count must grow
     * by 1), row buttons, then {@code selectLine} (the caller clicks the grid line) and the state/value of
     * Narration, Sub Ledger Balance and the Payment Information panel before vs after that selection, plus
     * Adjustment / Payment Information block state before vs after the row.
     */
    public VoucherUiValidationStage rowFlow(int rowCount, IntConsumer fillRow, IntConsumer selectLine,
            UiValidationLevel level) {
        for (int i = 0; i < rowCount; i++) {
            final int row = i;
            results.addAll(UiValidationScope.node(spec.gridSection().title(),
                    () -> UiValidationScope.node("Row " + (row + 1), () -> oneRow(row, fillRow, selectLine, level))));
        }
        return this;
    }

    private List<UiValidationFailure> oneRow(int row, IntConsumer fillRow, IntConsumer selectLine, UiValidationLevel level) {
        List<UiValidationFailure> f = new ArrayList<>();
        Map<String, String> footerBefore = chrome.readFooter();
        String adjBefore = chrome.sectionStateText(FormSection.ADJUSTMENT_INFORMATION);
        String payBefore = chrome.sectionStateText(FormSection.PAYMENT_INFORMATION);

        fillRow.accept(row);

        Map<String, String> footerAfter = chrome.readFooter();
        f.addAll(UiValidationScope.node("Grid footer · after fill",
                () -> chrome.validateFooterChange("row " + (row + 1) + " added", footerBefore, footerAfter, 1, null)));
        f.addAll(UiValidationScope.node("Row buttons", () -> chrome.validateRowButtons(row, level)));

        FieldSnapshot narrationBefore = spec.hasNarrationAndSubLedgerBalance() ? chrome.capture("Narration") : null;
        FieldSnapshot balanceBefore = spec.hasNarrationAndSubLedgerBalance() ? chrome.capture("Sub Ledger Balance") : null;
        Map<String, FieldSnapshot> panelBefore = chrome.capturePaymentPanel();

        selectLine.accept(row);

        if (spec.hasNarrationAndSubLedgerBalance()) {
            f.addAll(UiValidationScope.node("Narration and Sub Ledger Balance · after line selection", () -> {
                List<UiValidationFailure> r = new ArrayList<>();
                r.addAll(UiComponentValidator.validateFieldBeforeAfterBaselined("NARRATION", "line selection",
                        narrationBefore, chrome.capture("Narration"), null, null));
                r.addAll(UiComponentValidator.validateFieldBeforeAfterBaselined("SUB_LEDGER_BALANCE_FIELD", "line selection",
                        balanceBefore, chrome.capture("Sub Ledger Balance"), null, null));
                return r;
            }));
        }
        f.addAll(UiValidationScope.node(FormSection.PAYMENT_INFORMATION.title() + " · after line selection",
                () -> chrome.validatePaymentPanelAfterSelection(panelBefore, chrome.capturePaymentPanel())));
        f.addAll(UiValidationScope.node("Adjustment / Payment Information · state change", () -> {
            List<UiValidationFailure> r = new ArrayList<>();
            r.addAll(blockChange(FormSection.ADJUSTMENT_INFORMATION, adjBefore, row));
            r.addAll(blockChange(FormSection.PAYMENT_INFORMATION, payBefore, row));
            return r;
        }));
        return f;
    }

    private List<UiValidationFailure> blockChange(FormSection section, String before, int row) {
        String after = chrome.sectionStateText(section);
        if ("NOT PRESENT".equals(before) && "NOT PRESENT".equals(after)) {
            return UiComponentValidator.skip("SECTION_" + section.key(), "section presence",
                    "validated if the form shows it", "not present before or after row " + (row + 1));
        }
        Boolean expAfter = spec.expectedSectionOpen(section, "after row fill");
        Boolean expBefore = spec.expectedSectionOpen(section, "initial");
        if (expAfter == null || expBefore == null) {
            return List.of(new UiValidationFailure("SECTION_" + section.key(), "section state on row " + (row + 1) + " fill",
                    "(recorded)", before + " → " + after, UiValidationFailure.Status.PASS));
        }
        return UiComponentValidator.validateSectionStateChange(section.key(), "row " + (row + 1) + " fill",
                before, expBefore, after, expAfter);
    }

    // ── Output ──────────────────────────────────────────────────────────────────────────────

    public List<UiValidationFailure> results() {
        return results;
    }

    /** Renders the report and parks the FAILs for the flow's final assertion. UI validation is always
     *  soft — see {@link UiValidationReporter#renderAndAssert}. Clears the collected results (the
     *  before-fill snapshots stay, for {@link #afterFill}). */
    public void finish() {
        finishDeferred();
    }

    /** Holds the results collected so far; the next render (after the fill) merges them into ONE report. */
    public void holdForFill() {
        UiValidationReporter.holdForFill(new ArrayList<>(results));
        results.clear();
    }

    /** Same as {@link #finish()}. */
    public void finishDeferred() {
        UiValidationReporter.renderDeferred(new ArrayList<>(results));
        results.clear();
    }

    private static String columnKey(IVoucherGridColumnHeader col) {
        return col instanceof Enum<?> e ? e.name() : col.headerText().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }
}
