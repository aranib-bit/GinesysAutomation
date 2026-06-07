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
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import org.assertj.core.api.SoftAssertions;
import org.testng.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level 2 — finvchdet Line Item Validation for AP Voucher.
 *
 * Key differences from FinVchDetValidator (AR):
 *   - Compares sl.getAmountDr() against finvchdet.damount  (not camount)
 *   - Balance check: finvchmain.camount == SUM(finvchdet.damount)
 *   - Uses APVoucher SubLedgerInformation and PaymentInformation
 *   - Payment mode resolution delegates to PaymentModeResolver (shared)
 */
public class FinVchDetApValidator {

    private final FinVchDetDao dao;
    private final FinGlDao     glDao;
    private final FinSlDao     slDao;

    public FinVchDetApValidator() {
        this.dao   = DatabaseManager.getJdbi().onDemand(FinVchDetDao.class);
        this.glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        this.slDao = DatabaseManager.getJdbi().onDemand(FinSlDao.class);
    }

    public void validate(ApVoucherDbContext context, FinVchMain header) {
        long vchcode = header.getVchcode();
        MergedFinanceApVoucherData data = context.getTestData();
        List<SubLedgerInformation> inputLines = data.getSubLedgerInformationList();

        ReportLogger.info("Level 2 — finvchdet for AP vchcode [" + vchcode + "]");

        // Existence — BLOCKING
        List<FinVchDet> rows = dao.findByVoucherCode(vchcode);
        Assert.assertFalse(rows.isEmpty(),
                "[Level 2] No finvchdet rows for vchcode [" + vchcode + "]");

        String cashBankName = data.getDocumentInformation() != null
                ? data.getDocumentInformation().getCashbank() : null;

        Map<Long, String> slNames = new HashMap<>();
        Map<Long, String> glNames = new HashMap<>();

        // AP: sum debit side of finvchdet
        BigDecimal detDebit = dao.sumDebitByVoucherCode(vchcode);

        SoftAssertions.assertSoftly(soft -> {
            if (inputLines != null && !inputLines.isEmpty())
                soft.assertThat(rows.size())
                        .as("[Level 2] finvchdet count: expected " + inputLines.size()
                                + " found " + rows.size())
                        .isEqualTo(inputLines.size());

            // AP balance check: finvchmain.camount == SUM(finvchdet.damount)
            soft.assertThat(detDebit)
                    .as("[Level 2] SUM(finvchdet.damount)=" + detDebit
                            + " != finvchmain.camount=" + header.getCamount())
                    .isEqualByComparingTo(header.getCamount());

            if (inputLines != null)
                for (SubLedgerInformation line : inputLines)
                    validateSubLedgerLine(soft, line, rows, vchcode, data);

            validateTdsApp(soft, rows, data.isTdsProvided(), vchcode);

            validatePaymentDetails(soft, vchcode, inputLines, data.getPaymentInformationList(),
                    cashBankName, slNames, glNames);
        });

        ReportLogger.info("<b>finvchdet rows:</b>");
        ReportLogger.infoTable(buildDetTable(rows, slNames, glNames));
        ReportLogger.info("Level 2 — PASSED: vchcode [" + vchcode + "] " + rows.size() + " line(s)");
    }

    // ── Payment-mode validation ───────────────────────────────────────────────

