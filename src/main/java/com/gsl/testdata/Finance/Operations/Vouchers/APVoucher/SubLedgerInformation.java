package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.AdjustmentTypeConverter;
import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.PaymentSelectionConverter;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerFillRow;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Test-data model for one sub-ledger row in the AP Voucher SubLedgerInformation sheet.
 *
 * <p><b>Constructor note:</b> {@code data-supplier} v2.3.0 maps every constructor
 * parameter to an Excel column.  To prevent it from looking for "deductTdsConfirm"
 * in existing sheets (which don't have that column yet), we do NOT use
 * {@code @AllArgsConstructor}.  Instead we declare a 12-param constructor that covers
 * exactly the 12 {@code @Column}-annotated fields.  {@code deductTdsConfirm} is an
 * optional field populated post-load via its setter by {@code ExcelApVoucherDataSource}.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "SubLedgerInformation")
public class SubLedgerInformation extends SubLedgerInformationBase implements VoucherSubLedgerFillRow {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "Sub Ledger")
    private String subLedger;

    @Column(name = "Id")
    private String id;

    @Column(name = "General Ledger")
    private String generalLedger;

    @Column(name = "Reference Site")
    @Setter
    private String referenceSite;

    @Column(name = "Ledger Narration")
    private String ledgerNarration;

    @Column(name = "Is Reversal")
    private String isReversal;

    /**
     * Optional override for the auto-populated Deduct TDS value. Only honored when the
     * supplier has TDS applicable (tdsApp = 'Y') in the slCode combo record — otherwise
     * the framework warns and skips, matching the application's UI lock.
     */
    @Column(name = "Deduct TDS")
    private String deductTds;

    @Column(name = "Amount(Dr)", converter = BigDecimalConverter.class)
    private BigDecimal amountDr;

    @Column(name = "AdjustmentType", converter = AdjustmentTypeConverter.class)
    private AdjustmentType adjustmentType;

    @Column(name = "PaymentSelection", converter = PaymentSelectionConverter.class)
    private PaymentSelectionType paymentSelection;

    /**
     * 12-param constructor covering exactly the 12 {@code @Column}-annotated fields above.
     * Used by {@code data-supplier}'s {@code XlsxReader} — it maps every constructor
     * parameter to an Excel column, so {@code deductTdsConfirm} (which has no column in
     * existing sheets) is deliberately excluded here and populated via
     * {@link #setDeductTdsConfirm(String)} after load.
     */
    public SubLedgerInformation(String testCaseID, String lineNo, String subLedger,
                                 String id, String generalLedger, String referenceSite,
                                 String ledgerNarration, String isReversal,
                                 String deductTds, BigDecimal amountDr,
                                 AdjustmentType adjustmentType,
                                 PaymentSelectionType paymentSelection) {
        this.testCaseID      = testCaseID;
        this.lineNo          = lineNo;
        this.subLedger       = subLedger;
        this.id              = id;
        this.generalLedger   = generalLedger;
        this.referenceSite   = referenceSite;
        this.ledgerNarration = ledgerNarration;
        this.isReversal      = isReversal;
        this.deductTds       = deductTds;
        this.amountDr        = amountDr;
        this.adjustmentType  = adjustmentType;
        this.paymentSelection = paymentSelection;
    }

}
