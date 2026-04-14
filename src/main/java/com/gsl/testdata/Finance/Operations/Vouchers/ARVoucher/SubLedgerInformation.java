package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.AdjustmentTypeConverter;
import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.PaymentSelectionConverter;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Sheet(name= "SubLedgerInformation")
public class SubLedgerInformation {

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
