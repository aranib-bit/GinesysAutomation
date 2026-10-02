package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Test-data model for one row in the AP Voucher "TDS&amp;NetPayable" sheet — drives the TDS
 * section that appears when a sub-ledger line's supplier has TDS applicable, keyed by
 * {@code lineNo} to match a {@code SubLedgerInformation} row.
 */
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

    /**
     * Drives which TDS Applicable Amount scenario is exercised for this row.
     * Values: GROSS_EQUALS_TDS | PARTIAL_EDIT | CLEAR_ERROR_THEN_PARTIAL_EDIT
     *
     * <p>Blank/null → resolved to GROSS_EQUALS_TDS by
     * {@link com.gsl.businesslogic.Finance.Operations.APVoucher.TdsApplicableAmountBusinessLogic#resolve}
     * — existing Excel rows with no value in this column are unaffected.
     */
    @Column(name = "TDS Applicable Amount Behaviour")
    private String tdsApplicableAmountBehaviour;
}
