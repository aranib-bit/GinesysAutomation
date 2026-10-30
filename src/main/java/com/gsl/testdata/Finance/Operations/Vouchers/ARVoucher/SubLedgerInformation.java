package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.AdjustmentTypeConverter;
import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.PaymentSelectionConverter;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerFillRow;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Test-data model for one sub-ledger row in the AR Voucher SubLedgerInformation sheet — the AR
 * counterpart of {@code APVoucher.SubLedgerInformation}, posting to Amount(Cr) instead of
 * Amount(Dr). AR has no Deduct-TDS concept, so unlike AP's version this class declares no
 * {@code deductTds} column. Extends {@link SubLedgerInformationBase}, which — despite living in
 * this ARVoucher package — is also the base class AP's own {@code SubLedgerInformation} extends
 * (see that base class's Javadoc).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name= "SubLedgerInformation")
public class SubLedgerInformation extends SubLedgerInformationBase implements VoucherSubLedgerFillRow {

    @Column(name = "TestCaseID")
    private String testCaseID;
    @Column(name = "LineNo")
    private String lineNo;
    @Column(name="Sub Ledger")
    private String subLedger;
    @Column(name = "Id")
    private String id;
    @Column(name="General Ledger")
    private String generalLedger;
    @Column(name = "Reference Site")
    @lombok.Setter
    private String referenceSite;
    @Column(name = "Ledger Narration")
    private String ledgerNarration;
    @Column(name="Is Reversal")
    private String isReversal;
    @Column(name="Amount(Cr)",converter =  BigDecimalConverter.class)
    private BigDecimal amountCr;
    @Column(name = "AdjustmentType", converter = AdjustmentTypeConverter.class)
    private AdjustmentType adjustmentType;
    @Column(name = "PaymentSelection", converter = PaymentSelectionConverter.class)
    private PaymentSelectionType paymentSelection;

}
