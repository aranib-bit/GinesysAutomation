package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level 2 — finvchdet Line Item Validation.
 * Finance module only (finvchdet is not shared).
 *
 * BLOCKING: aborts if no line item rows exist for the vchcode.
 *
 * Payment-mode validation strategy:
 *   1. Resolve slcode  → finsl  WHERE slname = SubLedger  (+ slid when provided)
 *   2. Resolve glcode  → fingl  WHERE glname = GeneralLedger
 *   3. Fetch row       → finvchdet WHERE vchcode + slcode + glcode  (unique key)
 *   4. Find expected   → PaymentInformation matched by lineNo
 *   5. Map to DB code  → NEFT/RTGS → 'R' | Cheque → 'C' | Cash → 'H'
 *   6. Fallback        → cashBank srctype 'C' → 'H'; 'B' → 'C' (default Cheque)
 */
public class FinVchDetValidator {

    private final FinVchDetDao dao;
    private final FinGlDao     glDao;
    private final FinSlDao     slDao;

    public FinVchDetValidator() {
        this.dao   = DatabaseManager.getJdbi().onDemand(FinVchDetDao.class);
        this.glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        this.slDao = DatabaseManager.getJdbi().onDemand(FinSlDao.class);
    }

    public void validate(VoucherDbContext context, FinVchMain header) {
        long vchcode = header.getVchcode();
        MergedFinanceArVoucherData data = context.getTestData();
        List<SubLedgerInformation> inputLines = data.getSubLedgerInformationList();

        ReportLogger.db("Level 2 — finvchdet for vchcode [" + vchcode + "]");
        com.gsl.reports.ValidationBuffer.start();

        // Existence — BLOCKING
        List<FinVchDet> rows = dao.findByVoucherCode(vchcode);
        ActualResultsHolder.get().setDetRowCount(rows.size());
        Assert.assertFalse(rows.isEmpty(),
                "[Level 2] No finvchdet rows for vchcode [" + vchcode + "]");

        // Payment-mode + instrument detail validation (per-line, keyed by vchcode+slcode+glcode)
        String cashBankName = data.getDocumentInformation() != null
                ? data.getDocumentInformation().getCashbank() : null;

        // Maps populated during validatePaymentDetails — used to enrich the display table
        Map<Long, String> slNames = new HashMap<>();
        Map<Long, String> glNames = new HashMap<>();

        BigDecimal detCredit = dao.sumCreditByVoucherCode(vchcode);

        SoftAssertions.assertSoftly(soft -> {
            // Cardinality
            if (inputLines != null && !inputLines.isEmpty())
                soft.assertThat(rows.size())
                        .as("[Level 2] finvchdet count: expected " + inputLines.size()
                                + " found " + rows.size())
                        .isEqualTo(inputLines.size());

            // Cross-table balance: for AR Voucher, finvchmain.damount must equal SUM(finvchdet.camount)
            soft.assertThat(detCredit)
                    .as("[Level 2] SUM(finvchdet.camount)=" + detCredit
                            + " != finvchmain.damount=" + header.getDamount())
                    .isEqualByComparingTo(header.getDamount());

            // Per-row amount + narration checks against SubLedgerInformation
            if (inputLines != null)
                for (SubLedgerInformation line : inputLines)
                    validateSubLedgerLine(soft, line, rows, vchcode);

            validatePaymentDetails(soft, vchcode, inputLines, data.getPaymentInformationList(),
                    cashBankName, slNames, glNames);
        });

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finvchdet rows", buildDetTable(rows, slNames, glNames));
        ReportLogger.infoHtml(buildLevel2Summary(rows.size(), detCredit, header.getDamount(), data.isTdsProvided()));
        ReportLogger.db("Level 2 — PASSED: vchcode [" + vchcode + "] " + rows.size() + " line(s)");
    }

    // -------------------------------------------------------------------------
    // Payment-mode validation
    // -------------------------------------------------------------------------

