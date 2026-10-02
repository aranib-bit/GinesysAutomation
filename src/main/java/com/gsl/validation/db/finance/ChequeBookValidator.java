package com.gsl.validation.db.finance;

import com.gsl.database.dao.finance.FinChqDetDao;
import com.gsl.database.dao.finance.FinChqMainDao;
import com.gsl.model.finance.FinChqDet;
import com.gsl.model.finance.FinChqMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import org.assertj.core.api.SoftAssertions;

/**
 * Round 20 — shared cheque-book ({@code finchqdet}/{@code finchqmain}) linkage check, extracted
 * from {@link GeneralVoucherDbValidator}'s original Level 5 (Round 7/8) so AP's own known-cheque
 * scenarios can reuse the identical assertion logic instead of duplicating it — mirrors the exact
 * promotion precedent {@code FinVchMainBaseValidator.assertBothOrNeither} already set for
 * {@link FinPostValidator}.
 *
 * <p>Only meaningful for a KNOWN cheque number (one already registered in {@code finchqdet} —
 * resolved via {@code ChequeInstrumentResolver.resolveKnownSameBank}/{@code resolveKnownDifferentBank}).
 * A synthetic/unknown cheque number (the common case before Round 20, still the majority case
 * after it per each domain's configured ratio) has no matching {@code finchqdet} row at all — a
 * clean, expected, logged skip, not a failure.
 *
 * <p><b>AFTER-only</b>: asserts the {@code stat} transition landed at {@code 'I'} (Issued) —
 * asserting the true "was 'N' before Save" BEFORE-state would need a pre-Save capture threaded
 * into the caller's own test flow, which no caller of this method does today.
 */
public final class ChequeBookValidator {

    private ChequeBookValidator() {
    }

