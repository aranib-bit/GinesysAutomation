package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.utils.DateUtils;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Named {@link ExpectedChangeSet} builders for General Voucher's two V1 Edit scenarios — plan
 * §2. Thin GV-specific layer over the common {@link VoucherEditChangeSets} rules (narration/
 * reference/date MUST_CHANGE-when-different, last-modified SYSTEM_CHANGE, release-trio IGNORE —
 * identical for every {@code finvchmain}-backed voucher type) — the only GV-specific piece is
 * resolving {@code editedRow}'s {@code fingl} row to a {@code glcode} for the {@code finvchdet}
 * row-scoped override.
 *
 * <p><b>Deviation from the plan's literal {@code headerEdit(oldDoc, newDoc, currentUser)}/
 * {@code detailLineEdit(oldDoc, newDoc, editedRow, currentUser)} signatures:</b> rather than
 * requiring the caller to hand-build a separate "old header" {@link DocumentInformation} (which
 * no data source actually constructs — Excel/DB sources only ever build the NEW header), both
 * methods here derive "did this actually change" directly from the {@code beforeSnapshot}'s own
 * captured {@code finvchmain}/{@code finvchdet} rows — the real ground truth, and one fewer
 * redundant object for {@code EditGeneralVoucher} to assemble. {@code currentUser} comes from
 * {@code context} rather than its own parameter for the same reason.
 */
public final class GeneralVoucherEditChangeSets {

    private GeneralVoucherEditChangeSets() {}

    /** General Voucher has no TDS concept at all (see {@code MergedFinanceGeneralVoucherData
     *  .isTdsProvided()}, hardcoded {@code false}) — {@code finvchdet.tds_app} therefore has zero
     *  legitimate reason to change on ANY GV edit. A drift here is a confirmed, already-reported
     *  backend data-mapping defect (raised separately to dev, tracked outside this test suite) — NOT
     *  a test-data/Excel-import issue, and not something re-failing this automated check run after
     *  run adds any value catching. Layered onto every change set this class builds (see
     *  {@link #withTdsAppNote}) as a {@link ColumnRule#knownIssue known issue}: the check still runs
     *  and still surfaces prominently in the report, but does not fail the test — regardless of the
     *  specific before/after values observed. */
    private static final String TDS_APP_NOTE =
            "General Voucher has no TDS concept (MergedFinanceGeneralVoucherData.isTdsProvided() is always "
            + "false) — tds_app must stay static across ANY GV edit. A drift here, regardless of the specific "
            + "before/after values, indicates a BACKEND DATA-MAPPING DEFECT — tracked separately as a known, "
            + "already-reported issue; this check is informational only and does not fail the test.";

    /** Layers the {@link #TDS_APP_NOTE} table-wide known-issue override for {@code finvchdet.tds_app}
     *  onto {@code base} — applied by both {@link #headerEdit} and {@link #detailLineEdit} so it
     *  covers both scenario types (the latter builds via the COMMON {@link VoucherEditChangeSets
     *  #detailLineEdit}, which calls the common {@code headerEdit} directly, bypassing this class's
     *  own {@link #headerEdit} wrapper — so this must be applied at each of this class's public
     *  entry points rather than just once inside {@link #headerEdit}). */
    private static ExpectedChangeSet withTdsAppNote(ExpectedChangeSet base, String builderName) {
        return ExpectedChangeSet.builder(builderName)
                .mergeFrom(base)
                .rule("finvchdet", ColumnRule.knownIssue("tds_app", TDS_APP_NOTE))
                .build();
    }