    private void validatePaymentDetails(SoftAssertions soft, long vchcode,
                                         List<SubLedgerInformation> inputLines,
                                         List<? extends VoucherPaymentInfo> paymentList,
                                         String cashBankName,
                                         Map<Long, String> slNames,
                                         Map<Long, String> glNames) {
        if (inputLines == null || inputLines.isEmpty()) return;

        FinGl cashBankGl = null;
        if (cashBankName != null && !cashBankName.isBlank()) {
            cashBankGl = glDao.findByGlName(cashBankName);
        }
        String cashBankSrctype = cashBankGl != null ? cashBankGl.getSrctype() : null;

        Map<String, VoucherPaymentInfo> paymentByLineNo = new HashMap<>();
        if (paymentList != null) {
            for (VoucherPaymentInfo p : paymentList) {
                if (p.getLineNo() != null && !p.getLineNo().isBlank())
                    paymentByLineNo.put(p.getLineNo().trim(), p);
            }
        }

        for (SubLedgerInformation line : inputLines) {
            FinSl finSl = resolveFinSl(line);
            if (finSl == null) {
                soft.fail("[Level 2] Cannot resolve slcode for SubLedger='"
                        + line.getSubLedger() + "' id='" + line.getId() + "'");
                continue;
            }
            slNames.put(finSl.getSlcode(), finSl.getSlname());

            if (line.getGeneralLedger() == null || line.getGeneralLedger().isBlank()) {
                soft.fail("[Level 2] GeneralLedger is blank for SubLedger='" + line.getSubLedger() + "'");
                continue;
            }
            FinGl finGl = glDao.findByGlName(line.getGeneralLedger());
            if (finGl == null) {
                soft.fail("[Level 2] Cannot resolve glcode for GL='" + line.getGeneralLedger() + "'");
                continue;
            }
            glNames.put(finGl.getGlcode(), finGl.getGlname());

            FinVchDet row = dao.findByVchcodeSlcodeGlcode(
                    vchcode, finSl.getSlcode(), finGl.getGlcode());
            if (row == null) {
                soft.fail("[Level 2] No finvchdet row found for"
                        + " vchcode=" + vchcode
                        + " slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                        + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")");
                continue;
            }

            VoucherPaymentInfo paymentInfo = (line.getLineNo() != null)
                    ? paymentByLineNo.get(line.getLineNo().trim()) : null;

            String expectedCode = PaymentModeResolver.resolve(cashBankSrctype, paymentInfo);

            soft.assertThat(row.getPaymentMode())
                    .as("[Level 2] finvchdet.payment_mode expected [" + expectedCode + "]"
                            + " but was [" + row.getPaymentMode() + "]"
                            + " for slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                            + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")")
                    .isEqualTo(expectedCode);

            validateInstrumentDetails(soft, row, paymentInfo, finSl, finGl);
        }
    }

    private void validateInstrumentDetails(SoftAssertions soft, FinVchDet row,
                                            VoucherPaymentInfo paymentInfo,
                                            FinSl finSl, FinGl finGl) {
        if (paymentInfo == null) return;
        String mode = row.getPaymentMode();
        if (PaymentSelectionType.CASH.dbCode().equalsIgnoreCase(mode)) return;

        String context = " for slname=" + finSl.getSlname() + "(slcode=" + finSl.getSlcode() + ")"
                       + " glname=" + finGl.getGlname() + "(glcode=" + finGl.getGlcode() + ")";
        String instrNo   = paymentInfo.getInstrumentNo();
        String instrDate = paymentInfo.getInstrumentDate();

        ReportLogger.info("Instrument Validation:"
                + " payment_mode=" + mode
                + " | expected chqno=" + (instrNo   != null ? instrNo.trim()   : "—")
                + " | DB chqno="      + (row.getChqno() != null ? row.getChqno() : "—")
                + " | expected chqdt=" + (instrDate != null ? instrDate.trim() : "—")
                + " | DB chqdt="      + (row.getChqdt() != null ? row.getChqdt().toLocalDate() : "—")
                + context);

        if (instrNo != null && !instrNo.isBlank()) {
            soft.assertThat(row.getChqno())
                    .as("[Level 2] Instrument No expected [" + instrNo.trim() + "] but finvchdet.chqno is null" + context)
                    .isNotNull();
            soft.assertThat(row.getChqno())
                    .as("[Level 2] finvchdet.chqno [" + row.getChqno() + "] != instrumentNo [" + instrNo.trim() + "]" + context)
                    .isEqualTo(instrNo.trim());
        }

        if (instrDate != null && !instrDate.isBlank()) {
            soft.assertThat(row.getChqdt())
                    .as("[Level 2] Instrument Date expected [" + instrDate.trim() + "] but finvchdet.chqdt is null" + context)
                    .isNotNull();
            if (row.getChqdt() != null) {
                LocalDate expectedDate = parseInstrDate(instrDate);
                soft.assertThat(row.getChqdt().toLocalDate())
                        .as("[Level 2] finvchdet.chqdt date [" + row.getChqdt().toLocalDate() + "] != instrumentDate [" + expectedDate + "]" + context)
                        .isEqualTo(expectedDate);
                soft.assertThat(row.getChqdt().toLocalTime())
                        .as("[Level 2] finvchdet.chqdt has unexpected time [" + row.getChqdt().toLocalTime() + "]" + context)
                        .isEqualTo(LocalTime.MIDNIGHT);
            }
        }
    }

