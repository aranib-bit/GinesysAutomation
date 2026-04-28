package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.DbQueryLogger;
import com.gsl.reports.ReportLogger;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;

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

        ReportLogger.info("Level 3 — finpost GL validation for entcode [" + entcode + "]");

        // Existence — BLOCKING
        DbQueryLogger.log("SELECT * FROM finpost WHERE entcode = :entcode", "entcode", entcode);
        List<FinPost> entries = dao.findByEntCode(entcode);
        Assert.assertFalse(entries.isEmpty(),
                "[Level 3] No finpost rows for entcode [" + entcode
                        + "]. Posting engine may not have run.");

        Assert.assertTrue(entries.size() >= 2,
                "[Level 3] finpost has " + entries.size() + " row(s) for entcode ["
                        + entcode + "]. Minimum 2 required (debit + credit).");

        // Golden Rule — BLOCKING
        DbQueryLogger.log("SELECT COALESCE(SUM(damount), 0) FROM finpost WHERE entcode = :entcode",
                "entcode", entcode);
        BigDecimal totalDebit  = dao.sumDebitByEntCode(entcode);
        DbQueryLogger.log("SELECT COALESCE(SUM(camount), 0) FROM finpost WHERE entcode = :entcode",
                "entcode", entcode);
        BigDecimal totalCredit = dao.sumCreditByEntCode(entcode);

        Assert.assertEquals(totalDebit.compareTo(totalCredit), 0,
                "[Level 3] finpost NOT balanced for entcode [" + entcode
                        + "]: SUM(damount)=" + totalDebit
                        + " SUM(camount)=" + totalCredit);

        SoftAssert soft = new SoftAssert();

        // Per-entry column checks
        for (FinPost e : entries) {
            soft.assertTrue(e.getPostcode() > 0, "[Level 3] finpost.postcode invalid");
            soft.assertTrue(e.getGlcode()  > 0,
                    "[Level 3] finpost.glcode invalid for postcode [" + e.getPostcode() + "]");

            boolean hasDebit  = e.getDamount() != null
                                && e.getDamount().compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = e.getCamount() != null
                                && e.getCamount().compareTo(BigDecimal.ZERO) > 0;
            soft.assertTrue(hasDebit || hasCredit,
                    "[Level 3] postcode [" + e.getPostcode() + "] has zero on both sides");

            if (e.getAdjamt() != null)
                soft.assertTrue(e.getAdjamt().compareTo(BigDecimal.ZERO) >= 0,
                        "[Level 3] finpost.adjamt negative for postcode [" + e.getPostcode() + "]");
        }

        ReportLogger.info("<b>finpost rows:</b>");
        ReportLogger.infoTable(buildPostTable(entries));

        soft.assertAll();
        ReportLogger.info("Level 3 — PASSED: entcode [" + entcode + "] voucherNo [" + voucherNo + "] " + entries.size() + " GL entries balanced at " + totalDebit);
        return entries;
    }

    private static String[][] buildPostTable(List<FinPost> rows) {
        String[] headers = {"postcode","entcode","entno","doctype","docno","glcode",
                            "damount","camount","adjamt","narration"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinPost r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getPostcode()), str(r.getEntcode()), str(r.getEntno()),
                str(r.getDoctype()),  str(r.getDocno()),   str(r.getGlcode()),
                str(r.getDamount()),  str(r.getCamount()), str(r.getAdjamt()),
                str(r.getNarration())
            };
        }
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
