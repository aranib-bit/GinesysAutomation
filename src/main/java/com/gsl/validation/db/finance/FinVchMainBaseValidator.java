package com.gsl.validation.db.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Shared Level-1 logic for all Finance voucher types (AR, AP, GV, Contra).
 *
 * Blocking steps (scheme_docno lookup, vchcode validity) and common header
 * field assertions (vchdt, refno, nartext, entry_source, creation_dt) live here.
 * Each concrete subclass adds only the type-specific amount check:
 *   AR/GV/Contra → damount != null
 *   AP            → camount != null
 */
public abstract class FinVchMainBaseValidator {

    protected final FinVchMainDao dao;

    protected FinVchMainBaseValidator() {
        this.dao = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class);
    }

    // ── Shared blocking fetch ─────────────────────────────────────────────────

    protected FinVchMain fetchAndBlock(String schemeDocno) {
        ReportLogger.info("Level 1 — finvchmain for scheme_docno [" + schemeDocno + "]");
        System.out.println("scheme_docno (voucherNo) is " + schemeDocno);

        int count = dao.countBySchemeDocno(schemeDocno);
        Assert.assertEquals(count, 1,
                "[Level 1] Expected exactly 1 finvchmain row for scheme_docno ["
                        + schemeDocno + "] but found [" + count + "]");

        Optional<FinVchMain> result = dao.findBySchemeDocno(schemeDocno);
        Assert.assertTrue(result.isPresent(),
                "[Level 1] finvchmain NOT FOUND for scheme_docno [" + schemeDocno + "]");

        FinVchMain h = result.get();
        Assert.assertTrue(h.getVchcode() > 0,
                "[Level 1] finvchmain.vchcode invalid for scheme_docno [" + schemeDocno + "]");

        ReportLogger.info("Level 1 — scheme_docno [" + schemeDocno
                + "] resolved to vchcode [" + h.getVchcode() + "]");
        return h;
    }

    // ── Shared header-field assertions ────────────────────────────────────────

    protected void validateCommonHeaderFields(FinVchMain h, VoucherDocumentHeader doc,
                                               LocalDateTime saveStartTime) {
        Long diffSeconds = null;
        long toleranceSeconds = 0;
        if (saveStartTime != null && h.getCreationDt() != null) {
            toleranceSeconds = ConfigFactory.getConfig().dbCreationTolerance();
            diffSeconds = Math.abs(ChronoUnit.SECONDS.between(saveStartTime, h.getCreationDt()));
            ReportLogger.info("[Level 1] creation_dt tolerance:"
                    + " saveStartTime=" + saveStartTime
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
                                + ", saveStartTime=" + saveStartTime
                                + ", creation_dt=" + h.getCreationDt())
                        .isLessThanOrEqualTo(finalTolerance);
        });
    }

    // ── Shared display table ──────────────────────────────────────────────────

    protected static String[][] buildMainTable(FinVchMain r) {
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

    protected static String str(Object o) { return o == null ? "—" : o.toString(); }
    protected static String str(long v)   { return String.valueOf(v); }
}
