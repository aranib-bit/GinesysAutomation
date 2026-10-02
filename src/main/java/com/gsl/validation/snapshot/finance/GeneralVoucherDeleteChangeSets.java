package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;

/**
 * {@link ExpectedChangeSet} builder for General Voucher's Delete flow — Round 3 Item K, closed
 * out after the user's follow-up ("This was told somehow missed"). Brings Delete-mode's DB
 * validation up to the same snapshot-diff-engine coverage Create/Edit/Revert already have.
 *
 * <p>{@code DeleteGeneralVoucher.java}'s own hand-written checks (Step 9b "Database Validation —
 * Live Row Removed" / Step 10 "Database Validation — Audit Trail") only ever confirmed
 * {@code finvchmain} itself is gone and that {@code aud2_finvchmain} recorded the DELETE — there
 * was no check at all that the related {@code finvchdet}/{@code finpost}/{@code fincosttag} rows
 * (keyed by {@code vchcode}/{@code entcode}/{@code entcode} respectively — "the same relation"
 * every other changeset in this package already uses to scope a document's own rows) were
 * actually removed too. This class runs ALONGSIDE those existing hand-written checks, not instead
 * of them — same "complementary coverage" relationship {@link GeneralVoucherCreateChangeSets}'s
 * own Javadoc already documents for Create's Level 1-4 checks vs. its snapshot validation.
 *
 * <p>Delete is the inverse of Create: the BEFORE snapshot has the real rows, the AFTER snapshot
 * should have none. {@code row -> true} is safe here for the identical reason
 * {@link GeneralVoucherCreateChangeSets} uses it for inserts — the snapshot is already scoped to
 * this one document's own rows (via {@code VoucherDocumentContextResolver}/{@code SnapshotService}'s
 * fk-scoped capture), so "any row present in the BEFORE slice" already means "this document's own
 * row" — this step only needs to confirm cleanup, not re-validate content (Create/Edit/Revert's
 * own checks already established the content was correct when it was written).
 */
public final class GeneralVoucherDeleteChangeSets {

    private GeneralVoucherDeleteChangeSets() {}

    /** Builds the "every row this document owns must be gone" change set — see class Javadoc for
     *  why {@code row -> true} is a safe (not unchecked) matcher here. */
    public static ExpectedChangeSet delete(DocumentContext context) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherDeleteChangeSets.delete");

        builder.expectRowDelete("finvchmain", row -> true);
        builder.expectRowDelete("finvchdet", row -> true);
        builder.expectRowDelete("finpost", row -> true);

        // fincosttag only ever participates for a site-applicable document (Type2/Type3 line) —
        // same participationCondition VoucherPersistenceMapFactory.fincosttag() already declares
        // (DocumentContext::isCostTagApplicable); a document with no such line legitimately never
        // has fincosttag rows to delete, so no rule is declared for it there either.
        if (context.isCostTagApplicable()) {
            builder.expectRowDelete("fincosttag", row -> true);
        }

        return builder.build();
    }
}