    // ── tds_app assertion (Y = TDS provided, N = not provided) ───────────────

    private static void validateTdsApp(SoftAssertions soft, List<FinVchDet> rows,
                                        boolean tdsProvided, long vchcode) {
        String expected = tdsProvided ? "Y" : "N";
        for (FinVchDet row : rows) {
            soft.assertThat(row.getTdsApp())
                    .as("[Level 2] finvchdet.tds_app expected [" + expected
                            + "] (tdsProvided=" + tdsProvided + ")"
                            + " for vchcode [" + vchcode + "] slcode=" + row.getSlcode())
                    .isEqualToIgnoringCase(expected);
        }
    }

    // ── Sub-ledger line amount check (Dr side) ────────────────────────────────

    private void validateSubLedgerLine(SoftAssertions soft, SubLedgerInformation line,
                                        List<FinVchDet> rows, long vchcode,
                                        MergedFinanceApVoucherData data) {
        BigDecimal dr = line.getAmountDr();  // AP: Debit amount (gross)
        if (dr == null) return;

        // For Advance vouchers with TDS, the DB stores Net Payable (gross − TDS), not the gross amount.
        if (data.isTdsProvided() && data.isOnAccount()) {
            Optional<BigDecimal> netPayable = data.getNetPayableForLine(line.getLineNo());
            if (netPayable.isPresent()) {
                dr = netPayable.get();
                ReportLogger.info("[Level 2] Advance+TDS: using NetPayable=" + dr
                        + " for lineNo='" + line.getLineNo() + "' (DB damount validation)");
            }
        }

        final BigDecimal expectedAmount = dr;
        FinVchDet match = rows.stream()
                .filter(d -> d.getDamount() != null && d.getDamount().compareTo(expectedAmount) == 0)
                .findFirst().orElse(null);
        soft.assertThat(match)
                .as("[Level 2] No finvchdet row with damount=" + expectedAmount + " for vchcode [" + vchcode + "]")
                .isNotNull();
        if (match != null && line.getLedgerNarration() != null
                && !line.getLedgerNarration().isBlank())
            soft.assertThat(match.getNarration())
                    .as("[Level 2] finvchdet.narration mismatch")
                    .isEqualTo(line.getLedgerNarration());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FinSl resolveFinSl(SubLedgerInformation line) {
        if (line.getSubLedger() == null || line.getSubLedger().isBlank()) return null;
        if (line.getId() != null && !line.getId().isBlank()) {
            return slDao.findBySlnameAndSlid(line.getSubLedger(), line.getId());
        }
        List<FinSl> matches = slDao.findBySlname(line.getSubLedger());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static String[][] buildDetTable(List<FinVchDet> rows,
                                             Map<Long, String> slNames,
                                             Map<Long, String> glNames) {
        String[] headers = {"vchcode", "slcode", "slname", "glcode", "glname",
                            "refno", "damount", "camount",
                            "chqno", "chqdt", "payment_mode", "tds_app", "narration"};
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
                tdsAppLabel(r.getTdsApp()),  str(r.getNarration())
            };
        }
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }

    private static String tdsAppLabel(String v) {
        if ("Y".equalsIgnoreCase(v)) return "true";
        if ("N".equalsIgnoreCase(v)) return "false";
        return v == null ? "—" : v;
    }

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