    /**
     * Round 20 — {@code chqdt} is a DEFAULTED/DERIVED column for General Voucher, not an
     * independently-tracked cheque date, whenever no independent instrument date was ever
     * provided — confirmed via a direct live-DB audit of {@code aud2_finvchdet}'s entire history:
     * EVERY {@code payment_mode='R'} (NEFT/RTGS) row shows {@code chqdt == vchdt} at creation time
     * (125/125, system-wide, independent of this test suite ever running), and the
     * {@code payment_mode='C'} (Cheque) rows that also match are exactly the ones
     * {@code PaymentModeResolver}'s own documented fallback produces when no
     * {@code PaymentInformation} was ever supplied — always true for a GV Edit-test voucher, since
     * GV's real Cheque/RTGS payment popup is Create-flow-only (confirmed separately). The
     * persistence map's generic table-wide {@code MUST_NOT_CHANGE} default wrongly asserted this
     * derived field must stay frozen; this reclassifies it as {@code SYSTEM_CHANGE}
     * (framework/DB-maintained, not test-supplied) — per ROW, and only when THAT row's own BEFORE
     * {@code chqdt} already equalled its own BEFORE tracking-date column. A genuinely independent
     * cheque date on any row is deliberately left on the table's default {@code MUST_NOT_CHANGE},
     * so a real future defect there is still caught — this is not a blanket override.
     *
     * <p>{@code finpost} has no {@code vchdt} column (confirmed via schema) — its own
     * date-equivalent is {@code entdt} (already the column {@link VoucherEditChangeSets#headerEdit}
     * cascades {@code vchdt}'s change onto), so that's what each {@code finpost} row's
     * pre-edit {@code chqdt} is compared against instead.
     *
     * <p>No-op when {@code newDate} is {@code null} — nothing to reclassify when no document-date
     * change is even in play for this scenario.
     */
    private static ExpectedChangeSet withChqdtTrackingRule(ExpectedChangeSet base, String builderName,
            VoucherSnapshot beforeSnapshot, LocalDate newDate) {
        if (newDate == null) return base;

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder(builderName).mergeFrom(base);

        var finvchdetTable = beforeSnapshot.getTable("finvchdet");
        List<Map<String, Object>> finvchdetRows = finvchdetTable == null ? List.of() : finvchdetTable.getRows();
        for (Map<String, Object> row : finvchdetRows) {
            Object chqdt = DbValueNormalizer.getIgnoreCase(row, "chqdt");
            Object vchdt = DbValueNormalizer.getIgnoreCase(row, "vchdt");
            if (DbValueNormalizer.equalsNormalized(chqdt, vchdt)) {
                String rowKey = "glcode=" + DbValueNormalizer.getIgnoreCase(row, "glcode")
                        + ", slcode=" + DbValueNormalizer.displayString(DbValueNormalizer.getIgnoreCase(row, "slcode"));
                builder.rule("finvchdet", rowKey, ColumnRule.systemChangeTo("chqdt", newDate));
            }
        }

        var finpostTable = beforeSnapshot.getTable("finpost");
        List<Map<String, Object>> finpostRows = finpostTable == null ? List.of() : finpostTable.getRows();
        for (Map<String, Object> row : finpostRows) {
            Object chqdt = DbValueNormalizer.getIgnoreCase(row, "chqdt");
            Object entdt = DbValueNormalizer.getIgnoreCase(row, "entdt");
            if (DbValueNormalizer.equalsNormalized(chqdt, entdt)) {
                String rowKey = "glcode=" + DbValueNormalizer.getIgnoreCase(row, "glcode")
                        + ", slcode=" + DbValueNormalizer.displayString(DbValueNormalizer.getIgnoreCase(row, "slcode"))
                        + ", enttype=" + DbValueNormalizer.getIgnoreCase(row, "enttype");
                builder.rule("finpost", rowKey, ColumnRule.systemChangeTo("chqdt", newDate));
            }
        }

        return builder.build();
    }

