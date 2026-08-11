package com.gsl.reports;

import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

import java.math.BigDecimal;
import java.util.List;

/**
 * General Voucher Extent description logger.
 *
 * <p>Extends {@link VoucherExtentDescriptionLogger} but bypasses its generic AP/AR-shaped
 * sub-ledger table and payment-information table entirely ({@link #hasGenericSubLedgers()} /
 * {@link #supportsPaymentInformation()} both {@code false}) — GV structurally has neither
 * concept (see {@code GeneralVoucherIncomeExpenseRowView}'s Javadoc), so those columns rendered
 * blank for every row and the "Payment Information" table rendered a misleading
 * "(intentionally blank for this scenario)" placeholder for a concept that doesn't exist at
 * all, not one merely absent this scenario. Instead, GV renders its own dedicated
 * "Income/Expense Detail" table via {@link #logMiddleSections} with separate
 * {@code Expense(Dr)}/{@code Income(Cr)} columns (matching the Excel sheet's actual column
 * names) rather than one ambiguous merged "Amount (Dr/Cr)" column.
 *
 * <p>GV-specific positive Expected Results reference {@code fincosttag} (site allocation)
 * rather than AP/AR's {@code fintag} (adjustment tagging) — a structurally different
 * table/concept, not a renamed one.
 *
 * <p>Call the static entry point {@link #log(MergedFinanceGeneralVoucherData)} after lazy
 * loading — it delegates to the template-method base.
 */
public final class GeneralVoucherExtentDescriptionLogger
        extends VoucherExtentDescriptionLogger<MergedFinanceGeneralVoucherData> {

    private static final GeneralVoucherExtentDescriptionLogger INSTANCE = new GeneralVoucherExtentDescriptionLogger();

    private GeneralVoucherExtentDescriptionLogger() {}

    /** Static entry point — call-sites follow the same convention as AP/AR. */
    public static void log(MergedFinanceGeneralVoucherData data) {
        INSTANCE.render(data);
    }

    // ── Abstract implementations ───────────────────────────────────────────────

    @Override
    protected String loggerLabel() { return "GeneralVoucherExtentDescriptionLogger"; }

    /** Unreachable — {@link #hasGenericSubLedgers()} is {@code false}, so the base class never
     *  calls this or {@link #slAmount}. Required only to satisfy the abstract contract. */
    @Override
    protected String amountColumnHeader() { return "Amount (Dr/Cr)"; }

    @Override
    protected BigDecimal slAmount(VoucherSubLedgerRow sl) {
        var row = ((GeneralVoucherIncomeExpenseRowView) sl).getExpenseIncomeDetail();
        return row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
    }

    @Override
    protected void appendExtraConsoleHeaderLines(MergedFinanceGeneralVoucherData data, StringBuilder sb) {
        // GV has no extra flags beyond the shared Pmt Info Warning line — no On Account/TDS concept.
    }

    @Override
    protected void appendExtraHeaderRows(MergedFinanceGeneralVoucherData data, List<String[]> rows) {
        // GV header table has no extra rows
    }

    @Override
    protected void logPositiveExpected(MergedFinanceGeneralVoucherData data) {
        int lineCount = data.getExpenseIncomeDetailList() != null ? data.getExpenseIncomeDetailList().size() : 0;
        int siteRowCount = data.getSiteApplicableRows() != null ? data.getSiteApplicableRows().size() : 0;

        ExtentLogger.infoTable(new String[][]{
            { "Expected Outcome",  "Details" },
            { "UI — Save",         "Document saved; Document No. displayed on form" },
            { "DB — finvchmain",   "1 record: ownersite, cashbank, voucherno, docdate, refno, remarks, vchtype='G'" },
            { "DB — finvchdet",    lineCount + " row(s): glcode, damount/camount (per row's isDebit()), isreversal" },
            { "DB — finpost",      "1 VMG + " + lineCount + " VDG posting entries, balanced (BLOCKING — test fails if absent)" },
            { "DB — fincosttag",   siteRowCount + " row(s) — site allocation tagging for Type2/Type3 rows (COLLECT — non-blocking)" },
        });
    }

    // ── Hooks ──────────────────────────────────────────────────────────────────

    @Override protected boolean hasGenericSubLedgers() { return false; }
    @Override protected boolean supportsPaymentInformation() { return false; }

    /**
     * Renders GV's own "Income/Expense Detail" table in place of the generic sub-ledger table
     * — GL, Sub Ledger (Type3 rows only), separate Expense(Dr)/Income(Cr) columns, and Is
     * Reversal. Bypasses {@code GeneralVoucherIncomeExpenseRowView} entirely: that adapter
     * exists solely to satisfy {@code VoucherSubLedgerRow} for the generic renderer, which this
     * table no longer routes through.
     */
    @Override
    protected void logMiddleSections(MergedFinanceGeneralVoucherData data) {
        List<ExpenseIncomeDetail> lines = data.getExpenseIncomeDetailList();
        ExtentLogger.info("<b>Income/Expense Detail</b>");
        if (lines == null || lines.isEmpty()) {
            ExtentLogger.infoTable(new String[][]{
                { "Line#", "GL", "Sub-Ledger", "Sub Ledger Id", "Expense(Dr)", "Income(Cr)", "Is Reversal" },
                { "—", "(intentionally blank for this scenario)", "", "", "", "", "" }
            });
            return;
        }
        String[][] table = new String[1 + lines.size()][7];
        table[0] = new String[]{ "Line#", "GL", "Sub-Ledger", "Sub Ledger Id", "Expense(Dr)", "Income(Cr)", "Is Reversal" };
        for (int i = 0; i < lines.size(); i++) {
            ExpenseIncomeDetail r = lines.get(i);
            table[i + 1] = new String[]{
                nullSafe(r.getLineNo()), nullSafe(r.getGeneralLedger()), nullSafe(r.getSubLedger()),
                nullSafe(r.getSubLedgerId()),
                r.getExpenseDr() != null ? r.getExpenseDr().toPlainString() : "",
                r.getIncomeCr() != null ? r.getIncomeCr().toPlainString() : "",
                r.isReversalYes() ? "YES" : "NO"
            };
        }
        ExtentLogger.infoTable(table);
    }
}
