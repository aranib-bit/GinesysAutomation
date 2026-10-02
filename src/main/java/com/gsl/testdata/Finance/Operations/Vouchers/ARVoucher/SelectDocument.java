package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.SafeDateConverter;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test-data model for one row in the AR Voucher "SelectDocument" sheet — one document to tick in
 * {@code SelectDocumentPopup}'s grid (opened from a sub-ledger adjustment) plus the adjustment
 * amount to apply against it. Implements {@link VoucherSelectDocRow} so the shared report layer
 * can iterate AP and AR rows without per-voucher casts. Structurally identical to
 * {@code APVoucher.SelectDocument}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name = "SelectDocument")
public class SelectDocument implements VoucherSelectDocRow {
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
