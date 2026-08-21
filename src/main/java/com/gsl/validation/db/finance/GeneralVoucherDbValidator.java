package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.enums.VoucherType;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.DatabaseLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import org.assertj.core.api.SoftAssertions;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-level DB validation orchestrator for General Voucher — rewritten to mirror
 * {@code APVoucherDbValidator}/{@code ARVoucherDbValidator}'s reporting shape (per-level
 * banners via {@link ReportLogger#db}, structured pass/fail via {@link DatabaseLogger},
 * data tables via {@code ReportLogger.infoLabeledTable}, {@link ActualResultsHolder}
 * population) instead of the two bare log lines the original version had.
 *
 * <p>Execution order:
 * <ol>
 *     <li><b>Level 1</b> {@link FinVchMainGvValidator} — BLOCKING existence/vchcode fetch,
 *         DEFERRED field checks (vchtype, vchdt, refno, nartext, entry_source, creation_dt,
 *         damount) — reused as-is from the shared {@link FinVchMainBaseValidator}.</li>
 *     <li><b>Level 3</b> {@link FinPostValidator} — BLOCKING existence/balance, reused as-is
 *         via a minimal {@link VoucherDbContext} shim (same trick {@code APVoucherDbValidator}
 *         uses) — plus a GV-only VMG/VDG {@code enttype} cardinality check on the same
 *         {@code List<FinPost>} it returns (no second DB fetch).</li>
 *     <li><b>Level 2</b> finvchdet per-line checks — {@code (vchcode, glcode, slcode)} matching
 *         (same shape as AP/AR's own matching; {@code slcode} resolves to {@code 0} for GV's
 *         Type1 rows with no sub-ledger — {@code glcode} alone previously matched the wrong row
 *         when two lines shared a GL account with different sub-ledgers, confirmed live), amount
 *         (Dr or Cr per {@code isDebit()}), {@code isreversal}, and narration.</li>
 *     <li><b>Level 4</b> {@link FinCostTagValidator} — GV-specific: {@code fincosttag}
 *         site-allocation tagging for Type2/Type3 rows. A structurally different table from
 *         AP/AR's {@code fintag} (adjustment tagging), not a renamed one — kept as its own
 *         class rather than folded into {@code FinTagValidator}.</li>
 * </ol>
 *
 * <p>Levels 2/3(add-on)/4 are collected into one {@link SoftAssertions} block — matching the
 * original version's grouping (which held vchtype+VMG/VDG+per-line checks together) minus
 * vchtype, now owned by Level 1's own deferred mechanism.
 */
public class GeneralVoucherDbValidator {

    private final FinVchMainGvValidator finVchMainGvValidator;
    private final FinPostValidator      finPostValidator;
    private final FinCostTagValidator   finCostTagValidator;
    private final FinVchDetDao          finVchDetDao;
    private final FinGlDao              finGlDao;
    private final FinSlDao              finSlDao;

    public GeneralVoucherDbValidator() {
        Jdbi jdbi = DatabaseManager.getJdbi();
        this.finVchMainGvValidator = new FinVchMainGvValidator();
        this.finPostValidator      = new FinPostValidator();
        this.finCostTagValidator   = new FinCostTagValidator();
        this.finVchDetDao          = jdbi.onDemand(FinVchDetDao.class);
        this.finGlDao              = jdbi.onDemand(FinGlDao.class);
        this.finSlDao              = jdbi.onDemand(FinSlDao.class);
    }

    /** Backward-compatible entry point — {@code saveStartTime} unknown, creation_dt tolerance
     *  check is skipped (per {@code FinVchMainBaseValidator.validateCommonHeaderFields}'s own
     *  null-handling contract), same as the original version's behavior. */
    public void validate(MergedFinanceGeneralVoucherData data, String voucherNo) {
        validate(data, voucherNo, null);
    }

