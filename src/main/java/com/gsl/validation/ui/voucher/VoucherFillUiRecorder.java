package com.gsl.validation.ui.voucher;

import com.gsl.enums.AdjustmentType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiComponentValidator.FieldSnapshot;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates the form WHILE the business fill runs ({@link SubLedgerFillListener}): per row — cells and row
 * buttons, the state after clicking the row (Narration equals the grid's Ledger Narration, Sub Ledger Balance
 * format, Payment Information panel), the Adjustment Entry popup and the Payment Information popup as
 * sub-sections of that row, then the grid footer after that row (previous -> current).
 * Everything is collected, never thrown.
 */
final class VoucherFillUiRecorder implements SubLedgerFillListener {

    private final VoucherUiSpec spec;
    private final VoucherGridChrome chrome;
    private final UiValidationLevel level;
    private final List<UiValidationFailure> results = new ArrayList<>();
    private final AdjustmentEntryUi adjustment = new AdjustmentEntryUi();
    private final PaymentPopupUi payment;

    private Map<String, String> lastFooter;
    private AdjustmentEntryUi.Snapshot adjustmentBefore;
    private int rowsFilled;

    VoucherFillUiRecorder(VoucherUiSpec spec, VoucherGridChrome chrome, UiValidationLevel level,
            Map<String, String> footerBeforeFill) {
        this.spec = spec;
        this.chrome = chrome;
        this.level = level;
        this.lastFooter = footerBeforeFill == null ? Map.of() : footerBeforeFill;
        this.payment = new PaymentPopupUi(spec.domain());
    }

    List<UiValidationFailure> results() { return results; }
    int rowsFilled() { return rowsFilled; }

    // The shared path of every result: grid section > Row N · identity > <sub-section>.
    private void inRow(int rowIndex, String subSection, java.util.function.Supplier<List<UiValidationFailure>> body) {
        String rowTitle = "Row " + (rowIndex + 1) + " · " + spec.rowIdentity(rowIndex + 1);
        results.addAll(UiValidationScope.node(spec.gridSection().title(), () ->
                UiValidationScope.node(rowTitle, () -> subSection == null ? body.get()
                        : UiValidationScope.node(subSection, body))));
    }

    /** A cell reported NOT FOUND gets the column ids the row really renders appended, so a wrong header/itemId
     *  is fixed from one run instead of guessed. */
    private List<UiValidationFailure> withRenderedColumns(int rowIndex, List<UiValidationFailure> in) {
        if (in.stream().noneMatch(f -> f.isFail() && f.actual() != null && f.actual().startsWith("NOT FOUND"))) return in;
        String ids = String.join(", ", com.gsl.utils.Selenium.GridCellLocator.renderedColumnIds(chrome.scope(), rowIndex));
        List<UiValidationFailure> out = new ArrayList<>();
        for (UiValidationFailure f : in) {
            out.add(f.isFail() && f.actual() != null && f.actual().startsWith("NOT FOUND")
                    ? new UiValidationFailure(f.component(), f.property(), f.expected(),
                            "NOT FOUND - row renders columns: " + (ids.isEmpty() ? "(none)" : ids), f.status())
                    : f);
        }
        return out;
    }

    @Override
    public void onRowFilled(int rowIndex) {
        rowsFilled = rowIndex + 1;
        // Pickers used while filling this row (snapshots taken by the fill, before/after typing) - reported now
        // that the row's identity is known, so they nest under the same row node.
        if (spec.subLedger() != null) {
            for (com.gsl.validation.ui.PickerUi.Observation o : spec.subLedger().drainPickerObservations(rowIndex)) {
                inRow(rowIndex, o.label(), () -> com.gsl.validation.ui.PickerUi.validate(o));
            }
        }
        inRow(rowIndex, "Cells and row buttons", () -> {
            List<UiValidationFailure> r = new ArrayList<>();
            for (var col : spec.gridColumns()) {
                String text = chrome.gridCellText(rowIndex, col.headerText(), col.itemId());
                if (text.isEmpty() && col.kind() == com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind.USER) continue;
                String comp = "GRID_CELL_" + PopupUi.key(col.headerText()) + "_ROW_" + (rowIndex + 1);
                r.add(new UiValidationFailure(comp, "value", "(recorded)", text.isEmpty() ? "(empty)" : text,
                        UiValidationFailure.Status.PASS));
                // Optional (USER) columns may be hidden or scrolled out: SKIP there, never a missing-element FAIL.
                r.addAll(withRenderedColumns(rowIndex, col.kind() == com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind.USER
                        ? UiComponentValidator.validateGridCellOptional(chrome.gridCell(rowIndex, col.headerText(), col.itemId()), comp,
                                col.numeric(), null, level)
                        : UiComponentValidator.validateGridCell(chrome.gridCell(rowIndex, col.headerText(), col.itemId()), comp,
                                col.numeric(), level)));
            }
            r.addAll(chrome.validateRowButtons(rowIndex, level));
            return r;
        });
        inRow(rowIndex, "After clicking the row", () -> afterClickingRow(rowIndex));
        // Footer AFTER the row (its identity first, then the Record Count check).
        Map<String, String> now = chrome.readFooter();
        Map<String, String> before = lastFooter;
        results.addAll(UiValidationScope.node(spec.gridSection().title(), () ->
                UiValidationScope.node("Grid footer · after Row " + (rowIndex + 1),
                        () -> chrome.validateFooterChange("row " + (rowIndex + 1) + " added", before, now, 1, null))));
        lastFooter = now;
    }

