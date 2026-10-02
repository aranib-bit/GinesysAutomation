package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.BigDecimalConverter;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Test-data model for one row in the AR Voucher "ReferenceSitewiseFIFOsetoff" sheet — feeds the
 * Reference Sitewise FIFO Setoff popup ({@code ReferenceSiteWiseFIFOPopup}) that
 * {@code AdjustmentEntry} opens when a sub-ledger row's adjustment type is
 * {@code AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF}. Rows are grouped by {@code lineNo}
 * (matching a {@code SubLedgerInformation} row); each one selects one Reference Site in the
 * popup's combo before Auto Adjust is clicked. Structurally identical to
 * {@code APVoucher.ReferenceSitewiseFIFOsetoff}.
 */
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
