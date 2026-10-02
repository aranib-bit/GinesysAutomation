package com.gsl.validation.snapshot.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.enums.VoucherType;
import com.gsl.utils.Log;
import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Common {@link ExpectedChangeSet} rules for any {@code finvchmain}-backed voucher domain — plan
 * §2, generalized from the GV-only rules {@code GeneralVoucherEditChangeSets} used to build
 * directly. {@code GeneralVoucherEditChangeSets}/{@code ARVoucherEditChangeSets}/
 * {@code APVoucherEditChangeSets} each call {@link #headerEdit}/{@link #detailLineEdit} first,
 * then (for {@code headerEdit} callers) layer their own domain-specific overrides on top — same
 * "common base + thin domain layer" shape {@code CommonVoucherActionsMenu.resolveFor(domain)}
 * already uses for grid actions.
 *
 * <p>Deliberately takes plain primitives ({@code newRefno}/{@code newRemarks}/{@code newDate},
 * and for {@link #detailLineEdit} the edited {@code finvchdet} row's {@code glcode}/
 * {@code isDebit}/{@code newAmount}/{@code newIsReversalYes}) rather than a typed
 * {@code DocumentInformation}/{@code ExpenseIncomeDetail} — those are GV's own test-data DTOs;
 * this common layer must not depend on any one domain's package.
 */
public final class VoucherEditChangeSets {

    private VoucherEditChangeSets() {}

    /**
     * Header-edit rules — {@code refno}/{@code nartext}/{@code vchdt} MUST_CHANGE (only when
     * actually different from the BEFORE snapshot), {@code last_access_time}/
     * {@code last_access_ecode} SYSTEM_CHANGE (only when present in the BEFORE snapshot — see
     * {@link #addSystemAndReleaseRules}), {@code release_status}/{@code release_time}/
     * {@code release_ecode} IGNORE together (plan §14 — role-dependent whether the
     * Release-after-save dialog even fires, so none of the three can be asserted to a single
     * expected value). Everything else stays on the persistence map's normal
     * {@code MUST_NOT_CHANGE} default.
     */
    public static ExpectedChangeSet headerEdit(String builderName, DocumentContext context,
            VoucherSnapshot beforeSnapshot, String newRefno, String newRemarks, LocalDate newDate) {

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder(builderName);

        Map<String, Object> beforeRow = firstRow(beforeSnapshot, "finvchmain");

        // finpost has one header-level "main grid" posting row (GV: enttype=VMG, AR/AP: VMP —
        // see VoucherType#mainPostingEntType()'s Javadoc) plus one per-line posting row per
        // finvchdet detail line, each carrying its own unrelated narration. Resolving the
        // arbitrary "first" finpost row (as this used to) and asserting nartext MUST_CHANGE
        // table-wide against it wrongly failed every per-line row too — confirmed live (the
        // per-line row's nartext legitimately stays untouched by a header-only edit). Scoping to
        // just the main-posting row's enttype fixes this generically for every domain.
        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBeforeRow = findFinpostRowByMainEntType(beforeSnapshot, mainEntType);

        addIfChanged(builder, "finvchmain", "refno", DbValueNormalizer.getIgnoreCase(beforeRow, "refno"), newRefno);
        addIfChanged(builder, "finvchmain", "nartext", DbValueNormalizer.getIgnoreCase(beforeRow, "nartext"), newRemarks);
        // entry_source always reflects the LATEST save channel (e.g. a voucher originally created
        // via Excel import legitimately flips to "WEB" once edited through this UI-driven flow) —
        // per the user's explicit business rule. Unconditional (not gated on newRefno/newRemarks/
        // newDate being non-null) since entry_source changes independent of which specific header
        // field changed — any Save via this UI touches it.
        addIfChanged(builder, "finvchmain", "entry_source", DbValueNormalizer.getIgnoreCase(beforeRow, "entry_source"), "WEB");

        // Temporary workaround for a known, already-logged UI defect (not a mapping bug in this
        // framework): General Voucher's Numeric UDF displays/saves 0 instead of blank when empty.
        // Off by default (FinanceConfig.suppressUdfnum01ZeroBug()) — only skips this one column's
        // MUST_NOT_CHANGE check when a tester has deliberately opted in for an environment where
        // the bug is present; see that config method's Javadoc. Remove once the UI bug is fixed.
        if (ConfigFactory.getFinanceConfig().suppressUdfnum01ZeroBug()) {
            builder.rule("finvchmain", ColumnRule.ignore("udfnum01"));
        }
        // Scoped to just the main-posting (VMG) row — the main-grid row's docno reliably tracks
        // the header's refno on every save (confirmed live, repeatedly, including on 2026-08-21).
        if (newRefno != null) {
            boolean docnoChanged = !DbValueNormalizer.equalsNormalized(
                    DbValueNormalizer.getIgnoreCase(finpostBeforeRow, "docno"), newRefno);
            builder.rule("finpost", "enttype=" + mainEntType, docnoChanged
                    ? ColumnRule.mustChangeTo("docno", newRefno) : ColumnRule.mustNotChange("docno"));
        }

        // Plan §7: a per-line (non-main-grid, VDG/VDP) row's docno is confirmed live (2026-08-21,
        // entcode=17656, re-edited multiple times across this session) to be genuinely unreliable
        // — it sometimes tracks the header's refno in step, sometimes lags behind an older refno
        // value after a LATER edit moved the header/VMG on, and (on a few much older vouchers)
        // holds an outright unrelated value. This supersedes an earlier (2026-08-18) "confirmed
        // live: per-line row's docno stays fixed" finding, which happened not to have observed a
        // SECOND edit on the same voucher — the earlier finding wasn't wrong for what it tested,
        // just incomplete. Neither MUST_CHANGE nor MUST_NOT_CHANGE is reliably correct here, so
        // this is a known issue (same treatment GeneralVoucherEditChangeSets' TDS_APP_NOTE already
        // gives finvchdet.tds_app for an analogous confirmed-flaky-backend-column case) — the check
        // still runs and stays visible in the report, but doesn't fail the test either way. This is
        // table-wide, but the row-scoped MUST_CHANGE/MUST_NOT_CHANGE rule above for the main-grid
        // row still wins over it (row-scoped beats table-wide — see ExpectedChangeSet#findOverride),
        // so VMG's own docno stays a real, failing-if-wrong assertion.
        builder.rule("finpost", ColumnRule.knownIssue("docno",
                "finpost.docno on a per-line (VDG/VDP) row does not reliably track finvchmain.refno "
                + "across repeated edits (confirmed live, entcode=17656, 2026-08-21) — the main-grid "
                + "row's docno DOES reliably track refno; this is a backend inconsistency, not a "
                + "test defect."));

        if (newRemarks != null) {
            boolean nartextChanged = !DbValueNormalizer.equalsNormalized(
                    DbValueNormalizer.getIgnoreCase(finpostBeforeRow, "nartext"), newRemarks);
            builder.rule("finpost", "enttype=" + mainEntType, nartextChanged
                    ? ColumnRule.mustChangeTo("nartext", newRemarks) : ColumnRule.mustNotChange("nartext"));
        }

        if (newDate != null) {
            Object beforeDateRaw = DbValueNormalizer.getIgnoreCase(beforeRow, "vchdt");
            boolean changed = !DbValueNormalizer.equalsNormalized(beforeDateRaw, newDate);
            // Pass the LocalDate itself, not a formatted string — DbValueNormalizer normalizes both
            // sides to LocalDateTime for comparison; a String would never equal the DB's date value.
            builder.rule("finvchmain", changed
                    ? ColumnRule.mustChangeTo("vchdt", newDate)
                    : ColumnRule.mustNotChange("vchdt"));

            // A header date edit cascades to every finvchdet row's vchdt too — confirmed live, not
            // just the edited line — so this must be table-wide here in headerEdit (picked up by
            // detailLineEdit via mergeFrom below), the same way finvchdet.dataversion is below.
            builder.rule("finvchdet", changed
                    ? ColumnRule.mustChangeTo("vchdt", newDate)
                    : ColumnRule.mustNotChange("vchdt"));

            // Same cascade applies to finpost.entdt — confirmed live on BOTH the main-grid entry
            // (enttype=mainEntType) and every per-line entry, unlike nartext/docno above which
            // differ per row type. Table-wide (not scoped to "enttype=" + mainEntType) is what
            // makes this apply to every finpost row for the document.
            Object beforeEntdtRaw = DbValueNormalizer.getIgnoreCase(finpostBeforeRow, "entdt");
            boolean entdtChanged = !DbValueNormalizer.equalsNormalized(beforeEntdtRaw, newDate);
            builder.rule("finpost", entdtChanged
                    ? ColumnRule.mustChangeTo("entdt", newDate)
                    : ColumnRule.mustNotChange("entdt"));
        }

        addSystemAndReleaseRules(builder, beforeRow, finpostBeforeRow, context.getCurrentUser());

        // dataversion is an optimistic-locking version stamp — it bumps on ANY update to the
        // row, including a last_access_*-only touch, so it must never be MUST_NOT_CHANGE (the
        // persistence map's table-wide default). finvchmain has exactly one row per document,
        // always touched by any edit, so this is unconditional and table-wide.
        builder.rule("finvchmain", ColumnRule.mustChange("dataversion"));

        // finvchdet.dataversion ALSO bumps on EVERY row for the document on ANY save — not just
        // the specifically edited line. Confirmed via aud2_finvchdet/aud2_finvchmain: a pure
        // header-only edit transaction (touching only finvchmain.refno/nartext) produced an
        // UPDATE audit entry on a completely untouched finvchdet row (same transaction_id,
        // content unchanged, dataversion bumped). So this must be table-wide here in headerEdit
        // (picked up by detailLineEdit too via mergeFrom below) — NOT row-scoped to the edited
        // line the way damount/camount/isreversal correctly are.
        builder.rule("finvchdet", ColumnRule.mustChange("dataversion"));

        // A Save recreates the document's fincosttag row(s) under new surrogate `code` values
        // whenever the document has any cost-tag-applicable line — confirmed to happen even on a
        // pure header-only edit (narration/refno only, no amount change), not just when an
        // amount changes. Declared here (not gated on amountChanged) so detailLineEdit's
        // mergeFrom(headerEdit(...)) picks it up automatically for detail-line edits too.
        if (context.isCostTagApplicable()) {
            builder.expectRowDelete("fincosttag", row -> true);
            builder.expectRowInsert("fincosttag", row -> true);
        }

        return builder.build();
    }

    /**
     * Detail-line-edit rules — layers one {@code finvchdet} row's {@code damount}/{@code camount}/
     * {@code isreversal}/{@code nartext} override (MUST_CHANGE only if actually different from the
     * BEFORE snapshot; MUST_NOT_CHANGE otherwise) — row-scoped to the edited line's {@code glcode}
     * AND {@code slcode} (glcode alone doesn't disambiguate two Type3 lines sharing a GL account
     * with different sub-ledgers — confirmed live), every other {@code finvchdet} row stays
     * MUST_NOT_CHANGE — on top of {@link #headerEdit}'s rules.
     *
     * <p>{@code newLineNarration} is the edited line's OWN narration (e.g. GV's {@code
     * ExpenseIncomeDetail.ledgerNarration}/AR-AP's {@code SubLedgerInformation.ledgerNarration}) —
     * distinct from {@code newRemarks} above, which is the document HEADER's narration. Pass
     * {@code null} when the caller doesn't supply/edit a per-line narration (the column then stays
     * on the table's normal MUST_NOT_CHANGE default, same as before this parameter existed). When
     * non-null, asserts the change on both the {@code finvchdet} row (row-scoped the same way as
     * the amount/reversal rules above) and its matching {@code finpost} per-line ("VDG"/"VDP")
     * posting row (via {@link #addFinpostLineNarrationRule}, which reuses the now-disambiguated
     * {@link #findFinpostRowByGlcode}).
     */
    public static ExpectedChangeSet detailLineEdit(String builderName, DocumentContext context,
            VoucherSnapshot beforeSnapshot, String newRefno, String newRemarks, LocalDate newDate,
            long glcode, long slcode, boolean isDebit, BigDecimal newAmount, boolean newIsReversalYes,
            String newLineNarration) {

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder(builderName)
                .mergeFrom(headerEdit(builderName + ".header", context, beforeSnapshot, newRefno, newRemarks, newDate));

        String rowKeyContains = "glcode=" + glcode + ", slcode=" + slcodeDisplay(slcode);
        String amountColumn = isDebit ? "damount" : "camount";
        String newIsReversal = newIsReversalYes ? "Y" : "N";

        Map<String, Object> beforeRow = findFinvchdetRow(beforeSnapshot, glcode, slcode);
        Object beforeAmount = DbValueNormalizer.getIgnoreCase(beforeRow, amountColumn);
        Object beforeIsReversal = DbValueNormalizer.getIgnoreCase(beforeRow, "isreversal");

        boolean amountChanged = newAmount != null && !DbValueNormalizer.equalsNormalized(beforeAmount, newAmount);
        boolean reversalChanged = !DbValueNormalizer.equalsNormalized(beforeIsReversal, newIsReversal);

        builder.rule("finvchdet", rowKeyContains, amountChanged
                ? ColumnRule.mustChangeTo(amountColumn, newAmount) : ColumnRule.mustNotChange(amountColumn));
        builder.rule("finvchdet", rowKeyContains, reversalChanged
                ? ColumnRule.mustChangeTo("isreversal", newIsReversal) : ColumnRule.mustNotChange("isreversal"));

        if (newLineNarration != null) {
            Object beforeNartext = DbValueNormalizer.getIgnoreCase(beforeRow, "nartext");
            boolean nartextChanged = !DbValueNormalizer.equalsNormalized(beforeNartext, newLineNarration);
            builder.rule("finvchdet", rowKeyContains, nartextChanged
                    ? ColumnRule.mustChangeTo("nartext", newLineNarration) : ColumnRule.mustNotChange("nartext"));
            addFinpostLineNarrationRule(builder, context, beforeSnapshot, glcode, slcode, newLineNarration);
        }

        // dataversion is already covered table-wide by headerEdit's finvchdet rule above (picked
        // up here via mergeFrom) — every finvchdet row bumps dataversion on every save, not just
        // the edited line, so no row-scoped override is needed here.

        // fincosttag's row-replace expectation now lives in headerEdit (unconditional on
        // context.isCostTagApplicable(), not gated on amountChanged) — picked up here via
        // mergeFrom(headerEdit(...)) above, so no separate declaration is needed for detail-line
        // edits.

        // Double-entry balancing: a real accounting requirement, not optional — when this line's
        // amount changes, finvchmain's header total and finpost's main-grid row (which mirrors
        // it) must move to stay balanced against ALL finvchdet lines, and the matching finpost
        // per-line (VDG) posting entry must move to the edited line's own new amount. The header
        // side is domain-specific (GV: net of all lines, opposite side; AP: payment document; AR:
        // receivable document — see VoucherType#expectedHeaderAmount()'s Javadoc for the exact
        // per-domain formulas, confirmed against each domain's proven Create-side validator).
        if (amountChanged) {
            BigDecimal[] totals = projectedFinvchdetTotals(beforeSnapshot, glcode, slcode, isDebit, newAmount);
            addHeaderBalancingRules(builder, context, beforeSnapshot, totals);
            addFinpostLineBalancingRule(builder, context, beforeSnapshot, glcode, slcode, amountColumn, newAmount);
        }

        return builder.build();
    }

    /**
     * GL-type-transition detail-line-edit rules (plan §4B shape 1) — the edited line moves to a
     * DIFFERENT {@code glcode} (and, for a Sub-Ledger-applicable {@link com.gsl.enums.Finance
     * .Operations.GeneralVoucher.GlLineType}, possibly a different {@code slcode} too), so unlike
     * {@link #detailLineEdit} (which row-scopes a column override onto the SAME row), this
     * declares the old {@code (glcode, slcode)} row a {@code finvchdet}/{@code finpost} DELETE and
     * the new {@code (glcode, slcode)} row an INSERT — see {@link ExpectedChangeSet#isRowChangeExpected}:
     * an inserted/deleted row's own columns are never independently diffed, so the INSERT
     * predicates below encode the exact expected amount/reversal/narration themselves — a row that
     * appears with the WRONG values fails its predicate and is correctly reported as an
     * unexpected row change, not silently accepted.
     */
    public static ExpectedChangeSet detailLineGlTransition(String builderName, DocumentContext context,
            VoucherSnapshot beforeSnapshot, String newRefno, String newRemarks, LocalDate newDate,
            long oldGlcode, long oldSlcode, long newGlcode, long newSlcode, boolean isDebit,
            BigDecimal newAmount, boolean newIsReversalYes, String newLineNarration) {

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder(builderName)
                .mergeFrom(headerEdit(builderName + ".header", context, beforeSnapshot, newRefno, newRemarks, newDate));

        String amountColumn = isDebit ? "damount" : "camount";
        String newIsReversal = newIsReversalYes ? "Y" : "N";
        String mainEntType = context.getDocumentType().mainPostingEntType();

        builder.expectRowDelete("finvchdet", row -> matchesKey(row, oldGlcode, oldSlcode));
        builder.expectRowInsert("finvchdet", row -> matchesKey(row, newGlcode, newSlcode)
                && amountMatches(row, amountColumn, newAmount)
                && reversalMatches(row, newIsReversal)
                && (newLineNarration == null || narrationMatches(row, newLineNarration)));

        // finpost's per-line (VDG/VDP) row mirrors the same delete+insert — excluded from the
        // main-grid enttype row via notMainEntType, same scoping addFinpostLineBalancingRule/
        // addFinpostLineNarrationRule use for the in-place edit case.
        builder.expectRowDelete("finpost", row -> notMainEntType(row, mainEntType) && matchesKey(row, oldGlcode, oldSlcode));
        builder.expectRowInsert("finpost", row -> notMainEntType(row, mainEntType) && matchesKey(row, newGlcode, newSlcode)
                && amountMatches(row, amountColumn, newAmount)
                && (newLineNarration == null || narrationMatches(row, newLineNarration)));

        // Double-entry balancing: same substitution-based totals detailLineEdit uses, keyed by the
        // OLD glcode/slcode — the sum logic only needs to find and replace this line's own
        // contribution, it doesn't care which GL that line posts to.
        BigDecimal[] totals = projectedFinvchdetTotals(beforeSnapshot, oldGlcode, oldSlcode, isDebit, newAmount);
        addHeaderBalancingRules(builder, context, beforeSnapshot, totals);

        return builder.build();
    }

    /**
     * Add-new-line detail-line-edit rules (plan §4B shape 2) — a brand-new {@code finvchdet}/
     * {@code finpost} row appears that has no BEFORE counterpart at all (unlike
     * {@link #detailLineGlTransition}, there is no matching DELETE). Balancing is unconditional
     * (not gated on an "amountChanged" check) since a new line always perturbs the net by
     * definition — see {@link #projectedFinvchdetTotalsAddingRow}.
     */
    public static ExpectedChangeSet addNewDetailLine(String builderName, DocumentContext context,
            VoucherSnapshot beforeSnapshot, String newRefno, String newRemarks, LocalDate newDate,
            long newGlcode, long newSlcode, boolean isDebit, BigDecimal newAmount, boolean newIsReversalYes,
            String newLineNarration) {

        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder(builderName)
                .mergeFrom(headerEdit(builderName + ".header", context, beforeSnapshot, newRefno, newRemarks, newDate));

        String amountColumn = isDebit ? "damount" : "camount";
        String newIsReversal = newIsReversalYes ? "Y" : "N";
        String mainEntType = context.getDocumentType().mainPostingEntType();

        builder.expectRowInsert("finvchdet", row -> matchesKey(row, newGlcode, newSlcode)
                && amountMatches(row, amountColumn, newAmount)
                && reversalMatches(row, newIsReversal)
                && (newLineNarration == null || narrationMatches(row, newLineNarration)));

        builder.expectRowInsert("finpost", row -> notMainEntType(row, mainEntType) && matchesKey(row, newGlcode, newSlcode)
                && amountMatches(row, amountColumn, newAmount)
                && (newLineNarration == null || narrationMatches(row, newLineNarration)));

        BigDecimal[] totals = projectedFinvchdetTotalsAddingRow(beforeSnapshot, isDebit, newAmount);
        addHeaderBalancingRules(builder, context, beforeSnapshot, totals);

        return builder.build();
    }

    /**
     * Asserts {@code finvchmain}'s (and {@code finpost}'s main-grid row's, which mirrors it —
     * confirmed live) {@code damount}/{@code camount} against the domain-specific expected pair
     * from {@link com.gsl.enums.VoucherType#expectedHeaderAmount}, computed from {@code totals}
     * (the *projected* after-edit totals across every {@code finvchdet} row — see
     * {@link #projectedFinvchdetTotals}/{@link #projectedFinvchdetTotalsAddingRow}), per the
     * business rule: "the amount in posting will be balanced dr/cr between main and detail
     * amount... calculated depending on voucher type." Each column is compared against its own
     * BEFORE value ({@code addIfChanged} style) so a column that doesn't actually change still
     * correctly asserts {@code MUST_NOT_CHANGE}. Takes precomputed {@code totals} (rather than the
     * raw glcode/slcode/isDebit/newAmount this originally took) so both the substitution-based
     * ({@link #detailLineEdit}/{@link #detailLineGlTransition}) and additive
     * ({@link #addNewDetailLine}) callers share this one method.
     */
    private static void addHeaderBalancingRules(ExpectedChangeSet.Builder builder, DocumentContext context,
            VoucherSnapshot beforeSnapshot, BigDecimal[] totals) {

        if (totals == null) {
            Log.warn("[VoucherEditChangeSets] Could not sum the BEFORE snapshot's finvchdet rows (table "
                    + "missing/empty) — skipping the header/finpost balancing check for this detail-line edit.");
            return;
        }

        VoucherType.AmountPair expected = context.getDocumentType().expectedHeaderAmount(totals[0], totals[1]);

        Map<String, Object> finvchmainBeforeRow = firstRow(beforeSnapshot, "finvchmain");
        addAmountPairRules(builder, "finvchmain", null, finvchmainBeforeRow, expected);

        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBeforeRow = findFinpostRowByMainEntType(beforeSnapshot, mainEntType);
        addAmountPairRules(builder, "finpost", "enttype=" + mainEntType, finpostBeforeRow, expected);
    }

    /** Additive counterpart to {@link #projectedFinvchdetTotals} — sums every {@code finvchdet}
     *  row's {@code damount}/{@code camount} from the BEFORE snapshot, then ADDS the new line's
     *  own contribution on top (rather than substituting an existing row's contribution), since an
     *  {@link #addNewDetailLine} scenario's new row has no BEFORE counterpart to replace. Returns
     *  {@code null} if the BEFORE snapshot has no finvchdet rows to sum (same defensive reasoning
     *  as {@link #projectedFinvchdetTotals}). */
    private static BigDecimal[] projectedFinvchdetTotalsAddingRow(VoucherSnapshot beforeSnapshot,
            boolean isDebit, BigDecimal newAmount) {
        if (beforeSnapshot == null) return null;
        TableSnapshot finvchdet = beforeSnapshot.getTable("finvchdet");
        if (finvchdet == null || finvchdet.getRows().isEmpty()) return null;

        BigDecimal sumD = BigDecimal.ZERO;
        BigDecimal sumC = BigDecimal.ZERO;
        for (Map<String, Object> row : finvchdet.getRows()) {
            BigDecimal d = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(row, "damount"));
            BigDecimal c = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(row, "camount"));
            sumD = sumD.add(d != null ? d : BigDecimal.ZERO);
            sumC = sumC.add(c != null ? c : BigDecimal.ZERO);
        }
        sumD = sumD.add(isDebit ? newAmount : BigDecimal.ZERO);
        sumC = sumC.add(isDebit ? BigDecimal.ZERO : newAmount);
        return new BigDecimal[]{sumD, sumC};
    }

    /** {@code true} when {@code row}'s {@code glcode}/{@code slcode} match the given values —
     *  shared row-identity predicate behind {@link #detailLineGlTransition}/{@link #addNewDetailLine}'s
     *  {@code expectRowInsert}/{@code expectRowDelete} matchers. Same NULL-safe {@code slcode}
     *  handling as {@link #findFinvchdetRow}/{@link #findFinpostRowByGlcode}. Package-private (not
     *  {@code private}) so {@link GeneralVoucherCreateChangeSets} can reuse it for the same
     *  finvchdet/finpost row-identity matching, rather than re-implementing it. */
    static boolean matchesKey(Map<String, Object> row, long glcode, long slcode) {
        Object rawGlcode = DbValueNormalizer.getIgnoreCase(row, "glcode");
        Object rawSlcode = DbValueNormalizer.getIgnoreCase(row, "slcode");
        return rawGlcode != null && String.valueOf(glcode).equals(String.valueOf(rawGlcode))
                && slcodeDisplay(slcode).equals(DbValueNormalizer.displayString(rawSlcode));
    }

    /** Package-private for the same reason as {@link #matchesKey} — reused by
     *  {@link GeneralVoucherCreateChangeSets}. */
    static boolean amountMatches(Map<String, Object> row, String amountColumn, BigDecimal expected) {
        return DbValueNormalizer.equalsNormalized(DbValueNormalizer.getIgnoreCase(row, amountColumn), expected);
    }

    /** Package-private for the same reason as {@link #matchesKey} — reused by
     *  {@link GeneralVoucherCreateChangeSets}. */
    static boolean reversalMatches(Map<String, Object> row, String expectedIsReversal) {
        return DbValueNormalizer.equalsNormalized(DbValueNormalizer.getIgnoreCase(row, "isreversal"), expectedIsReversal);
    }

    /** Package-private for the same reason as {@link #matchesKey} — reused by
     *  {@link GeneralVoucherCreateChangeSets}. */
    static boolean narrationMatches(Map<String, Object> row, String expectedNarration) {
        return DbValueNormalizer.equalsNormalized(DbValueNormalizer.getIgnoreCase(row, "nartext"), expectedNarration);
    }

    /** {@code true} when {@code row}'s {@code enttype} is NOT {@code mainEntType} — i.e. it's a
     *  per-line ("VDG"/"VDP") {@code finpost} posting row, not the document's header-level
     *  "main grid" row. Same exclusion {@link #findFinpostRowByGlcode} already applies.
     *  Package-private for the same reason as {@link #matchesKey} — reused by
     *  {@link GeneralVoucherCreateChangeSets}. */
    static boolean notMainEntType(Map<String, Object> row, String mainEntType) {
        if (mainEntType == null) return true;
        Object enttype = DbValueNormalizer.getIgnoreCase(row, "enttype");
        return !mainEntType.equalsIgnoreCase(String.valueOf(enttype));
    }

    /** Sums every {@code finvchdet} row's {@code damount}/{@code camount} from the BEFORE
     *  snapshot, substituting the edited row's (matched by {@code glcode}+{@code slcode}, same
     *  NULL-safe match as {@link #findFinvchdetRow}) contribution with its NEW value on the
     *  correct side instead of its before-value — exact, not approximate, since V1 only ever
     *  edits one line per Save. Returns {@code null} if the BEFORE snapshot has no finvchdet rows
     *  to sum (shouldn't happen for a real voucher, but defensive rather than NPE-prone). */
    private static BigDecimal[] projectedFinvchdetTotals(VoucherSnapshot beforeSnapshot, long glcode, long slcode,
            boolean isDebit, BigDecimal newAmount) {
        if (beforeSnapshot == null) return null;
        TableSnapshot finvchdet = beforeSnapshot.getTable("finvchdet");
        if (finvchdet == null || finvchdet.getRows().isEmpty()) return null;

        String editedSlcode = slcodeDisplay(slcode);
        BigDecimal sumD = BigDecimal.ZERO;
        BigDecimal sumC = BigDecimal.ZERO;
        for (Map<String, Object> row : finvchdet.getRows()) {
            Object rawGlcode = DbValueNormalizer.getIgnoreCase(row, "glcode");
            Object rawSlcode = DbValueNormalizer.getIgnoreCase(row, "slcode");
            boolean isEditedRow = rawGlcode != null && String.valueOf(glcode).equals(String.valueOf(rawGlcode))
                    && editedSlcode.equals(DbValueNormalizer.displayString(rawSlcode));

            if (isEditedRow) {
                sumD = sumD.add(isDebit ? newAmount : BigDecimal.ZERO);
                sumC = sumC.add(isDebit ? BigDecimal.ZERO : newAmount);
            } else {
                BigDecimal d = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(row, "damount"));
                BigDecimal c = DbValueNormalizer.asBigDecimal(DbValueNormalizer.getIgnoreCase(row, "camount"));
                sumD = sumD.add(d != null ? d : BigDecimal.ZERO);
                sumC = sumC.add(c != null ? c : BigDecimal.ZERO);
            }
        }
        return new BigDecimal[]{sumD, sumC};
    }

    /** Applies an {@link VoucherType.AmountPair}'s {@code damount}/{@code camount} to
     *  {@code tableName} ({@code rowKeyContains} {@code null} = table-wide, e.g.
     *  {@code finvchmain}; non-null = row-scoped, e.g. {@code finpost}'s main-grid row) —
     *  {@code addIfChanged} style against each column's own BEFORE value, so an unaffected column
     *  still correctly asserts {@code MUST_NOT_CHANGE} rather than a spurious {@code MUST_CHANGE}. */
    /** Applies both halves of {@code expected} (damount and camount) via {@link #addAmountColumnRule}. */
    private static void addAmountPairRules(ExpectedChangeSet.Builder builder, String tableName,
            String rowKeyContains, Map<String, Object> beforeRow, VoucherType.AmountPair expected) {
        addAmountColumnRule(builder, tableName, rowKeyContains, beforeRow, "damount", expected.damount());
        addAmountColumnRule(builder, tableName, rowKeyContains, beforeRow, "camount", expected.camount());
    }

    /** {@code addIfChanged}-style single-column rule: MUST_CHANGE-to {@code expectedValue} if it
     *  differs from {@code beforeRow}'s own value, MUST_NOT_CHANGE otherwise. */
    private static void addAmountColumnRule(ExpectedChangeSet.Builder builder, String tableName,
            String rowKeyContains, Map<String, Object> beforeRow, String column, BigDecimal expectedValue) {
        boolean changed = !DbValueNormalizer.equalsNormalized(
                DbValueNormalizer.getIgnoreCase(beforeRow, column), expectedValue);
        ColumnRule rule = changed ? ColumnRule.mustChangeTo(column, expectedValue) : ColumnRule.mustNotChange(column);
        if (rowKeyContains == null) builder.rule(tableName, rule);
        else builder.rule(tableName, rowKeyContains, rule);
    }

    /**
     * Asserts the {@code finpost} per-line ("VDG"/"VDP") posting row for the edited
     * {@code finvchdet} line moves its {@code damount}/{@code camount} to the new amount, on the
     * SAME side as the edited line (unlike {@code finvchmain}'s counter-leg, a per-line posting
     * row mirrors its own detail line directly, not the opposite side — see Create's "1 VMG + N
     * VDG posting entries, balanced" Expected Results row). {@code finpost}'s declared business
     * key is {@code [entcode, glcode, slcode, enttype]} (see
     * {@code VoucherPersistenceMapFactory#finpost()}) — {@link #findFinpostRowByGlcode} resolves
     * the edited line's own {@code glcode}/{@code slcode} to that row, and the row-scoped key
     * built here ({@code glcode}+{@code slcode}+{@code enttype}) is a subset of that same
     * business key, which {@link ExpectedChangeSet}'s exact-segment matching accepts.
     */
    private static void addFinpostLineBalancingRule(ExpectedChangeSet.Builder builder, DocumentContext context,
            VoucherSnapshot beforeSnapshot, long glcode, long slcode, String amountColumn, BigDecimal newAmount) {

        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBeforeRow = findFinpostRowByGlcode(beforeSnapshot, glcode, slcode, mainEntType);
        if (finpostBeforeRow == null) {
            Log.warn("[VoucherEditChangeSets] Could not find a unique finpost row for glcode={}, slcode={} "
                    + "(excluding the main-grid enttype='{}') in the BEFORE snapshot — skipping the per-line "
                    + "posting balancing check for this detail-line edit.", glcode, slcode, mainEntType);
            return;
        }

        String finpostRowKey = finpostLineRowKey(finpostBeforeRow, glcode, slcode);
        builder.rule("finpost", finpostRowKey, ColumnRule.mustChangeTo(amountColumn, newAmount));
    }

    /**
     * Asserts the {@code finpost} per-line ("VDG"/"VDP") posting row for the edited
     * {@code finvchdet} line moves its {@code nartext} to the new line narration — only called
     * when {@link #detailLineEdit} is given a non-null {@code newLineNarration}. Mirrors
     * {@link #addFinpostLineBalancingRule}'s row resolution via {@link #findFinpostRowByGlcode}.
     */
    private static void addFinpostLineNarrationRule(ExpectedChangeSet.Builder builder, DocumentContext context,
            VoucherSnapshot beforeSnapshot, long glcode, long slcode, String newLineNarration) {

        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBeforeRow = findFinpostRowByGlcode(beforeSnapshot, glcode, slcode, mainEntType);
        if (finpostBeforeRow == null) {
            Log.warn("[VoucherEditChangeSets] Could not find a unique finpost row for glcode={}, slcode={} "
                    + "(excluding the main-grid enttype='{}') in the BEFORE snapshot — skipping the per-line "
                    + "finpost narration check for this detail-line edit.", glcode, slcode, mainEntType);
            return;
        }

        String finpostRowKey = finpostLineRowKey(finpostBeforeRow, glcode, slcode);

        Object beforeNartext = DbValueNormalizer.getIgnoreCase(finpostBeforeRow, "nartext");
        boolean nartextChanged = !DbValueNormalizer.equalsNormalized(beforeNartext, newLineNarration);
        builder.rule("finpost", finpostRowKey, nartextChanged
                ? ColumnRule.mustChangeTo("nartext", newLineNarration) : ColumnRule.mustNotChange("nartext"));
    }

    /** Builds the row-scoped key string for a {@code finpost} per-line row already resolved by
     *  {@link #findFinpostRowByGlcode} — {@code glcode}+{@code slcode} (matching the caller's own
     *  values, formatted via {@link DbValueNormalizer#displayString} the same way
     *  {@code DiffEngine.businessKeyOf} formats every segment of the table's actual
     *  {@code [entcode, glcode, slcode, enttype]} business key) plus the resolved row's own
     *  {@code enttype} — a subset of that key, which {@link ExpectedChangeSet}'s exact-segment
     *  matching accepts. Deliberately does NOT include {@code postcode} (not part of the business
     *  key — see {@code VoucherPersistenceMapFactory#finpost()}) or {@code entcode} (constant
     *  within one document, adds nothing to disambiguate rows within it). */
    private static String finpostLineRowKey(Map<String, Object> finpostRow, long glcode, long slcode) {
        Object enttype = DbValueNormalizer.getIgnoreCase(finpostRow, "enttype");
        return "glcode=" + DbValueNormalizer.displayString(glcode)
                + ", slcode=" + slcodeDisplay(slcode)
                + ", enttype=" + DbValueNormalizer.displayString(enttype);
    }

    /**
     * Adds the {@code last_access_time}/{@code last_access_ecode} SYSTEM_CHANGE checks (only when
     * present in the BEFORE snapshot) and the {@code release_status}/{@code release_time}/
     * {@code release_ecode} IGNORE-together rule (plan §14/Context "Schema realities") — common to
     * every {@code finvchmain}-backed voucher type. {@code finpost} carries the same release-status
     * trio and last-access columns (mirrored below) and changes together with {@code finvchmain}
     * whenever the Release-after-save dialog fires.
     *
     * <p>{@code last_access_ecode} stores an employee-code FK, not a username string — it's
     * resolved to the current user's ecode via {@code HrEmpDao.findEcodeByAbbre(...)} (the
     * existing username→ecode lookup already used by every Create data source) and asserted as a
     * known expected value ({@link ColumnRule#systemChangeTo}), not compared against the raw
     * username the way {@link ColumnRule#systemUser} would.
     */
    private static void addSystemAndReleaseRules(ExpectedChangeSet.Builder builder,
            Map<String, Object> finvchmainBeforeRow, Map<String, Object> finpostBeforeRow, String currentUsername) {
        addReleaseTrio(builder, "finvchmain", finvchmainBeforeRow, currentUsername);
        addReleaseTrio(builder, "finpost", finpostBeforeRow, currentUsername);
    }

    /**
     * Shared body for one table's {@code last_access_time}/{@code last_access_ecode}
     * SYSTEM_CHANGE checks plus its {@code release_status}/{@code release_time}/
     * {@code release_ecode} IGNORE-together rule — see {@link #addSystemAndReleaseRules}.
     */
    private static void addReleaseTrio(ExpectedChangeSet.Builder builder, String tableName,
            Map<String, Object> beforeRow, String currentUsername) {
        if (beforeRow == null) return;

        if (containsKeyIgnoreCase(beforeRow, "last_access_time")) {
            builder.rule(tableName, ColumnRule.systemTimestamp("last_access_time", 300L));
        } else {
            Log.warn("[VoucherEditChangeSets] {}.last_access_time not present in the BEFORE "
                    + "snapshot's captured columns — skipping the SYSTEM_CHANGE check for it.", tableName);
        }

        if (containsKeyIgnoreCase(beforeRow, "last_access_ecode")) {
            Long ecode = resolveEcode(currentUsername);
            if (ecode != null) {
                builder.rule(tableName, ColumnRule.systemChangeTo("last_access_ecode", ecode));
            } else {
                Log.warn("[VoucherEditChangeSets] Could not resolve ecode for current user '{}' via "
                        + "HrEmpDao — skipping the {}.last_access_ecode SYSTEM_CHANGE check.", currentUsername, tableName);
            }
        }

        // release_status/release_time/release_ecode change together only when the Release-after-
        // save confirmation dialog fires (role-dependent, not test-data-declared) — IGNORE all
        // three rather than the persistence map's normal MUST_NOT_CHANGE default (plan §14).
        builder.rule(tableName, ColumnRule.ignore("release_status"));
        builder.rule(tableName, ColumnRule.ignore("release_time"));
        builder.rule(tableName, ColumnRule.ignore("release_ecode"));
    }

    /** Package-private (Round 12 Item 5) — {@link GeneralVoucherRevertChangeSets} reuses this
     *  same ecode resolution for its own "same user released before → release_ecode unchanged"
     *  check, instead of duplicating the lookup. */
    static Long resolveEcode(String username) {
        if (username == null || username.isBlank()) return null;
        try {
            return DatabaseManager.getJdbi().onDemand(HrEmpDao.class).findEcodeByAbbre(username).orElse(null);
        } catch (RuntimeException e) {
            Log.warn("[VoucherEditChangeSets] ecode resolution failed for user '{}': {}", username, e.getMessage());
            return null;
        }
    }

    static Map<String, Object> firstRow(VoucherSnapshot snapshot, String tableName) {
        if (snapshot == null) return null;
        TableSnapshot table = snapshot.getTable(tableName);
        if (table == null || table.getRows().isEmpty()) return null;
        return table.getRows().get(0);
    }

    /** Finds the one {@code finpost} row whose {@code enttype} matches {@code mainEntType} —
     *  the document's header-level "main grid" posting row (see
     *  {@code VoucherType#mainPostingEntType()}). Mirrors {@link #findFinvchdetRow}'s style. */
    static Map<String, Object> findFinpostRowByMainEntType(VoucherSnapshot beforeSnapshot, String mainEntType) {
        if (beforeSnapshot == null) return null;
        TableSnapshot finpostBefore = beforeSnapshot.getTable("finpost");
        if (finpostBefore == null) return null;
        return finpostBefore.getRows().stream()
                .filter(row -> mainEntType.equalsIgnoreCase(
                        String.valueOf(DbValueNormalizer.getIgnoreCase(row, "enttype"))))
                .findFirst()
                .orElse(null);
    }

    /** Finds the one {@code finpost} row for {@code glcode} AND {@code slcode} that is NOT the
     *  main-grid entry ({@code enttype == excludeMainEntType}) — i.e. the per-line "VDG"/"VDP"
     *  posting row for a specific {@code finvchdet} line, used to row-scope the balancing/
     *  narration checks in {@link #addFinpostLineBalancingRule}/{@link #addFinpostLineNarrationRule}.
     *  {@code glcode} alone does NOT disambiguate — confirmed live: two real {@code finpost} rows
     *  on the same document shared {@code glcode=1218} with different {@code slcode} (342 vs 343)
     *  and different narrations, the same collision {@link #findFinvchdetRow} already guards
     *  against for {@code finvchdet}. {@code slcode} is matched NULL-safely via
     *  {@link #slcodeDisplay}, mirroring {@link #findFinvchdetRow}'s own handling.
     *
     *  <p>If more than one row still matches (a genuine ambiguity — e.g. two Type1 lines sharing a
     *  GL account with no sub-ledger), logs a warning and returns {@code null} rather than
     *  silently picking one via {@code findFirst()} — callers must never assert against a row
     *  that wasn't uniquely resolved. Mirrors {@link #findFinpostRowByMainEntType}'s style
     *  otherwise. */
    static Map<String, Object> findFinpostRowByGlcode(VoucherSnapshot beforeSnapshot, long glcode, long slcode,
            String excludeMainEntType) {
        if (beforeSnapshot == null) return null;
        TableSnapshot finpostBefore = beforeSnapshot.getTable("finpost");
        if (finpostBefore == null) return null;

        String expectedSlcode = slcodeDisplay(slcode);
        List<Map<String, Object>> matches = finpostBefore.getRows().stream()
                .filter(row -> {
                    Object rawGlcode = DbValueNormalizer.getIgnoreCase(row, "glcode");
                    Object rawSlcode = DbValueNormalizer.getIgnoreCase(row, "slcode");
                    Object enttype = DbValueNormalizer.getIgnoreCase(row, "enttype");
                    boolean glcodeMatches = rawGlcode != null && String.valueOf(glcode).equals(String.valueOf(rawGlcode));
                    boolean slcodeMatches = expectedSlcode.equals(DbValueNormalizer.displayString(rawSlcode));
                    boolean isNotMainGrid = excludeMainEntType == null
                            || !excludeMainEntType.equalsIgnoreCase(String.valueOf(enttype));
                    return glcodeMatches && slcodeMatches && isNotMainGrid;
                })
                .collect(Collectors.toList());

        if (matches.size() > 1) {
            Log.warn("[VoucherEditChangeSets] {} finpost rows matched glcode={}, slcode={} (excluding the "
                    + "main-grid enttype='{}') in the BEFORE snapshot — ambiguous, skipping the per-line "
                    + "finpost check for this detail-line edit rather than picking one arbitrarily.",
                    matches.size(), glcode, slcode, excludeMainEntType);
            return null;
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Finds the one {@code finvchdet} row matching both {@code glcode} AND {@code slcode} —
     *  {@code glcode} alone doesn't disambiguate two Type3 (sub-ledger-applicable) lines sharing
     *  the same GL account with different sub-ledgers (confirmed live: two such rows on the same
     *  document). {@code slcode=0} for Type1/Type2 lines with no sub-ledger — see
     *  {@link #slcodeDisplay} for why the raw column's real DB value is {@code NULL}, not the
     *  literal {@code 0}, for those rows. */
    static Map<String, Object> findFinvchdetRow(VoucherSnapshot beforeSnapshot, long glcode, long slcode) {
        if (beforeSnapshot == null) return null;
        TableSnapshot finvchdetBefore = beforeSnapshot.getTable("finvchdet");
        if (finvchdetBefore == null) return null;
        String expectedSlcode = slcodeDisplay(slcode);
        return finvchdetBefore.getRows().stream()
                .filter(row -> {
                    Object rawGlcode = DbValueNormalizer.getIgnoreCase(row, "glcode");
                    Object rawSlcode = DbValueNormalizer.getIgnoreCase(row, "slcode");
                    return rawGlcode != null && String.valueOf(glcode).equals(String.valueOf(rawGlcode))
                            && expectedSlcode.equals(DbValueNormalizer.displayString(rawSlcode));
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Renders a resolved {@code slcode} (per {@code GeneralVoucherEditChangeSets.resolveSlcode()}'s
     * {@code 0} = "no sub-ledger" sentinel) the same way {@link DbValueNormalizer#displayString}
     * renders the real DB column — confirmed live that a no-sub-ledger {@code finvchdet.slcode} is
     * genuinely {@code NULL} in the database, not the literal {@code 0} — comparing/building row
     * keys with the literal string {@code "0"} silently never matched the real business key
     * (which shows {@code "slcode=—"}), dropping the edited line's row-scoped rule to the table's
     * {@code MUST_NOT_CHANGE} default for every Type1 (no-sub-ledger) line. Used both for the
     * row-key string built here and for {@link #findFinvchdetRow}'s own lookup.
     */
    static String slcodeDisplay(long slcode) {
        return slcode == 0 ? DbValueNormalizer.displayString(null) : String.valueOf(slcode);
    }

    /** {@code newValue == null} leaves {@code column} on its table-wide default (no override added);
     *  otherwise MUST_CHANGE-to {@code newValue} if different from {@code beforeRaw}, else MUST_NOT_CHANGE. */
    private static void addIfChanged(ExpectedChangeSet.Builder builder, String tableName, String column,
            Object beforeRaw, String newValue) {
        if (newValue == null) return; // no override supplied — leave column on MUST_NOT_CHANGE default
        boolean changed = !DbValueNormalizer.equalsNormalized(beforeRaw, newValue);
        builder.rule(tableName, changed ? ColumnRule.mustChangeTo(column, newValue) : ColumnRule.mustNotChange(column));
    }

    private static boolean containsKeyIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return true;
        for (String k : row.keySet()) {
            if (k.equalsIgnoreCase(key)) return true;
        }
        return false;
    }
}