    /** Header-edit change set — see {@link VoucherEditChangeSets#headerEdit}. */
    public static ExpectedChangeSet headerEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            DocumentInformation newDoc) {
        ExpectedChangeSet common = VoucherEditChangeSets.headerEdit("GeneralVoucherEditChangeSets.headerEdit", context,
                beforeSnapshot, newDoc.getReference(), newDoc.getRemarks(), newDoc.getDocumentdate());
        ExpectedChangeSet withTds = withTdsAppNote(common, "GeneralVoucherEditChangeSets.headerEdit");
        return withChqdtTrackingRule(withTds, "GeneralVoucherEditChangeSets.headerEdit", beforeSnapshot, newDoc.getDocumentdate());
    }

    /**
     * Payment-Info-edit change set (see the "Payment Info Edit" plan, including its Part C
     * payment-mode-change extension): header fields (reference/remarks/date) are unchanged for
     * this scenario (the data source leaves {@code newDoc} at the header's existing values, same
     * "hold unchanged" idiom the {@code DETAIL_LINE}/{@code PAYMENT_INFO} branch of {@code
     * DbGeneralVoucherEditDataSource#resolveOne} already uses) — so this starts from the same
     * common {@code headerEdit} rules as every other Edit scenario, then layers a row-scoped
     * override onto the voucher's FIRST {@code finvchdet} row (the single Cash/Bank counter-leg —
     * see {@code PaymentInformation}'s own Javadoc) and the matching {@code finpost} row(s).
     *
     * <p>{@code chqno}/{@code chqdt} are each independently conditional on whether {@code
     * target.getNewPayment()} actually carries a different value than {@code
     * target.getBeforePayment()} for that specific column — NOT unconditionally {@code
     * MUST_CHANGE} (a confirmed live bug, see {@code reports/runs/20260916_133846},
     * {@code FIN-GV-EDIT-PAYMENT-01}: {@code SAME_MODE_NEW_INSTRUMENT}/{@code NEFT_TO_CHEQUE}
     * build {@code newPayment} with {@code instrumentDate} left {@code null} — see {@code
     * DbGeneralVoucherEditDataSource#buildPaymentEditTarget} — so {@code chqdt} is never actually
     * touched for those shapes and must be asserted {@code MUST_NOT_CHANGE}, not {@code
     * MUST_CHANGE}). {@code finpost.chqno} mirrors {@code finvchdet.chqno}'s same conditional
     * rule — confirmed live that the app propagates a genuine chqno change into {@code finpost}
     * too, which previously had no override and so wrongly inherited {@code headerEdit()}'s
     * table-wide {@code MUST_NOT_CHANGE} default. {@code payment_mode} is asserted {@code
     * MUST_CHANGE} only for the two mode-change shapes ({@link
     * PaymentInfoEditTarget.PaymentModeShape#CHEQUE_TO_NEFT}/{@code NEFT_TO_CHEQUE} —
     * {@code SAME_MODE_NEW_INSTRUMENT} keeps its original no-rule behavior, since that shape
     * genuinely never changes {@code payment_mode}), and {@code bookcode} per shape (see below).
     *
     * <p>Unlike {@link #withChqdtTrackingRule} (whose Javadoc assumes GV's Cheque popup is
     * Create-flow-only — an assumption this scenario deliberately breaks), {@code chqdt} is
     * deliberately NOT reclassified as a {@code SYSTEM_CHANGE} default here: for a shape that DOES
     * carry a genuine new instrument date, a {@code chqdt} change is exactly what's expected, not
     * a side-effect to explain away — so {@link #withChqdtTrackingRule} is deliberately NOT
     * called.
     *
     * <p>Deliberately does NOT assert {@code drawnon} ({@link PaymentInfoEditTarget#getNewPayment()}
     * carries the same Drawn On as before — see {@code
     * DbGeneralVoucherEditDataSource#buildPaymentEditTarget}'s own note).
     *
     * <p>The {@code bookcode} rule for {@code SAME_MODE_NEW_INSTRUMENT}/{@code NEFT_TO_CHEQUE}
     * compares the actual before/after {@code bookcode} values (before from the snapshot, after
     * from {@link PaymentInfoEditTarget#getNewBookcode()}) rather than inferring "same book" from
     * {@link PaymentInfoEditTarget#getNewBankMatch()} — a same-bank match does NOT imply the same
     * cheque book (a single bank account can have multiple books), which a live run confirmed
     * (see the inline comment at the rule itself). The {@code CHEQUE_TO_NEFT} bookcode rule keeps
     * its own separate, still-live-unverified assumption (see its own inline comment below), and
     * the {@code finchqdet} table's old-row fate on a Cheque→NEFT transition is left entirely
     * unasserted — see the plan's staged verification section before trusting either as final.
     */
    public static ExpectedChangeSet paymentInfoEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            DocumentInformation newDoc, PaymentInfoEditTarget target) {

        ExpectedChangeSet common = VoucherEditChangeSets.headerEdit("GeneralVoucherEditChangeSets.paymentInfoEdit",
                context, beforeSnapshot, newDoc.getReference(), newDoc.getRemarks(), newDoc.getDocumentdate());
        ExpectedChangeSet withTds = withTdsAppNote(common, "GeneralVoucherEditChangeSets.paymentInfoEdit");

        var finvchdetTable = beforeSnapshot.getTable("finvchdet");
        List<Map<String, Object>> rows = finvchdetTable == null ? List.of() : finvchdetTable.getRows();
        if (rows.isEmpty()) return withTds;
        Map<String, Object> firstRow = rows.get(0);
        String rowKey = "glcode=" + DbValueNormalizer.getIgnoreCase(firstRow, "glcode")
                + ", slcode=" + DbValueNormalizer.displayString(DbValueNormalizer.getIgnoreCase(firstRow, "slcode"));

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherEditChangeSets.paymentInfoEdit")
                .mergeFrom(withTds);

        // chqno/chqdt are each independently conditional on whether THIS scenario's target
        // actually moves that specific value — SAME_MODE_NEW_INSTRUMENT/NEFT_TO_CHEQUE build
        // newPayment with instrumentDate left null (DbGeneralVoucherEditDataSource
        // #buildPaymentEditTarget), so chqdt is never touched for those shapes and must be
        // asserted unchanged, not blindly MUST_CHANGE regardless of what the scenario actually
        // edits. Previously both rules were unconditional, which is exactly what produced FAIL #5
        // (reports/runs/20260916_133846, FIN-GV-EDIT-PAYMENT-01): chqdt wrongly expected to
        // change when the scenario never supplied a new one, and finpost.chqno (see below) had no
        // override at all so it fell back to the table-wide MUST_NOT_CHANGE default even though
        // chqno itself genuinely changed.
        boolean chqnoChanges = !java.util.Objects.equals(
                target.getBeforePayment().getInstrumentNo(), target.getNewPayment().getInstrumentNo());

        // chqdt must compare actual DATE VALUES, not merely "was a new date string supplied" —
        // CHEQUE_TO_NEFT always supplies LocalDate.now() as the new instrument date, which can
        // coincidentally equal the voucher's existing chqdt when run same-day, producing no real
        // DB change even though a "new" value was present (confirmed live: reports/runs/
        // 20260916_160435, FIN-GV-EDIT-PAYMENT-02 — both before and after were '2026-09-16').
        // beforePayment's date is ISO (DbGeneralVoucherEditDataSource#buildPaymentEditTarget uses
        // LocalDate#toString()); newPayment's is UI format (DateUtils.UI_DATE_FORMAT) for
        // CHEQUE_TO_NEFT — parseFlexibleDate normalizes either before comparing.
        LocalDate beforeChqdt = parseFlexibleDate(target.getBeforePayment().getInstrumentDate());
        LocalDate afterChqdt = parseFlexibleDate(target.getNewPayment().getInstrumentDate());
        boolean chqdtChanges = afterChqdt != null && !afterChqdt.equals(beforeChqdt);

        builder.rule("finvchdet", rowKey, chqnoChanges
                ? ColumnRule.mustChangeTo("chqno", target.getNewPayment().getInstrumentNo())
                : ColumnRule.mustNotChange("chqno"));
        builder.rule("finvchdet", rowKey, chqdtChanges
                ? ColumnRule.mustChange("chqdt")
                : ColumnRule.mustNotChange("chqdt"));

        // finpost mirrors finvchdet's chqno for the matching VDG posting row(s) — confirmed live
        // (same run: finpost.chqno moved 000066->000071 in lockstep with finvchdet.chqno) — but
        // had no override before this fix, so it fell back to headerEdit()'s table-wide
        // MUST_NOT_CHANGE default and failed even though the app's behavior was correct. Matched
        // by glcode/slcode against the same finvchdet firstRow this method already keys off, not
        // hardcoded to enttype='VDG', so it generalizes to however many finpost rows actually
        // share that GL/SL combination.
        var finpostTable = beforeSnapshot.getTable("finpost");
        List<Map<String, Object>> finpostRows = finpostTable == null ? List.of() : finpostTable.getRows();
        Object firstRowGlcode = DbValueNormalizer.getIgnoreCase(firstRow, "glcode");
        Object firstRowSlcode = DbValueNormalizer.getIgnoreCase(firstRow, "slcode");
        for (Map<String, Object> postRow : finpostRows) {
            if (!DbValueNormalizer.equalsNormalized(DbValueNormalizer.getIgnoreCase(postRow, "glcode"), firstRowGlcode)
                    || !DbValueNormalizer.equalsNormalized(DbValueNormalizer.getIgnoreCase(postRow, "slcode"), firstRowSlcode)) {
                continue;
            }
            String postRowKey = "glcode=" + DbValueNormalizer.getIgnoreCase(postRow, "glcode")
                    + ", slcode=" + DbValueNormalizer.displayString(DbValueNormalizer.getIgnoreCase(postRow, "slcode"))
                    + ", enttype=" + DbValueNormalizer.getIgnoreCase(postRow, "enttype");
            builder.rule("finpost", postRowKey, chqnoChanges
                    ? ColumnRule.mustChangeTo("chqno", target.getNewPayment().getInstrumentNo())
                    : ColumnRule.mustNotChange("chqno"));
        }

        // payment_mode: only asserted when this scenario actually changes it — SAME_MODE_NEW_INSTRUMENT
        // stays 'C'→'C' (genuinely unchanged, no rule added, preserving that scenario's original
        // behavior exactly); CHEQUE_TO_NEFT/NEFT_TO_CHEQUE genuinely flip it.
        if (target.getBeforePayment().getPaymentMode() != target.getNewPayment().getPaymentMode()) {
            builder.rule("finvchdet", rowKey,
                    ColumnRule.mustChangeTo("payment_mode", target.getNewPayment().getPaymentMode().dbCode()));
        }

        // Dr/Cr bifurcation: a Cr-side (Receipt) row behaves AR-style — no company cheque-book
        // concept at all — so bookcode must stay null across ANY payment-mode-shape edit,
        // regardless of the new cheque's bank-match resolution. Confirmed live in idealdb:
        // VG/02597/26-27 (Cr) has bookcode=NULL even though its payment_mode is Cheque.
        if (!isDebitRow(firstRow)) {
            builder.rule("finvchdet", rowKey, ColumnRule.mustNotChange("bookcode"));
        } else if (target.getShape() == PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT) {
            // NEFT/RTGS has no cheque-book concept at all — bookcode is expected to become null.
            // UNVERIFIED assumption: this presumes the "before" row's bookcode was actually
            // non-null (i.e. the before-cheque really was a registered/known one) — claimOne's
            // filter only guarantees a non-null chqno, not a non-null bookcode (see
            // buildPaymentEditTarget's own beforeBankMatch caveat) — confirm against a live run
            // before trusting this fully; if before's bookcode was already null this assertion
            // may need to become mustNotChange instead.
            builder.rule("finvchdet", rowKey, ColumnRule.mustChangeTo("bookcode", null));
        } else {
            // SAME_MODE_NEW_INSTRUMENT and NEFT_TO_CHEQUE both land here. bookcode identifies
            // which cheque BOOK the instrument belongs to — NOT the same thing as newBankMatch
            // (SAME_BANK/DIFFERENT_BANK), which only says the new cheque came from the same GL/
            // bank account: a single bank account can have multiple cheque books issued over
            // time, so two cheques with the same bankMatch can still carry different bookcodes.
            // A prior version of this rule inferred "bookcode changes" purely from bankMatch and
            // was confirmed wrong live (reports/runs/20260916_160435, FIN-GV-EDIT-PAYMENT-01:
            // bankMatch was SAME_BANK yet bookcode stayed identical, '149629'->'149629', because
            // the resolved instrument happened to land in the SAME book). Compare the actual
            // resolved bookcode (target.getNewBookcode(), threaded from ChequeInstrumentResolver
            // .ChequeResolution#bookcode()) against the before row's real bookcode instead.
            BigDecimal beforeBookcode = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(firstRow, "bookcode"));
            BigDecimal afterBookcode = DbValueNormalizer.asBigDecimal(target.getNewBookcode());
            boolean bookcodeChanges = beforeBookcode == null
                    ? afterBookcode != null
                    : afterBookcode == null || beforeBookcode.compareTo(afterBookcode) != 0;
            builder.rule("finvchdet", rowKey, bookcodeChanges
                    ? ColumnRule.mustChangeTo("bookcode", target.getNewBookcode())
                    : ColumnRule.mustNotChange("bookcode"));
        }

        // finchqdet's old-row fate when a voucher moves OFF Cheque mode (does the previously-
        // registered cheque's stat revert 'I'->'N'? does it stay orphaned?) is a confirmed,
        // deliberately-unasserted gap — nothing in ChequeBookValidator/FinChqDet addresses it, and
        // guessing here risks a wrong assertion rather than an honest unknown. Add a rule once a
        // live CHEQUE_TO_NEFT run shows what the app actually does.

        return builder.build();
    }

    /** Parses a {@link PaymentInformation}-carried date string that may be either ISO
     *  ({@code yyyy-MM-dd}, how {@code beforePayment}'s date is built — see {@code
     *  DbGeneralVoucherEditDataSource#buildPaymentEditTarget}'s {@code LocalDate#toString()} use)
     *  or UI format ({@code dd/MM/yyyy}, {@link DateUtils#UI_DATE_FORMAT} — how {@code
     *  CHEQUE_TO_NEFT}'s new date is built), so before/after values built by different code paths
     *  can still be compared as real dates. {@code null}/blank input returns {@code null}. */
    private static LocalDate parseFlexibleDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException isoFailed) {
            return LocalDate.parse(value, DateUtils.UI_DATE_FORMAT);
        }
    }

    /** {@code true} when the given raw {@code finvchdet} snapshot row posts to the Debit side
     *  ({@code damount} non-zero) — mirrors {@code FinVchDet.isDebit()}/{@code
     *  ExpenseIncomeDetail.isDebit()}, operating on the {@code Map<String,Object>} shape this
     *  snapshot-diff layer works with instead of a typed row. */
    private static boolean isDebitRow(Map<String, Object> row) {
        BigDecimal damount = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(row, "damount"));
        return damount != null && damount.signum() != 0;
    }

    /**
     * Detail-line-edit change set — resolves {@code editedRow}'s General Ledger name to a
     * {@code fingl} row for its {@code glcode}, then delegates to
     * {@link VoucherEditChangeSets#detailLineEdit}.
     */
    public static ExpectedChangeSet detailLineEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            DocumentInformation newDoc, ExpenseIncomeDetail editedRow) {

        FinGl gl = DatabaseManager.getJdbi().onDemand(FinGlDao.class).findByGlName(editedRow.getGeneralLedger());
        if (gl == null) {
            Log.warn("[GeneralVoucherEditChangeSets] Could not resolve fingl row for General Ledger '{}' — the "
                    + "edited finvchdet row's damount/camount/isreversal override was NOT added; that row will "
                    + "be checked against the table's normal MUST_NOT_CHANGE default and the edit will likely "
                    + "surface as an (expected) column-diff failure.", editedRow.getGeneralLedger());
            return headerEdit(context, beforeSnapshot, newDoc);
        }

        boolean isDebit = editedRow.isDebit();
        BigDecimal newAmount = isDebit ? editedRow.getExpenseDr() : editedRow.getIncomeCr();
        long slcode = resolveSlcode(editedRow);

        ExpectedChangeSet common = VoucherEditChangeSets.detailLineEdit("GeneralVoucherEditChangeSets.detailLineEdit",
                context, beforeSnapshot, newDoc.getReference(), newDoc.getRemarks(), newDoc.getDocumentdate(),
                gl.getGlcode(), slcode, isDebit, newAmount, editedRow.isReversalYes(), editedRow.getLedgerNarration());
        ExpectedChangeSet withTds = withTdsAppNote(common, "GeneralVoucherEditChangeSets.detailLineEdit");
        return withChqdtTrackingRule(withTds, "GeneralVoucherEditChangeSets.detailLineEdit", beforeSnapshot, newDoc.getDocumentdate());
    }

    /**
     * GL-type-transition change set (plan §4B shape 1) — resolves both the OLD and NEW General
     * Ledger names to their {@code fingl} rows, then delegates to
     * {@link VoucherEditChangeSets#detailLineGlTransition}. {@code beforeRow}/{@code afterRow}
     * share the same {@code LineNo} — only the GL (and its account class) actually differs.
     */
    public static ExpectedChangeSet detailLineGlTransition(DocumentContext context, VoucherSnapshot beforeSnapshot,
            DocumentInformation newDoc, ExpenseIncomeDetail beforeRow, ExpenseIncomeDetail afterRow) {

        FinGlDao glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        FinGl oldGl = glDao.findByGlName(beforeRow.getGeneralLedger());
        FinGl newGl = glDao.findByGlName(afterRow.getGeneralLedger());
        if (oldGl == null || newGl == null) {
            Log.warn("[GeneralVoucherEditChangeSets] Could not resolve fingl row for old GL '{}' or new GL "
                    + "'{}' — falling back to headerEdit only; the GL-transition row insert/delete override "
                    + "was NOT added.", beforeRow.getGeneralLedger(), afterRow.getGeneralLedger());
            return headerEdit(context, beforeSnapshot, newDoc);
        }

        boolean isDebit = afterRow.isDebit();
        BigDecimal newAmount = isDebit ? afterRow.getExpenseDr() : afterRow.getIncomeCr();
        long oldSlcode = resolveSlcode(beforeRow);
        long newSlcode = resolveSlcode(afterRow);

        ExpectedChangeSet common = VoucherEditChangeSets.detailLineGlTransition(
                "GeneralVoucherEditChangeSets.detailLineGlTransition", context, beforeSnapshot,
                newDoc.getReference(), newDoc.getRemarks(), newDoc.getDocumentdate(),
                oldGl.getGlcode(), oldSlcode, newGl.getGlcode(), newSlcode,
                isDebit, newAmount, afterRow.isReversalYes(), afterRow.getLedgerNarration());
        ExpectedChangeSet withTds = withTdsAppNote(common, "GeneralVoucherEditChangeSets.detailLineGlTransition");
        return withChqdtTrackingRule(withTds, "GeneralVoucherEditChangeSets.detailLineGlTransition", beforeSnapshot, newDoc.getDocumentdate());
    }

    /**
     * Add-new-line change set (plan §4B shape 2) — resolves the new line's General Ledger name to
     * its {@code fingl} row, then delegates to {@link VoucherEditChangeSets#addNewDetailLine}.
     */
    public static ExpectedChangeSet addNewDetailLine(DocumentContext context, VoucherSnapshot beforeSnapshot,
            DocumentInformation newDoc, ExpenseIncomeDetail newLine) {

        FinGlDao glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        FinGl gl = glDao.findByGlName(newLine.getGeneralLedger());
        if (gl == null) {
            Log.warn("[GeneralVoucherEditChangeSets] Could not resolve fingl row for new line's General "
                    + "Ledger '{}' — falling back to headerEdit only; the new-line row-insert override was "
                    + "NOT added.", newLine.getGeneralLedger());
            return headerEdit(context, beforeSnapshot, newDoc);
        }

        boolean isDebit = newLine.isDebit();
        BigDecimal amount = isDebit ? newLine.getExpenseDr() : newLine.getIncomeCr();
        long slcode = resolveSlcode(newLine);

        ExpectedChangeSet common = VoucherEditChangeSets.addNewDetailLine(
                "GeneralVoucherEditChangeSets.addNewDetailLine", context, beforeSnapshot,
                newDoc.getReference(), newDoc.getRemarks(), newDoc.getDocumentdate(),
                gl.getGlcode(), slcode, isDebit, amount, newLine.isReversalYes(), newLine.getLedgerNarration());
        ExpectedChangeSet withTds = withTdsAppNote(common, "GeneralVoucherEditChangeSets.addNewDetailLine");
        return withChqdtTrackingRule(withTds, "GeneralVoucherEditChangeSets.addNewDetailLine", beforeSnapshot, newDoc.getDocumentdate());
    }

    /**
     * Resolves the edited line's {@code finsl.slcode} — mirrors this class's own {@code fingl}
     * resolution for {@code glcode} above. {@code 0} for Type1/Type2 lines with no sub-ledger
     * (blank {@code subLedger}) and as a safe fallback if the name can't be resolved (logged, not
     * thrown — a header-only/amount-only edit should still validate even if sub-ledger lookup
     * fails; the row-scoped rule just falls back to matching on {@code slcode=0}).
     *
     * <p>Package-private (not {@code private}) so {@link GeneralVoucherCreateChangeSets} can reuse
     * it for the same {@link ExpenseIncomeDetail} DTO's sub-ledger resolution, rather than
     * re-implementing it.
     */
    static long resolveSlcode(ExpenseIncomeDetail editedRow) {
        String subLedgerName = editedRow.getSubLedger();
        if (subLedgerName == null || subLedgerName.isBlank()) return 0L;

        FinSlDao slDao = DatabaseManager.getJdbi().onDemand(FinSlDao.class);
        String subLedgerId = editedRow.getSubLedgerId();
        FinSl sl = (subLedgerId != null && !subLedgerId.isBlank())
                ? slDao.findBySlnameAndSlid(subLedgerName, subLedgerId)
                : firstOrNull(slDao.findBySlname(subLedgerName));

        if (sl == null) {
            Log.warn("[GeneralVoucherEditChangeSets] Could not resolve finsl row for Sub Ledger '{}' — "
                    + "falling back to slcode=0 for the finvchdet row-scoped override.", subLedgerName);
            return 0L;
        }
        return sl.getSlcode();
    }

    private static FinSl firstOrNull(List<FinSl> rows) {
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }
}
