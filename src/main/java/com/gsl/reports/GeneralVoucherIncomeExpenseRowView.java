package com.gsl.reports;

import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;

/**
 * Reporting-only adapter so {@link ExpenseIncomeDetail} rows can satisfy
 * {@link VoucherSubLedgerRow} for {@code VoucherExtentDescriptionLogger}/{@code ActualResultsLogger}
 * without retrofitting the interface onto the Excel/DB-mapped POJO itself.
 *
 * <p>{@link ExpenseIncomeDetail}'s own Javadoc explains why it deliberately does not implement
 * {@link VoucherSubLedgerRow} directly: {@code getAdjustmentType()}/{@code getPaymentSelection()}
 * assume a document-adjustment concept General Voucher rows don't have. This adapter is the
 * reporting-layer workaround that class's Javadoc calls "a documented follow-up" — it lives here,
 * not on the data-mapping POJO, so {@code ExpenseIncomeDetail} stays free of reporting-only
 * concerns and immune to any risk of disturbing {@code XlsxReader}'s column scan.
 *
 * <p>{@link #getSlType()}, {@link #getAdjustmentType()}, and {@link #getPaymentSelection()} return
 * {@code null} unconditionally — General Voucher rows have no sub-ledger-class, document-adjustment,
 * or per-line payment-mode concept. Every existing consumer already null-guards these (see
 * {@code VoucherExtentDescriptionLogger}'s {@code nullSafe()} calls).
 */
public final class GeneralVoucherIncomeExpenseRowView implements VoucherSubLedgerRow {

    private final ExpenseIncomeDetail row;

    public GeneralVoucherIncomeExpenseRowView(ExpenseIncomeDetail row) {
        this.row = row;
    }

    /** The wrapped row — used by {@code GeneralVoucherExtentDescriptionLogger.slAmount} to read
     *  whichever of {@code expenseDr}/{@code incomeCr} applies, since {@link VoucherSubLedgerRow}
     *  has no Dr/Cr-direction concept of its own. */
    public ExpenseIncomeDetail getExpenseIncomeDetail() { return row; }

    @Override public String getLineNo() { return row.getLineNo(); }
    @Override public String getSubLedger() { return row.getSubLedger(); }
    @Override public String getId() { return row.getSubLedgerId(); }
    @Override public String getGeneralLedger() { return row.getGeneralLedger(); }
    @Override public String getSlType() { return null; }
    @Override public AdjustmentType getAdjustmentType() { return null; }
    @Override public PaymentSelectionType getPaymentSelection() { return null; }
}
