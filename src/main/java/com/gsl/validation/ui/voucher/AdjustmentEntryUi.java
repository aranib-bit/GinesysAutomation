package com.gsl.validation.ui.voucher;

import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI contract of the Adjustment Entry popup (live screenshot, 2026-10-06): window title, the "Adjusted
 * Document" section with its FIFO Set-Off button, the document grid (Document No., Document Date, Reference
 * Site, Reference No., Reference Date, Document Type, Actual Amount ...), the grid footer (Record Count,
 * Actual / Pending / Adjusted Amount), the read-only amount footer (Transaction Amount, Adjusted Amount,
 * Pending to Adjust) and the Show Reference Site Summary / OK / Cancel buttons.
 *
 * <p>Documents reach the popup in bulk (Select Document, Reference Site wise Select Document, FIFO Set-Off),
 * so the per-document checks read the grid rows AFTER the adjustment is applied, one node per document.
 */
public final class AdjustmentEntryUi {

    public static final String[] GRID_COLUMNS = {"Document No.", "Document Date", "Reference Site", "Reference No.",
            "Reference Date", "Document Type", "Actual Amount"};
    private static final String[] FOOTER = {"Record Count:", "Actual Amount:", "Pending Amount:", "Adjusted Amount:"};
    private static final String[] AMOUNTS = {"Transaction Amount:", "Adjusted Amount:", "Pending to Adjust:"};

    private final PopupUi popup = new PopupUi("Adjustment Entry");

    /** Popup state at one moment: both footers. */
    public record Snapshot(Map<String, String> footer, Map<String, String> amounts) {}

    public Snapshot snapshot() {
        return new Snapshot(popup.footerValues(FOOTER), popup.amountValues(AMOUNTS));
    }

    /** Everything the freshly opened popup shows, with the full property set per field. */
    public List<UiValidationFailure> beforeFill(UiValidationLevel level, Snapshot opened) {
        List<UiValidationFailure> f = new ArrayList<>(popup.windowTitle());
        f.addAll(popup.sectionHeader("ADJUSTMENT_POPUP_SECTION_ADJUSTED_DOCUMENT", "Adjusted Document", level));
        f.addAll(popup.button("ADJUSTMENT_POPUP_FIFO_SET_OFF_BUTTON", "FIFO Set-Off", level));
        for (String col : GRID_COLUMNS) {
            f.addAll(popup.gridHeader("ADJUSTMENT_POPUP_GRID_HEADER_" + PopupUi.key(col), col, level));
        }
        f.addAll(popup.recordValues("ADJUSTMENT_POPUP_FOOTER_", "before fill", opened.footer()));
        f.addAll(popup.field("ADJUSTMENT_POPUP_TRANSACTION_AMOUNT", "Transaction Amount:", PopupUi.BEFORE,
                FieldStyleSpec.READONLY_AMOUNT, level));
        f.addAll(popup.field("ADJUSTMENT_POPUP_ADJUSTED_AMOUNT", "Adjusted Amount:", PopupUi.BEFORE,
                FieldStyleSpec.READONLY_AMOUNT, level));
        f.addAll(popup.field("ADJUSTMENT_POPUP_PENDING_TO_ADJUST", "Pending to Adjust:", PopupUi.BEFORE,
                FieldStyleSpec.READONLY_AMOUNT, level));
        f.addAll(popup.button("ADJUSTMENT_POPUP_SHOW_REFERENCE_SITE_SUMMARY_BUTTON", "Show Reference Site Summary", level));
        f.addAll(popup.button("ADJUSTMENT_POPUP_OK_BUTTON", "OK", level));
        f.addAll(popup.button("ADJUSTMENT_POPUP_CANCEL_BUTTON", "Cancel", level));
        return f;
    }

    /**
     * After the adjustment is applied (before OK): one node per document now in the grid, titled by Document
     * No. and Reference Site, with each cell's value and the footer/amount changes, then a summary node.
     *
     * @param source how the documents were added, e.g. "Select Document"
     */
    public List<UiValidationFailure> afterFill(UiValidationLevel level, String source, Snapshot before) {
        List<UiValidationFailure> f = new ArrayList<>();
        int rows = popup.rowCount();
        BigDecimal adjustedSum = BigDecimal.ZERO;
        for (int i = 0; i < rows; i++) {
            final int row = i;
            String docNo = popup.cellText(i, "Document No.");
            String refSite = popup.cellText(i, "Reference Site");
            f.addAll(UiValidationScope.node("Added via " + source + " · Adjusted document " + (i + 1) + " · " + docNo
                    + " · Reference Site: " + refSite, () -> {
                List<UiValidationFailure> r = new ArrayList<>();
                for (String col : GRID_COLUMNS) {
                    boolean numeric = col.endsWith("Amount");
                    String comp = "ADJUSTMENT_POPUP_CELL_" + PopupUi.key(col);
                    String text = popup.cellText(row, col);
                    r.add(new com.gsl.validation.ui.UiValidationFailure(comp, "value", "(recorded)",
                            text.isEmpty() ? "(empty)" : text, UiValidationFailure.Status.PASS));
                    r.addAll(UiComponentValidator.validateGridCell(popup.cell(row, col), comp, numeric, level));
                }
                return r;
            }));
            adjustedSum = adjustedSum.add(parse(popup.cellText(i, "Adjusted Amount")));
        }
        Snapshot after = snapshot();
        final BigDecimal sum = adjustedSum;
        f.addAll(UiValidationScope.node("After fill · summary", () -> {
            List<UiValidationFailure> r = new ArrayList<>();
            Integer b = intOf(before.footer().get("Record Count:"));
            Integer a = intOf(after.footer().get("Record Count:"));
            r.addAll(UiComponentValidator.validateCountPair("ADJUSTMENT_POPUP_FOOTER_RECORD_COUNT", "value on fill",
                    b, a, rows - (b == null ? 0 : b)));
            r.addAll(popup.changes("ADJUSTMENT_POPUP_FOOTER_", "fill", before.footer(), after.footer(), null));
            r.addAll(popup.changes("ADJUSTMENT_POPUP_AMOUNT_", "fill", before.amounts(), after.amounts(), null));
            // Arithmetic the popup must satisfy: Pending to Adjust = Transaction Amount − Adjusted Amount.
            BigDecimal txn = parse(after.amounts().get("Transaction Amount:"));
            BigDecimal adj = parse(after.amounts().get("Adjusted Amount:"));
            BigDecimal pending = parse(after.amounts().get("Pending to Adjust:"));
            r.addAll(UiComponentValidator.validateEquals("ADJUSTMENT_POPUP_AMOUNT_PENDING_TO_ADJUST",
                    "Transaction − Adjusted", txn.subtract(adj).toPlainString(), pending.toPlainString()));
            if (rows > 0) {
                r.addAll(UiComponentValidator.validateEquals("ADJUSTMENT_POPUP_AMOUNT_ADJUSTED_AMOUNT",
                        "equals sum of document Adjusted Amount cells", sum.toPlainString(), adj.toPlainString()));
            }
            return r;
        }));
        return f;
    }

    private static BigDecimal parse(String s) {
        try {
            return new BigDecimal(s == null || s.isBlank() ? "0" : s.replace(",", "").replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static Integer intOf(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