    /**
     * Resolves {@code finchqdet} by {@code (chqno, glcode)} and, when found, asserts:
     * <ul>
     *     <li>{@code finchqdet.stat} has transitioned to {@code 'I'} (Issued)</li>
     *     <li>{@code actualBookcode} (the caller's own already-fetched {@code finvchdet.bookcode})
     *         matches {@code finchqdet.bookcode}</li>
     *     <li>{@code finchqmain} resolves for that {@code bookcode} (existence check)</li>
     * </ul>
     * and deposits both resolved rows into {@link ActualResultsHolder} so "Actual Results" can
     * render its own dedicated finchqdet/finchqmain sections.
     *
     * @param soft            the caller's own {@link SoftAssertions} collector
     * @param finChqDetDao    DAO for {@code finchqdet}
     * @param finChqMainDao   DAO for {@code finchqmain}
     * @param chqno           the cheque number actually written to {@code finvchdet}/{@code finpost}
     * @param glcode          the Cash/Bank GL code this cheque was drawn against
     * @param actualBookcode  the caller's own already-fetched {@code finvchdet.bookcode} value
     *                        (nullable — a null actual value against a resolved cheque's real
     *                        bookcode correctly fails the match assertion below)
     * @param contextLabel    a short caller-supplied label (e.g. {@code "entcode [625502]"} or
     *                        {@code "vchcode [123] slcode [45]"}) folded into every assertion
     *                        message/log line, so a failure is traceable back to its own domain
     */
    public static void assertKnownChequeIssued(SoftAssertions soft, FinChqDetDao finChqDetDao,
            FinChqMainDao finChqMainDao, String chqno, long glcode, Long actualBookcode,
            String contextLabel) {
        if (chqno == null || chqno.isBlank()) return;

        FinChqDet chqDet = finChqDetDao.findByChqnoAndGlcode(chqno, glcode);
        if (chqDet == null) {
            ReportLogger.db("── Cheque-Book (finchqdet/finchqmain) ── chqno [" + chqno + "] glcode ["
                    + glcode + "] not found in any registered cheque book — expected for a synthetic/"
                    + "unknown cheque number; skipped, not a failure. " + contextLabel);
            return;
        }

        // Deposit the resolved rows so "Actual Results" can render its own dedicated
        // finchqdet/finchqmain sections instead of having none at all.
        ActualResultsHolder.get().setChequeDet(chqDet);
        // Round 22: also deposit the voucher's own finvchdet.bookcode value so "Actual Results"
        // can show the real cross-table comparison against finchqdet.bookcode, not just
        // finchqdet's own not-null-ness.
        ActualResultsHolder.get().setChequeDetBookcodeOnVoucher(actualBookcode);

        ReportLogger.db("── Cheque-Book (finchqdet/finchqmain) ── " + contextLabel);
        soft.assertThat(chqDet.getStat())
                .as("[Cheque-Book] finchqdet.stat for chqno [" + chqno + "] glcode [" + glcode
                        + "] — expected 'I' (Issued) after being used. " + contextLabel)
                .isEqualToIgnoringCase("I");

        // Round 22: an always-printed comparison line — the assertion below already caught a
        // mismatch, but silently (nothing was ever visible in the report unless it failed).
        // Mirrors FinVchDetApValidator.validateInstrumentDetails's own "Instrument Validation:"
        // line, printed before its own asserts, for the same chqno/chqdt fields.
        boolean bookcodeMatches = java.util.Objects.equals(actualBookcode, chqDet.getBookcode());
        ReportLogger.db("[Cheque-Book] bookcode validation — finvchdet.bookcode (actual) [" + actualBookcode
                + "] vs finchqdet.bookcode (expected) [" + chqDet.getBookcode() + "] — "
                + (bookcodeMatches ? "MATCH" : "MISMATCH") + ". " + contextLabel);

        soft.assertThat(actualBookcode)
                .as("[Cheque-Book] bookcode mismatch — expected to match finchqdet.bookcode for chqno ["
                        + chqno + "]. " + contextLabel)
                .isEqualTo(chqDet.getBookcode());

        FinChqMain chqMain = null;
        if (chqDet.getBookcode() != null) {
            chqMain = finChqMainDao.findByBookcode(chqDet.getBookcode());
            soft.assertThat(chqMain)
                    .as("[Cheque-Book] finchqmain row not found for bookcode [" + chqDet.getBookcode()
                            + "]. " + contextLabel)
                    .isNotNull();
            ActualResultsHolder.get().setChequeMain(chqMain);
        }

        // Round 23: Level 5's own green checklist box — every other level (1/2/3/4/4a) already
        // gets one (see e.g. FinCostTagValidator.buildLineSummary), this was the only level still
        // on plain ReportLogger.db(...) text lines. Additive — the plain lines above stay, this
        // just gives the same "at a glance" summary the other levels already have. Only fired here
        // in the KNOWN-cheque branch, matching every other level's own precedent of not boxing the
        // "nothing to check" (skipped/unknown-cheque) case.
        ReportLogger.infoHtml(buildLevel5Summary(chqno, actualBookcode, chqDet, chqMain, contextLabel));
    }

    /** Mirrors {@code FinCostTagValidator.buildLineSummary}'s green-checklist-box shape (Level 4)
     *  — same inline CSS, same checkmark-bullet-line structure — applied to the cheque-book
     *  linkage checks {@link #assertKnownChequeIssued} already performs. */
    private static String buildLevel5Summary(String chqno, Long actualBookcode, FinChqDet chqDet,
            FinChqMain chqMain, String contextLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;")
          .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>");
        sb.append("<b>Level 5 &mdash; cheque-book (finchqdet/finchqmain)</b> (").append(esc(contextLabel)).append(")<br/>");
        sb.append("&#10003;&nbsp; chqno: <b>").append(esc(chqno)).append("</b><br/>");
        sb.append("&#10003;&nbsp; finchqdet.stat: <b>").append(esc(chqDet.getStat())).append("</b> (expected 'I' — Issued)<br/>");
        sb.append("&#10003;&nbsp; bookcode match: finvchdet.bookcode (actual) <b>").append(esc(actualBookcode))
          .append("</b> = finchqdet.bookcode (expected) <b>").append(esc(chqDet.getBookcode())).append("</b><br/>");
        sb.append("&#10003;&nbsp; finchqmain resolved for bookcode <b>").append(esc(chqDet.getBookcode()))
          .append("</b>: <b>").append(chqMain != null ? "found" : "not found").append("</b><br/>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String esc(Object o) {
        if (o == null) return "&mdash;";
        return o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
