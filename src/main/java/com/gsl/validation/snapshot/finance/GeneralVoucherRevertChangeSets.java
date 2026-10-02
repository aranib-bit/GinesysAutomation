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
 * {@link VoucherPersistenceMapFactory}) — so any column with NO explicit rule anywhere (scenario
 * or table-wide) already correctly asserts "must be unchanged" for a Revert, zero declarations
 * needed here. {@code finvchmain}'s {@code release_status}/{@code release_time}/
 * {@code release_ecode} trio, {@code finpost}'s SAME trio on <b>every</b> row of the table (not
 * just the main-grid row — confirmed live, run 20260909_173510: a per-line VDG row transitions
 * identically to VMG), and {@code finvchmain.dataversion} (bumped by the real UPDATE a genuine
 * Revert issues — same optimistic-locking treatment {@link VoucherEditChangeSets#headerEdit}
 * already gives it for Edit) all need explicit overrides, since those columns DO intentionally
 * change on a real Revert.
 *
 * <p><b>Round 23 correction:</b> every table also has its own table-wide {@code mandatoryNotNull}
 * columns (see each {@code VoucherPersistenceMapFactory} table method's "Item G" block) — those
 * columns do NOT fall through to the table's {@code MUST_NOT_CHANGE} default described above,
 * since a table-wide {@code columnRule} override wins over it in
 * {@code DiffEngine.resolveColumnRule}'s priority order. That was silently giving a Revert run's
 * report the generic "Mandatory (not null)" label for columns like {@code finvchmain.vchtype}/
 * {@code glcode}/{@code ycode}, AND — more importantly — meant a genuine defect (an identity/
 * header column accidentally mutated during Revert) went completely undetected, since
 * "not null" never asserts "unchanged". This class now explicitly overrides every such column
 * (across all 4 tables) to {@code MUST_NOT_CHANGE} for the Revert scenario specifically.
 *
 * <p>{@code newStatus} is the ABSOLUTE target {@code release_status} value this Revert attempt
 * should have produced — {@code "U"} for a real Unposted-transition or an already-Unposted no-op,
 * or the document's own immediate-before value for a blocked ({@code CANNOT_REVERT_BRS_DONE}/
 * {@code CANNOT_REVERT_MONTH_CLOSED}) outcome, where nothing should have changed at all. Whether
 * {@code release_status}/{@code release_time} are asserted as MUST_CHANGE or MUST_NOT_CHANGE is
 * derived purely from comparing the BEFORE snapshot's own captured value against {@code
 * newStatus} — the same "addIfChanged"-style pattern {@link VoucherEditChangeSets#headerEdit}
 * already uses for its own conditionally-changing columns. {@code release_ecode} is refined one
 * step further (Round 12): even when a real transition occurs, the SAME user releasing/reverting
 * their own document twice leaves {@code release_ecode} genuinely unchanged (it only changes value
 * when a DIFFERENT user performs the action) — see {@link #addReleaseTransition}.
 */
public final class GeneralVoucherRevertChangeSets {

    private GeneralVoucherRevertChangeSets() {}

    // finvchmain identity/header columns a genuine Revert must NEVER touch — previously
    // uncovered here, silently falling through to VoucherPersistenceMapFactory.finvchmain()'s
    // table-wide `mandatoryNotNull` rules, which only assert "non-null", not "unchanged". That
    // meant an accidental identity-field mutation during Revert (e.g. vchtype flipped by a bug)
    // would have gone completely undetected — DiffEngine.resolveColumnRule's priority (scenario
    // override > table-wide override > default) applies a table-wide `columnRule` to every
    // BY_KEY UPDATE match, not just an INSERT/DELETE row-explode, despite that method's own
    // Item G comment claiming otherwise (see VoucherPersistenceMapFactory.finvchmain()'s
    // corrected comment). Explicit MUST_NOT_CHANGE here both gives the report a meaningful
    // Revert-scenario expectation ("Unchanged (expected)") instead of the generic not-null
    // label, and closes that real gap. Non-null itself isn't re-asserted — it was already
    // validated once when the row was first created, and Revert doesn't touch these at all when
    // it behaves correctly. `dataversion` is handled separately below (conditional on `released`).
    private static final String[] UNCHANGED_HEADER_COLUMNS = {
            "vchcode", "vchdt", "vchtype", "glcode", "ycode", "ecode",
            "entry_source", "admsite_code_owner", "creation_dt", "scheme_docno", "time", "doccode"
    };

    // Same fallthrough, same fix, for finvchdet/finpost/fincosttag's own table-wide
    // `mandatoryNotNull` columns (VoucherPersistenceMapFactory.finvchdet()/finpost()/
    // fincosttag()) — a plain Revert never touches any of these three tables at all (confirmed
    // by this class's own evidence: no dataversion mismatch ever reported on finvchdet), so
    // MUST_NOT_CHANGE is safe for every one.
    private static final String[] UNCHANGED_FINVCHDET_COLUMNS = {
            "vchcode", "glcode", "damount", "camount", "isreversal", "gl_cc_appl", "ycode"
    };
    private static final String[] UNCHANGED_FINPOST_COLUMNS = {
            "entcode", "enttype", "glcode", "damount", "camount", "ycode", "scheme_docno", "time"
    };
    private static final String[] UNCHANGED_FINCOSTTAG_COLUMNS = {
            "entcode", "enttype", "glcode", "ycode", "code", "postcode",
            "admsite_code", "ref_admsite_code", "admou_code"
    };

    /**
     * Builds the Revert-mode {@link ExpectedChangeSet} — the release-status trio's transition (or
     * non-transition) on {@code finvchmain}/{@code finpost}, {@code dataversion}'s conditional bump,
     * and the explicit {@code MUST_NOT_CHANGE} overrides for every table's own mandatory-not-null
     * identity/header columns (see class Javadoc for why those need an explicit override here).
     *
     * @param newStatus the absolute target {@code release_status} this Revert attempt should have
     *                  produced — see class Javadoc
     */
    public static ExpectedChangeSet revert(DocumentContext context, VoucherSnapshot beforeSnapshot, String newStatus) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherRevertChangeSets.revert");

        Map<String, Object> finvchmainBefore = VoucherEditChangeSets.firstRow(beforeSnapshot, "finvchmain");
        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBefore =
                VoucherEditChangeSets.findFinpostRowByMainEntType(beforeSnapshot, mainEntType);

        boolean released = addReleaseTransition(builder, "finvchmain", null, finvchmainBefore, newStatus,
                context.getCurrentUser());
        // Table-wide (not row-scoped to "enttype="+mainEntType) — a real Revert transitions the
        // release trio on EVERY finpost row for the document, not just the main-grid (VMG) row;
        // confirmed live (run 20260909_173510) via a per-line VDG row transitioning identically
        // to VMG. Mirrors VoucherEditChangeSets.addReleaseTrio's own table-wide treatment for Edit
        // — Revert previously scoped this down to just VMG, silently leaving every VDG row on the
        // persistence map's MUST_NOT_CHANGE default despite genuinely changing.
        addReleaseTransition(builder, "finpost", null, finpostBefore, newStatus, context.getCurrentUser());

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
        } else {
            // Item (Round 23): the not-released branch (blocked/no-op Revert — nothing was ever
            // written) had the identical fallthrough as the 12 header columns above — no override
            // meant it fell through to the table-wide `mandatoryNotNull` rule instead of correctly
            // asserting "unchanged".
            builder.rule("finvchmain", ColumnRule.mustNotChange("dataversion"));
        }
        // finvchdet.dataversion: unlike finvchmain's, this is unconditional — per this class's own
        // documented evidence (see class Javadoc), a plain Revert does NOT touch finvchdet at all,
        // so there's no `released`-style gating needed here.
        builder.rule("finvchdet", ColumnRule.mustNotChange("dataversion"));

        // Item (Round 23): finvchmain/finvchdet/finpost/fincosttag identity/header columns that a
        // genuine Revert must never touch (see the field Javadocs above for the full rationale) —
        // previously this section only asserted "finvchdet/fincosttag: deliberately NO overrides,
        // the table-wide MUST_NOT_CHANGE default already covers them", which was true only for
        // columns WITHOUT an explicit `mandatoryNotNull` override; every column below DOES have
        // one, so it silently fell through to that instead. Explicit MUST_NOT_CHANGE here closes
        // that real gap on all 4 tables, not just finvchmain.
        for (String column : UNCHANGED_HEADER_COLUMNS) {
            builder.rule("finvchmain", ColumnRule.mustNotChange(column));
        }
        for (String column : UNCHANGED_FINVCHDET_COLUMNS) {
            builder.rule("finvchdet", ColumnRule.mustNotChange(column));
        }
        for (String column : UNCHANGED_FINPOST_COLUMNS) {
            builder.rule("finpost", ColumnRule.mustNotChange(column));
        }
        for (String column : UNCHANGED_FINCOSTTAG_COLUMNS) {
            builder.rule("fincosttag", ColumnRule.mustNotChange(column));
        }

        return builder.build();
    }

    /**
     * Applies the {@code release_status}/{@code release_time}/{@code release_ecode} trio to
     * {@code tableName} ({@code rowKeyContains} {@code null} = table-wide, e.g. {@code finvchmain}
     * or {@code finpost}; non-null = row-scoped) — {@code addIfChanged} style against
     * {@code beforeRow}'s own captured {@code release_status}, so a table/row that genuinely
     * didn't transition (the {@code CANNOT_REVERT_*}/already-unposted no-op cases) correctly
     * asserts {@code MUST_NOT_CHANGE} on all three columns rather than a spurious
     * {@code MUST_CHANGE}. {@code release_ecode} gets one more refinement on top (Item 5, Round
     * 12): even when a real transition occurs, it only asserts MUST_CHANGE when {@code
     * currentUsername}'s resolved ecode actually differs from {@code beforeRow}'s own captured
     * {@code release_ecode} — the same user performing this action twice leaves the column's
     * value genuinely unchanged.
     *
     * @return whether {@code beforeRow}'s release_status actually differs from {@code newStatus}
     *         — {@code false} when {@code beforeRow} is {@code null} (nothing to compare). Reused
     *         by {@link #revert} to gate {@code finvchmain.dataversion}'s own MUST_CHANGE rule on
     *         the same "did this Revert actually write to the row" signal, rather than
     *         re-deriving it a second time.
     */
    private static boolean addReleaseTransition(ExpectedChangeSet.Builder builder, String tableName,
            String rowKeyContains, Map<String, Object> beforeRow, String newStatus, String currentUsername) {
        if (beforeRow == null) return false;

        boolean changed = !DbValueNormalizer.equalsNormalized(
                DbValueNormalizer.getIgnoreCase(beforeRow, "release_status"), newStatus);

        ColumnRule statusRule = changed
                ? ColumnRule.mustChangeTo("release_status", newStatus)
                : ColumnRule.mustNotChange("release_status");
        // release_time is framework-maintained once release_status actually transitions —
        // MUST_CHANGE (exact new value not asserted, same as finvchdet/finvchmain's dataversion
        // bump in VoucherEditChangeSets) rather than a literal expected value, since the precise
        // timestamp isn't known at change-set-build time here. It always advances on a genuine
        // transition regardless of which user performs it.
        ColumnRule timeRule = changed ? ColumnRule.mustChange("release_time") : ColumnRule.mustNotChange("release_time");

        // Item 5 (Round 12): release_ecode records WHO performed the release/revert — it only
        // genuinely changes value when a DIFFERENT user performs this transition than whoever is
        // recorded in the BEFORE snapshot (e.g. the same user creates, releases, then later
        // reverts/re-releases their own document — release_ecode stays their own ecode throughout,
        // even though release_status/release_time both change). Previously asserted as an
        // unconditional "changed to something" (NOT_EQUAL) whenever release_status transitioned,
        // which would have false-failed the same-user case once a real value comparison is added.
        ColumnRule ecodeRule;
        if (!changed) {
            ecodeRule = ColumnRule.mustNotChange("release_ecode");
        } else {
            Long currentEcode = VoucherEditChangeSets.resolveEcode(currentUsername);
            Object beforeEcode = DbValueNormalizer.getIgnoreCase(beforeRow, "release_ecode");
            if (currentEcode != null && DbValueNormalizer.equalsNormalized(beforeEcode, currentEcode)) {
                // Same user releasing/reverting again — the ecode genuinely stays the same value.
                ecodeRule = ColumnRule.mustNotChange("release_ecode");
            } else if (currentEcode != null) {
                ecodeRule = ColumnRule.mustChangeTo("release_ecode", currentEcode);
            } else {
                // Couldn't resolve the current user's ecode — fall back to the old, weaker
                // "changed to something" check rather than skip the column entirely.
                ecodeRule = ColumnRule.mustChange("release_ecode");
            }
        }

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
