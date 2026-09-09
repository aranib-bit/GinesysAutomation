package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;

import java.math.BigDecimal;

/**
 * {@link ExpectedChangeSet} builder for General Voucher's Create flow — the Create-side
 * counterpart to {@link GeneralVoucherEditChangeSets}, bringing Create-mode's DB validation up
 * to the same snapshot-diff-engine coverage Edit already has (unexpected-row detection +
 * {@code aud2_*} audit-trail INSERT checks), which {@link com.gsl.validation.db.finance.GeneralVoucherDbValidator}'s
 * hand-written "Level 1-4" checklist has never covered.
 *
 * <p>Unlike Edit, Create needs no BEFORE snapshot resolved from the database — the document
 * didn't exist before the save, so the caller passes a manufactured empty
 * {@link com.gsl.validation.snapshot.VoucherSnapshot} as BEFORE. {@link com.gsl.validation.snapshot.DiffEngine}
 * treats a missing/empty table as zero rows, so every row genuinely present in the AFTER
 * snapshot is, by construction, an INSERT — this class only has to declare which specific rows
 * are the EXPECTED inserts (so a row that appears with the wrong content, or an entirely
 * unrelated row, still fails as "unexpectedly appeared" rather than being silently accepted).
 *
 * <p>{@code finvchmain}/{@code finpost}(main-grid)/{@code fincosttag} use a permissive
 * {@code row -> true} insert matcher — safe here specifically because BEFORE is empty (so
 * "any row appearing" already means "newly inserted for this document"), NOT because their
 * content goes unchecked: {@link com.gsl.validation.db.finance.GeneralVoucherDbValidator}'s
 * existing Level 1/3/4 checks already assert their field content and VMG/VDG row-count
 * cardinality precisely — a check this engine has no generic vocabulary for (see the plan's
 * "keep GeneralVoucherDbValidator, run alongside" decision). {@code finvchdet} gets a precise
 * per-line matcher (glcode/slcode/amount/reversal/narration), reusing the exact matchers
 * {@link VoucherEditChangeSets}/{@link GeneralVoucherEditChangeSets} already use for the same
 * {@link ExpenseIncomeDetail} DTO, so a line landing with the wrong values is still caught as a
 * distinct failure rather than accepted as "just some insert".
 */
public final class GeneralVoucherCreateChangeSets {

    private GeneralVoucherCreateChangeSets() {}

    public static ExpectedChangeSet build(DocumentContext context, MergedFinanceGeneralVoucherData data) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherCreateChangeSets.build");
        String mainEntType = context.getDocumentType().mainPostingEntType();

        // finvchmain — exactly one row per document; BEFORE is empty, so any row appearing here is
        // by definition new. See class Javadoc for why "row -> true" doesn't mean unchecked.
        builder.expectRowInsert("finvchmain", row -> true);

        // finpost — main-grid (VMG/VMP) row. Same "empty BEFORE" reasoning as finvchmain.
        builder.expectRowInsert("finpost", row -> mainEntType.equalsIgnoreCase(
                String.valueOf(DbValueNormalizer.getIgnoreCase(row, "enttype"))));

        // finvchdet — one row per Expense/Income Detail line, matched by (glcode, slcode) AND the
        // expected amount/reversal/narration (reusing VoucherEditChangeSets' matchers — see class
        // Javadoc). Each line also declares its matching finpost per-line (VDG/VDP) row insert,
        // mirroring VoucherEditChangeSets#addNewDetailLine's own shape for an Edit-time new line.
        FinGlDao glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        for (ExpenseIncomeDetail line : data.getExpenseIncomeDetailList()) {
            FinGl gl = glDao.findByGlName(line.getGeneralLedger());
            if (gl == null) {
                Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve fingl row for General Ledger '{}' "
                        + "— that finvchdet/finpost row's precise insert-match override was NOT added; if it "
                        + "genuinely inserted, it will surface as an 'unexpectedly appeared' failure instead of "
                        + "a clean, attributable pass.", line.getGeneralLedger());
                continue;
            }

            boolean isDebit = line.isDebit();
            BigDecimal amount = isDebit ? line.getExpenseDr() : line.getIncomeCr();
            long slcode = GeneralVoucherEditChangeSets.resolveSlcode(line);
            String amountColumn = isDebit ? "damount" : "camount";
            String expectedIsReversal = line.isReversalYes() ? "Y" : "N";
            String narration = line.getLedgerNarration();
            long glcode = gl.getGlcode();

            builder.expectRowInsert("finvchdet", row -> VoucherEditChangeSets.matchesKey(row, glcode, slcode)
                    && VoucherEditChangeSets.amountMatches(row, amountColumn, amount)
                    && VoucherEditChangeSets.reversalMatches(row, expectedIsReversal)
                    && (narration == null || VoucherEditChangeSets.narrationMatches(row, narration)));

            builder.expectRowInsert("finpost", row -> VoucherEditChangeSets.notMainEntType(row, mainEntType)
                    && VoucherEditChangeSets.matchesKey(row, glcode, slcode)
                    && VoucherEditChangeSets.amountMatches(row, amountColumn, amount));
        }

        // fincosttag — participates only when the document has a site-applicable Income/Expense
        // line (resolved live post-save by VoucherDocumentContextResolver into
        // context.isCostTagApplicable()). "row -> true" is safe for the same "empty BEFORE"
        // reasoning as finvchmain/finpost above; precise site-allocation-vs-amount content is
        // independently checked by GeneralVoucherDbValidator's Level 4 (FinCostTagValidator).
        if (context.isCostTagApplicable()) {
            builder.expectRowInsert("fincosttag", row -> true);
        }

        return builder.build();
    }
}