    private void validatePaymentDetails(SoftAssertions soft, long vchcode,
                                         List<SubLedgerInformation> inputLines,
                                         List<? extends VoucherPaymentInfo> paymentList,
                                         String cashBankName,
                                         Map<Long, String> slNames,
                                         Map<Long, String> glNames) {
        if (inputLines == null || inputLines.isEmpty()) return;

        // Resolve Cash/Bank GL srctype  (fingl.srctype: 'C' = Cash, 'B' = Bank)
        FinGl cashBankGl = null;
        if (cashBankName != null && !cashBankName.isBlank()) {
            cashBankGl = glDao.findByGlName(cashBankName);
        }
        String cashBankSrctype = cashBankGl != null ? cashBankGl.getSrctype() : null;

        // Build lineNo → VoucherPaymentInfo map for O(1) lookup
        Map<String, VoucherPaymentInfo> paymentByLineNo = new HashMap<>();
        if (paymentList != null) {
            for (VoucherPaymentInfo p : paymentList) {
                if (p.getLineNo() != null && !p.getLineNo().isBlank())
                    paymentByLineNo.put(p.getLineNo().trim(), p);
            }
        }

        for (SubLedgerInformation line : inputLines) {

            // Step 1: resolve slcode from finsl
            FinSl finSl = resolveFinSl(line);
            if (finSl == null) {
                soft.fail("[Level 2] Cannot resolve slcode for SubLedger='"
                        + line.getSubLedger() + "' id='" + line.getId() + "'");
                continue;
            }
            // Collect for table enrichment
            slNames.put(finSl.getSlcode(), finSl.getSlname());

            // Step 2: resolve glcode from fingl
            if (line.getGeneralLedger() == null || line.getGeneralLedger().isBlank()) {
                soft.fail("[Level 2] GeneralLedger is blank for SubLedger='" + line.getSubLedger() + "'");
                continue;
            }
            FinGl finGl = glDao.findByGlName(line.getGeneralLedger());
            if (finGl == null) {
                soft.fail("[Level 2] Cannot resolve glcode for GL='" + line.getGeneralLedger() + "'");
                continue;
            }
            // Collect for table enrichment
            glNames.put(finGl.getGlcode(), finGl.getGlname());

            // Step 3: fetch the specific finvchdet row
            FinVchDet row = dao.findByVchcodeSlcodeGlcode(
                    vchcode, finSl.getSlcode(), finGl.getGlcode());
            if (row == null) {
                soft.fail("[Level 2] No finvchdet row found for"
                        + " vchcode=" + vchcode
                        + " slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                        + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")");
                continue;
            }

            // Step 4: find PaymentInformation for this line via lineNo
            VoucherPaymentInfo paymentInfo = (line.getLineNo() != null)
                    ? paymentByLineNo.get(line.getLineNo().trim()) : null;

            // Step 5: derive expected payment_mode DB code
            String expectedCode = resolveExpectedPaymentCode(cashBankSrctype, paymentInfo);

            // Step 6: assert payment mode
            soft.assertThat(row.getPaymentMode())
                    .as("[Level 2] finvchdet.payment_mode expected [" + expectedCode + "]"
                            + " but was [" + row.getPaymentMode() + "]"
                            + " for slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                            + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")")
                    .isEqualTo(expectedCode);

            // Step 7: validate instrument details for rows with explicit payment info
            validateInstrumentDetails(soft, row, paymentInfo, finSl, finGl);
        }
    }

    /**
     * Derives the expected finvchdet.payment_mode DB code.
     *
     * If PaymentInformation is provided for the line → use its payment mode directly:
     *   NEFT/RTGS → 'R' | Cheque → 'C' | Cash → 'H'
     *
     * Otherwise fall back to the Cash/Bank GL type:
     *   srctype 'C' (Cash GL) → 'H'
     *   srctype 'B' (Bank GL) → 'C'  (system default Cheque)
     */
    private String resolveExpectedPaymentCode(String cashBankSrctype,
                                               VoucherPaymentInfo paymentInfo) {
        return PaymentModeResolver.resolve(cashBankSrctype, paymentInfo);
    }

    /**
     * Validates chqno / chqdt only when PaymentInformation was explicitly provided for the line.
     *
     * Both NEFT/RTGS ('R') and Cheque ('C') store instrument details in finvchdet:
     *   chqno  ← instrumentNo
     *   chqdt  ← instrumentDate  (parsed from "dd/MM/yyyy")
     *
     * Cash ('H') has no instrument details — skipped entirely.
     *
     * Row is already uniquely identified by vchcode + slcode + glcode before this method is called.
     * slname(slcode) and glname(glcode) are included in all log and assert messages for easy
     * correlation with the Extent Report and log file.
     */
    private void validateInstrumentDetails(SoftAssertions soft, FinVchDet row,
                                            VoucherPaymentInfo paymentInfo,
                                            FinSl finSl, FinGl finGl) {
        // No explicit payment info for this line → skip instrument validation
        if (paymentInfo == null) return;

        String mode = row.getPaymentMode();

        // Cash — no instrument details stored in finvchdet
        if (PaymentSelectionType.CASH.dbCode().equalsIgnoreCase(mode)) return;

        // Build a reusable context label for all assert/log messages in this method
        String context = " for slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                       + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")";

        // NEFT/RTGS ('R') and Cheque ('C') — instrument number saved in chqno, date in chqdt
        String instrNo   = paymentInfo.getInstrumentNo();
        String instrDate = paymentInfo.getInstrumentDate();

        // Log correlation context before assertions so report readers can cross-reference easily
        ReportLogger.db("Instrument Validation:"
                + " payment_mode=" + mode
                + " | expected chqno=" + (instrNo   != null ? instrNo.trim()   : "—")
                + " | DB chqno="      + (row.getChqno() != null ? row.getChqno() : "—")
                + " | expected chqdt=" + (instrDate != null ? instrDate.trim() : "—")
                + " | DB chqdt="      + (row.getChqdt() != null ? row.getChqdt().toLocalDate() : "—")
                + context);

