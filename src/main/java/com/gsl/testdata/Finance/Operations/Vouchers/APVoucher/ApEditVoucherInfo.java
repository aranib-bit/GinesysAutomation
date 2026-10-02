package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.converter.SafeDateConverter;
import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Test-data model for the AP Voucher "ApEditVoucherInfo" sheet — Excel-mode source for
 * {@code EditApVoucher}'s single header-only scenario. Mirrors General Voucher's
 * {@code EditVoucherInfo} shape, trimmed to header-only fields — AP Edit this pass has no
 * DETAIL_LINE/PAYMENT_INFO variant (unlike {@code EditGeneralVoucher}), so this sheet carries no
 * {@code Edit Scenario}/{@code Edited Line No.}/{@code New Amount}/{@code New Is Reversal}
 * columns at all.
 *
 * <p><b>IMPORTANT — the sample row shipped in {@code ApVoucher.xlsx}'s ApEditVoucherInfo tab uses
 * a PLACEHOLDER "Existing Voucher No." value ({@code "VP/02484/26-27"})</b> — replace it with a
 * real, currently-existing AP Voucher number from your test environment first, same
 * "replace with a real voucher from your environment first" caveat {@code EditVoucherInfo}'s own
 * Javadoc carries for General Voucher.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "ApEditVoucherInfo")
public class ApEditVoucherInfo {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Mandatory
    @Column(name = "Existing Voucher No.")
    private String existingVoucherNo;

    @Column(name = "New Reference")
    private String newReference;

    @Column(name = "New Remarks")
    private String newRemarks;

    /** Optional — blank resolves an accounting-year-aware date instead (see
     *  {@code ExcelApVoucherEditDataSource}'s Javadoc), the same convention
     *  {@code ExcelGeneralVoucherEditDataSource} already uses for its own blank-cell case. */
    @Column(name = "New Date", converter = SafeDateConverter.class)
    private LocalDate newDate;

    /** All-args constructor covering the five {@code @Column}-annotated fields above. */
    public ApEditVoucherInfo(String testCaseId, String existingVoucherNo, String newReference,
            String newRemarks, LocalDate newDate) {
        this.testCaseId = testCaseId;
        this.existingVoucherNo = existingVoucherNo;
        this.newReference = newReference;
        this.newRemarks = newRemarks;
        this.newDate = newDate;
    }
}
