package com.gsl.reports;

import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;

import java.math.BigDecimal;
import java.util.List;

/**
 * AP Voucher Extent description logger.
 *
 * <p>Extends {@link VoucherExtentDescriptionLogger} and provides AP-specific behaviour:
 * On Account / TDS flags in the header, the FIFO set-off table, the TDS &amp; Net Payable
 * table, and AP-specific positive Expected Results.
 *
 * <p>Call the static entry point {@link #log(MergedFinanceApVoucherData)} after lazy
 * loading — it delegates to the template-method base.
 */
public final class ApVoucherExtentDescriptionLogger
        extends VoucherExtentDescriptionLogger<MergedFinanceApVoucherData> {

    private static final ApVoucherExtentDescriptionLogger INSTANCE = new ApVoucherExtentDescriptionLogger();

    private ApVoucherExtentDescriptionLogger() {}

    /** Static entry point — call-sites are unchanged from the previous static-only design. */
    public static void log(MergedFinanceApVoucherData data) {
        INSTANCE.render(data);
    }

    // ── Abstract implementations ───────────────────────────────────────────────

    @Override
    protected String loggerLabel() { return "ApVoucherExtentDescriptionLogger"; }

    @Override
    protected String amountColumnHeader() { return "Amount Dr"; }

    @Override
    protected BigDecimal slAmount(VoucherSubLedgerRow sl) {
        return ((com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation) sl).getAmountDr();
    }

    @Override
    protected void appendExtraConsoleHeaderLines(MergedFinanceApVoucherData data, StringBuilder sb) {
        sb.append(String.format("  On Account      = %s%n", data.isOnAccount() ? "YES" : "NO"));
        sb.append(String.format("  TDS Provided    = %s%n", data.isTdsProvided() ? "YES" : "NO"));
    }

    @Override
    protected void appendExtraHeaderRows(MergedFinanceApVoucherData data, List<String[]> rows) {
        rows.add(new String[]{ "On Account",   data.isOnAccount()   ? "YES" : "NO" });
        rows.add(new String[]{ "TDS Provided", data.isTdsProvided() ? "YES" : "NO" });
    }

    @Override
    protected void logPositiveExpected(MergedFinanceApVoucherData data) {
        int slCount     = data.getSubLedgerInformationList() != null ? data.getSubLedgerInformationList().size() : 0;
        boolean onAcct  = data.isOnAccount();
        boolean tds     = data.isTdsProvided();
        boolean hasPmt  = data.getPaymentInformationList()   != null && !data.getPaymentInformationList().isEmpty();
        boolean hasDoc  = data.getSelectDocumentList()        != null && !data.getSelectDocumentList().isEmpty();
        boolean hasFifo = data.getReferenceSitewiseFIFOList() != null && !data.getReferenceSitewiseFIFOList().isEmpty();

        String vchdet = slCount + " row(s): slcode, glcode, damount"
                + (tds  ? " (= NetPayable after TDS deduction)" : "")
                + ", payment_mode, tds_app=" + (tds ? "'Y'" : "'N'")
                + (hasPmt ? ", chqno + chqdt validated for CHEQUE/NEFT lines" : "");

        String fintag = hasDoc  ? "Tag entries matching selected document adjustment amounts (COLLECT — non-blocking)"
                      : hasFifo ? "Tag entries matching FIFO set-off amounts (COLLECT — non-blocking)"
                      :           "Tag entries for advance payment lines (COLLECT — non-blocking)";

        String onAcctDetail = onAcct    ? "YES — advance payment; no pending document selection required"
                            : hasDoc    ? "NO — documents manually selected from pending list and adjusted"
                            : hasFifo   ? "NO — FIFO set-off applied (reference sitewise FIFO if applicable)"
                            :             "NO — document adjustment method derived from scenario";

        ExtentLogger.infoTable(new String[][]{
            { "Expected Outcome",    "Details" },
            { "UI — Save",           "Document saved; Document No. displayed on form" },
            { "DB — finvchmain",     "1 record: ownersite, cashbank, voucherno, docdate, refno, remarks, onaccount=" + (onAcct ? "1" : "0") },
            { "DB — finvchdet",      vchdet },
            { "DB — finpost",        "GL balance postings created and balanced (BLOCKING — test fails if absent)" },
            { "DB — fintag",         fintag },
            { "Flag — On Account",   onAcctDetail },
            { "Flag — TDS Provided", tds ? "YES — tds_app='Y'; damount = NetPayable (gross − TDS)"
                                              : "NO — tds_app='N'; damount = gross AmountDr" },
        });
    }

    // ── Hooks ──────────────────────────────────────────────────────────────────

    @Override
    protected void appendExtraConsoleSections(MergedFinanceApVoucherData data, StringBuilder sb) {
        List<TdsAndNetPayable> tdsList = data.getTdsAndNetPayableList();
        if (tdsList != null && !tdsList.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  TDS & Net Payable:\n");
            for (TdsAndNetPayable t : tdsList) {
                sb.append(String.format(
                        "    lineNo=%s | tdsName=%s | grossPayable=%s | tdsApplicableAmt=%s | exceptionRate=%s%n",
                        nullSafe(t.getLineNo()),
                        nullSafe(t.getTdsName()),
                        t.getGrossPayable() != null ? t.getGrossPayable().toPlainString() : "",
                        t.getTdsApplicableAmount() != null ? t.getTdsApplicableAmount().toPlainString() : "",
                        nullSafe(t.getPopulateTdsExceptionRate())));
            }
        }
    }

    @Override
    protected void logMiddleSections(MergedFinanceApVoucherData data) {
        logFifoTable(data);
        logTdsTable(data);
    }

    // ── AP-specific tables ─────────────────────────────────────────────────────

    private void logFifoTable(MergedFinanceApVoucherData data) {
        List<ReferenceSitewiseFIFOsetoff> fifoList = data.getReferenceSitewiseFIFOList();
        if (fifoList == null || fifoList.isEmpty()) return;
        String[][] table = new String[1 + fifoList.size()][4];
        table[0] = new String[]{ "SL Line#", "Reference Site Name", "Site Name", "Adj Amount" };
        for (int i = 0; i < fifoList.size(); i++) {
            ReferenceSitewiseFIFOsetoff r = fifoList.get(i);
            table[i + 1] = new String[]{
                nullSafe(r.getLineNo()),
                nullSafe(r.getReferenceSiteName()),
                nullSafe(r.getSiteName()),
                r.getAdjustmentAmount() != null ? r.getAdjustmentAmount().toPlainString() : ""
            };
        }
        ExtentLogger.info("<b>Reference Site wise FIFO Set-Off</b>");
        ExtentLogger.infoTable(table);
    }

    private void logTdsTable(MergedFinanceApVoucherData data) {
        List<TdsAndNetPayable> tdsList = data.getTdsAndNetPayableList();
        if (tdsList == null || tdsList.isEmpty()) return;
        String[][] table = new String[1 + tdsList.size()][5];
        table[0] = new String[]{ "SL Line#", "TDS Name", "Gross Payable", "TDS Applicable Amt", "Exception Rate" };
        for (int i = 0; i < tdsList.size(); i++) {
            TdsAndNetPayable t = tdsList.get(i);
            table[i + 1] = new String[]{
                nullSafe(t.getLineNo()),
                nullSafe(t.getTdsName()),
                t.getGrossPayable() != null ? t.getGrossPayable().toPlainString() : "",
                t.getTdsApplicableAmount() != null ? t.getTdsApplicableAmount().toPlainString() : "",
                nullSafe(t.getPopulateTdsExceptionRate())
            };
        }
        ExtentLogger.info("<b>TDS &amp; Net Payable</b>");
        ExtentLogger.infoTable(table);
    }
}
