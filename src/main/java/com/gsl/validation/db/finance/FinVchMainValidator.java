package com.gsl.validation.db.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Level 1 — finvchmain Header Validation.
 * Finance module only (finvchmain is not shared).
 *
 * BLOCKING: if voucher row not found, all downstream levels are aborted.
 * Returns the validated FinVchMain for use by Levels 2 and 3.
 */
public class FinVchMainValidator {

    private final FinVchMainDao dao;

    public FinVchMainValidator() {
        this.dao = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class);
    }

    public FinVchMain validate(VoucherDbContext context) {
        String schemeDocno = context.getVoucherNo();
        System.out.println("scheme_docno (voucherNo) is " + schemeDocno);
        MergedFinanceArVoucherData data = context.getTestData();
        DocumentInformation doc = data.getDocumentInformation();

        ReportLogger.info("Level 1 — finvchmain for scheme_docno [" + schemeDocno + "]");

        // Step 1: count check — BLOCKING
        int count = dao.countBySchemeDocno(schemeDocno);
        Assert.assertEquals(count, 1,
                "[Level 1] Expected exactly 1 finvchmain row for scheme_docno [" + schemeDocno + "] but found [" + count + "]");

        // Step 2: fetch full record by scheme_docno — BLOCKING
        Optional<FinVchMain> result = dao.findBySchemeDocno(schemeDocno);
        Assert.assertTrue(result.isPresent(),
                "[Level 1] finvchmain NOT FOUND for scheme_docno [" + schemeDocno + "]");

        FinVchMain h = result.get();

        // Step 3: confirm extracted vchcode is valid — BLOCKING
        Assert.assertTrue(h.getVchcode() > 0,
                "[Level 1] finvchmain.vchcode invalid for scheme_docno [" + schemeDocno + "]");

        ReportLogger.info("Level 1 — scheme_docno [" + schemeDocno + "] resolved to vchcode [" + h.getVchcode() + "]");

        validateHeaderFields(h, doc, context);

        ReportLogger.info("<b>finvchmain row:</b>");
        ReportLogger.infoTable(buildMainTable(h));

        ReportLogger.info("Level 1 — PASSED: scheme_docno [" + schemeDocno + "] vchcode [" + h.getVchcode() + "]");
        return h;
    }

    private void validateHeaderFields(FinVchMain h, DocumentInformation doc, VoucherDbContext context) {
        Long diffSeconds = null;
        long toleranceSeconds = 0;
        if (context.getSaveStartTime() != null && h.getCreationDt() != null) {
            toleranceSeconds = ConfigFactory.getConfig().dbCreationTolerance();
            diffSeconds = Math.abs(ChronoUnit.SECONDS.between(context.getSaveStartTime(), h.getCreationDt()));
            ReportLogger.info("[Level 1] creation_dt tolerance:"
                    + " saveStartTime=" + context.getSaveStartTime()
                    + " | creation_dt=" + h.getCreationDt()
                    + " | diff=" + diffSeconds + "s"
                    + " | tolerance=" + toleranceSeconds + "s");
        }
        final Long finalDiff = diffSeconds;
        final long finalTolerance = toleranceSeconds;

        SoftAssertions.assertSoftly(soft -> {
            if (doc.getDocumentdate() != null)
                soft.assertThat(h.getVchdt())
                        .as("[Level 1] finvchmain.vchdt != input documentdate")
                        .isEqualTo(doc.getDocumentdate());

            if (doc.getReference() != null && !doc.getReference().isBlank())
                soft.assertThat(h.getRefno())
                        .as("[Level 1] finvchmain.refno != input reference")
                        .isEqualTo(doc.getReference());

            if (doc.getRemarks() != null && !doc.getRemarks().isBlank())
                soft.assertThat(h.getNartext())
                        .as("[Level 1] finvchmain.nartext != input remarks")
                        .isEqualTo(doc.getRemarks());

            soft.assertThat(h.getDamount())
                    .as("[Level 1] finvchmain.damount is null")
                    .isNotNull();

            if (h.getEntrySource() != null)
                soft.assertThat(h.getEntrySource())
                        .as("[Level 1] Unexpected entry_source [" + h.getEntrySource() + "]")
                        .matches("(?i)WEB|API");

            soft.assertThat(h.getCreationDt())
                    .as("[Level 1] finvchmain.creation_dt is null")
                    .isNotNull();

            if (finalDiff != null)
                soft.assertThat(finalDiff)
                        .as("[Level 1] finvchmain.creation_dt out of tolerance:"
                                + " diff=" + finalDiff + "s"
                                + ", tolerance=" + finalTolerance + "s"
                                + ", saveStartTime=" + context.getSaveStartTime()
                                + ", creation_dt=" + h.getCreationDt())
                        .isLessThanOrEqualTo(finalTolerance);
        });
    }

    private static String[][] buildMainTable(FinVchMain r) {
        String[] headers = {"vchcode", "vchno", "scheme_docno", "vchdt", "vchtype",
                            "refno", "nartext", "glcode", "damount", "camount", "siteid",
                            "ycode", "entry_source", "status", "release_status", "creation_dt"};
        String[][] table = new String[2][headers.length];
        table[0] = headers;
        table[1] = new String[]{
            str(r.getVchcode()),      str(r.getVchno()),        str(r.getSchemeDocno()),
            str(r.getVchdt()),        str(r.getVchtype()),      str(r.getRefno()),
            str(r.getNartext()),      str(r.getGlcode()),       str(r.getDamount()),
            str(r.getCamount()),      str(r.getSiteid()),       str(r.getYcode()),
            str(r.getEntrySource()),  str(r.getStatus()),       str(r.getReleaseStatus()),
            str(r.getCreationDt())
        };
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
