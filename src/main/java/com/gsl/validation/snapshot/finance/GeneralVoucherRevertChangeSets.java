package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.util.Map;

/**
 * {@link ExpectedChangeSet} builder for General Voucher's Revert flow — brings Revert-mode's DB
 * validation up to the same snapshot-diff-engine coverage Edit/Create already have. Revert's
 * previous validation (see {@code RevertGeneralVoucher}) only ever checked a single
 * {@code finvchmain.release_status} column plus a single-table audit check — this class adds the
 * missing {@code finvchdet}/{@code fincosttag} "must stay unchanged" coverage essentially for
 * free, and {@code finpost}'s parallel release-status transition, by reusing the generic
 * snapshot-diff engine instead of hand-written column checks.
 *
 * <p>Key insight: the persistence map's table-wide {@link com.gsl.validation.snapshot.ChangeExpectation}
 * default is already {@code MUST_NOT_CHANGE} for every base table (see
 * {@link VoucherPersistenceMapFactory}) — so simply running the <em>unmodified</em> engine over
 * {@code finvchdet}/{@code fincosttag} for a Revert already asserts "must be unchanged" with zero
 * extra rule declarations here. {@code finvchmain}'s {@code release_status}/{@code release_time}/
 * {@code release_ecode} trio, {@code finpost}'s SAME trio on <b>every</b> row of the table (not
 * just the main-grid row — confirmed live, run 20260909_173510: a per-line VDG row transitions
 * identically to VMG), and {@code finvchmain.dataversion} (bumped by the real UPDATE a genuine
 * Revert issues — same optimistic-locking treatment {@link VoucherEditChangeSets#headerEdit}
 * already gives it for Edit) all need explicit overrides, since those columns DO intentionally
 * change on a real Revert.
 *
 * <p>{@code newStatus} is the ABSOLUTE target {@code release_status} value this Revert attempt
 * should have produced — {@code "U"} for a real Unposted-transition or an already-Unposted no-op,
 * or the document's own immediate-before value for a blocked ({@code CANNOT_REVERT_BRS_DONE}/
 * {@code CANNOT_REVERT_MONTH_CLOSED}) outcome, where nothing should have changed at all. Whether
 * the release trio is asserted as MUST_CHANGE or MUST_NOT_CHANGE is derived purely from comparing
 * the BEFORE snapshot's own captured value against {@code newStatus} — the same
 * "addIfChanged"-style pattern {@link VoucherEditChangeSets#headerEdit} already uses for its own
 * conditionally-changing columns.
 */
public final class GeneralVoucherRevertChangeSets {

    private GeneralVoucherRevertChangeSets() {}

    public static ExpectedChangeSet revert(DocumentContext context, VoucherSnapshot beforeSnapshot, String newStatus) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherRevertChangeSets.revert");

        Map<String, Object> finvchmainBefore = VoucherEditChangeSets.firstRow(beforeSnapshot, "finvchmain");
        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBefore =
                VoucherEditChangeSets.findFinpostRowByMainEntType(beforeSnapshot, mainEntType);

        boolean released = addReleaseTransition(builder, "finvchmain", null, finvchmainBefore, newStatus);
        // Table-wide (not row-scoped to "enttype="+mainEntType) — a real Revert transitions the
        // release trio on EVERY finpost row for the document, not just the main-grid (VMG) row;
        // confirmed live (run 20260909_173510) via a per-line VDG row transitioning identically
        // to VMG. Mirrors VoucherEditChangeSets.addReleaseTrio's own table-wide treatment for Edit
        // — Revert previously scoped this down to just VMG, silently leaving every VDG row on the
        // persistence map's MUST_NOT_CHANGE default despite genuinely changing.
        addReleaseTransition(builder, "finpost", null, finpostBefore, newStatus);

