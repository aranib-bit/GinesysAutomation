package com.gsl.reports;

import com.gsl.database.DatabaseManager;
import com.gsl.database.JdbiSqlLogger;
import com.gsl.reports.contract.TestResultsAccumulator;
import com.gsl.reports.contract.TestResultsRenderer;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.enums.Finance.Operations.APVoucher.OnAccountValues;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders the "Actual Results" table from {@link ActualResultsHolder} to the Extent Report.
 *
 * Called by TestListener in both onTestSuccess and onTestFailure — the table therefore
 * appears in every test regardless of outcome. Null/unset fields render as "— (not reached)".
 */
public final class ActualResultsLogger {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ActualResultsLogger() {}

    /**
     * Returns a {@link TestResultsRenderer} singleton backed by {@link #flushToReport()}.
     * Used by {@link FinanceDomainReporter} to flush via the domain-neutral contract.
     * The {@code accumulator} argument is accepted but not used — {@link #flushToReport()}
     * fetches the current thread's {@link ActualResultsHolder} from its own ThreadLocal.
     */
    public static TestResultsRenderer renderer() {
        return acc -> flushToReport();
    }

    public static void flushToReport() {
        ActualResultsHolder h = ActualResultsHolder.get();
        JdbiSqlLogger.startCapturing();
        try {
            StepExecutor.step("Actual Results", () -> {
                if (h.isNegativeScenario()) {
                    renderNegativeTable(h);
                } else {
                    renderPositiveHtml(h);
                }
            });
        } finally {
            JdbiSqlLogger.stopCapturing();
        }
    }

    // ─── Positive HTML ────────────────────────────────────────────────────────

    private static void renderPositiveHtml(ActualResultsHolder h) {
        try {
            ExtentLogger.infoHtml(buildPositiveHtml(h));
        } catch (Exception e) {
            Log.warn("[ActualResultsLogger] Failed to build positive table: {}", e.getMessage());
            ExtentLogger.info("(Actual Results table could not be rendered: " + e.getMessage() + ")");
        }
    }

    private static String buildPositiveHtml(ActualResultsHolder h) {
        FinVchMain header = h.getHeader();

        String siteActual = h.getUiOwnerSite() != null ? h.getUiOwnerSite() : NOT_REACHED;
        String glActual   = h.getUiCashBank()  != null ? h.getUiCashBank()  : NOT_REACHED;

        String[] exp = extractExpected(h);
        String expOwnerSite      = exp[0];
        String expCashBank       = exp[1];
        String expDocDate        = exp[2];
        String expReference      = exp[3];
        String expRemarks        = exp[4];
        String expSlCount        = exp[5];
        String expSelectDocEntry = exp[6];

        String docNoActual   = ns(h.getVoucherNo());
        String docDateActual = header != null && header.getVchdt() != null
                ? header.getVchdt().format(DATE_FMT) : NOT_REACHED;
        String refActual     = header != null ? ns(header.getRefno())   : NOT_REACHED;
        String remarkActual  = header != null ? ns(header.getNartext()) : NOT_REACHED;
        String vchcodeActual = header != null
                ? "1 record found (vchcode=" + header.getVchcode() + ")" : NOT_REACHED;
        String detActual     = h.getDetRowCount() >= 0
                ? h.getDetRowCount() + " row(s) found" : NOT_REACHED;
        String postActual    = h.getPostEntryCount() >= 0
                ? h.getPostEntryCount() + " entries, balanced" : NOT_REACHED;
        String tagActual;
        if      (h.getTagCount() == -2) tagActual = NOT_REACHED;
        else if (h.getTagCount() == -1) tagActual = "skipped (ADVANCE_PAYMENT — no SelectDocument)";
        else                            tagActual = h.getTagCount() + " entries";

        boolean isAp = "AP".equals(h.getVoucherType());

        StringBuilder sb = new StringBuilder();
        sb.append(CSS);
        sb.append("<div class='rs-wrap'>");
        sb.append(LEGEND);

        // ── UI Validation section ──────────────────────────────────────────────
        sb.append("<details open><summary class='rs-top rs-ui'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:8px'>" + ICO_MONITOR + " UI Validation</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");

        // Document Information sub-section
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_DOC + " Document Information</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append(tableOpen());
        sb.append(headerRow("Field", "Expected (test input)", "Actual (UI)", "Status"));

        // Doc No — label depends on whether it's auto-assigned
        String docNoLabel = "Doc no";
        String docNoExpected = "(assigned on save)";
        Status docNoStatus = docNoActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row(docNoLabel, docNoExpected, docNoActual, docNoStatus));

        sb.append(row("Owner site",     expOwnerSite, siteActual,   matchContains(siteActual, expOwnerSite)));
        sb.append(row("Cash / bank GL", expCashBank,  glActual,     matchContains(glActual, expCashBank)));
        sb.append(row("Doc date",       expDocDate,   docDateActual, matchExact(docDateActual, expDocDate)));
        sb.append(row("Reference",      expReference, refActual,     matchExact(refActual, expReference)));
        sb.append(row("Remarks",        expRemarks,   remarkActual,  matchExact(remarkActual, expRemarks)));
        sb.append("</tbody>").append(TABLE_CLOSE).append("</details>");

