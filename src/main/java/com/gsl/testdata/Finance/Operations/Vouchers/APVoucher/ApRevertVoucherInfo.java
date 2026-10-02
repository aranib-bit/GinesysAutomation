package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Test-data model for the AP Voucher "ApRevertVoucherInfo" sheet — Excel-mode source of the
 * existing, already-posted voucher number that {@code RevertApVoucher} operates on. Mirrors
 * General Voucher's {@code RevertVoucherInfo} shape exactly — deliberately flat and Revert-only,
 * unlike {@code DocumentInformation} — Revert has no header/line shape to fill, it just needs to
 * locate one existing document in the list page. Untag detection is a live DB read regardless of
 * source (Excel vs DB — see {@code DocumentAdjustmentFlow}), so this sheet needs no separate
 * "is it tagged" column.
 *
 * <p><b>IMPORTANT — the sample row shipped in {@code ApVoucher.xlsx}'s ApRevertVoucherInfo tab
 * uses a PLACEHOLDER "Existing Voucher No." value ({@code "VP/02484/26-27"})</b> that will NOT
 * exist in a real database once your environment's data changes — replace it with a real,
 * currently-existing AP Voucher number from your test environment first, same caveat
 * {@code EditVoucherInfo}'s own Javadoc already carries for General Voucher.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "ApRevertVoucherInfo")
public class ApRevertVoucherInfo {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Mandatory
    @Column(name = "Existing Voucher No.")
    private String existingVoucherNo;

    /** All-args constructor covering the two {@code @Column}-annotated fields above. */
    public ApRevertVoucherInfo(String testCaseId, String existingVoucherNo) {
        this.testCaseId = testCaseId;
        this.existingVoucherNo = existingVoucherNo;
    }
}