    public void validate(MergedFinanceGeneralVoucherData data, String voucherNo, LocalDateTime saveStartTime) {
        Assert.assertNotNull(voucherNo, "voucherNo is null — General Voucher creation may have failed");
        Assert.assertFalse(voucherNo.isBlank(), "voucherNo is blank");

        ActualResultsHolder holder = ActualResultsHolder.get();
        holder.setVoucherNo(voucherNo);
        holder.setVoucherType(VoucherType.GENERAL.name());
        holder.setTestData(data);

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        DatabaseLogger.validationStart("General Voucher DB Validation — [" + voucherNo + "]");

        // Level 1 field assertions are deferred so a nartext/vchdt/damount mismatch does NOT
        // block Levels 2–4 — same convention as ARVoucherDbValidator/APVoucherDbValidator.
        SoftAssertions deferredLevel1 = new SoftAssertions();

        ReportLogger.db("── Level 1 : finvchmain ──");
        FinVchMain header = finVchMainGvValidator.validate(
                voucherNo, data.getDocumentInformation(), saveStartTime, deferredLevel1);

        // FinPostValidator only uses voucherNo — a minimal shim is safe here (same trick
        // APVoucherDbValidator.java uses to reuse this shared, VoucherDbContext-typed validator).
        VoucherDbContext finPostCtx = VoucherDbContext.builder()
                .voucherNo(voucherNo)
                .preState(Collections.emptyMap())
                .postState(Collections.emptyMap())
                .build();

        ReportLogger.db("── Level 3 : finpost (GL balance) ──");
        List<FinPost> postEntries = finPostValidator.validate(finPostCtx, header);
        DatabaseLogger.checkPassed("Level 3 — finpost GL balance", postEntries.size() + " GL entries balanced");

        long vchcode = header.getVchcode();
        String entcode = String.valueOf(vchcode);
        List<FinVchDet> detailRows = finVchDetDao.findByVoucherCode(vchcode);
        List<FinCostTag> allCostTags = new ArrayList<>();
        int[] siteRowsValidated = {0};

        try {
            SoftAssertions.assertSoftly(soft -> {
                validateVmgVdgCardinality(soft, data, postEntries, header);
                for (ExpenseIncomeDetail row : data.getExpenseIncomeDetailList()) {
                    List<FinCostTag> lineTags = validateLine(soft, entcode, detailRows, row, data);
                    if (lineTags != null) {
                        siteRowsValidated[0]++;
                        allCostTags.addAll(lineTags);
                    }
                }
                validateCounterLegPaymentInfo(soft, entcode, detailRows, data, header);
            });
            DatabaseLogger.checkPassed("Level 2/3(VMG-VDG)/4 — finvchdet, finpost cardinality, fincosttag",
                    detailRows.size() + " finvchdet row(s), " + allCostTags.size() + " fincosttag row(s)");
        } catch (AssertionError e) {
            DatabaseLogger.checkFailed("Level 2/3(VMG-VDG)/4 — finvchdet, finpost cardinality, fincosttag",
                    "all checks valid", e.getMessage(), "(see finvchdet/finpost/fincosttag fetches above)",
                    voucherNo, 0, detailRows.size());
            throw e;
        }

        holder.setDetRowCount(detailRows.size());
        if (siteRowsValidated[0] == 0) {
            ReportLogger.db("Level 4 — skipped [" + voucherNo + "]: no site-applicable rows");
            holder.setTagCount(-1);
        } else {
            holder.setTagCount(allCostTags.size());
        }

        // Now surface any Level 1 field mismatches collected above
        try {
            deferredLevel1.assertAll();
            DatabaseLogger.checkPassed("Level 1 — finvchmain header fields", "all deferred field checks passed");
        } catch (AssertionError e) {
            DatabaseLogger.checkFailed("Level 1 — finvchmain header fields", "all fields valid", e.getMessage(),
                    "(see finvchmain fetch above)", voucherNo, 0, 1);
            throw e;
        }

        DatabaseLogger.validationComplete("General Voucher DB Validation — [" + voucherNo + "]", true);
    }

    /** GV-only — AP/AR have no {@code enttype} (VMG/VDG) concept in finpost. */
    private void validateVmgVdgCardinality(SoftAssertions soft, MergedFinanceGeneralVoucherData data,
                                            List<FinPost> postRows, FinVchMain header) {
        long vmgCount = postRows.stream().filter(p -> "VMG".equalsIgnoreCase(p.getEnttype())).count();
        long vdgCount = postRows.stream().filter(p -> "VDG".equalsIgnoreCase(p.getEnttype())).count();
        int expectedVdg = data.getExpenseIncomeDetailList() != null ? data.getExpenseIncomeDetailList().size() : 0;

        soft.assertThat(vmgCount)
                .as("[Level 3] finpost 'VMG' (Cash/Bank header) row count for vchcode [" + header.getVchcode() + "]")
                .isEqualTo(1L);
        soft.assertThat(vdgCount)
                .as("[Level 3] finpost 'VDG' row count for vchcode [" + header.getVchcode() + "]")
                .isEqualTo((long) expectedVdg);
    }

