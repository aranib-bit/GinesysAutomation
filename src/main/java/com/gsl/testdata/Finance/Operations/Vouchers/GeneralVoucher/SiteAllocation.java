package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Test-data model for one row in the General Voucher "SiteAllocation" sheet
 * — the nested-site-amount-pairs sheet feeding the Allocate Sites popup.
 *
 * <p>Flat, repeated-key rows (TestCaseID + LineNo), structurally identical to
 * {@code APVoucher.ReferenceSitewiseFIFOsetoff} — the proven precedent in
 * this codebase for "one line item ↔ many nested picks". Only rows for
 * Type 2/Type 3 {@code ExpenseIncomeDetail} lines carry entries here; Type 1
 * lines have none, since Site isn't applicable.
 */
@Getter
@Setter
@Sheet(name = "SiteAllocation")
public class SiteAllocation {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "Site Name")
    private String siteName;

    @Column(name = "Amount", converter = BigDecimalConverter.class)
    private BigDecimal amount;
}
