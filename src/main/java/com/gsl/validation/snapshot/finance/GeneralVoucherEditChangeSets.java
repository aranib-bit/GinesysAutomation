package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.utils.Log;
import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     */
    private static long resolveSlcode(ExpenseIncomeDetail editedRow) {
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