    private List<UiValidationFailure> afterClickingRow(int rowIndex) {
        List<UiValidationFailure> r = new ArrayList<>();
        Map<String, FieldSnapshot> panelBefore = chrome.capturePaymentPanel();
        if (!chrome.selectRow(rowIndex)) {
            return UiComponentValidator.skip("GRID_ROW_" + (rowIndex + 1), "row selection", "row clickable", "could not click the row");
        }
        if (spec.hasNarrationAndSubLedgerBalance()) {
            String cellNarration = chrome.gridCellText(rowIndex, "Ledger Narration", "ledgerNarrationCode");
            r.addAll(chrome.validateLabelledFieldBaselined("NARRATION", "Narration", "after clicking the row"));
            r.addAll(UiComponentValidator.validateEquals("NARRATION", "equals grid Ledger Narration",
                    cellNarration, UiComponentValidator.readValue(chrome.labelledField("Narration"))));
            r.addAll(chrome.validateLabelledFieldBaselined("SUB_LEDGER_BALANCE_FIELD", "Sub Ledger Balance",
                    "after clicking the row"));
            r.addAll(UiComponentValidator.validateBalanceFormat("SUB_LEDGER_BALANCE_FIELD",
                    UiComponentValidator.readValue(chrome.labelledField("Sub Ledger Balance"))));
        }
        r.addAll(chrome.validatePaymentPanelAfterSelection(panelBefore, chrome.capturePaymentPanel()));
        return r;
    }

    // ── Adjustment Entry popup ──────────────────────────────────────────────────────────────

    @Override
    public void onAdjustmentOpened(int rowIndex, AdjustmentType type) {
        adjustmentBefore = adjustment.snapshot();
        AdjustmentEntryUi.Snapshot opened = adjustmentBefore;
        inRow(rowIndex, "Adjustment Entry popup", () -> UiValidationScope.node("Before fill",
                () -> adjustment.beforeFill(level, opened)));
    }

    @Override
    public void onAdjustmentApplied(int rowIndex, AdjustmentType type) {
        AdjustmentEntryUi.Snapshot before = adjustmentBefore == null ? adjustment.snapshot() : adjustmentBefore;
        String source = switch (type) {
            case SELECT_DOCUMENT -> "Select Document";
            case REFERENCE_SITEWISE_FIFO_SETOFF -> "Reference Site wise Select Document";
            default -> "FIFO Set-Off";
        };
        inRow(rowIndex, "Adjustment Entry popup", () -> adjustment.afterFill(level, source, before));
    }

    // ── Payment Information popup ───────────────────────────────────────────────────────────

    @Override
    public void onPaymentOpened(int rowIndex, PaymentInformationPopup popup) {
        inRow(rowIndex, "Payment Information popup", () -> UiValidationScope.node("Before fill",
                () -> payment.beforeFill(level)));
    }

    @Override
    public void onPaymentFilled(int rowIndex, PaymentInformationPopup popup, VoucherPaymentInfo p) {
        inRow(rowIndex, "Payment Information popup", () -> UiValidationScope.node("After fill",
                () -> payment.afterFill(level, p.getPaymentMode() == null ? null : p.getPaymentMode().label(),
                        p.getInstrumentNo(), null, p.getDrawnOn(), p.getReference())));
    }
}
