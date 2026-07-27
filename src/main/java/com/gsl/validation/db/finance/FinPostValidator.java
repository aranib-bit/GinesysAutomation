package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.math.BigDecimal;
import java.util.List;

/**
 * Level 3 — finpost Accounting Entries Validation.
 *
 * SHARED TABLE NOTE: finpost is used by Finance and Procurement (PI, GRN).
 * Queries scoped to entno = vchno only.
 *
 * BLOCKING: aborts if GL entries are missing or not balanced (double-entry golden rule).
 * Returns voucher-side finpost rows for Levels 4a and 4b.
 */
public class FinPostValidator {

    private final FinPostDao dao;

    public FinPostValidator() {
        this.dao = DatabaseManager.getJdbi().onDemand(FinPostDao.class);
    }

    public List<FinPost> validate(VoucherDbContext context, FinVchMain header) {
        String entcode = String.valueOf(header.getVchcode());
        String voucherNo = context.getVoucherNo();

        ReportLogger.db("Level 3 — finpost GL validation for entcode [" + entcode + "]");
        com.gsl.reports.ValidationBuffer.start();

        // Existence — BLOCKING
        List<FinPost> entries = dao.findByEntCode(entcode);
        ActualResultsHolder.get().setPostEntryCount(entries.size());
        ActualResultsHolder.get().setPostEntries(entries);
        ReportLogger.trace("[finpost] fetched " + entries.size()
                + " GL entries for entcode=" + entcode);
        Assert.assertFalse(entries.isEmpty(),
                "[Level 3] No finpost rows for entcode [" + entcode
                        + "]. Posting engine may not have run.");

        Assert.assertTrue(entries.size() >= 2,
                "[Level 3] finpost has " + entries.size() + " row(s) for entcode ["
                        + entcode + "]. Minimum 2 required (debit + credit).");

        // Golden Rule — BLOCKING
        BigDecimal totalDebit  = dao.sumDebitByEntCode(entcode);
        BigDecimal totalCredit = dao.sumCreditByEntCode(entcode);
        ReportLogger.trace("[finpost] golden rule: SUM(damount)=" + totalDebit
                + " SUM(camount)=" + totalCredit
                + " balanced=" + (totalDebit.compareTo(totalCredit) == 0));

        Assert.assertEquals(totalDebit.compareTo(totalCredit), 0,
                "[Level 3] finpost NOT balanced for entcode [" + entcode
                        + "]: SUM(damount)=" + totalDebit
                        + " SUM(camount)=" + totalCredit);

        // Per-entry column checks
        SoftAssertions.assertSoftly(soft ->
                entries.forEach(e -> validatePostEntry(soft, e)));

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finpost rows", buildPostTable(entries));
        ReportLogger.infoHtml(buildLevel3Summary(entries.size(), totalDebit));
        ReportLogger.db("Level 3 — PASSED: entcode [" + entcode + "] voucherNo [" + voucherNo + "] " + entries.size() + " GL entries balanced at " + totalDebit);
        return entries;
    }

    private static String buildLevel3Summary(int entryCount, BigDecimal balanced) {
        return "<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;"
            + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>"
            + "<b>Level 3 &mdash; finpost GL balance check</b><br/>"
            + "&#10003;&nbsp; GL posting entries: <b>" + entryCount + "</b><br/>"
            + "&#10003;&nbsp; Balanced: debit = credit = <b>" + balanced + "</b><br/>"
            + "</div>";
    }

    private void validatePostEntry(SoftAssertions soft, FinPost e) {
        soft.assertThat(e.getPostcode())
                .as("[Level 3] finpost.postcode invalid")
                .isGreaterThan(0L);

        soft.assertThat(e.getGlcode())
                .as("[Level 3] finpost.glcode invalid for postcode [" + e.getPostcode() + "]")
                .isGreaterThan(0L);

        boolean hasDebit  = e.getDamount() != null && e.getDamount().compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = e.getCamount() != null && e.getCamount().compareTo(BigDecimal.ZERO) > 0;
        soft.assertThat(hasDebit || hasCredit)
                .as("[Level 3] postcode [" + e.getPostcode() + "] has zero on both sides")
                .isTrue();

        if (e.getAdjamt() != null)
            soft.assertThat(e.getAdjamt())
                    .as("[Level 3] finpost.adjamt negative for postcode [" + e.getPostcode() + "]")
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    private static String[][] buildPostTable(List<FinPost> rows) {
        String[] headers = {"postcode","entcode","entno","enttype","docno","glcode",
                            "damount","camount","adjamt","nartext"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinPost r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getPostcode()), str(r.getEntcode()), str(r.getEntno()),
                str(r.getEnttype()),  str(r.getDocno()),   str(r.getGlcode()),
                str(r.getDamount()),  str(r.getCamount()), str(r.getAdjamt()),
                str(r.getNartext())
            };
        }
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