        // --- Instrument Number (chqno) ---
        if (instrNo != null && !instrNo.isBlank()) {
            soft.assertThat(row.getChqno())
                    .as("[Level 2] Instrument No expected [" + instrNo.trim() + "]"
                            + " but finvchdet.chqno is null" + context)
                    .isNotNull();
            soft.assertThat(row.getChqno())
                    .as("[Level 2] finvchdet.chqno [" + row.getChqno() + "]"
                            + " != instrumentNo [" + instrNo.trim() + "]" + context)
                    .isEqualTo(instrNo.trim());
        }

        // --- Instrument Date (chqdt) ---
        if (instrDate != null && !instrDate.isBlank()) {
            soft.assertThat(row.getChqdt())
                    .as("[Level 2] Instrument Date expected [" + instrDate.trim() + "]"
                            + " but finvchdet.chqdt is null" + context)
                    .isNotNull();
            if (row.getChqdt() != null) {
                LocalDate expectedDate = parseInstrDate(instrDate);

                // Date part must match the supplied instrument date
                soft.assertThat(row.getChqdt().toLocalDate())
                        .as("[Level 2] finvchdet.chqdt date [" + row.getChqdt().toLocalDate() + "]"
                                + " != instrumentDate [" + expectedDate + "]" + context)
                        .isEqualTo(expectedDate);

                // Time part must be midnight — chqdt is a date-only value stored as TIMESTAMP
                soft.assertThat(row.getChqdt().toLocalTime())
                        .as("[Level 2] finvchdet.chqdt has unexpected time component ["
                                + row.getChqdt().toLocalTime() + "] — expected 00:00:00" + context)
                        .isEqualTo(LocalTime.MIDNIGHT);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves FinSl for a SubLedgerInformation line:
     *   - If slid is provided → exact match on slname + slid
     *   - Otherwise           → match on slname alone (requires unique result)
     */
    private FinSl resolveFinSl(SubLedgerInformation line) {
        if (line.getSubLedger() == null || line.getSubLedger().isBlank()) return null;
        if (line.getId() != null && !line.getId().isBlank()) {
            return slDao.findBySlnameAndSlid(line.getSubLedger(), line.getId());
        }
        List<FinSl> matches = slDao.findBySlname(line.getSubLedger());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private void validateSubLedgerLine(SoftAssertions soft, SubLedgerInformation line,
                                        List<FinVchDet> rows, long vchcode) {
        BigDecimal cr = line.getAmountCr();
        if (cr == null) return;
        FinVchDet match = rows.stream()
                .filter(d -> d.getCamount() != null && d.getCamount().compareTo(cr) == 0)
                .findFirst().orElse(null);
        soft.assertThat(match)
                .as("[Level 2] No finvchdet row with camount=" + cr + " for vchcode [" + vchcode + "]")
                .isNotNull();
        if (match != null && line.getLedgerNarration() != null
                && !line.getLedgerNarration().isBlank())
            soft.assertThat(match.getNartext())
                    .as("[Level 2] finvchdet.nartext mismatch")
                    .isEqualTo(line.getLedgerNarration());
    }

    /**
     * Builds the finvchdet display table for the Extent Report.
     * slname and glname columns are enriched from the lookup maps collected
     * during payment validation so users can correlate codes to names at a glance.
     */
    private static String[][] buildDetTable(List<FinVchDet> rows,
                                             Map<Long, String> slNames,
                                             Map<Long, String> glNames) {
        String[] headers = {"vchcode", "slcode", "slname", "glcode", "glname",
                            "refno", "damount", "camount",
                            "chqno", "chqdt", "payment_mode", "tds_app", "nartext"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinVchDet r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getVchcode()),
                str(r.getSlcode()),  slNames.getOrDefault(r.getSlcode(), "—"),
                str(r.getGlcode()),  glNames.getOrDefault(r.getGlcode(), "—"),
                str(r.getRefno()),   str(r.getDamount()), str(r.getCamount()),
                str(r.getChqno()),   str(r.getChqdt()),   str(r.getPaymentMode()),
                str(r.getTdsApp()),  str(r.getNartext())
            };
        }
        return table;
    }

    private static String buildLevel2Summary(int rowCount, BigDecimal sumCredit,
                                              BigDecimal damount, boolean tdsProvided) {
        return "<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;"
            + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>"
            + "<b>Level 2 &mdash; finvchdet line checks</b><br/>"
            + "&#10003;&nbsp; Line count: <b>" + rowCount + "</b> row(s)<br/>"
            + "&#10003;&nbsp; Balance: SUM(camount) = <b>" + sumCredit + "</b>"
            + " matches finvchmain.damount = <b>" + damount + "</b><br/>"
            + "&#10003;&nbsp; TDS flag (tds_app): <b>" + (tdsProvided ? "Y" : "N") + "</b><br/>"
            + "</div>";
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }

    private static final List<DateTimeFormatter> INSTR_DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private static LocalDate parseInstrDate(String raw) {
        String s = raw.trim();
        for (DateTimeFormatter fmt : INSTR_DATE_FORMATS) {
            try { return LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse instrument date: '" + raw + "'");
    }
}
