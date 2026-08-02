package com.gsl.reports;

import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.dto.VoucherReportData;
import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Template-method base for all Finance voucher Extent description loggers.
 *
 * <p>Call the static {@code log()} facade on the concrete subclass immediately after
 * lazy loading so all fields are populated; it delegates to {@link #render(VoucherReportData)}.
 *
 * <p>Section order is fixed here and enforced for every voucher type:
 * <ol>
 *   <li>Console summary (Log4j)</li>
 *   <li>Header table (common fields + voucher-specific flag rows)</li>
 *   <li>Expected Results table (negative branch shared; positive delegated)</li>
 *   <li>Sub-Ledgers table (shared structure; amount column delegated)</li>
 *   <li>Selected Documents table (fully shared)</li>
 *   <li>Middle sections hook (AP overrides: FIFO + TDS tables)</li>
 *   <li>Payment Information table (fully shared)</li>
 * </ol>
 *
 * @param <T> the merged voucher DTO type (must implement {@link VoucherReportData})
 */
public abstract class VoucherExtentDescriptionLogger<T extends VoucherReportData> {

    protected static final java.time.format.DateTimeFormatter DATE_FMT = VoucherReportUtils.DATE_FMT;

    // ── Template method ────────────────────────────────────────────────────────

    public final void render(T data) {
        printToConsole(data);
        logHeaderTable(data);
        logExpectedResultsSection(data);
        logSubLedgersTable(data);
        logSelectDocsTable(data);
        logMiddleSections(data);
        logPaymentsTable(data);
    }

    // ── Abstract — voucher-specific ────────────────────────────────────────────

    /** Short label used in the console header line (e.g. {@code "ApVoucherExtentDescriptionLogger"}). */
    protected abstract String loggerLabel();

    /** Column header for the amount column in the sub-ledgers table (e.g. {@code "Amount Dr"}). */
    protected abstract String amountColumnHeader();

    /** Extracts the voucher-direction amount from a sub-ledger row (Dr for AP, Cr for AR). */
    protected abstract BigDecimal slAmount(VoucherSubLedgerRow sl);

    /** Appends voucher-specific lines between Remarks and Payment Info Warning in the console. */
    protected abstract void appendExtraConsoleHeaderLines(T data, StringBuilder sb);

    /** Appends voucher-specific rows (e.g. On Account, TDS Provided) to the header table. */
    protected abstract void appendExtraHeaderRows(T data, List<String[]> rows);

    /** Renders the positive-scenario Expected Results table (voucher-specific content). */
    protected abstract void logPositiveExpected(T data);

    // ── No-op hooks ────────────────────────────────────────────────────────────

    /** Appends extra console sections after Select Docs (AP overrides to add TDS block). */
    protected void appendExtraConsoleSections(T data, StringBuilder sb) {}

    /** Logs sections between Select Docs and Payments (AP overrides: FIFO + TDS tables). */
    protected void logMiddleSections(T data) {}

    // ── Console ────────────────────────────────────────────────────────────────

    private void printToConsole(T data) {
        var master = data.getMasterTestData();
        var doc    = data.getDocumentInformation();
        String docDate = doc.getDocumentdate() != null ? doc.getDocumentdate().format(DATE_FMT) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("[").append(loggerLabel()).append("] Test Data Summary\n");
        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("  Scenario        = %s%n", nullSafe(data.getScenarioTypeLabel())));
        sb.append(String.format("  TestCase ID     = %s%n", nullSafe(master.getTestcaseid())));
        sb.append(String.format("  TestCase Name   = %s%n", nullSafe(master.getTestcasename())));
        sb.append(String.format("  Owner Site      = %s%n", nullSafe(doc.getOwnersite())));
        sb.append(String.format("  Cash/Bank GL    = %s%n", nullSafe(doc.getCashbank())));
        sb.append(String.format("  Doc Scheme      = %s%n", nullSafe(doc.getVoucherno())));
        sb.append(String.format("  Doc Date        = %s%n", docDate));
        sb.append(String.format("  Reference       = %s%n", nullSafe(doc.getReference())));
        sb.append(String.format("  Remarks         = %s%n", nullSafe(doc.getRemarks())));
        appendExtraConsoleHeaderLines(data, sb);
        sb.append(String.format("  Pmt Info Warning= %s%n", data.isNeedsPaymentInfoWarning() ? "YES" : "NO"));

        // Sub-Ledgers
        sb.append("------------------------------------------------------------\n");
        List<? extends VoucherSubLedgerRow> sls = data.getSubLedgerInformationList();
        if (sls == null || sls.isEmpty()) {
            sb.append("  Sub-Ledgers     : (none)\n");
        } else {
            sb.append("  Sub-Ledgers     :\n");
            for (VoucherSubLedgerRow sl : sls) {
                BigDecimal amt = slAmount(sl);
                sb.append(String.format(
                        "    [%s] %s (id=%s) | slClass=%s | GL=%s | adjType=%s | amount=%s | pmt=%s%n",
                        nullSafe(sl.getLineNo()),
                        nullSafe(sl.getSubLedger()),
                        nullSafe(sl.getId()),
                        nullSafe(sl.getSlType()),
                        nullSafe(sl.getGeneralLedger()),
                        nullSafe(sl.getAdjustmentType()),
                        amt != null ? amt.toPlainString() : "",
                        sl.getPaymentSelection() != null ? sl.getPaymentSelection().toString() : ""));
            }
        }

        // Select Documents
        List<? extends VoucherSelectDocRow> docs = data.getSelectDocumentList();
        if (docs != null && !docs.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  Select Docs     :\n");
            for (VoucherSelectDocRow d : docs) {
                String dDate = d.getDocumentDate() != null ? d.getDocumentDate().format(DATE_FMT) : "";
                sb.append(String.format(
                        "    lineNo=%s | docNo=%s | date=%s | type=%s | amount=%s%n",
                        nullSafe(d.getLineNo()),
                        nullSafe(d.getDocumentNumber()),
                        dDate,
                        nullSafe(d.getDocumentType()),
                        d.getAdjustmentAmount() != null ? d.getAdjustmentAmount().toPlainString() : ""));
            }
        }

        appendExtraConsoleSections(data, sb);

        // Payment Information
        List<? extends VoucherPaymentInfo> payments = data.getPaymentInformationList();
        if (payments != null && !payments.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  Payment Info    :\n");
            for (VoucherPaymentInfo p : payments) {
                sb.append(String.format(
                        "    lineNo=%s | mode=%s | instrumentNo=%s | date=%s%n",
                        nullSafe(p.getLineNo()),
                        p.getPaymentMode() != null ? p.getPaymentMode().toString() : "",
                        nullSafe(p.getInstrumentNo()),
                        nullSafe(p.getInstrumentDate())));
            }
        }

        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("  Expected Result = %s%n",
                data.getExpectedValidation() != null
                        ? "VALIDATION ERROR: " + data.getExpectedValidation().expectedMessage()
                        : "Successful save | finvchmain + finvchdet + finpost + fintag created"));
        sb.append("============================================================");
        Log.info(sb.toString());
    }

    // ── Section 1: Header ──────────────────────────────────────────────────────

    private void logHeaderTable(T data) {
        var master = data.getMasterTestData();
        var doc    = data.getDocumentInformation();
        String docDate = doc.getDocumentdate() != null ? doc.getDocumentdate().format(DATE_FMT) : "";

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{ "Field",            "Value" });
        rows.add(new String[]{ "Scenario",          nullSafe(data.getScenarioTypeLabel()) });
        rows.add(new String[]{ "TestCase ID",       nullSafe(master.getTestcaseid()) });
        rows.add(new String[]{ "TestCase Name",     nullSafe(master.getTestcasename()) });
        rows.add(new String[]{ "Owner Site",        nullSafe(doc.getOwnersite()) });
        rows.add(new String[]{ "Cash/Bank GL",      nullSafe(doc.getCashbank()) + resolveCashBankType(doc.getCashbank()) });
        rows.add(new String[]{ "Doc Scheme",        nullSafe(doc.getVoucherno()) });
        rows.add(new String[]{ "Doc Date",          docDate });
        rows.add(new String[]{ "Current Date",      LocalDate.now().format(DATE_FMT) });
        rows.add(new String[]{ "Reference",         nullSafe(doc.getReference()) });
        rows.add(new String[]{ "Remarks",           nullSafe(doc.getRemarks()) });
        appendExtraHeaderRows(data, rows);
        rows.add(new String[]{ "Payment Info Warning", data.isNeedsPaymentInfoWarning() ? "YES" : "NO" });

        ExtentLogger.info("<b>Test Data &#8212; Header</b>");
        ExtentLogger.infoTable(rows.toArray(new String[0][]));
    }

    // ── Section 1b: Expected Results ──────────────────────────────────────────

    private void logExpectedResultsSection(T data) {
        ExtentLogger.info("<b>Expected Results</b>");
        if (data.getExpectedValidation() != null) {
            ExtentLogger.infoTable(new String[][]{
                { "Expected Outcome",      "Details" },
                { "UI — Validation Error",  data.getExpectedValidation().expectedMessage() },
                { "DB — finvchmain",        "No record created (save blocked by UI validation)" },
                { "DB — finvchdet",         "No record created" },
                { "DB — finpost",           "No posting created" },
                { "DB — fintag",            "No tag entries created" },
            });
            return;
        }
        logPositiveExpected(data);
    }

    // ── Section 2: Sub-Ledgers ─────────────────────────────────────────────────

    private void logSubLedgersTable(T data) {
        List<? extends VoucherSubLedgerRow> sls = data.getSubLedgerInformationList();
        String amtCol = amountColumnHeader();
        ExtentLogger.info("<b>Sub-Ledgers</b>");
        if (sls == null || sls.isEmpty()) {
            ExtentLogger.infoTable(new String[][]{
                { "Line#", "Sub-Ledger", "Sub Ledger Id", "Sub Ledger Class Name", "GL", "Adj Type", amtCol, "Payment Mode" },
                { "—", "(intentionally blank for this scenario)", "", "", "", "", "", "" }
            });
            return;
        }
        String[][] table = new String[1 + sls.size()][8];
        table[0] = new String[]{ "Line#", "Sub-Ledger", "Sub Ledger Id", "Sub Ledger Class Name", "GL", "Adj Type", amtCol, "Payment Mode" };
        for (int i = 0; i < sls.size(); i++) {
            VoucherSubLedgerRow sl = sls.get(i);
            BigDecimal amt = slAmount(sl);
            table[i + 1] = new String[]{
                nullSafe(sl.getLineNo()),
                nullSafe(sl.getSubLedger()),
                nullSafe(sl.getId()),
                nullSafe(sl.getSlType()),
                nullSafe(sl.getGeneralLedger()),
                nullSafe(sl.getAdjustmentType()),
                amt != null ? amt.toPlainString() : "",
                sl.getPaymentSelection() != null ? sl.getPaymentSelection().toString() : ""
            };
        }
        ExtentLogger.infoTable(table);
    }

    // ── Section 3: Selected Documents ─────────────────────────────────────────

    protected final void logSelectDocsTable(T data) {
        List<? extends VoucherSelectDocRow> docs = data.getSelectDocumentList();
        if (docs == null || docs.isEmpty()) return;
        String[][] table = new String[1 + docs.size()][5];
        table[0] = new String[]{ "SL Line#", "Doc No", "Doc Date", "Doc Type", "Adj Amount" };
        for (int i = 0; i < docs.size(); i++) {
            VoucherSelectDocRow d = docs.get(i);
            String docDate = d.getDocumentDate() != null ? d.getDocumentDate().format(DATE_FMT) : "";
            table[i + 1] = new String[]{
                nullSafe(d.getLineNo()),
                nullSafe(d.getDocumentNumber()),
                docDate,
                nullSafe(d.getDocumentType()),
                d.getAdjustmentAmount() != null ? d.getAdjustmentAmount().toPlainString() : ""
            };
        }
        ExtentLogger.info("<b>Selected Documents</b>");
        ExtentLogger.infoTable(table);
    }

    // ── Section: Payment Information ───────────────────────────────────────────

    protected final void logPaymentsTable(T data) {
        List<? extends VoucherPaymentInfo> payments = data.getPaymentInformationList();
        if (payments == null || payments.isEmpty()) {
            ExtentLogger.info("<b>Payment Information</b>");
            ExtentLogger.infoTable(new String[][]{
                { "SL Line#", "Mode", "Instrument No", "Instrument Date" },
                { "—", "(intentionally blank for this scenario)", "", "" }
            });
            return;
        }
        String[][] table = new String[1 + payments.size()][4];
        table[0] = new String[]{ "SL Line#", "Mode", "Instrument No", "Instrument Date" };
        for (int i = 0; i < payments.size(); i++) {
            VoucherPaymentInfo p = payments.get(i);
            table[i + 1] = new String[]{
                nullSafe(p.getLineNo()),
                p.getPaymentMode() != null ? p.getPaymentMode().toString() : "",
                nullSafe(p.getInstrumentNo()),
                nullSafe(p.getInstrumentDate())
            };
        }
        ExtentLogger.info("<b>Payment Information</b>");
        ExtentLogger.infoTable(table);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    protected static String nullSafe(Object value)           { return VoucherReportUtils.nullSafe(value); }
    protected static String resolveCashBankType(String name) { return VoucherReportUtils.resolveCashBankType(name); }
}
