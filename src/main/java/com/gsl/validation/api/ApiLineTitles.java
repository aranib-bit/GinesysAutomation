package com.gsl.validation.api;

import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Report titles for one voucher line in the API Validation tree, in the SAME format the UI validation tree uses
 * ({@code Row 1 · GL: X · Dr 901.00 · Sub Ledger: no · Site: no · Payment: no}), so both reports read alike and the
 * Sub Ledger / Site / Payment tags render as the same chips.
 */
final class ApiLineTitles {

    private ApiLineTitles() {}

    /** From what the test entered (Create / Edit). */
    static String fromExpected(int zeroBasedIndex, ExpenseIncomeDetail row, List<SiteAllocation> allocations, PaymentInformation payment) {
        return GeneralVoucherExpenseIncomeDetail.rowNodeLabel(zeroBasedIndex, new RowUiPlan(row, allocations, payment, row.isDebit()));
    }

    /** From what the application holds (Revert / Delete / unchanged Edit lines). */
    static String fromLine(int zeroBasedIndex, GeneralVoucherDetailLine l) {
        boolean hasSl = l.getSlName() != null && !l.getSlName().isBlank();
        boolean debit = l.getExpense() != 0;
        BigDecimal amount = BigDecimal.valueOf(debit ? l.getExpense() : l.getIncome());
        boolean site = l.getCostCenterAllocations() != null && !l.getCostCenterAllocations().isEmpty();
        String mode = l.getPaymentMode();
        boolean payment = mode != null && !"H".equalsIgnoreCase(mode.trim())
                && l.getInstrumentNumber() != null && !l.getInstrumentNumber().isBlank();
        return "Row " + (zeroBasedIndex + 1) + " · GL: " + l.getGlName()
                + (hasSl ? " · SL: " + l.getSlName() : "")
                + " · " + (debit ? "Dr " : "Cr ") + new DecimalFormat("0.00").format(amount)
                + " · Sub Ledger: " + (hasSl ? "yes" : "no")
                + " · Site: " + (site ? "yes" : "no") + " · Payment: " + (payment ? "yes" : "no");
    }
}
