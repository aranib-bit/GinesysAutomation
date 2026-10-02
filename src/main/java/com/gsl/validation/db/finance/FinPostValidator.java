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

    /**
     * Blocking: asserts at least 2 {@code finpost} rows exist for {@code header}'s vchcode
     * (entcode) and that {@code SUM(damount) == SUM(camount)} (double-entry golden rule), then
     * soft-asserts each row's own column sanity via {@link #validatePostEntry}.
     *
     * @return the voucher's own {@code finpost} rows, for reuse by Levels 4a/4b
     */
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
        boolean isPublicApiEntry = header.getEntrySource() != null
                && header.getEntrySource().equalsIgnoreCase("API");
        SoftAssertions.assertSoftly(soft -> entries.forEach(e -> {
            validatePostEntry(soft, e);
            // Round 7: last_access_time/last_access_ecode and release_time/release_ecode are
            // each a "both or neither" pair on finpost too (same physical column pairing/
            // reasoning as finvchmain's own — see FinVchMainBaseValidator), reusing the
            // promoted shared utility rather than duplicating the logic here.
            FinVchMainBaseValidator.assertBothOrNeither(soft, "finpost", "release_time/release_ecode",
                    e.getReleaseTime() != null, e.getReleaseEcode() != null && e.getReleaseEcode() > 0);
            FinVchMainBaseValidator.assertBothOrNeither(soft, "finpost", "last_access_time/last_access_ecode",
                    e.getLastAccessTime() != null, e.getLastAccessEcode() != null && e.getLastAccessEcode() > 0);

            // Round 23: intg_code is populated only for a PUBLICAPI-created entry — WEBAPI hits
            // the SAME internal endpoint the web app itself calls, so entry_source is "WEB" for
            // both a real UI click and WEBAPI test data; only PUBLICAPI shows "API" (see
            // FinPost.intgCode's own Javadoc). Domain-agnostic (unlike GeneralVoucherCreateChangeSets'
            // fixed "always blank" assumption, which only holds because GV never drives tests
            // through PUBLICAPI) — this is the one place that actually branches on entry_source,
            // for whichever domain/flow genuinely does use PUBLICAPI.
            boolean hasIntgCode = e.getIntgCode() != null && !e.getIntgCode().isBlank();
            if (isPublicApiEntry) {
                soft.assertThat(hasIntgCode)
                        .as("[Level 3] finpost.intg_code should be populated for postcode ["
                                + e.getPostcode() + "] — entry_source is 'API' (PUBLICAPI)")
                        .isTrue();
            } else {
                soft.assertThat(hasIntgCode)
                        .as("[Level 3] finpost.intg_code should be blank for postcode ["
                                + e.getPostcode() + "] — entry_source is '" + header.getEntrySource()
                                + "', not a PUBLICAPI entry")
                        .isFalse();
            }
        }));

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finpost rows", buildPostTable(entries));
        ReportLogger.infoHtml(buildLevel3Summary(entries.size(), totalDebit));
        ReportLogger.db("Level 3 — PASSED: entcode [" + entcode + "] voucherNo [" + voucherNo + "] " + entries.size() + " GL entries balanced at " + totalDebit);
        return entries;
    }

    /** Builds the Level 3 green-checklist HTML box (entry count, balanced total). */
    private static String buildLevel3Summary(int entryCount, BigDecimal balanced) {
        return "<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;"
            + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>"
            + "<b>Level 3 &mdash; finpost GL balance check</b><br/>"
            + "&#10003;&nbsp; GL posting entries: <b>" + entryCount + "</b><br/>"
            + "&#10003;&nbsp; Balanced: debit = credit = <b>" + balanced + "</b><br/>"
            + "</div>";
    }

    /** Per-row column sanity checks: postcode/glcode/admou_code populated, not zero on both debit and credit, non-negative adjamt, the release/last-access "both or neither" pairs, intg_code presence matching entry_source, and the cash-discount trio's populated-together consistency. */
    private void validatePostEntry(SoftAssertions soft, FinPost e) {
        // postcode is a per-row surrogate PK — deliberately excluded from finpost's business key
        // in VoucherPersistenceMapFactory ("a per-row surrogate with no business meaning") and
        // legitimately different for every row, even within the same document. Only ever used
        // here for existence/sanity (>0) and as a row-identifying label in other assertions'
        // messages below — never for an equality/expected-value check.
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

        // Round 23 (Item I follow-up): admou_code was previously only displayed in buildPostTable,
        // never actually asserted — a simple existence check, same shape as postcode/glcode above.
        soft.assertThat(e.getAdmouCode())
                .as("[Level 3] finpost.admou_code missing/invalid for postcode [" + e.getPostcode() + "]")
                .isNotNull()
                .isGreaterThan(0L);

        // Round 23: cash-discount trio consistency — domain-agnostic (unlike
        // GeneralVoucherCreateChangeSets' fixed "always N/blank" assumption, which only holds for
        // GV). "Y" means cash discount genuinely applies (an AP Voucher concept) and both
        // percentage/period must be populated; "N"/blank means neither should be.
        boolean cashDiscountApplicable = "Y".equalsIgnoreCase(e.getCashDiscountApplicable());
        boolean hasPercentage = e.getCashDiscountPercentage() != null;
        boolean hasPeriod = e.getCashDiscountPeriod() != null;
        soft.assertThat(hasPercentage)
                .as("[Level 3] finpost.cash_discount_percentage should be "
                        + (cashDiscountApplicable ? "populated" : "blank") + " for postcode ["
                        + e.getPostcode() + "] — cash_discount_applicable=[" + e.getCashDiscountApplicable() + "]")
                .isEqualTo(cashDiscountApplicable);
        soft.assertThat(hasPeriod)
                .as("[Level 3] finpost.cash_discount_period should be "
                        + (cashDiscountApplicable ? "populated" : "blank") + " for postcode ["
                        + e.getPostcode() + "] — cash_discount_applicable=[" + e.getCashDiscountApplicable() + "]")
                .isEqualTo(cashDiscountApplicable);
    }

    /** Builds the {@code finpost} rows' display table for the Extent Report. */
    private static String[][] buildPostTable(List<FinPost> rows) {
        // Item I (Round 9): admou_code was asserted only in the DiffEngine/changeset layer.
        // Round 23: cash-discount trio + intg_code added — previously invisible here entirely
        // (silently dropped by the bean mapper, see FinPost.java's own Javadoc on these fields).
        String[] headers = {"postcode","entcode","entno","enttype","docno","glcode",
                            "damount","camount","adjamt","admou_code","nartext",
                            "cash_discount_applicable","cash_discount_percentage","cash_discount_period",
                            "intg_code"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinPost r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getPostcode()), str(r.getEntcode()), str(r.getEntno()),
                str(r.getEnttype()),  str(r.getDocno()),   str(r.getGlcode()),
                str(r.getDamount()),  str(r.getCamount()), str(r.getAdjamt()),
                str(r.getAdmouCode()),str(r.getNartext()),
                str(r.getCashDiscountApplicable()), str(r.getCashDiscountPercentage()),
                str(r.getCashDiscountPeriod()), str(r.getIntgCode())
            };
        }
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
