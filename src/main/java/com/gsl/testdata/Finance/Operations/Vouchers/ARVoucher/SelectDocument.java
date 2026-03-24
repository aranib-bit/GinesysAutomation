package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.SafeDateConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Sheet(name = "SelectDocument")
public class SelectDocument {
    @Column(name="TestCaseID")
    private String testCaseID;
    @Column(name="LineNo")
    private String lineNo;
    @Column(name = "Document number")
    private String documentNumber;
    @Column(name = "Document Date",converter = SafeDateConverter.class)
    private LocalDate documentDate;
    @Column(name = "Document Type")
    private String documentType;
    @Column(name="Reference No")
    private String referenceNo;
    @Column(name="Reference Date",converter = SafeDateConverter.class)
    private LocalDate referenceDate;
    @Column(name = "Reference Site")
    private String referenceSite;
    @Column(name="Adjustment Amount",converter = BigDecimalConverter.class)
    private BigDecimal adjustmentAmount;
}
