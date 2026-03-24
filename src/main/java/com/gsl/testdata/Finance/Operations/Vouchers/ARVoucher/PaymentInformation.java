package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

@Getter
@Sheet(name= "PaymentInformation")
public class PaymentInformation {

    @Column(name = "TestCaseID")
    private String testCaseID;
    @Column(name = "LineNo")
    private String lineNo;
    @Column(name = "Payment Mode")
    private String paymentMode;
    @Column(name = "Instrument No")
    private String instrumentNo;
    @Column(name = "Instrument Date")
    private String instrumentDate;
    @Column(name="Drawn On")
    private String drawnOn;
    @Column(name="Reference")
    private String reference;
}