    /** @return the fincosttag rows fetched for this line's site allocation, or {@code null}
     *          when the row isn't site-applicable (skipped, not a failure). */
    private List<FinCostTag> validateLine(SoftAssertions soft, String entcode, List<FinVchDet> detailRows,
                               ExpenseIncomeDetail row, MergedFinanceGeneralVoucherData data) {

        FinGl gl = finGlDao.findByGlName(row.getGeneralLedger());
        soft.assertThat(gl)
                .as("[Level 2] fingl row not found for General Ledger '"
                        + row.getGeneralLedger() + "' (LineNo [" + row.getLineNo() + "])")
                .isNotNull();
        if (gl == null) return null;

        // glcode alone doesn't disambiguate two lines sharing a GL account with different
        // sub-ledgers — confirmed live (same collision class VoucherEditChangeSets.
        // findFinvchdetRow/findFinpostRowByGlcode already guard against). slcode resolves to 0
        // for a Type1 row with no sub-ledger (blank Sub Ledger), matching FinVchDet.slcode's
        // (primitive long) representation of a NULL DB column.
        long slcode = resolveSlcode(row);

        FinVchDet detailRow = detailRows.stream()
                .filter(d -> d.getGlcode() == gl.getGlcode() && d.getSlcode() == slcode)
                .findFirst().orElse(null);
        soft.assertThat(detailRow)
                .as("[Level 2] finvchdet row not found for entcode [" + entcode
                        + "] glcode [" + gl.getGlcode() + "] slcode [" + slcode
                        + "] (LineNo [" + row.getLineNo() + "])")
                .isNotNull();
        if (detailRow == null) return null;

        BigDecimal expectedAmount = row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
        BigDecimal actualAmount   = row.isDebit() ? detailRow.getDamount() : detailRow.getCamount();
        soft.assertThat(actualAmount)
                .as("[Level 2] finvchdet amount mismatch for entcode [" + entcode
                        + "] glcode [" + gl.getGlcode() + "] slcode [" + slcode
                        + "]: expected=" + expectedAmount + " actual=" + actualAmount)
                .isEqualByComparingTo(expectedAmount);

        soft.assertThat(detailRow.getIsReversal())
                .as("[Level 2] finvchdet.isreversal mismatch for entcode [" + entcode
                        + "] glcode [" + gl.getGlcode() + "] slcode [" + slcode + "]")
                .isEqualToIgnoringCase(row.isReversalYes() ? "Y" : "N");

        // Mirrors AR/AP's FinVchDetValidator/FinVchDetApValidator nartext assertion — only
        // checked when test data actually supplies a per-line narration.
        if (row.getLedgerNarration() != null && !row.getLedgerNarration().isBlank()) {
            soft.assertThat(detailRow.getNartext())
                    .as("[Level 2] finvchdet.nartext mismatch for entcode [" + entcode
                            + "] glcode [" + gl.getGlcode() + "] slcode [" + slcode + "]")
                    .isEqualTo(row.getLedgerNarration());
        }

        if (row.getResolvedGlType() != null && row.getResolvedGlType().isSiteApplicable()) {
            List<SiteAllocation> expectedAllocations = data.getSiteAllocationsForLine(row.getLineNo());
            return finCostTagValidator.validate(soft, entcode, gl, detailRow, row, expectedAllocations);
        }
        return null;
    }

    /** Resolves the line's {@code finsl.slcode} — {@code 0} for a blank Sub Ledger (Type1, no
     *  sub-ledger) and as a safe fallback if the name can't be resolved (logged via the soft
     *  assertion trail elsewhere, not thrown — mirrors
     *  {@code GeneralVoucherEditChangeSets.resolveSlcode}'s same fallback). */
    private long resolveSlcode(ExpenseIncomeDetail row) {
        String subLedgerName = row.getSubLedger();
        if (subLedgerName == null || subLedgerName.isBlank()) return 0L;

        String subLedgerId = row.getSubLedgerId();
        FinSl sl = (subLedgerId != null && !subLedgerId.isBlank())
                ? finSlDao.findBySlnameAndSlid(subLedgerName, subLedgerId)
                : firstOrNull(finSlDao.findBySlname(subLedgerName));
        return sl == null ? 0L : sl.getSlcode();
    }

    private static FinSl firstOrNull(List<FinSl> rows) {
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }

