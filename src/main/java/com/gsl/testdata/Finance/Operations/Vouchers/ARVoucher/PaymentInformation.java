package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.PaymentModeConverter;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Test-data model for one row in the AR Voucher "PaymentInformation" sheet — the payment-mode
 * details (mode, instrument no./date, drawn-on bank, reference) for a sub-ledger adjustment line.
 * Structurally identical to {@code APVoucher.PaymentInformation}; both implement
 * {@link VoucherPaymentInfo} so shared resolvers can work against either uniformly.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name= "PaymentInformation")
public class PaymentInformation implements VoucherPaymentInfo {

    @Column(name = "TestCaseID")
    private String testCaseID;
    @Column(name = "LineNo")
    private String lineNo;
    @Column(name = "Payment Mode", converter = PaymentModeConverter.class)
    private PaymentSelectionType paymentMode;
    @Column(name = "Instrument No")
    private String instrumentNo;
    @Column(name = "Instrument Date")
    private String instrumentDate;
    @Column(name="Drawn On")
    private String drawnOn;
    @Column(name="Reference")
    private String reference;
}
