package com.gsl.validation.db.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.config.FinanceConfig;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.adm.AdmYearDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinNarDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.CashBankType;
import com.gsl.enums.VoucherType;
import com.gsl.model.adm.AdmYear;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinNar;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final FinGlDao glDao;
    private final FinNarDao narDao;
    private final AdmYearDao yearDao;

    protected FinVchMainBaseValidator() {
        this.dao = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class);
        this.glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        this.narDao = DatabaseManager.getJdbi().onDemand(FinNarDao.class);
        this.yearDao = DatabaseManager.getJdbi().onDemand(AdmYearDao.class);
    }

    // ── Shared blocking fetch ─────────────────────────────────────────────────

    protected FinVchMain fetchAndBlock(String schemeDocno) {
        ReportLogger.db("Level 1 — finvchmain for scheme_docno [" + schemeDocno + "]");
        com.gsl.reports.ValidationBuffer.start();
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

        ReportLogger.db("Level 1 — scheme_docno [" + schemeDocno
                + "] resolved to vchcode [" + h.getVchcode() + "]");
        return h;
    }

    // ── Shared header-field assertions ────────────────────────────────────────

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Collects Level-1 header field assertions into the caller-supplied {@code soft} instance.
     * The caller is responsible for calling {@code soft.assertAll()} when all levels are done,
     * so that a nartext/vchdt mismatch here does NOT block Levels 2–4 from running.
     *
     * @return pre-built HTML summary to be logged after the finvchmain table
     */
    protected String validateCommonHeaderFields(FinVchMain h, VoucherDocumentHeader doc,
                                                LocalDateTime saveStartTime,
                                                VoucherType expectedType,
                                                SoftAssertions soft) {
        soft.assertThat(VoucherType.fromCode(h.getVchtype()))
                .as("[Level 1] finvchmain.vchtype mismatch — expected " + expectedType
                        + " (" + expectedType.code() + ") but got [" + h.getVchtype() + "]")
                .isEqualTo(expectedType);

        FinGl gl = glDao.findByGlCode(h.getGlcode());
        Optional<FinNar> nar = narDao.findByGlcodeAndNartext(h.getGlcode(), h.getNartext());
        soft.assertThat(nar)
                .as("[Level 1] nartext [" + h.getNartext() + "] is not a registered finnar entry for glcode ["
                        + h.getGlcode() + "]")
                .isPresent();

        if (gl != null) {
            ReportLogger.infoLabeledTable("fingl", buildGlTable(gl));
        }
        ReportLogger.infoLabeledTable("finnar", buildNarTable(nar, h.getGlcode()));

        Optional<AdmYear> year = yearDao.findByYcode(h.getYcode());
        soft.assertThat(year)
                .as("[Level 1] finvchmain.ycode [" + h.getYcode() + "] has no admyear row")
                .isPresent();
        if (year.isPresent()) {
            AdmYear y = year.get();
            LocalDateTime vchdtStart = h.getVchdt().atStartOfDay();
            boolean inRange = !vchdtStart.isBefore(y.getDtfr()) && !vchdtStart.isAfter(y.getDtto());
            soft.assertThat(inRange)
                    .as("[Level 1] finvchmain.vchdt [" + h.getVchdt() + "] not within admyear " + y.getYname()
                            + " range [" + y.getDtfr() + " – " + y.getDtto() + "] for ycode [" + h.getYcode() + "]")
                    .isTrue();
        }
        ReportLogger.infoLabeledTable("admyear", buildYearTable(year, h.getYcode()));

        Long diffSeconds = null;
        long toleranceSeconds = 0;
        if (saveStartTime != null && h.getCreationDt() != null) {
            toleranceSeconds = ConfigFactory.getFinanceConfig().dbCreationTolerance();
            diffSeconds = Math.abs(ChronoUnit.SECONDS.between(saveStartTime, h.getCreationDt()));
            ActualResultsHolder.get().setSaveStartTime(saveStartTime);
            ActualResultsHolder.get().setCreationDtDelaySeconds(diffSeconds);
            ActualResultsHolder.get().setCreationDtToleranceSeconds(toleranceSeconds);
            String result = diffSeconds <= toleranceSeconds ? "&#10003; within tolerance" : "&#10007; exceeds tolerance";
            ReportLogger.db("[Level 1] creation_dt: saved at " + saveStartTime.format(TIME_FMT)
                    + ", DB recorded at " + h.getCreationDt().format(TIME_FMT)
                    + " (" + diffSeconds + "s delay — " + result + " of " + toleranceSeconds + "s)");
        }
        final Long finalDiff = diffSeconds;
        final long finalTolerance = toleranceSeconds;

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

        return buildLevel1Summary(h, doc, saveStartTime, diffSeconds, toleranceSeconds, expectedType, gl, nar, year);
    }

    private static String buildLevel1Summary(FinVchMain h, VoucherDocumentHeader doc,
                                              LocalDateTime saveStartTime,
                                              Long diffSeconds, long toleranceSeconds,
                                              VoucherType expectedType, FinGl gl, Optional<FinNar> nar,
                                              Optional<AdmYear> year) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;")
          .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>");
        sb.append("<b>Level 1 &mdash; finvchmain field checks</b><br/>");
        sb.append("&#10003;&nbsp; vchtype: <b>").append(esc(h.getVchtype()))
          .append(" (").append(expectedType.label()).append(")</b><br/>");
        if (gl != null) {
            String glType = CashBankType.fromSrctype(gl.getSrctype()).label();
            sb.append("&#10003;&nbsp; glcode: <b>").append(str(h.getGlcode())).append(" &rarr; ")
              .append(esc(gl.getGlname())).append("</b> (").append(glType).append(" GL)<br/>");
        }
        if (nar.isPresent())
            sb.append("&#10003;&nbsp; finnar: narration registered (narcode=")
              .append(str(nar.get().getNarcode())).append(") for glcode ")
              .append(str(h.getGlcode())).append("<br/>");
        else
            sb.append("&#10007;&nbsp; finnar: nartext NOT registered for glcode ")
              .append(str(h.getGlcode())).append("<br/>");
        if (year.isPresent()) {
            AdmYear y = year.get();
            boolean inRange = h.getVchdt() != null
                    && !h.getVchdt().atStartOfDay().isBefore(y.getDtfr())
                    && !h.getVchdt().atStartOfDay().isAfter(y.getDtto());
            sb.append(inRange ? "&#10003;&nbsp; admyear: " : "&#10007;&nbsp; admyear: ")
              .append("vchdt within <b>").append(esc(y.getYname())).append("</b> range (")
              .append(y.getDtfr().format(DATE_FMT)).append(" &ndash; ")
              .append(y.getDtto().format(DATE_FMT)).append(")<br/>");
        } else {
            sb.append("&#10007;&nbsp; admyear: no fiscal year row for ycode ")
              .append(str(h.getYcode())).append("<br/>");
        }
        if (doc.getDocumentdate() != null && h.getVchdt() != null)
            sb.append("&#10003;&nbsp; vchdt: <b>")
              .append(h.getVchdt().format(DATE_FMT))
              .append("</b><br/>");
        if (doc.getReference() != null && !doc.getReference().isBlank())
            sb.append("&#10003;&nbsp; refno: <b>").append(esc(h.getRefno())).append("</b><br/>");
        if (doc.getRemarks() != null && !doc.getRemarks().isBlank())
            sb.append("&#10003;&nbsp; nartext: <b>").append(esc(h.getNartext())).append("</b><br/>");
        if (h.getEntrySource() != null)
            sb.append("&#10003;&nbsp; entry_source: ").append(esc(h.getEntrySource())).append("<br/>");
        if (diffSeconds != null && saveStartTime != null && h.getCreationDt() != null) {
            String result = diffSeconds <= toleranceSeconds ? "&#10003; within tolerance" : "&#10007; exceeds tolerance";
            sb.append("&#10003;&nbsp; creation_dt: saved at <b>")
              .append(saveStartTime.format(TIME_FMT))
              .append("</b> from UI, DB recorded at <b>")
              .append(h.getCreationDt().format(TIME_FMT))
              .append("</b> (").append(diffSeconds).append("s delay &mdash; ")
              .append(result).append(" of ").append(toleranceSeconds).append("s)<br/>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String esc(Object o) {
        if (o == null) return "&mdash;";
        return o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Shared display table ──────────────────────────────────────────────────

    protected static String[][] buildMainTable(FinVchMain r) {
        String[] headers = {"vchcode", "vchno", "scheme_docno", "vchdt", "vchtype",
                            "refno", "nartext", "glcode", "damount", "camount", "admsite_code_owner",
                            "ycode", "entry_source", "status", "release_status", "creation_dt"};
        String[][] table = new String[2][headers.length];
        table[0] = headers;
        table[1] = new String[]{
            str(r.getVchcode()),      str(r.getVchno()),        str(r.getSchemeDocno()),
            str(r.getVchdt()),        str(r.getVchtype()),      str(r.getRefno()),
            str(r.getNartext()),      str(r.getGlcode()),       str(r.getDamount()),
            str(r.getCamount()),      str(r.getAdmsiteCodeOwner()), str(r.getYcode()),
            str(r.getEntrySource()),  str(r.getStatus()),       str(r.getReleaseStatus()),
            str(r.getCreationDt())
        };
        return table;
    }

    protected static String[][] buildGlTable(FinGl r) {
        String[] headers = {"glcode", "glname", "srctype"};
        return new String[][]{
            headers,
            {str(r.getGlcode()), str(r.getGlname()), str(r.getSrctype())}
        };
    }

    protected static String[][] buildNarTable(Optional<FinNar> r, long glcode) {
        String[] headers = {"narcode", "nartext", "glcode"};
        if (r.isEmpty())
            return new String[][]{headers, {"—", "not registered", str(glcode)}};
        FinNar n = r.get();
        return new String[][]{
            headers,
            {str(n.getNarcode()), str(n.getNartext()), str(n.getGlcode())}
        };
    }

    protected static String[][] buildYearTable(Optional<AdmYear> r, long ycode) {
        String[] headers = {"ycode", "yname", "dtfr", "dtto"};
        if (r.isEmpty())
            return new String[][]{headers, {str(ycode), "not found", "—", "—"}};
        AdmYear y = r.get();
        return new String[][]{
            headers,
            {str(y.getYcode()), str(y.getYname()), str(y.getDtfr()), str(y.getDtto())}
        };
    }

    protected static String str(Object o) { return o == null ? "—" : o.toString(); }
    protected static String str(long v)   { return String.valueOf(v); }
}
