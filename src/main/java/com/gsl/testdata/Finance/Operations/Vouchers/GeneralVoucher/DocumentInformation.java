package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.converter.SafeDateConverter;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Test-data model for the General Voucher "DocumentInformation" sheet.
 *
 * <p>Unlike AP, General Voucher has no "On Account" concept — its header
 * shape is a plain {@link VoucherDocumentHeader}.
 *
 * <p><b>Optional field note (see APVoucher.DocumentInformationBase for the
 * same idiom):</b> XlsxReader calls {@code getDeclaredFields()} on this class
 * only — inherited fields are invisible to it. {@link #getOwnersite()} lives
 * in {@link DocumentInformationBase} for exactly that reason: it is
 * deliberately NOT sourced from Excel (resolved live via the logged-in
 * user's site, same as AP/AR's Excel mode) and is populated only via its
 * setter when a test needs to override the default. Declaring it directly
 * on this class (as a plain, unannotated field) does NOT exclude it —
 * XlsxReader still expects a matching column and throws when one is absent.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "DocumentInformation")
public class DocumentInformation extends DocumentInformationBase implements VoucherDocumentHeader {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Column(name = "Voucher No.")
    private String voucherno;

    @Mandatory
    @Column(name = "Date", converter = SafeDateConverter.class)
    private LocalDate documentdate;

    @Column(name = "Reference")
    private String reference;

    @Column(name = "Cash/Bank")
    private String cashbank;

    @Column(name = "Remarks")
    private String remarks;

    /**
     * 6-param constructor covering exactly the 6 {@code @Column}-annotated
     * fields above. {@code ownersite} (inherited from {@link DocumentInformationBase})
     * is deliberately excluded — see class Javadoc.
     */
    public DocumentInformation(String testCaseId, String voucherno, LocalDate documentdate,
                                String reference, String cashbank, String remarks) {
        this.testCaseId   = testCaseId;
        this.voucherno    = voucherno;
        this.documentdate = documentdate;
        this.reference    = reference;
        this.cashbank     = cashbank;
        this.remarks      = remarks;
    }
}