    /**
     * Validates the Cash/Bank counter-leg for combination-matrix rows only
     * ({@code data.getFixtureOptions() != null} — Excel mode and the 3 existing negative fixtures
     * never set it, so they're left untouched).
     *
     * <p><b>Confirmed via live DB query</b> (two saved vouchers — one Bank/Cheque, one Cash — see
     * the plan's "Post-implementation finding 2"): the counter-leg is recorded ONLY on
     * {@code finvchmain} ({@link FinVchMain#getGlcode()}/{@code damount}/{@code camount}, the
     * header) and posted to {@code finpost} as the VMG entry — it never gets its own
     * {@code finvchdet} row. {@code finvchdet} holds only the Expense/Income Detail grid row(s),
     * and {@code payment_mode} is stored on THAT row, not a "counter-leg row" (which doesn't
     * exist). The previous version of this method searched {@code finvchdet} for a counter-leg
     * row keyed by the counter-leg's own glcode — structurally impossible to find, so it failed
     * for every voucher regardless of payment variant.
     *
     * <p>Only asserts a specific expected payment_mode code for variants where one is confirmed
     * ({@code GeneralVoucherPaymentVariant.expectedDbPaymentModeCode()} — {@code CHEQUE} → {@code
     * 'C'}, {@code CASH} → {@code 'H'}, both confirmed live in the two samples above). For
     * {@code NONE}, the actual stored default is an open item — logged for diagnostics, not
     * asserted, until confirmed against the live DB.
     */
    private void validateCounterLegPaymentInfo(SoftAssertions soft, String entcode,
            List<FinVchDet> detailRows, MergedFinanceGeneralVoucherData data, FinVchMain header) {
        if (data.getFixtureOptions() == null) return; // not a combination-matrix row

        String cashBankName = data.getDocumentInformation().getCashbank();
        FinGl cashBankGl = finGlDao.findByGlName(cashBankName);
        if (cashBankGl == null) {
            ReportLogger.warn("[Level 2 — payment info] fingl row not found for counter-leg '"
                    + cashBankName + "' — skipping payment_mode check.");
            return;
        }

        // The counter-leg IS the header — not a finvchdet row (see method Javadoc).
        soft.assertThat(header.getGlcode())
                .as("[Level 2] finvchmain counter-leg glcode mismatch for entcode [" + entcode
                        + "]: expected [" + cashBankGl.getGlcode() + "] ('" + cashBankName + "')")
                .isEqualTo(cashBankGl.getGlcode());

        List<com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail> rows =
                data.getExpenseIncomeDetailList();
        if (rows != null && !rows.isEmpty()) {
            // Single Expense/Income Detail line per combination-matrix voucher today (see method
            // Javadoc's plan reference) — the header's counter-leg amount must land on the
            // OPPOSITE side from the detail line, same magnitude (confirmed in both live samples:
            // detail Cr 900 <-> header Dr 900, and detail Dr 900 <-> header Cr 900).
            var firstRow = rows.get(0);
            BigDecimal expectedTotal = firstRow.isDebit() ? firstRow.getExpenseDr() : firstRow.getIncomeCr();
            BigDecimal actualHeaderAmount = firstRow.isDebit() ? header.getCamount() : header.getDamount();
            soft.assertThat(actualHeaderAmount)
                    .as("[Level 2] finvchmain counter-leg amount mismatch for entcode [" + entcode
                            + "] glcode [" + cashBankGl.getGlcode() + "]: expected=" + expectedTotal
                            + " actual=" + actualHeaderAmount)
                    .isEqualByComparingTo(expectedTotal);
        }

        // payment_mode lives on the Expense/Income detail row(s), not a counter-leg row.
        FinVchDet firstDetailRow = detailRows.isEmpty() ? null : detailRows.get(0);
        if (firstDetailRow == null) return;

        String expectedCode = data.getFixtureOptions().getPaymentVariant().expectedDbPaymentModeCode();
        if (expectedCode == null) {
            ReportLogger.db("[Level 2 — payment info] " + data.getFixtureOptions().getPaymentVariant()
                    + " — actual finvchdet.payment_mode='" + firstDetailRow.getPaymentMode()
                    + "' (not asserted — expected value unconfirmed, see plan's Open Items)");
            return;
        }
        soft.assertThat(firstDetailRow.getPaymentMode())
                .as("[Level 2] finvchdet.payment_mode mismatch for entcode [" + entcode
                        + "] (" + data.getFixtureOptions().getPaymentVariant() + ")")
                .isEqualToIgnoringCase(expectedCode);
    }
}
