package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name = "TDS&NetPayable")
public class TdsAndNetPayable {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "TDS Name")
    private String tdsName;

    @Column(name = "Gross Payable", converter = BigDecimalConverter.class)
    private BigDecimal grossPayable;

    @Column(name = "TDS Applicable Amount", converter = BigDecimalConverter.class)
    private BigDecimal tdsApplicableAmount;

    @Column(name = "Populate TDS Exception Rate")
    private String populateTdsExceptionRate;
}
