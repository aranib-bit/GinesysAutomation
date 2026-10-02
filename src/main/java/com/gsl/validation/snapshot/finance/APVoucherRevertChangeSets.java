package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.util.Map;

/**
 * {@link ExpectedChangeSet} builder for AP Voucher's Revert flow.
 *
 * <p><b>Deliberately narrower than {@link GeneralVoucherRevertChangeSets}</b> — that class also
 * declares explicit {@code MUST_NOT_CHANGE} overrides for a long list of {@code finvchmain}/
 * {@code finvchdet}/{@code finpost}/{@code fincosttag} identity/header columns (e.g.
 * {@code isreversal}, {@code gl_cc_appl}), closing a real gap where those columns' table-wide
 * {@code mandatoryNotNull} rule would otherwise silently win over the persistence map's
 * {@code MUST_NOT_CHANGE} default (see that class's own Javadoc for the full "Round 23
 * correction" reasoning). Those specific column names are confirmed to exist and apply to GV's
 * own {@code finvchdet} schema — they are NOT confirmed to exist/apply identically on AP
 * Voucher's schema. Rather than risk silently-wrong assertions (or, worse, a build failure from
 * asserting on a column AP's schema doesn't even have) by copying that list verbatim without a
 * live-DB schema confirmation pass, this class deliberately builds ONLY the safe, genuinely
 * domain-agnostic core every Revert needs — the release-status trio transition on
 * {@code finvchmain}/{@code finpost} plus {@code finvchmain.dataversion}'s conditional bump —
 * and leaves every other column on the persistence map's own table-wide {@code MUST_NOT_CHANGE}/
 * {@code mandatoryNotNull} defaults exactly as they already are. A future pass that confirms AP's
 * exact column list against a live schema can safely extend this class the same way
 * {@code GeneralVoucherRevertChangeSets} was extended — this is an intentional scope reduction,
 * not an oversight.
 *
 * <p>{@code addReleaseTransition} below is copied near-verbatim from
 * {@code GeneralVoucherRevertChangeSets}'s own private helper of the same name — that method's
 * body is already domain-agnostic (table name/row-key/beforeRow/newStatus/currentUsername are all
 * plain parameters, nothing GV-specific inside it), so this is a deliberate, reviewed copy rather
 * than a risky cross-class extraction into a shared base without a schema-confirmation pass to
 * back it.
 */
public final class APVoucherRevertChangeSets {

    private APVoucherRevertChangeSets() {}

    /**
     * Builds the Revert-mode {@link ExpectedChangeSet} — the release-status trio's transition (or
     * non-transition) on {@code finvchmain}/{@code finpost}, plus {@code finvchmain.dataversion}'s
     * conditional bump. See class Javadoc for why this is deliberately narrower than
     * {@code GeneralVoucherRevertChangeSets} — every other column stays on the persistence map's
     * own defaults, not an explicit override here.
     *
     * @param newStatus the absolute target {@code release_status} this Revert attempt should have
     *                  produced — {@code "U"} for a real Unposted-transition or an already-Unposted
     *                  no-op, or the document's own immediate-before value for a blocked
     *                  ({@code CANNOT_REVERT_BRS_DONE}/{@code CANNOT_REVERT_MONTH_CLOSED}) outcome,
     *                  where nothing should have changed at all
     */
    public static ExpectedChangeSet revert(DocumentContext context, VoucherSnapshot beforeSnapshot, String newStatus) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("APVoucherRevertChangeSets.revert");

        Map<String, Object> finvchmainBefore = VoucherEditChangeSets.firstRow(beforeSnapshot, "finvchmain");
        String mainEntType = context.getDocumentType().mainPostingEntType();
        Map<String, Object> finpostBefore =
                VoucherEditChangeSets.findFinpostRowByMainEntType(beforeSnapshot, mainEntType);

        boolean released = addReleaseTransition(builder, "finvchmain", finvchmainBefore, newStatus,
                context.getCurrentUser());
        // Table-wide (not row-scoped to the main-grid enttype) — mirrors GeneralVoucherRevertChangeSets'
        // own confirmed-live finding that every finpost row for the document transitions identically,
        // not just the main-grid (VMP) row.
        addReleaseTransition(builder, "finpost", finpostBefore, newStatus, context.getCurrentUser());

        if (released) {
            builder.rule("finvchmain", ColumnRule.mustChange("dataversion"));
        } else {
            builder.rule("finvchmain", ColumnRule.mustNotChange("dataversion"));
        }
        // finvchdet.dataversion deliberately gets NO rule here (table-wide MUST_NOT_CHANGE default
        // applies) — same reasoning GeneralVoucherRevertChangeSets documents: a plain Revert does
        // not touch finvchdet at all.

        return builder.build();
    }

    /**
     * Applies the {@code release_status}/{@code release_time}/{@code release_ecode} trio to
     * {@code tableName} (table-wide) — copied near-verbatim from
     * {@code GeneralVoucherRevertChangeSets#addReleaseTransition} (see class Javadoc for why a
     * copy, not a shared-base extraction, was chosen here). {@code addIfChanged}-style against
     * {@code beforeRow}'s own captured {@code release_status}, so a table that genuinely didn't
     * transition (a blocked/no-op outcome) correctly asserts {@code MUST_NOT_CHANGE} on all three
     * columns rather than a spurious {@code MUST_CHANGE}. {@code release_ecode} additionally
     * stays unchanged when the SAME user performs this action twice (its value only changes when a
     * DIFFERENT user performs it).
     *
     * @return whether {@code beforeRow}'s release_status actually differs from {@code newStatus}
     */
    private static boolean addReleaseTransition(ExpectedChangeSet.Builder builder, String tableName,
            Map<String, Object> beforeRow, String newStatus, String currentUsername) {
        if (beforeRow == null) return false;

        boolean changed = !DbValueNormalizer.equalsNormalized(
                DbValueNormalizer.getIgnoreCase(beforeRow, "release_status"), newStatus);

        ColumnRule statusRule = changed
                ? ColumnRule.mustChangeTo("release_status", newStatus)
                : ColumnRule.mustNotChange("release_status");
        ColumnRule timeRule = changed ? ColumnRule.mustChange("release_time") : ColumnRule.mustNotChange("release_time");

        ColumnRule ecodeRule;
        if (!changed) {
            ecodeRule = ColumnRule.mustNotChange("release_ecode");
        } else {
            Long currentEcode = VoucherEditChangeSets.resolveEcode(currentUsername);
            Object beforeEcode = DbValueNormalizer.getIgnoreCase(beforeRow, "release_ecode");
            if (currentEcode != null && DbValueNormalizer.equalsNormalized(beforeEcode, currentEcode)) {
                ecodeRule = ColumnRule.mustNotChange("release_ecode");
            } else if (currentEcode != null) {
                ecodeRule = ColumnRule.mustChangeTo("release_ecode", currentEcode);
            } else {
                ecodeRule = ColumnRule.mustChange("release_ecode");
            }
        }

        builder.rule(tableName, statusRule);
        builder.rule(tableName, timeRule);
        builder.rule(tableName, ecodeRule);

        return changed;
    }
}
