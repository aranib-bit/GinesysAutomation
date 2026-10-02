package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinTag;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ExtentLogger;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Level 4b — adjamt Cascade Validation (Before / After State).
 *
 * Core assertion:  adjamt_after - adjamt_before == fintag.amount
 *
 * SHARED TABLE NOTE: finpost.adjamt is updated by Finance AND Procurement settlements.
 * The before/after snapshot isolates only the delta caused by THIS voucher via fintag.amount,
 * making the check module-agnostic regardless of other module activity.
 *
 * NON-BLOCKING: all delta failures collected and reported together.
 */
public class AdjAmtCascadeValidator {

    private final FinTagDao finTagDao;

    public AdjAmtCascadeValidator() {
        this.finTagDao = DatabaseManager.getJdbi().onDemand(FinTagDao.class);
    }

    /**
     * For each invoice in {@code context}'s pre/post snapshots, asserts {@code adjamt} didn't
     * decrease, its delta matches the amount tagged in {@code fintag} for this voucher, and it
     * never exceeds the invoice's own {@code camount}. Skips entirely when either snapshot is
     * empty (ADVANCE_PAYMENT — no invoice rows to compare). All failures are collected via
     * {@link SoftAssert} and reported together.
     */
    public void validate(VoucherDbContext context,
                         FinVchMain header,
                         List<FinPost> postEntries) {

        String voucherNo = context.getVoucherNo();
        Map<String, FinPost> preState  = context.getPreState();
        Map<String, FinPost> postState = context.getPostState();

        if (preState.isEmpty() || postState.isEmpty()) {
            ExtentLogger.info("Level 4b — skipped [" + voucherNo + "]: no snapshot (ADVANCE_PAYMENT)");
            return;
        }

        ExtentLogger.info("Level 4b — adjamt cascade for voucherNo [" + voucherNo + "] " + preState.size() + " invoice(s)");

        SoftAssert soft = new SoftAssert();

        for (Map.Entry<String, FinPost> entry : preState.entrySet()) {
            String  invoiceNo = entry.getKey();
            FinPost pre       = entry.getValue();
            FinPost post      = postState.get(invoiceNo);

            soft.assertNotNull(pre,
                    "[Level 4b] Pre-state finpost null for invoice [" + invoiceNo + "]");
            soft.assertNotNull(post,
                    "[Level 4b] Post-state finpost null for invoice [" + invoiceNo
                            + "]. Check async posting.");

            if (pre == null || post == null) continue;

            BigDecimal adjBefore = pre.getAdjamt()  != null ? pre.getAdjamt()  : BigDecimal.ZERO;
            BigDecimal adjAfter  = post.getAdjamt() != null ? post.getAdjamt() : BigDecimal.ZERO;

            // adjamt must not decrease
            soft.assertTrue(adjAfter.compareTo(adjBefore) >= 0,
                    "[Level 4b] adjamt DECREASED for invoice [" + invoiceNo
                            + "]: before=" + adjBefore + " after=" + adjAfter);

            // Expected delta from fintag
            BigDecimal expectedDelta = resolveTaggedAmount(postEntries, post);
            if (expectedDelta.compareTo(BigDecimal.ZERO) == 0) {
                ExtentLogger.info("Level 4b — no fintag.amount resolved for invoice [" + invoiceNo + "]");
                continue;
            }

            BigDecimal actualDelta = adjAfter.subtract(adjBefore);
            soft.assertEquals(actualDelta.compareTo(expectedDelta), 0,
                    "[Level 4b] adjamt delta mismatch for invoice [" + invoiceNo
                            + "]: expected=" + expectedDelta
                            + " actual=" + actualDelta
                            + " (adjamt: " + adjBefore + " → " + adjAfter + ")");

            // Over-application guard
            BigDecimal original = post.getCamount() != null ? post.getCamount() : BigDecimal.ZERO;
            soft.assertTrue(adjAfter.compareTo(original) <= 0,
                    "[Level 4b] adjamt EXCEEDS camount for invoice [" + invoiceNo
                            + "]: adjamt_after=" + adjAfter + " camount=" + original);

            BigDecimal outstanding = original.subtract(adjAfter);
            if (outstanding.compareTo(BigDecimal.ZERO) == 0)
                ExtentLogger.info("Level 4b — [" + invoiceNo + "] FULLY SETTLED adjamt=" + adjAfter);
            else
                ExtentLogger.info("Level 4b — [" + invoiceNo + "] PARTIAL adjamt=" + adjAfter + " outstanding=" + outstanding);
        }

        soft.assertAll();
        ExtentLogger.info("Level 4b — PASSED for voucherNo [" + voucherNo + "]");
    }

    /** Sums {@code fintag.amount} linking any of {@code voucherEntries}' postcodes to {@code invoiceRow}'s own — the expected adjamt delta for this invoice. */
    private BigDecimal resolveTaggedAmount(List<FinPost> voucherEntries, FinPost invoiceRow) {
        for (FinPost ve : voucherEntries) {
            List<FinTag> tags = finTagDao.findByPostCode1AndPostCode2(
                    ve.getPostcode(), invoiceRow.getPostcode());
            if (!tags.isEmpty())
                return tags.stream().map(FinTag::getAmount)
                        .filter(a -> a != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return BigDecimal.ZERO;
    }
}
