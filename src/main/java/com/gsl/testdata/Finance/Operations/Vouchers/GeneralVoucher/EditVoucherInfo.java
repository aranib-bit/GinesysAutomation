package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.converter.BigDecimalConverter;
import com.gsl.converter.SafeDateConverter;
import com.gsl.testdata.annotations.Mandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test-data model for the General Voucher "EditVoucherInfo" sheet — Excel-mode source for
 * {@code EditGeneralVoucher}'s two scenarios (header-only edit, detail-line edit) — plan §5.
 *
 * <p>Mirrors {@link RevertVoucherInfo}'s shape (an existing-voucher-locator sheet, not a
 * full header/line/site-allocation shape like {@link DocumentInformation}) with the edit-specific
 * override columns layered on: {@code Edit Scenario} selects which of the two flows this row
 * drives; {@code New Reference}/{@code New Remarks}/{@code New Date} are the header overrides
 * (always read); {@code Edited Line No.}/{@code New Amount}/{@code New Is Reversal} are only
 * meaningful when {@code Edit Scenario=DETAIL_LINE} — matched against a {@code LineNo} already
 * present in the {@link ExpenseIncomeDetail} sheet for the same {@code TestCaseID}
 * ({@link com.gsl.utils.dataprovider.source.finance.ExcelGeneralVoucherEditDataSource} reads
 * that sheet directly; this class never duplicates its columns).
 *
 * <p><b>IMPORTANT — the sample rows shipped in {@code GeneralVoucher.xlsx}'s EditVoucherInfo tab
 * use PLACEHOLDER "Existing Voucher No." values</b> (e.g. {@code "VG/00120/25-26"}) that will NOT
 * exist in a real database — replace them with a real, currently-existing General Voucher number
 * from your test environment before running {@code EditGeneralVoucher} against
 * {@code testdata.source=EXCEL}.
 */
@Getter
@NoArgsConstructor
@Sheet(name = "EditVoucherInfo")
public class EditVoucherInfo {

    @Mandatory
    @Column(name = "TestCaseID")
    private String testCaseId;

    @Mandatory
    @Column(name = "Existing Voucher No.")
    private String existingVoucherNo;

    /** {@code HEADER_ONLY} or {@code DETAIL_LINE} — selects which of
     *  {@code GeneralVoucherEditChangeSets}'s two builders applies. */
    @Mandatory
    @Column(name = "Edit Scenario")
    private String editScenario;

    @Column(name = "New Reference")
    private String newReference;

    @Column(name = "New Remarks")
    private String newRemarks;

    /** Optional — blank defaults to today. Must never be a future date (see
     *  {@code ExcelGeneralVoucherEditDataSource}'s Javadoc — a future-dated cell throws
     *  {@link org.testng.SkipException} rather than being silently sent to the UI). */
    @Column(name = "New Date", converter = SafeDateConverter.class)
    private LocalDate newDate;

    /** Only read when {@link #editScenario} is {@code DETAIL_LINE} — must match a {@code LineNo}
     *  already present in the {@code ExpenseIncomeDetail} sheet for this {@code TestCaseID}. */
    @Column(name = "Edited Line No.")
    private String editedLineNo;

    /** Only read when {@link #editScenario} is {@code DETAIL_LINE}. */
    @Column(name = "New Amount", converter = BigDecimalConverter.class)
    private BigDecimal newAmount;

    /** Only read when {@link #editScenario} is {@code DETAIL_LINE} — "Yes"/"No"/blank. */
    @Column(name = "New Is Reversal")
    private String newIsReversal;

    /** Only read when {@link #editScenario} is {@code DETAIL_LINE} — optional; blank/absent
     *  leaves the edited line's Ledger Narration unchanged (the pre-existing default), matching
     *  {@link ExpenseIncomeDetail#getLedgerNarration()}'s existing value rather than forcing every
     *  scenario row to declare one. Exercises {@code VoucherEditChangeSets.detailLineEdit}'s
     *  per-line {@code nartext} assertions on {@code finvchdet}/{@code finpost} — see the "Fix
     *  finpost/finvchdet narration-change detection" plan. */
    @Column(name = "New Ledger Narration")
    private String newLedgerNarration;

    /**
     * {@code ACTIONS_EDIT} or {@code DOUBLE_CLICK_EDIT} — selects which live UI path opens the
     * voucher for edit (plan §9.5). Blank defaults to {@code ACTIONS_EDIT} — not a hard business
     * rule like the future-date check, so {@code ExcelGeneralVoucherEditDataSource} doesn't throw
     * on a missing/unrecognized value, only logs a warning and defaults.
     */
    @Column(name = "Entry Method")
    private String entryMethod;

    public boolean isDetailLineScenario() {
        return "DETAIL_LINE".equalsIgnoreCase(editScenario);
    }

    public boolean newIsReversalYes() {
        return "Yes".equalsIgnoreCase(newIsReversal);
    }
}
