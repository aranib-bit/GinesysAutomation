package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Test-data model for the AP Voucher "ApDeleteVoucherInfo" sheet — Excel-mode source of the
 * existing voucher number that {@code DeleteApVoucher} operates on. Mirrors
 * {@link ApRevertVoucherInfo}'s shape exactly (a plain existing-voucher-locator sheet) — Delete
 * has no header/line shape to fill either, and (per {@code DeleteArVoucher}'s own convention)
 * {@code release_status} is read live from {@code finvchmain} at test-execution time, not
 * declared as a sheet column.
 *
 * <p><b>IMPORTANT — the sample row shipped in {@code ApVoucher.xlsx}'s ApDeleteVoucherInfo tab
 * uses a PLACEHOLDER "Existing Voucher No." value ({@code "VP/02485/26-27"})</b> — replace it
 * with a real, currently-existing AP Voucher number from your test environment first.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "ApDeleteVoucherInfo")
public class ApDeleteVoucherInfo {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Mandatory
    @Column(name = "Existing Voucher No.")
    private String existingVoucherNo;

    /** All-args constructor covering the two {@code @Column}-annotated fields above. */
    public ApDeleteVoucherInfo(String testCaseId, String existingVoucherNo) {
        this.testCaseId = testCaseId;
        this.existingVoucherNo = existingVoucherNo;
    }
}
