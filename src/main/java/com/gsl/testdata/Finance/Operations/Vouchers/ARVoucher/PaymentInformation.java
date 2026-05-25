package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.PaymentModeConverter;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
