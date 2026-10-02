package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;

/**
 * {@link ExpectedChangeSet} builder for AP Voucher's Delete flow — copied near-verbatim from
 * {@link GeneralVoucherDeleteChangeSets}, which is already safely domain-agnostic (plain
 * {@code expectRowDelete("finvchmain"/"finvchdet"/"finpost", row -> true)} plus a conditional
 * {@code fincosttag} block gated on {@code context.isCostTagApplicable()}) — AP uses the exact
 * same {@code finvchmain}/{@code finvchdet}/{@code finpost}/{@code fincosttag} tables as GV/AR, so
 * no AP-specific column knowledge is required here (unlike Revert's own change-set builder, which
 * deliberately stays narrower — see {@link APVoucherRevertChangeSets}'s Javadoc for why).
 */
public final class APVoucherDeleteChangeSets {

    private APVoucherDeleteChangeSets() {}

    /** Builds the "every row this document owns must be gone" change set — {@code row -> true} is
     *  safe here for the same reason {@link GeneralVoucherDeleteChangeSets#delete} documents: the
     *  snapshot is already scoped to this one document's own rows (via
     *  {@code VoucherDocumentContextResolver}/{@code SnapshotService}'s fk-scoped capture). */
    public static ExpectedChangeSet delete(DocumentContext context) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("APVoucherDeleteChangeSets.delete");

        builder.expectRowDelete("finvchmain", row -> true);
        builder.expectRowDelete("finvchdet", row -> true);
        builder.expectRowDelete("finpost", row -> true);

        if (context.isCostTagApplicable()) {
            builder.expectRowDelete("fincosttag", row -> true);
        }

        return builder.build();
    }
}
