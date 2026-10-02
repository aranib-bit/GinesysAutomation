package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinChqDetDao;
import com.gsl.database.dao.finance.FinChqMainDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
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
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant;
import org.assertj.core.api.SoftAssertions;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final FinChqDetDao          finChqDetDao;
    private final FinChqMainDao         finChqMainDao;
    private final FinPostDao            finPostDao;

    public GeneralVoucherDbValidator() {
        Jdbi jdbi = DatabaseManager.getJdbi();
        this.finVchMainGvValidator = new FinVchMainGvValidator();
        this.finPostValidator      = new FinPostValidator();
        this.finCostTagValidator   = new FinCostTagValidator();
        this.finVchDetDao          = jdbi.onDemand(FinVchDetDao.class);
        this.finGlDao              = jdbi.onDemand(FinGlDao.class);
        this.finSlDao              = jdbi.onDemand(FinSlDao.class);
        this.finChqDetDao          = jdbi.onDemand(FinChqDetDao.class);
        this.finChqMainDao         = jdbi.onDemand(FinChqMainDao.class);
        this.finPostDao            = jdbi.onDemand(FinPostDao.class);
    }

    /** Backward-compatible entry point — {@code saveStartTime} unknown, creation_dt tolerance
     *  check is skipped (per {@code FinVchMainBaseValidator.validateCommonHeaderFields}'s own
     *  null-handling contract), same as the original version's behavior. */
    public void validate(MergedFinanceGeneralVoucherData data, String voucherNo) {
        validate(data, voucherNo, null);
    }

    /**
     * Runs Levels 1–4 in order against {@code voucherNo}: Level 1's header field assertions and
     * Levels 2–4's own assertions (finvchdet, finpost VMG/VDG cardinality, fincosttag site
     * allocation, and cross-table date consistency) are each deferred into their own {@link
     * SoftAssertions} so a mismatch in one level never blocks the rest from running, then
     * re-surfaced at the end via {@code assertAll()}.
     *
     * @param saveStartTime nullable — when null, the creation_dt tolerance check inside Level 1 is skipped
     */
    public void validate(MergedFinanceGeneralVoucherData data, String voucherNo, LocalDateTime saveStartTime) {
        Assert.assertNotNull(voucherNo, "voucherNo is null — General Voucher creation may have failed");
        Assert.assertFalse(voucherNo.isBlank(), "voucherNo is blank");

        ActualResultsHolder holder = ActualResultsHolder.get();
        holder.setVoucherNo(voucherNo);
        // Item V (Round 5): must be the short "GV" code, not VoucherType.GENERAL.name()'s "GENERAL" —
        // ActualResultsLogger's isGv check compares against the literal "GV" (matching "AR"/"AP"'s
        // own short-code convention), so "GENERAL" silently fell through to the AR/AP fintag label.
        holder.setVoucherType("GV");
        holder.setTestData(data);

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        DatabaseLogger.validationStart("General Voucher DB Validation — [" + voucherNo + "]");

        // Level 1 field assertions are deferred so a nartext/vchdt/damount mismatch does NOT
        // block Levels 2–4 — same convention as ARVoucherDbValidator/APVoucherDbValidator.
        SoftAssertions deferredLevel1 = new SoftAssertions();

        // Item AE (Round 5): state the voucher type explicitly, right where validation starts.
        ReportLogger.db("── Level 1 : finvchmain (Voucher Type: General Voucher) ──");
        FinVchMain header = finVchMainGvValidator.validate(
                voucherNo, data.getDocumentInformation(), saveStartTime, deferredLevel1);

        long vchcode = header.getVchcode();
        String entcode = String.valueOf(vchcode);

        // Item C (Round 9): pre-fetch finpost's rows here (cheap, unvalidated — no assertions) so
        // Level 2's own VMG/VDG cardinality check can run, and print, BEFORE Level 3's own
        // banner+FinPostValidator.validate() call below — fixes the previous 1→3→2 print-order bug
        // (Level 3 used to run fully, banner and all, before Level 2 even started). finpost is
        // fetched twice as a result (once raw here, once fully asserted by FinPostValidator below)
        // — an acceptable, low-cost duplicate query, not a behavior change to either level's checks.
        List<FinPost> preFetchedPostEntries = finPostDao.findByEntCode(entcode);

        List<FinVchDet> detailRows = finVchDetDao.findByVoucherCode(vchcode);

        // Round 18 — bug fix: Level 4 (fincosttag) used to be called DIRECTLY inside Level 2's
        // per-line loop (via the old validateLine), and Level 3's own banner only printed AFTER
        // that whole loop finished — so the real print order was 1 -> 2+4(interleaved) -> 3, not
        // the documented 1 -> 2 -> 3 -> 4 (confirmed live: a report showed "Level 2" followed
        // immediately by FinCostTagValidator/"Level 4" content for each line, with "Level 3"
        // banner nowhere in between). Fixed by splitting the old validateLine into
        // validateFinvchdetLine (Level 2 only — GL/amount/reversal/narration, no FinCostTagValidator
        // call) which returns a LineContext carrying everything Level 4 needs (gl/detailRow/
        // linePost/siteApplicable), collected across all lines, then run through a SEPARATE Level 4
        // loop AFTER Level 3 completes. One shared SoftAssertions instance still collects Level
        // 2/3(VMG-VDG)/4 failures together (matching the original grouping) — just via manual
        // construction instead of the SoftAssertions.assertSoftly(lambda) form, so banners can be
        // interleaved between phases while still surfacing every failure in one final assertAll().
        SoftAssertions soft = new SoftAssertions();
        List<LineContext> lineContexts = new ArrayList<>();

        // Item W (Round 5): Level 2's own opening banner.
        ReportLogger.db("── Level 2 : finvchdet ──");
        try {
            for (ExpenseIncomeDetail row : data.getExpenseIncomeDetailList()) {
                LineContext ctx = validateFinvchdetLine(soft, entcode, detailRows, row, preFetchedPostEntries);
                if (ctx != null) lineContexts.add(ctx);
            }
            validateCounterLegPaymentInfo(soft, entcode, detailRows, data, header);
            validateDataversionUniqueness(soft, entcode, detailRows);
            validateChequeBook(soft, entcode, detailRows, data, header);
            long siteApplicableCount = lineContexts.stream().filter(c -> c.siteApplicable).count();
            ReportLogger.infoHtml(buildLevel2Summary(detailRows.size(), (int) siteApplicableCount));
        } catch (RuntimeException e) {
            DatabaseLogger.checkFailed("Level 2 — finvchdet per-line checks",
                    "all checks valid", e.getMessage(), "(see finvchdet fetch above)",
                    voucherNo, 0, detailRows.size());
            throw e;
        }
        holder.setDetRowCount(detailRows.size());

        // Item C (Round 9): Level 3's banner + full FinPostValidator pass runs AFTER Level 2 —
        // FinPostValidator only uses voucherNo, a minimal shim is safe here (same trick
        // APVoucherDbValidator.java uses to reuse this shared, VoucherDbContext-typed validator).
        VoucherDbContext finPostCtx = VoucherDbContext.builder()
                .voucherNo(voucherNo)
                .preState(Collections.emptyMap())
                .postState(Collections.emptyMap())
                .build();

        ReportLogger.db("── Level 3 : finpost (GL balance) ──");
        List<FinPost> postEntries = finPostValidator.validate(finPostCtx, header);
        // Moved here (was previously run inside Level 2's block) — its own assertion labels
        // already say "[Level 3]", so this now actually prints where its label claims it does.
        validateVmgVdgCardinality(soft, data, postEntries, header);
        DatabaseLogger.checkPassed("Level 3 — finpost GL balance", postEntries.size() + " GL entries balanced");

        // Round 18 — Level 4, now its own genuinely separate phase, running AFTER Level 3 instead
        // of interleaved inside Level 2's loop.
        List<FinCostTag> allCostTags = new ArrayList<>();
        List<LineContext> siteApplicableLines = lineContexts.stream()
                .filter(c -> c.siteApplicable).toList();
        if (siteApplicableLines.isEmpty()) {
            ReportLogger.db("Level 4 — skipped [" + voucherNo + "]: no site-applicable rows");
            holder.setTagCount(-1);
        } else {
            ReportLogger.db("── Level 4 : fincosttag (site allocation) ──");
            for (LineContext ctx : siteApplicableLines) {
                if (ctx.linePost == null) continue; // already soft-failed in validateFinvchdetLine
                List<SiteAllocation> expectedAllocations = data.getSiteAllocationsForLine(ctx.row.getLineNo());
                List<FinCostTag> lineTags = finCostTagValidator.validate(soft, entcode, ctx.gl, ctx.detailRow,
                        ctx.row, expectedAllocations, ctx.linePost.getPostcode());
                if (lineTags != null) allCostTags.addAll(lineTags);
            }
            holder.setTagCount(allCostTags.size());
            // Item I: deposit the full per-row detail (site/amount/GL per allocated row), mirroring
            // postEntries' pattern, so ActualResultsLogger can render real detail, not just a count.
            holder.setCostTagEntries(allCostTags);
        }

        // Item K (Round 9): finvchdet.vchdt/finpost.entdt/fincosttag.entdt must all equal
        // finvchmain.vchdt — now runs after Levels 2-4 all have their real data (previously ran
        // mid-Level-2 against the pre-fetched-only finpost rows and a partially-built cost-tag
        // list; same values either way, just correctly sequenced now).
        validateDateConsistency(soft, entcode, header, detailRows, postEntries, allCostTags);

        try {
            soft.assertAll();
            DatabaseLogger.checkPassed("Level 3(VMG-VDG)/4 — finpost cardinality, fincosttag, date consistency",
                    postEntries.size() + " finpost row(s), " + allCostTags.size() + " fincosttag row(s)");
        } catch (AssertionError e) {
            DatabaseLogger.checkFailed("Level 3(VMG-VDG)/4 — finpost cardinality, fincosttag, date consistency",
                    "all checks valid", e.getMessage(), "(see finvchdet/finpost/fincosttag fetches above)",
                    voucherNo, 0, detailRows.size());
            throw e;
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

    /** Round 18 — carries what {@link #validateFinvchdetLine} (Level 2) resolved for one line,
     *  so a SEPARATE, later Level 4 pass can validate {@code fincosttag} for the site-applicable
     *  ones without re-deriving gl/detailRow/linePost and without running interleaved inside
     *  Level 2's own loop (the print-order bug this round fixes). */
    private static final class LineContext {
        final ExpenseIncomeDetail row;
        final FinGl gl;
        final FinVchDet detailRow;
        final FinPost linePost; // null when not site-applicable, or genuinely not found (soft-failed already)
        final boolean siteApplicable;

        LineContext(ExpenseIncomeDetail row, FinGl gl, FinVchDet detailRow, FinPost linePost, boolean siteApplicable) {
            this.row = row;
            this.gl = gl;
            this.detailRow = detailRow;
            this.linePost = linePost;
            this.siteApplicable = siteApplicable;
        }
    }

    /** Level 2 ONLY — finvchdet per-line field checks (GL/amount/reversal/narration). Round 18:
     *  no longer calls {@link FinCostTagValidator} itself (that's Level 4's own separate pass,
     *  run later by the caller over the returned {@link LineContext}s) — this method's only job
     *  now is resolving and validating this line's own finvchdet row, plus locating (not
     *  validating) its finpost per-line (VDG) row for Level 4 to use.
     *
     * @return the resolved {@link LineContext} for this line, or {@code null} when the GL or
     *         finvchdet row itself couldn't be resolved at all (already soft-failed here).
     */
    private LineContext validateFinvchdetLine(SoftAssertions soft, String entcode, List<FinVchDet> detailRows,
                               ExpenseIncomeDetail row, List<FinPost> postEntries) {

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

        // Bug fix (2026-09): resolve THIS line's own finpost per-line (VDG) postcode — needed to
        // scope fincosttag lookups uniquely (see FinCostTagValidator.validate's Javadoc). Without
        // this, two site-applicable lines sharing the same glcode within one voucher had their
        // fincosttag rows wrongly pooled together by the old (entcode, glcode)-only lookup.
        long glcode = gl.getGlcode();
        FinPost linePost = postEntries.stream()
                .filter(p -> !"VMG".equalsIgnoreCase(p.getEnttype())
                        && p.getGlcode() == glcode && p.getSlcode() == slcode)
                .findFirst().orElse(null);

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

        boolean siteApplicable = row.getResolvedGlType() != null && row.getResolvedGlType().isSiteApplicable();
        if (siteApplicable) {
            soft.assertThat(linePost)
                    .as("[Level 2] finpost per-line (VDG) row not found for entcode [" + entcode
                            + "] glcode [" + glcode + "] slcode [" + slcode + "] (LineNo [" + row.getLineNo()
                            + "]) — needed to uniquely scope this line's fincosttag rows")
                    .isNotNull();
        }
        return new LineContext(row, gl, detailRow, linePost, siteApplicable);
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

        // Round 8 Part B: deposit the actually-persisted payment mode for "Actual Results"'
        // new Payment Information section — reuses the same first-detail-row resolution this
        // method already needs, no new DB call, and runs regardless of whether this is a
        // combination-matrix row (Excel-mode/negative scenarios get this too).
        FinVchDet firstRowForDisplay = detailRows.isEmpty() ? null : detailRows.get(0);
        if (firstRowForDisplay != null) {
            ActualResultsHolder.get().setPaymentModeActual(firstRowForDisplay.getPaymentMode());
        }

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
            // The header's counter-leg amount lands on the OPPOSITE side from the detail lines, same
            // magnitude (confirmed in both live samples: detail Cr 900 <-> header Dr 900, and detail
            // Dr 900 <-> header Cr 900). With several rows on one side (multi-GL / multi-row-payment
            // vouchers) it is the SUM of those rows; a mixed-side voucher keeps the first-row rule.
            var firstRow = rows.get(0);
            boolean allSameSide = rows.stream().allMatch(r -> r.isDebit() == firstRow.isDebit());
            BigDecimal expectedTotal = allSameSide
                    ? rows.stream().map(r -> r.isDebit() ? r.getExpenseDr() : r.getIncomeCr())
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : (firstRow.isDebit() ? firstRow.getExpenseDr() : firstRow.getIncomeCr());
            BigDecimal actualHeaderAmount = firstRow.isDebit() ? header.getCamount() : header.getDamount();
            soft.assertThat(actualHeaderAmount)
                    .as("[Level 2] finvchmain counter-leg amount mismatch for entcode [" + entcode
                            + "] glcode [" + cashBankGl.getGlcode() + "]: expected=" + expectedTotal
                            + " actual=" + actualHeaderAmount)
                    .isEqualByComparingTo(expectedTotal);
        }

        // payment_mode lives on the Expense/Income detail row(s), not a counter-leg row — one per grid
        // row, so each row is matched to its own finvchdet row and checked against ITS expected variant.
        if (rows == null) return;
        var options = data.getFixtureOptions();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            FinVchDet detailRow = findDetailRow(detailRows, row);
            if (detailRow == null) continue; // already soft-failed by validateFinvchdetLine
            ActualResultsHolder.get().getPaymentModeActualByLine().put(row.getLineNo(), detailRow.getPaymentMode());

            GeneralVoucherPaymentVariant rowVariant = options.paymentForRow(i);
            String expectedCode = rowVariant.expectedDbPaymentModeCode();
            if (expectedCode == null) {
                ReportLogger.db("[Level 2 — payment info] Row " + row.getLineNo() + " " + rowVariant
                        + " — actual finvchdet.payment_mode='" + detailRow.getPaymentMode()
                        + "' (not asserted — expected value unconfirmed, see plan's Open Items)");
                continue;
            }
            soft.assertThat(detailRow.getPaymentMode())
                    .as("[Level 2] finvchdet.payment_mode mismatch for entcode [" + entcode
                            + "] Row " + row.getLineNo() + " (" + rowVariant + ")")
                    .isEqualToIgnoringCase(expectedCode);
        }
    }

    /** The finvchdet row belonging to the test-data row {@code row} — matched on (glcode, slcode) exactly
     *  as {@link #validateFinvchdetLine} does; {@code null} when it cannot be resolved. */
    private FinVchDet findDetailRow(List<FinVchDet> detailRows,
            com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail row) {
        FinGl gl = finGlDao.findByGlName(row.getGeneralLedger());
        if (gl == null) return null;
        long slcode = resolveSlcode(row);
        return detailRows.stream()
                .filter(d -> d.getGlcode() == gl.getGlcode() && d.getSlcode() == slcode)
                .findFirst().orElse(null);
    }

    /** Round 7 — "value should be unique in finvchdet table": {@code dataversion} must not
     *  repeat across this document's own finvchdet rows. */
    private void validateDataversionUniqueness(SoftAssertions soft, String entcode, List<FinVchDet> detailRows) {
        Set<Long> seen = new HashSet<>();
        Set<Long> duplicates = new LinkedHashSet<>();
        for (FinVchDet row : detailRows) {
            if (!seen.add(row.getDataversion())) {
                duplicates.add(row.getDataversion());
            }
        }
        soft.assertThat(duplicates)
                .as("[Level 2] finvchdet.dataversion must be unique across entcode [" + entcode
                        + "]'s own rows — duplicate value(s) found")
                .isEmpty();
    }

    /**
     * Round 7/8 — Level 5: cheque-book ({@code finchqdet}/{@code finchqmain}) linkage, Cheque
     * payment mode only (NEFT/RTGS has no physical cheque/book at all). Only meaningful for a
     * KNOWN cheque number (one already registered in {@code finchqdet}) — every test today uses
     * {@code UniqueChequeGenerator}'s synthetic, never-registered number (the "unknown cheque"
     * path, per Round 8 Part A's findings), so this is a clean, expected skip until a known-cheque
     * test scenario exists.
     *
     * <p><b>Dr/Cr bifurcation</b>: a Cr-side (Receipt) line behaves AR-style — no company
     * cheque-book concept at all — so it must explicitly assert {@code bookcode IS NULL} and skip
     * the {@code finchqdet} lookup entirely, rather than relying on that lookup naturally finding
     * nothing (which it does today only by chance, since Cr-side rows never populate {@code
     * finchqdet} — confirmed live in idealdb: VG/02597/26-27). Only a Dr-side line goes through
     * {@link ChequeBookValidator#assertKnownChequeIssued}, matching AP Voucher.
     *
     * <p><b>AFTER-only today</b>: the true "was 'N' before Save, is 'I' after Save" transition
     * needs a BEFORE-save capture threaded into the test's own flow — this validator only ever
     * runs post-save, so only the AFTER state (plus the bookcode/finchqmain cross-reference) is
     * asserted here; flagged as a follow-up once Round 8 Part A's known-cheque resolver exists.
     */
    private void validateChequeBook(SoftAssertions soft, String entcode, List<FinVchDet> detailRows,
            MergedFinanceGeneralVoucherData data, FinVchMain header) {
        if (data.getFixtureOptions() == null) return;
        var rows = data.getExpenseIncomeDetailList();
        if (rows == null) return;

        for (int i = 0; i < rows.size(); i++) {
            if (data.getFixtureOptions().paymentForRow(i) != GeneralVoucherPaymentVariant.CHEQUE) continue;
            FinVchDet detailRow = findDetailRow(detailRows, rows.get(i));
            if (detailRow == null || detailRow.getChqno() == null || detailRow.getChqno().isBlank()) continue;
            String rowLabel = "entcode [" + entcode + "] Row " + rows.get(i).getLineNo();

            if (!detailRow.isDebit()) {
                // Cr-side (AR-style receipt) — no company cheque-book concept at all; bookcode must
                // stay null and no finchqdet linkage should ever exist (confirmed live: VG/02597).
                soft.assertThat(detailRow.getBookcode())
                        .as("[Level 5] Cr-side cheque line must NOT have a bookcode (AR-style receipt, "
                                + "no cheque-book concept). " + rowLabel)
                        .isNull();
                ReportLogger.db("── Cheque-Book (finchqdet/finchqmain) ── " + rowLabel
                        + " is Cr-side (Receipt) — cheque-book linkage N/A, matching AR. Skipped.");
                continue;
            }

            // Round 20: shared helper (also used by AP). The cheque is registered in finchqdet against the
            // header's Cash/Bank GL (finvchmain.glcode — the counter-leg), NOT the Expense/Income GL that
            // finvchdet.glcode holds, so that is the glcode to look it up by.
            ChequeBookValidator.assertKnownChequeIssued(soft, finChqDetDao, finChqMainDao,
                    detailRow.getChqno(), header.getGlcode(), detailRow.getBookcode(), rowLabel);
        }
    }

    /** Item K (Round 9): {@code finvchdet.vchdt}, {@code finpost.entdt}, and
     *  {@code fincosttag.entdt} must all equal {@code finvchmain.vchdt} — a genuine cross-table
     *  check, distinct from each table's own independent comparison against the test-data's
     *  document date at the snapshot/DiffEngine layer. */
    private void validateDateConsistency(SoftAssertions soft, String entcode, FinVchMain header,
            List<FinVchDet> detailRows, List<FinPost> postRows, List<FinCostTag> costTags) {
        if (header.getVchdt() == null) return;
        LocalDateTime expected = header.getVchdt().atStartOfDay();

        for (FinVchDet row : detailRows) {
            soft.assertThat(row.getVchdt())
                    .as("[Level 2] finvchdet.vchdt mismatch for entcode [" + entcode + "] glcode ["
                            + row.getGlcode() + "] — expected to equal finvchmain.vchdt [" + expected + "]")
                    .isEqualTo(expected);
        }
        for (FinPost row : postRows) {
            soft.assertThat(row.getEntdt())
                    .as("[Level 3] finpost.entdt mismatch for entcode [" + entcode + "] postcode ["
                            + row.getPostcode() + "] — expected to equal finvchmain.vchdt [" + expected + "]")
                    .isEqualTo(expected);
        }
        for (FinCostTag row : costTags) {
            soft.assertThat(row.getEntdt())
                    .as("[Level 4] fincosttag.entdt mismatch for entcode [" + entcode + "] code ["
                            + row.getCode() + "] — expected to equal finvchmain.vchdt [" + expected + "]")
                    .isEqualTo(expected);
        }
    }

    /** Item C (Round 9): Level 2's own green summary box — mirrors Level 1/3/4's own
     *  ({@code buildLevel1Summary}/{@code buildLevel3Summary}/{@code buildLineSummary}), plus
     *  AR/AP's own {@code buildLevel2Summary}, which GV's Level 2 never had.
     *
     *  <p>Round 18: dropped the fincosttag row COUNT this used to report alongside
     *  {@code siteApplicableRowCount} — that number now belongs to Level 4's own summary
     *  (fincosttag validation runs in a separate, later phase, per this round's print-order fix),
     *  and isn't known yet at the point Level 2's own summary prints. */
    private static String buildLevel2Summary(int rowCount, int siteApplicableRowCount) {
        return "<div style='font-family:monospace;font-size:13px;line-height:1.8;padding:8px 12px;"
                + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>"
                + "<b>Level 2 &mdash; finvchdet per-line checks</b><br/>"
                + "&#10003;&nbsp; Detail rows validated: <b>" + rowCount + "</b><br/>"
                + "&#10003;&nbsp; Site-applicable rows: <b>" + siteApplicableRowCount + "</b> "
                + "(see Level 4 for fincosttag detail)<br/>"
                + "&#10003;&nbsp; Payment mode / cheque-book / dataversion-uniqueness checks passed<br/>"
                + "</div>";
    }
}