        // Flags sub-section — AP only
        if (isAp) {
            String expOnAccount = h.getOnAccount() != null ? OnAccountValues.from(h.getOnAccount()).getLabel() : "—";
            String uiOnAccount  = h.getUiOnAccount() != null ? h.getUiOnAccount().trim() : NOT_REACHED;
            sb.append("<details open><summary class='rs-sub'>");
            sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_FLAG + " Flags</span>");
            sb.append("<span class='rs-chev'>&#9650;</span></summary>");
            sb.append(tableOpen());
            sb.append(headerRow("Field", "SL Name", "SL ID", "Expected", "Actual (UI)", "Status"));
            // Document-level flag — no SL row association
            sb.append(flagRow("On account", "—", "—", expOnAccount, uiOnAccount, matchExact(uiOnAccount, expOnAccount)));
            sb.append("</tbody>").append(TABLE_CLOSE).append("</details>");
        }

        // Sub Ledger Information sub-section
        sb.append(buildSubLedgerSection(h));

        sb.append("</details>"); // end UI Validation

        // ── DB Validation section ──────────────────────────────────────────────
        sb.append("<details open><summary class='rs-top rs-db'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:8px'>" + ICO_DB + " DB Validation</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");

        // finvchmain
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_TABLE + " finvchmain</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status vchmainStatus = vchcodeActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("Record existence", "1 record created", vchcodeActual, vchmainStatus));
        String dbSiteActual = resolveOwnerSite(header);
        String dbGlActual   = resolveCashBankGl(header);
        sb.append(row("Owner site",   expOwnerSite, dbSiteActual, matchContains(dbSiteActual, expOwnerSite)));
        sb.append(row("Cash/bank GL", expCashBank,  dbGlActual,   matchContains(dbGlActual, expCashBank)));
        sb.append(row("vchdt",        expDocDate,   docDateActual, matchExact(docDateActual, expDocDate)));
        sb.append(row("refno",        expReference, refActual,     matchExact(refActual, expReference)));
        sb.append(row("nartext",      expRemarks,   remarkActual,  matchExact(remarkActual, expRemarks)));
        String entrySourceActual = header != null && header.getEntrySource() != null
                ? header.getEntrySource() : NOT_REACHED;
        String creationDtActual;
        Status creationDtStatus;
        if (header == null || header.getCreationDt() == null) {
            creationDtActual = NOT_REACHED;
            creationDtStatus = Status.SKIP;
        } else if (h.getCreationDtDelaySeconds() != null) {
            long delay = h.getCreationDtDelaySeconds();
            long tol   = h.getCreationDtToleranceSeconds();
            creationDtActual = delay + "s delay — within " + tol + "s tolerance";
            creationDtStatus = delay <= tol ? Status.PASS : Status.FAIL;
        } else {
            creationDtActual = header.getCreationDt().format(DATE_FMT);
            creationDtStatus = Status.PASS;
        }
        sb.append(row("entry_source", "WEB", entrySourceActual,
                entrySourceActual.equals(NOT_REACHED) ? Status.SKIP
                : entrySourceActual.matches("(?i)WEB|API") ? Status.PASS : Status.FAIL));
        java.time.LocalDateTime saveTime = h.getSaveStartTime();
        String creationDtExpected = saveTime != null
                ? "saved at " + saveTime.format(TIME_FMT) + " (within " + h.getCreationDtToleranceSeconds() + "s)"
                : "not null";
        sb.append(row("creation_dt", creationDtExpected, creationDtActual, creationDtStatus));
        sb.append("</tbody>").append(TABLE_CLOSE).append("</details>");

        // finvchdet
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_TABLE + " finvchdet</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status detStatus = detActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("Row count", expSlCount + " row(s)", detActual, detStatus));
        sb.append("</tbody>").append(TABLE_CLOSE).append("</details>");

        // finpost
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_TABLE + " finpost</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status postStatus = postActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("GL balance", "GL balanced", postActual, postStatus));
        sb.append("</tbody>").append(TABLE_CLOSE);
        if (h.getPostEntries() != null && !h.getPostEntries().isEmpty()) {
            sb.append(buildGlEntriesTable(h.getPostEntries()));
        }
        sb.append("</details>");

        // fintag
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>" + ICO_TABLE + " fintag</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status tagStatus;
        if (h.getTagCount() == -2) tagStatus = Status.SKIP;
        else if (h.getTagCount() == -1) tagStatus = Status.SKIP;
        else tagStatus = Status.PASS;
        sb.append(row("Tag check", expSelectDocEntry, tagActual, tagStatus));
        sb.append("</tbody>").append(TABLE_CLOSE).append("</details>");

        sb.append("</details>"); // end DB Validation

        // DB Lookups section — captured queries from this build
        List<String> captured = JdbiSqlLogger.getCaptured();
        if (!captured.isEmpty()) {
            sb.append(buildDbLookupsSection(captured));
        }

        sb.append("</div>"); // end rs-wrap
        sb.append(HIDE_TS_SCRIPT);

        return sb.toString();
    }

    // ─── GL entries sub-table ─────────────────────────────────────────────────

    private static String buildGlEntriesTable(List<FinPost> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;width:100%;padding:6px 12px 10px;background:#fff'>");
        sb.append("<div style='font-size:11px;color:#64748B;margin-bottom:5px;letter-spacing:.03em'>GL entries</div>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:12px;border:0.5px solid #E2E8F0;border-radius:6px;overflow:hidden;table-layout:fixed'>");
        sb.append("<thead><tr>");
        sb.append(glTh("GL",        "left",  "65%"));
        sb.append(glTh("Dr amount", "right", "17.5%"));
        sb.append(glTh("Cr amount", "right", "17.5%"));
        sb.append("</tr></thead><tbody>");
        for (FinPost e : entries) {
            String glName  = resolveGlName(e.getGlcode());
            String glLabel = glName + " (" + e.getGlcode() + ")";
            String dr = fmtAmount(e.getDamount());
            String cr = fmtAmount(e.getCamount());
            sb.append("<tr>");
            sb.append("<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;color:#1E293B'>").append(esc(glLabel)).append("</td>");
            sb.append(amountTd(dr));
            sb.append(amountTd(cr));
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String glTh(String label) { return glTh(label, "left", null); }
    private static String glTh(String label, String align) { return glTh(label, align, null); }
    private static String glTh(String label, String align, String width) {
        String wStyle = width != null ? "width:" + width + ";" : "";
        return "<th style='background:#F8FAFC;color:#64748B;font-size:11px;font-weight:500;padding:5px 10px;"
                + wStyle + "text-align:" + align + ";border-bottom:0.5px solid #E2E8F0'>" + esc(label) + "</th>";
    }
    private static String amountTd(String val) {
        boolean blank = "—".equals(val);
        String color = blank ? "#64748B" : "#1E293B";
        return "<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;text-align:right;"
                + "font-family:monospace;color:" + color + "'>" + val + "</td>";
    }
    // ─── DB Lookups section ───────────────────────────────────────────────────

    private static String buildDbLookupsSection(List<String> queries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<details open><summary class='rs-lookup-sum'>")
          .append("<span style='display:inline-flex;align-items:center;gap:6px'>")
          .append(ICO_LOOKUP).append(" DB Lookups &mdash; ").append(queries.size()).append(" quer")
          .append(queries.size() == 1 ? "y" : "ies").append(" fired during rendering</span>")
          .append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append("<div style='padding:6px 12px 10px;background:#fff'>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:11px;font-family:monospace'>");
        sb.append("<thead><tr>")
          .append("<th style='text-align:left;padding:4px 8px;color:#64748B;font-weight:500;border-bottom:0.5px solid #E2E8F0;font-family:sans-serif;font-size:11px'>SQL</th>")
          .append("<th style='text-align:right;padding:4px 8px;color:#64748B;font-weight:500;border-bottom:0.5px solid #E2E8F0;font-family:sans-serif;font-size:11px;white-space:nowrap'>Time (ms)</th>")
          .append("</tr></thead><tbody>");
        for (String q : queries) {
            // Format: "[DB Query ⏱ 71ms] SELECT ..." — split on "] "
            String timing = "";
            String sql = q;
            int bracket = q.indexOf("] ");
            if (bracket >= 0) {
                String prefix = q.substring(0, bracket); // e.g. "[DB Query ⏱ 71ms"
                sql = q.substring(bracket + 2);
                int msIdx = prefix.lastIndexOf(' ');
                if (msIdx >= 0) timing = prefix.substring(msIdx + 1).replace("ms", "");
            }
            sb.append("<tr>")
              .append("<td style='padding:4px 8px;border-bottom:0.5px solid #F1F5F9;color:#334155;word-break:break-all'>")
              .append(esc(sql)).append("</td>")
              .append("<td style='padding:4px 8px;border-bottom:0.5px solid #F1F5F9;color:#64748B;text-align:right;white-space:nowrap'>")
              .append(esc(timing)).append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div></details>");
        return sb.toString();
    }

    private static String fmtAmount(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "—";
        return String.format("%,.2f", v);
    }

    // ─── Sub Ledger Information sub-section ──────────────────────────────────

    private static String buildSubLedgerSection(ActualResultsHolder h) {
        Object td = h.getTestData();
        List<?> slList = null;
        boolean isAp = false;

        if (td instanceof MergedFinanceApVoucherData) {
            slList = ((MergedFinanceApVoucherData) td).getSubLedgerInformationList();
            isAp = true;
        } else if (td instanceof MergedFinanceArVoucherData) {
            slList = ((MergedFinanceArVoucherData) td).getSubLedgerInformationList();
        }

        if (slList == null || slList.isEmpty()) return "";

        // Heading: AP uses isOnAccount() to distinguish Advance Payment vs Document Adjustment
        String slTypeLabel;
        if (isAp) {
            slTypeLabel = ((MergedFinanceApVoucherData) td).isOnAccount()
                    ? "Advance Payment" : "Document Adjustment";
        } else {
            slTypeLabel = "Document Adjustment";
        }

        boolean needsPaymentInfoWarning = isAp
                ? ((MergedFinanceApVoucherData) td).isNeedsPaymentInfoWarning()
                : ((MergedFinanceArVoucherData) td).isNeedsPaymentInfoWarning();
        Map<String, Boolean> popupMap  = h.getUiPaymentInfoPopupMap();
        Map<String, String>  uiTdsInfoMap = h.getUiTdsInfoMap();
        Map<String, String>  deductMap    = h.getUiDeductTdsMap();

        StringBuilder sb = new StringBuilder();
        sb.append("<details open><summary class='rs-sub'>");
        sb.append("<span style='display:inline-flex;align-items:center;gap:6px'>"
                + ICO_SUBLEDGER + " Sub Ledger Information &#8212; " + slTypeLabel + "</span>");
        sb.append("<span class='rs-chev'>&#9650;</span></summary>");
        sb.append("<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>");

        if (isAp) {
            sb.append(headerRow("#", "SL name", "SL ID", "Amount (Dr)", "Adj type",
                    "Deduct TDS (UI)", "Payment Info Popup", "TDS Info"));
        } else {
            sb.append(headerRow("#", "SL name", "SL ID", "Amount (Cr)", "Adj type", "Payment Info Popup"));
        }

        int idx = 1;
        for (Object row : slList) {
            if (isAp && row instanceof SubLedgerInformation) {
                SubLedgerInformation sl = (SubLedgerInformation) row;
                String adjType = sl.getAdjustmentType() != null ? sl.getAdjustmentType().displayLabel() : "—";
                String amount  = sl.getAmountDr() != null ? String.format("%,.2f", sl.getAmountDr()) : "—";
                String key     = (sl.getId() != null && !sl.getId().isBlank()) ? sl.getId() : sl.getSubLedger();
                String tdsInfo = uiTdsInfoMap.containsKey(key) ? ns(uiTdsInfoMap.get(key)) : "—";
                String uiTds   = deductMap.containsKey(key) ? ns(deductMap.get(key)) : NOT_REACHED;
                String[] popup = resolvePopupCell(needsPaymentInfoWarning, popupMap, key);
                sb.append(slRowAp(idx++, ns(sl.getSubLedger()), ns(sl.getId()), amount, adjType,
                        uiTds, popup[0], popup[1], popup[2], tdsInfo));
            } else if (row instanceof com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation) {
                com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation sl =
                        (com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation) row;
                String adjType = sl.getAdjustmentType() != null ? sl.getAdjustmentType().displayLabel() : "—";
                String amount  = sl.getAmountCr() != null ? String.format("%,.2f", sl.getAmountCr()) : "—";
                String key     = (sl.getId() != null && !sl.getId().isBlank()) ? sl.getId() : sl.getSubLedger();
                String[] popup = resolvePopupCell(needsPaymentInfoWarning, popupMap, key);
                sb.append(slRowAr(idx++, ns(sl.getSubLedger()), ns(sl.getId()), amount, adjType,
                        popup[0], popup[1], popup[2]));
            }
        }

        sb.append("</tbody></table></div>");

        // Footer flag row — Payment Info blank warning (document-level)
        sb.append(buildPaymentWarningFooter(td, isAp, h.getUiPaymentInfoWarningAppeared()));

        sb.append("</details>");
        return sb.toString();
    }

    /** Resolves expected/actual/statusClass for Payment Info Popup per SL row.
     *  Returns String[3]: {expLabel, actualLabel, statusCssClass} */
    private static String[] resolvePopupCell(boolean needsPaymentInfoWarning,
                                             Map<String, Boolean> popupMap, String key) {
        String expLabel = needsPaymentInfoWarning ? "YES" : "NO";
        String uiLabel;
        String statusClass;
        if (!popupMap.containsKey(key)) {
            uiLabel     = NOT_REACHED;
            statusClass = "skip";
        } else {
            uiLabel     = Boolean.TRUE.equals(popupMap.get(key)) ? "YES" : "NO";
            statusClass = uiLabel.equals(expLabel) ? "pass" : "fail";
        }
        return new String[]{expLabel, uiLabel, statusClass};
    }

    /** Builds the Payment Info blank warning footer flag row. */
    private static String buildPaymentWarningFooter(Object td, boolean isAp, Boolean uiWarned) {
        boolean expWarning = isAp
                ? ((MergedFinanceApVoucherData) td).isNeedsPaymentInfoWarning()
                : ((MergedFinanceArVoucherData) td).isNeedsPaymentInfoWarning();
        String expStr  = expWarning ? "YES" : "NO";
        String uiStr   = uiWarned == null ? NOT_REACHED : (uiWarned ? "YES" : "NO");
        Status st      = uiWarned == null ? Status.SKIP : (uiStr.equals(expStr) ? Status.PASS : Status.FAIL);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:0 0 8px'><table class='rs-tbl'><tbody><tr>");
        sb.append(td("<span style='font-size:11px;color:#475569;font-weight:500'>"
                + "Payment Info blank warning</span>"));
        sb.append(td(esc(expStr)));
        sb.append(td(esc(uiStr)));
        sb.append("<td>" + badge(st) + "</td>");
        sb.append("</tr></tbody></table></div>");
        return sb.toString();
    }

    private static String slRowAp(int idx, String slName, String slId, String amount,
                                   String adjType, String uiDeductTds,
                                   String expPopup, String uiPopup, String popupStatusClass,
                                   String tdsInfo) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append(td("<span style='font-size:10px;color:#64748B;background:#F8FAFC;"
                + "border:0.5px solid #E2E8F0;padding:2px 5px;border-radius:99px'>" + idx + "</span>"));
        sb.append(td(esc(slName)));
        sb.append(td("<span style='font-family:monospace'>" + esc(slId) + "</span>"));
        sb.append(td(esc(amount)));
        sb.append(td(esc(adjType)));
        // Deduct TDS — informational only, no status badge
        sb.append(td("<span style='font-size:11px;color:#475569'>" + esc(uiDeductTds) + "</span>"));
        // Payment Info Popup — expected vs actual with status
        sb.append(popupCell(expPopup, uiPopup, popupStatusClass));
        // TDS Info — shows "—" until uiTdsInfoMap is populated via readTdsInfo()
        sb.append(td("<span style='font-size:11px;color:#64748B'>" + esc(tdsInfo) + "</span>"));
        sb.append("</tr>");
        return sb.toString();
    }

    private static String slRowAr(int idx, String slName, String slId, String amount,
                                   String adjType,
                                   String expPopup, String uiPopup, String popupStatusClass) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append(td("<span style='font-size:10px;color:#64748B;background:#F8FAFC;"
                + "border:0.5px solid #E2E8F0;padding:2px 5px;border-radius:99px'>" + idx + "</span>"));
        sb.append(td(esc(slName)));
        sb.append(td("<span style='font-family:monospace'>" + esc(slId) + "</span>"));
        sb.append(td(esc(amount)));
        sb.append(td(esc(adjType)));
        sb.append(popupCell(expPopup, uiPopup, popupStatusClass));
        sb.append("</tr>");
        return sb.toString();
    }

    /** Renders the Payment Info Popup cell: "Exp: X → UI: Y [badge]" */
    private static String popupCell(String exp, String ui, String statusClass) {
        Status s = "pass".equals(statusClass) ? Status.PASS
                 : "fail".equals(statusClass) ? Status.FAIL
                 : Status.SKIP;
        return "<td><span style='font-size:11px'>Exp: " + esc(exp)
                + " &rarr; UI: " + esc(ui) + "</span> " + badge(s) + "</td>";
    }

    // ─── Negative table ───────────────────────────────────────────────────────

    private static void renderNegativeTable(ActualResultsHolder h) {
        try {
            ExtentLogger.infoTable(buildNegativeTable(h));
        } catch (Exception e) {
            Log.warn("[ActualResultsLogger] Failed to build negative table: {}", e.getMessage());
        }
    }

    private static String[][] buildNegativeTable(ActualResultsHolder h) {
        String expMsg = "(unknown)";
        Object td = h.getTestData();
        if (td instanceof MergedFinanceArVoucherData) {
            MergedFinanceArVoucherData ar = (MergedFinanceArVoucherData) td;
            if (ar.getExpectedValidation() != null) expMsg = ar.getExpectedValidation().expectedMessage();
        } else if (td instanceof MergedFinanceApVoucherData) {
            MergedFinanceApVoucherData ap = (MergedFinanceApVoucherData) td;
            if (ap.getExpectedValidation() != null) expMsg = ap.getExpectedValidation().expectedMessage();
        }
        return new String[][]{
            {"Field",           "Expected",                                          "Actual"},
            {"UI — Validation", expMsg,                                              "Asserted by scenario behavior"},
            {"DB — finvchmain", "No record created (save blocked by UI validation)", "0 records (not queried)"},
            {"DB — finvchdet",  "No record created",                                 "0 records (not queried)"},
            {"DB — finpost",    "No posting created",                                "0 entries (not queried)"},
            {"DB — fintag",     "No entries created",                                "0 entries (not queried)"},
        };
    }

    // ─── Table row / cell helpers ─────────────────────────────────────────────

    private enum Status { PASS, FAIL, SKIP }

    private static final String STATUS_COL = "Status";
    private static final String TD_BASE    = "padding:7px 12px;border-bottom:0.5px solid #E2E8F0;word-break:break-word";

    private static String tableOpen() {
        return "<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>";
    }
    private static final String TABLE_CLOSE = "</table></div>";

    private static final String[] WIDTHS_4COL = {"18%", "36%", "36%", "10%"};

    private static String headerRow(String... cols) {
        StringBuilder sb = new StringBuilder("<thead><tr>");
        for (int i = 0; i < cols.length; i++) {
            String c = cols[i];
            boolean isStatus = STATUS_COL.equals(c);
            String wStyle = (cols.length == 4) ? "width:" + WIDTHS_4COL[i] + ";" : "";
            sb.append("<th style='font-size:11px;font-weight:500;color:#64748B;"
                    + "padding:6px 12px;background:#fff;border-bottom:0.5px solid #E2E8F0;"
                    + wStyle
                    + (isStatus ? "text-align:center" : "text-align:left") + "'>")
              .append(esc(c)).append("</th>");
        }
        return sb.append("</tr></thead><tbody>").toString();
    }

    private static String row(String field, String expected, String actual, Status status) {
        String rowBg      = status == Status.FAIL ? "#FEF2F2" : "transparent";
        String actualColor = status == Status.FAIL ? "#991B1B" : "#1E293B";
        StringBuilder sb = new StringBuilder();
        sb.append("<tr style='background:").append(rowBg).append("'>");
        sb.append("<td style='").append(TD_BASE).append(";color:#64748B;font-size:12px'>")
          .append(esc(field)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B'>")
          .append(esc(expected)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:").append(actualColor).append("'>")
          .append(esc(actual)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";text-align:center'>")
          .append(badge(status)).append("</td>");
        sb.append("</tr>");
        return sb.toString();
    }

    // Overload for sub-ledger header rows (variable columns, no status)
    private static String headerRow(String c1, String c2, String c3, String c4, String c5) {
        return headerRow(new String[]{c1, c2, c3, c4, c5});
    }
    private static String headerRow(String c1, String c2, String c3, String c4, String c5, String c6) {
        return headerRow(new String[]{c1, c2, c3, c4, c5, c6});
    }
    /** Row for the Flags section — 6 columns: Field | SL Name | SL ID | Expected | Actual (UI) | Status */
    private static String flagRow(String field, String slName, String slId,
                                  String expected, String actual, Status status) {
        String rowBg      = status == Status.FAIL ? "#FEF2F2" : "transparent";
        String actualColor = status == Status.FAIL ? "#991B1B" : "#1E293B";
        StringBuilder sb = new StringBuilder();
        sb.append("<tr style='background:").append(rowBg).append("'>");
        sb.append("<td style='").append(TD_BASE).append(";color:#64748B;font-size:12px'>")
          .append(esc(field)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B;font-size:12px'>")
          .append(esc(slName)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B;font-family:monospace;font-size:12px'>")
          .append(esc(slId)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B'>")
          .append(esc(expected)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:").append(actualColor).append("'>")
          .append(esc(actual)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";text-align:center'>")
          .append(badge(status)).append("</td>");
        sb.append("</tr>");
        return sb.toString();
    }

    private static String td(String content) {
        return "<td style='" + TD_BASE + ";color:#1E293B'>" + content + "</td>";
    }

    private static String badge(Status s) {
        return switch (s) {
            case PASS -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#EAF3DE;color:#27500A;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#10003; Pass</span>";
            case FAIL -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#FEE2E2;color:#991B1B;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#10007; Fail</span>";
            case SKIP -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#F1EFE8;color:#5F5E5A;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#8722; Skip</span>";
        };
    }

    // ─── Status match helpers ─────────────────────────────────────────────────

    private static Status matchExact(String actual, String expected) {
        if (actual.equals(NOT_REACHED)) return Status.SKIP;
        return actual.trim().equals(expected.trim()) ? Status.PASS : Status.FAIL;
    }

    private static Status matchContains(String actual, String expected) {
        if (actual.equals(NOT_REACHED)) return Status.SKIP;
        return actual.contains(expected) ? Status.PASS : Status.FAIL;
    }

    // ─── Lookup helpers ───────────────────────────────────────────────────────

    private static String resolveOwnerSite(FinVchMain header) {
        if (header == null) return NOT_REACHED;
        try {
            AdmSiteDao dao  = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class);
            AdmSite    site = dao.findByCode(header.getAdmsiteCodeOwner());
            String     name = site != null ? site.getName() : "unknown";
            return "admsite_code_owner = " + header.getAdmsiteCodeOwner() + "  (" + name + ")";
        } catch (Exception e) {
            return "admsite_code_owner = " + header.getAdmsiteCodeOwner() + "  (name lookup failed)";
        }
    }

    private static String resolveCashBankGl(FinVchMain header) {
        if (header == null) return NOT_REACHED;
        try {
            FinGlDao dao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
            FinGl    gl  = dao.findByGlCode(header.getGlcode());
            String   nm  = gl != null ? gl.getGlname() : "unknown";
            return "glcode = " + header.getGlcode() + "  (" + nm + ")";
        } catch (Exception e) {
            return "glcode = " + header.getGlcode() + "  (name lookup failed)";
        }
    }

    private static String resolveGlName(long glcode) {
        try {
            FinGlDao dao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
            FinGl    gl  = dao.findByGlCode(glcode);
            return gl != null ? gl.getGlname() : "GL " + glcode;
        } catch (Exception e) {
            return "GL " + glcode;
        }
    }

    // ─── Expected column extraction ───────────────────────────────────────────

    /**
     * Returns [ownerSite, cashBank, docDate, reference, remarks, slCount, tagExpected]
     * extracted from the testData snapshot stored in the holder.
     */
    private static String[] extractExpected(ActualResultsHolder h) {
        Object td = h.getTestData();
        if (td instanceof MergedFinanceArVoucherData) {
            MergedFinanceArVoucherData ar = (MergedFinanceArVoucherData) td;
            var doc = ar.getDocumentInformation();
            int slCount = ar.getSubLedgerInformationList() != null
                    ? ar.getSubLedgerInformationList().size() : 0;
            boolean hasDoc = ar.getSelectDocumentList() != null
                    && !ar.getSelectDocumentList().isEmpty();
            String tagExp = hasDoc
                    ? ar.getSelectDocumentList().size() + " entries"
                    : "skipped (ADVANCE_PAYMENT)";
            return new String[]{
                doc != null ? ns(doc.getOwnersite()) : "—",
                doc != null ? ns(doc.getCashbank())  : "—",
                doc != null && doc.getDocumentdate() != null
                        ? doc.getDocumentdate().format(DATE_FMT) : "—",
                doc != null ? ns(doc.getReference()) : "—",
                doc != null ? ns(doc.getRemarks())   : "—",
                String.valueOf(slCount),
                tagExp
            };
        } else if (td instanceof MergedFinanceApVoucherData) {
            MergedFinanceApVoucherData ap = (MergedFinanceApVoucherData) td;
            var doc = ap.getDocumentInformation();
            int slCount = ap.getSubLedgerInformationList() != null
                    ? ap.getSubLedgerInformationList().size() : 0;
            boolean hasDoc = ap.getSelectDocumentList() != null
                    && !ap.getSelectDocumentList().isEmpty();
            String tagExp = hasDoc
                    ? ap.getSelectDocumentList().size() + " entries"
                    : "skipped (ADVANCE_PAYMENT)";
            return new String[]{
                doc != null ? ns(doc.getOwnersite()) : "—",
                doc != null ? ns(doc.getCashbank())  : "—",
                doc != null && doc.getDocumentdate() != null
                        ? doc.getDocumentdate().format(DATE_FMT) : "—",
                doc != null ? ns(doc.getReference()) : "—",
                doc != null ? ns(doc.getRemarks())   : "—",
                String.valueOf(slCount),
                tagExp
            };
        }
        return new String[]{"—", "—", "—", "—", "—", "—", "—"};
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private static final String NOT_REACHED = "— (not reached)";

    private static String ns(Object v)  { return v != null ? v.toString() : "—"; }
    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── Icons ────────────────────────────────────────────────────────────────

    private static String icon(String path, int size) {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='" + size + "' height='" + size
             + "' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' "
             + "stroke-linecap='round' stroke-linejoin='round' "
             + "style='vertical-align:middle;flex-shrink:0'>" + path + "</svg>";
    }

    private static final String ICO_MONITOR   = icon(
        "<rect x='2' y='3' width='20' height='14' rx='2'/>"
        + "<line x1='8' y1='21' x2='16' y2='21'/><line x1='12' y1='17' x2='12' y2='21'/>", 14);
    private static final String ICO_DOC       = icon(
        "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/>"
        + "<polyline points='14 2 14 8 20 8'/>"
        + "<line x1='16' y1='13' x2='8' y2='13'/><line x1='16' y1='17' x2='8' y2='17'/>", 12);
    private static final String ICO_FLAG      = icon(
        "<path d='M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z'/>"
        + "<line x1='4' y1='22' x2='4' y2='15'/>", 12);
    private static final String ICO_SUBLEDGER = icon(
        "<path d='M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2'/><circle cx='12' cy='7' r='4'/>", 12);
    private static final String ICO_DB        = icon(
        "<ellipse cx='12' cy='5' rx='9' ry='3'/>"
        + "<path d='M21 12c0 1.66-4 3-9 3s-9-1.34-9-3'/>"
        + "<path d='M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5'/>", 14);
    private static final String ICO_TABLE     = icon(
        "<rect x='3' y='3' width='18' height='18' rx='2'/>"
        + "<line x1='3' y1='9' x2='21' y2='9'/><line x1='9' y1='9' x2='9' y2='21'/>", 12);
    private static final String ICO_LOOKUP    = icon(
        "<circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/>", 12);

    // ─── Status legend ────────────────────────────────────────────────────────

    private static final String LEGEND =
        "<div class='rs-legend'>" +
        "<span class='rs-leg-lbl'>Status Badges:</span>" +
        "<span style='display:inline-flex;align-items:center;gap:3px;background:#EAF3DE;" +
            "color:#27500A;font-size:11px;padding:2px 7px;border-radius:99px;font-weight:500;" +
            "white-space:nowrap'>&#10003; Pass</span>" +
        "<span class='rs-leg-sep'>&#8212; matched</span>" +
        "<span style='display:inline-flex;align-items:center;gap:3px;background:#FEE2E2;" +
            "color:#991B1B;font-size:11px;padding:2px 7px;border-radius:99px;font-weight:500;" +
            "white-space:nowrap'>&#10007; Fail</span>" +
        "<span class='rs-leg-sep'>&#8212; mismatch, row highlighted red</span>" +
        "<span style='display:inline-flex;align-items:center;gap:3px;background:#FEF3C7;" +
            "color:#92400E;font-size:11px;padding:2px 7px;border-radius:99px;font-weight:500;" +
            "white-space:nowrap'>&#8635; Retry</span>" +
        "<span class='rs-leg-sep'>&#8212; passed after retry, row highlighted yellow" +
            "&nbsp;&#xB7;&#xB7;&#xB7;</span>" +
        "<span style='display:inline-flex;align-items:center;gap:3px;background:#F1EFE8;" +
            "color:#5F5E5A;font-size:11px;padding:2px 7px;border-radius:99px;font-weight:500;" +
            "white-space:nowrap'>&#8722; Skip</span>" +
        "<span class='rs-leg-sep'>&#8212; not applicable</span>" +
        "</div>";

    // ─── Hide Extent timestamp script ─────────────────────────────────────────

    private static final String HIDE_TS_SCRIPT =
        "<script>(function(){" +
        // ── Hide timestamp/status cells of the parent Extent log row ──────────
        // Use currentScript to find THIS script's exact <tr>, not the first .rs-wrap on page
        "var me=document.currentScript;" +
        "var tr=me?me.closest('tr'):null;" +
        "if(!tr){var w=document.querySelector('.rs-wrap');tr=w?w.closest('tr'):null;}" +
        "if(tr){var c=tr.cells;" +
        "for(var i=0;i<c.length-1;i++){c[i].style.cssText='display:none!important;width:0;padding:0'}" +
        "if(c.length>0)c[c.length-1].style.padding='0';}" +
        // ── Inject collapsible test-list panel toggle (once per page) ─────────
        "if(!document.body.dataset.rsTlDone){document.body.dataset.rsTlDone='1';" +
        "var s=document.createElement('style');" +
        "s.textContent=" +
        "`#rs-tl-btn{position:fixed;top:54px;z-index:9999;cursor:pointer;" +
        "background:#1F4E79;color:#fff;border:none;" +
        "border-radius:0 4px 4px 0;padding:5px 10px;" +
        "font-size:11px;font-weight:600;" +
        "box-shadow:2px 2px 8px rgba(0,0,0,.2);transition:left .2s}" +
        "#rs-tl-btn:hover{background:#163D63}" +
        ".rs-tl-col .test-list{display:none!important}" +
        ".rs-tl-col .test-content{max-width:100%!important;flex:1!important;width:100%!important}`;" +
        "document.head.appendChild(s);" +
        "var btn=document.createElement('button');" +
        "btn.id='rs-tl-btn';btn.title='Toggle test list';" +
        "function posBtn(col){" +
        "var tl=document.querySelector('.test-list');" +
        "var left=col||!tl?56:Math.round(tl.getBoundingClientRect().right);" +
        "btn.style.left=left+'px';" +
        "btn.innerHTML=col?'&#9654;&nbsp;Tests':'&#9664;&nbsp;Hide';}" +
        "btn.onclick=function(){" +
        "var tw=document.querySelector('.test-wrapper.test-view');" +
        "if(!tw)return;" +
        "tw.classList.toggle('rs-tl-col');" +
        "posBtn(tw.classList.contains('rs-tl-col'));};" +
        "document.body.appendChild(btn);" +
        "setTimeout(function(){posBtn(false);},150);}" +
        "})();</script>";

    // ─── CSS ─────────────────────────────────────────────────────────────────

    private static final String CSS =
        "<style>" +
        ".rs-top{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:8px;" +
            "font-size:12px;font-weight:500;letter-spacing:.04em;text-transform:uppercase;" +
            "padding:9px 14px;user-select:none;border-radius:8px 8px 0 0;margin-top:10px}" +
        ".rs-ui{background:#EAF2FB;color:#1F4E79;border:0.5px solid #B8D4EC}" +
        ".rs-db{background:#E6F4F4;color:#2C7A7B;border:0.5px solid #B2D8D8}" +
        ".rs-wrap details[open] .rs-top,.rs-sub{border-radius:0}" +
        ".rs-sub{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:6px;" +
            "font-size:11px;font-weight:500;color:#64748B;padding:6px 14px;" +
            "background:#F8FAFC;text-transform:uppercase;letter-spacing:.05em;" +
            "border:0.5px solid #E2E8F0;border-top:none}" +
        ".rs-wrap details{border:0.5px solid #E2E8F0;border-radius:8px;overflow:hidden;margin-bottom:2px}" +
        ".rs-wrap details.rs-inner{border:none;border-bottom:0.5px solid #E2E8F0;border-radius:0}" +
        ".rs-tbl{width:100%;min-width:540px;border-collapse:collapse;border:none;table-layout:fixed}" +
        ".rs-tbl tbody tr:last-child td{border-bottom:none}" +
        ".rs-wrap summary::-webkit-details-marker{display:none}" +
        ".rs-lookup-sum{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:6px;font-size:11px;font-weight:500;" +
            "color:#475569;padding:6px 14px;background:#F8FAFC;text-transform:uppercase;" +
            "letter-spacing:.04em;border:0.5px solid #E2E8F0;border-top:none;user-select:none}" +
        ".rs-chev{font-size:9px;opacity:0.55;flex-shrink:0;display:inline-block;transition:transform .15s}" +
        ".rs-wrap details:not([open]) .rs-chev{transform:rotate(180deg)}" +
        // Hide the Status and Timestamp columns of the Extent log row that wraps the widget.
        // Use > (direct-child combinator) so only the outer Extent <tr>'s own <td>s are hidden,
        // not the <td>s inside the nested tables within .rs-wrap.
        // :has() supported: Chrome 105+, Edge 105+, Firefox 121+
        "tr:has(.rs-wrap)>td:not(:last-child){display:none!important;width:0!important;padding:0!important}" +
        "tr:has(.rs-wrap)>td:last-child{padding:0!important}" +
        ".rs-legend{display:flex;align-items:center;flex-wrap:nowrap;overflow-x:auto;gap:6px 10px;" +
            "padding:8px 14px;background:#F8FAFC;border:0.5px solid #E2E8F0;" +
            "border-radius:8px;margin-bottom:8px}" +
        ".rs-leg-lbl{font-size:10px;font-weight:600;color:#64748B;text-transform:uppercase;" +
            "letter-spacing:.06em;margin-right:2px;white-space:nowrap}" +
        ".rs-leg-sep{color:#94A3B8;font-size:11px;white-space:nowrap}" +
        "</style>";
}