        // finvchmain.dataversion is an optimistic-locking version stamp that bumps on ANY update
        // to the row — same treatment VoucherEditChangeSets.headerEdit already gives it for Edit
        // (confirmed live there; now also confirmed live here, run 20260909_173510: before=
        // '9678023' after='9678167' on a genuine P->U revert). Gated on `released` (not
        // unconditional like Edit's headerEdit, which runs for every Edit save) — a blocked
        // Revert (CANNOT_REVERT_BRS_DONE/CANNOT_REVERT_MONTH_CLOSED) is rejected before any DB
        // write, so finvchmain is never actually touched for those outcomes and dataversion should
        // correctly stay on the table's MUST_NOT_CHANGE default. finvchdet.dataversion
        // deliberately gets NO equivalent rule — unlike an Edit save (which bumps EVERY finvchdet
        // row's dataversion, not just finvchmain's), this run's evidence shows a plain Revert does
        // NOT touch finvchdet at all (no dataversion mismatch reported there), so its existing
        // MUST_NOT_CHANGE default is already correct.
        if (released) {
            builder.rule("finvchmain", ColumnRule.mustChange("dataversion"));
        }

        // finvchdet/fincosttag: deliberately NO overrides — the persistence map's table-wide
        // MUST_NOT_CHANGE default already asserts every row/column on both tables stays untouched
        // by a Revert, which is exactly the coverage Revert never had before this change set
        // existed. A real, unexpected drift on either table now fails just like it would for Edit.

        return builder.build();
    }

    /**
     * Applies the {@code release_status}/{@code release_time}/{@code release_ecode} trio to
     * {@code tableName} ({@code rowKeyContains} {@code null} = table-wide, e.g. {@code finvchmain}
     * or {@code finpost}; non-null = row-scoped) — {@code addIfChanged} style against
     * {@code beforeRow}'s own captured {@code release_status}, so a table/row that genuinely
     * didn't transition (the {@code CANNOT_REVERT_*}/already-unposted no-op cases) correctly
     * asserts {@code MUST_NOT_CHANGE} on all three columns rather than a spurious
     * {@code MUST_CHANGE}.
     *
     * @return whether {@code beforeRow}'s release_status actually differs from {@code newStatus}
     *         — {@code false} when {@code beforeRow} is {@code null} (nothing to compare). Reused
     *         by {@link #revert} to gate {@code finvchmain.dataversion}'s own MUST_CHANGE rule on
     *         the same "did this Revert actually write to the row" signal, rather than
     *         re-deriving it a second time.
     */
    private static boolean addReleaseTransition(ExpectedChangeSet.Builder builder, String tableName,
            String rowKeyContains, Map<String, Object> beforeRow, String newStatus) {
        if (beforeRow == null) return false;

        boolean changed = !DbValueNormalizer.equalsNormalized(
                DbValueNormalizer.getIgnoreCase(beforeRow, "release_status"), newStatus);

        ColumnRule statusRule = changed
                ? ColumnRule.mustChangeTo("release_status", newStatus)
                : ColumnRule.mustNotChange("release_status");
        // release_time/release_ecode are framework-maintained once release_status actually
        // transitions — MUST_CHANGE (exact new value not asserted, same as finvchdet/finvchmain's
        // dataversion bump in VoucherEditChangeSets) rather than a literal expected value, since
        // the precise timestamp/ecode aren't known at change-set-build time here.
        ColumnRule timeRule = changed ? ColumnRule.mustChange("release_time") : ColumnRule.mustNotChange("release_time");
        ColumnRule ecodeRule = changed ? ColumnRule.mustChange("release_ecode") : ColumnRule.mustNotChange("release_ecode");

        if (rowKeyContains == null) {
            builder.rule(tableName, statusRule);
            builder.rule(tableName, timeRule);
            builder.rule(tableName, ecodeRule);
        } else {
            builder.rule(tableName, rowKeyContains, statusRule);
            builder.rule(tableName, rowKeyContains, timeRule);
            builder.rule(tableName, rowKeyContains, ecodeRule);
        }

        return changed;
    }
}
