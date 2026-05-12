package com.gsl.reports;

import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes a structured Extent Report description block for an AR Voucher test.
 *
 * <p>Call {@link #log(MergedFinanceArVoucherData)} immediately after lazy loading
 * (after {@code loadFreshForScenario}) so that runtime-generated values such as
 * Reference, Remarks, Sub-Ledgers, and Select Documents are all fully populated.
 *
 * <p>Output sections logged to the current Extent test node:
 * <ol>
 *   <li>Header table  — scenario metadata + document header fields (incl. Reference, Remarks)</li>
 *   <li>Sub-Ledgers table — one row per SubLedgerInformation (Adj Type, Amount, Payment Mode)</li>
 *   <li>Select Documents table — only when selectDocumentList is non-empty</li>
 *   <li>Payment Information table — only when paymentInformationList is non-empty</li>
 * </ol>
 */
public final class ArVoucherExtentDescriptionLogger {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ArVoucherExtentDescriptionLogger() {}

    /**
     * Logs all description tables to the current Extent test node AND to the console.
     * Must be called after lazy loading so all fields are populated.
     *
     * @param data fully populated {@link MergedFinanceArVoucherData}
     */
    public static void log(MergedFinanceArVoucherData data) {
        printToConsole(data);
        logHeaderTable(data);
        logSubLedgersTable(data);
        logSelectDocsTable(data);
        logPaymentsTable(data);
    }

    // ─── Console print ────────────────────────────────────────────────────────

    private static void printToConsole(MergedFinanceArVoucherData data) {
        var master = data.getMasterTestData();
        var doc    = data.getDocumentInformation();
        String docDate = doc.getDocumentdate() != null
                ? doc.getDocumentdate().format(DATE_FMT) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("[ArVoucherExtentDescriptionLogger] Test Data Summary\n");
        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("  Scenario        = %s%n", nullSafe(data.getScenarioType())));
        sb.append(String.format("  TestCase ID     = %s%n", nullSafe(master.getTestcaseid())));
        sb.append(String.format("  TestCase Name   = %s%n", nullSafe(master.getTestcasename())));
        sb.append(String.format("  Owner Site      = %s%n", nullSafe(doc.getOwnersite())));
        sb.append(String.format("  Cash/Bank GL    = %s%n", nullSafe(doc.getCashbank())));
        sb.append(String.format("  Doc Scheme      = %s%n", nullSafe(doc.getVoucherno())));
        sb.append(String.format("  Doc Date        = %s%n", docDate));
        sb.append(String.format("  Reference       = %s%n", nullSafe(doc.getReference())));
        sb.append(String.format("  Remarks         = %s%n", nullSafe(doc.getRemarks())));
        sb.append(String.format("  Ignore&Proceed  = %s%n", data.isNeedsIgnoreProceed() ? "YES" : "NO"));

        // Sub-Ledgers
        sb.append("------------------------------------------------------------\n");
        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        if (sls == null || sls.isEmpty()) {
            sb.append("  Sub-Ledgers     : (none)\n");
        } else {
            sb.append("  Sub-Ledgers     :\n");
            for (SubLedgerInformation sl : sls) {
                String pmt = sl.getPaymentSelection() != null
                        ? sl.getPaymentSelection().toString() : "";
                sb.append(String.format("    [%s] %s (id=%s) | type=%s | GL=%s | adjType=%s | amount=%s | pmt=%s%n",
                        nullSafe(sl.getLineNo()),
                        nullSafe(sl.getSubLedger()),
                        nullSafe(sl.getId()),
                        nullSafe(sl.getSlType()),
                        nullSafe(sl.getGeneralLedger()),
                        nullSafe(sl.getAdjustmentType()),
                        sl.getAmountCr() != null ? sl.getAmountCr().toPlainString() : "",
                        pmt));
            }
        }

        // Select Documents
        List<SelectDocument> docs = data.getSelectDocumentList();
        if (docs != null && !docs.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  Select Docs     :\n");
            for (SelectDocument d : docs) {
                String dDate = d.getDocumentDate() != null
                        ? d.getDocumentDate().format(DATE_FMT) : "";
                sb.append(String.format("    lineNo=%s | docNo=%s | date=%s | type=%s | amount=%s%n",
                        nullSafe(d.getLineNo()),
                        nullSafe(d.getDocumentNumber()),
                        dDate,
                        nullSafe(d.getDocumentType()),
                        d.getAdjustmentAmount() != null ? d.getAdjustmentAmount().toPlainString() : ""));
            }
        }

        // Payment Information
        List<PaymentInformation> payments = data.getPaymentInformationList();
        if (payments != null && !payments.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  Payment Info    :\n");
            for (PaymentInformation p : payments) {
                sb.append(String.format("    lineNo=%s | mode=%s | instrumentNo=%s | date=%s%n",
                        nullSafe(p.getLineNo()),
                        p.getPaymentMode() != null ? p.getPaymentMode().toString() : "",
                        nullSafe(p.getInstrumentNo()),
                        nullSafe(p.getInstrumentDate())));
            }
        }

        sb.append("============================================================");
        Log.info(sb.toString());
    }

    // ─── Section 1: Header ────────────────────────────────────────────────────

    private static void logHeaderTable(MergedFinanceArVoucherData data) {
        var master = data.getMasterTestData();
        var doc    = data.getDocumentInformation();
        String docDate = doc.getDocumentdate() != null
                ? doc.getDocumentdate().format(DATE_FMT) : "";
        String[][] table = {
            { "Field",            "Value" },
            { "Scenario",         nullSafe(data.getScenarioType()) },
            { "TestCase ID",      nullSafe(master.getTestcaseid()) },
            { "TestCase Name",    nullSafe(master.getTestcasename()) },
            { "Owner Site",       nullSafe(doc.getOwnersite()) },
            { "Cash/Bank GL",     nullSafe(doc.getCashbank()) },
            { "Doc Scheme",       nullSafe(doc.getVoucherno()) },
            { "Doc Date",         docDate },
            { "Reference",        nullSafe(doc.getReference()) },
            { "Remarks",          nullSafe(doc.getRemarks()) },
            { "Ignore & Proceed", data.isNeedsIgnoreProceed() ? "YES" : "NO" },
        };
        ExtentLogger.info("<b>Test Data &#8212; Header</b>");
        ExtentLogger.infoTable(table);
    }

    // ─── Section 2: Sub-Ledgers ───────────────────────────────────────────────

    private static void logSubLedgersTable(MergedFinanceArVoucherData data) {
        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        if (sls == null || sls.isEmpty()) {
            ExtentLogger.info("<i>Sub-Ledgers: (none)</i>");
            return;
        }
        String[][] table = new String[1 + sls.size()][7];
        table[0] = new String[]{ "Line#", "Sub-Ledger", "SL Type", "GL", "Adj Type", "Amount Cr", "Payment Mode" };
        for (int i = 0; i < sls.size(); i++) {
            SubLedgerInformation sl = sls.get(i);
            table[i + 1] = new String[]{
                nullSafe(sl.getLineNo()),
                nullSafe(sl.getSubLedger()),
                nullSafe(sl.getSlType()),
                nullSafe(sl.getGeneralLedger()),
                nullSafe(sl.getAdjustmentType()),
                sl.getAmountCr() != null ? sl.getAmountCr().toPlainString() : "",
                sl.getPaymentSelection() != null ? sl.getPaymentSelection().toString() : ""
            };
        }
        ExtentLogger.info("<b>Sub-Ledgers</b>");
        ExtentLogger.infoTable(table);
    }

    // ─── Section 3: Selected Documents ───────────────────────────────────────

    private static void logSelectDocsTable(MergedFinanceArVoucherData data) {
        List<SelectDocument> docs = data.getSelectDocumentList();
        if (docs == null || docs.isEmpty()) return;
        String[][] table = new String[1 + docs.size()][5];
        table[0] = new String[]{ "SL Line#", "Doc No", "Doc Date", "Doc Type", "Adj Amount" };
        for (int i = 0; i < docs.size(); i++) {
            SelectDocument d = docs.get(i);
            String docDate = d.getDocumentDate() != null
                    ? d.getDocumentDate().format(DATE_FMT) : "";
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

    // ─── Section 4: Payment Information ──────────────────────────────────────

    private static void logPaymentsTable(MergedFinanceArVoucherData data) {
        List<PaymentInformation> payments = data.getPaymentInformationList();
        if (payments == null || payments.isEmpty()) return;
        String[][] table = new String[1 + payments.size()][4];
        table[0] = new String[]{ "SL Line#", "Mode", "Instrument No", "Instrument Date" };
        for (int i = 0; i < payments.size(); i++) {
            PaymentInformation p = payments.get(i);
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

    // ─── Null-safe helper ─────────────────────────────────────────────────────

    /** Returns the value as a String, or blank string if null. */
    private static String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }
}
