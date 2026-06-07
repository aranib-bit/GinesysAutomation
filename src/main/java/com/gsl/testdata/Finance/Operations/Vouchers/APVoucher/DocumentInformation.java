package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.SafeDateConverter;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * <p><b>Optional field note:</b> {@code onAccountConfirm} lives in
 * {@link DocumentInformationBase}. XlsxReader calls {@code getDeclaredFields()} on
 * this class only — inherited fields are invisible to it, so the field is never
 * treated as a required Excel column. It is populated post-load via
 * {@link #setOnAccountConfirm(String)} by {@code ExcelApVoucherDataSource}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name = "DocumentInformation")
public class DocumentInformation extends DocumentInformationBase implements VoucherDocumentHeader {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Column(name = "Owner Site")
    private String ownersite;

    @Column(name = "Voucher No")
    private String voucherno;

    @Column(name = "Cash/Bank")
    private String cashbank;

    @Column(name = "Remarks")
    private String remarks;

    @Mandatory
    @Column(name = "Date", converter = SafeDateConverter.class)
    private LocalDate documentdate;

    @Column(name = "Reference")
    private String reference;

    @Column(name = "On Account")
    private String onAccount;

    public boolean isOnAccount() {
        return "Yes".equalsIgnoreCase(onAccount);
    }
}
