package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Test-data model for one row in the General Voucher "ExpenseIncomeDetail"
 * sheet — the line-item sheet equivalent of AP/AR's SubLedgerInformation.
 *
 * <p>Deliberately does NOT implement
 * {@link com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow}
 * — that interface's {@code getAdjustmentType()}/{@code getPaymentSelection()}
 * assume a document-adjustment concept General Voucher's Expense/Income
 * Detail rows don't have. Consequently
 * {@code MergedFinanceGeneralVoucherData} does not implement
 * {@link com.gsl.testdata.dto.VoucherReportData} either (that interface's
 * {@code getSubLedgerInformationList()} is typed to
 * {@code VoucherSubLedgerRow}) — a deliberate, documented scope limitation
 * for this phase rather than forcing an awkward no-op implementation.
 *
 * <p><b>GL-type source of truth is the DB, not Excel.</b> {@code expectedGlType}
 * (optional, read via raw POI post-load — same idiom as
 * {@code ExcelApVoucherDataSource.readOptionalOnAccountConfirm}, never
 * breaks {@code XlsxReader} if the column is absent) is only a soft
 * assertion against the live-resolved {@code resolvedGlType}, which every
 * data source (Excel or DB) must populate via
 * {@code FinGlDao.findByGlName(generalLedger)} +
 * {@code GlLineType.fromFlags(gl.isSiteApplicable(), gl.isSubLedgerApplicable())}.
 * Both fields live in {@link ExpenseIncomeDetailBase} — declaring them directly
 * on this class would NOT exclude them from XlsxReader's column scan (see that
 * class's Javadoc).
 */
@Getter
@NoArgsConstructor
@Sheet(name = "ExpenseIncomeDetail")
public class ExpenseIncomeDetail extends ExpenseIncomeDetailBase {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "General Ledger")
    private String generalLedger;

    @Column(name = "Sub Ledger")
    private String subLedger;

    @Column(name = "Sub Ledger Id")
    private String subLedgerId;

    @Column(name = "Ledger Narration")
    private String ledgerNarration;

    @Column(name = "Is Reversal")
    private String isReversal;

    @Column(name = "Expense(Dr)", converter = BigDecimalConverter.class)
    private BigDecimal expenseDr;

    @Column(name = "Income(Cr)", converter = BigDecimalConverter.class)
    private BigDecimal incomeCr;

    /**
     * 9-param constructor covering exactly the 9 {@code @Column}-annotated
     * fields above. {@code expectedGlType}/{@code resolvedGlType} (inherited
     * from {@link ExpenseIncomeDetailBase}) are deliberately excluded — see
     * class Javadoc.
     */
    public ExpenseIncomeDetail(String testCaseID, String lineNo, String generalLedger,
                                String subLedger, String subLedgerId, String ledgerNarration,
                                String isReversal, BigDecimal expenseDr, BigDecimal incomeCr) {
        this.testCaseID      = testCaseID;
        this.lineNo          = lineNo;
        this.generalLedger   = generalLedger;
        this.subLedger       = subLedger;
        this.subLedgerId     = subLedgerId;
        this.ledgerNarration = ledgerNarration;
        this.isReversal      = isReversal;
        this.expenseDr       = expenseDr;
        this.incomeCr        = incomeCr;
    }

    public boolean isReversalYes() {
        return "Yes".equalsIgnoreCase(isReversal);
    }

    /** {@code true} when this row posts to Expense(Dr); {@code false} when it posts to Income(Cr). */
    public boolean isDebit() {
        return expenseDr != null && expenseDr.signum() != 0;
    }
}
