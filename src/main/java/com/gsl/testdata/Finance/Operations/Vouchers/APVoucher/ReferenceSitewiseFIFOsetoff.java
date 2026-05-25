package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Sheet(name = "ReferenceSitewiseFIFOsetoff")
public class ReferenceSitewiseFIFOsetoff {

    @Column(name = "Reference Site Name")
    private String referenceSiteName;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "Adjustment Amount", converter = BigDecimalConverter.class)
    private BigDecimal adjustmentAmount;
}
