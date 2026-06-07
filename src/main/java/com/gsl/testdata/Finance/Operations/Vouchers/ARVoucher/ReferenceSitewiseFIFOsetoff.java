package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Sheet(name="ReferenceSitewiseFIFOsetoff")
public class ReferenceSitewiseFIFOsetoff {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name="Reference Site Name")
    private String referenceSiteName;

    /** Name of the row to click in the sub-grid (may match referenceSiteName or differ by site grouping). */
    @Column(name = "Site Name")
    private String siteName;

    @Column(name = "LineNo")
    private String lineNo;
    @Column(name = "Adjustment Amount",converter = BigDecimalConverter.class)
    private BigDecimal adjustmentAmount;
}
