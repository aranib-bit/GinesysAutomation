package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Test-data model for the General Voucher "RevertVoucherInfo" sheet — Excel-mode source of the
 * existing, already-posted voucher number that {@code RevertGeneralVoucher} operates on.
 *
 * <p>Deliberately flat and Revert-only, unlike {@link DocumentInformation} — Revert has no
 * header/line/site-allocation shape to fill, it just needs to locate one existing document in
 * the list page. Read from the same workbook {@code ExcelDataConfig.generalVoucherPath()}
 * already points to (add this as a new tab, not a new file).
 */
@Getter
@NoArgsConstructor
@Sheet(name = "RevertVoucherInfo")
public class RevertVoucherInfo {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Mandatory
    @Column(name = "Existing Voucher No.")
    private String existingVoucherNo;

    /** All-args constructor covering the two {@code @Column}-annotated fields above. */
    public RevertVoucherInfo(String testCaseId, String existingVoucherNo) {
        this.testCaseId = testCaseId;
        this.existingVoucherNo = existingVoucherNo;
    }
}
