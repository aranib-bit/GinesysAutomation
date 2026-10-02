package com.gsl.reports;

import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;

import java.math.BigDecimal;
import java.util.List;

/**
 * AR Voucher Extent description logger.
 *
 * <p>Extends {@link VoucherExtentDescriptionLogger} with AR-specific behaviour:
 * Amount Cr column and AR-specific positive Expected Results. No extra header flags,
 * no FIFO/TDS tables — the base no-op hooks cover the absent sections.
 *
 * <p>Call the static entry point {@link #log(MergedFinanceArVoucherData)} after lazy
 * loading — it delegates to the template-method base.
 */
public final class ArVoucherExtentDescriptionLogger
        extends VoucherExtentDescriptionLogger<MergedFinanceArVoucherData> {

    private static final ArVoucherExtentDescriptionLogger INSTANCE = new ArVoucherExtentDescriptionLogger();

    private ArVoucherExtentDescriptionLogger() {}

    /** Static entry point — call-sites are unchanged from the previous static-only design. */
    public static void log(MergedFinanceArVoucherData data) {
        INSTANCE.render(data);
    }

    // ── Abstract implementations ───────────────────────────────────────────────

    @Override
    protected String loggerLabel() { return "ArVoucherExtentDescriptionLogger"; }

    @Override
    protected String amountColumnHeader() { return "Amount Cr"; }

    @Override
    protected BigDecimal slAmount(VoucherSubLedgerRow sl) {
        return ((com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation) sl).getAmountCr();
    }

    @Override
    protected void appendExtraConsoleHeaderLines(MergedFinanceArVoucherData data, StringBuilder sb) {
        // AR has no extra flags beyond the shared Pmt Info Warning line
    }

    @Override
    protected void appendExtraHeaderRows(MergedFinanceArVoucherData data, List<String[]> rows) {
        // AR header table has no extra rows
    }

    @Override
    protected void logPositiveExpected(MergedFinanceArVoucherData data) {
        int slCount    = data.getSubLedgerInformationList()       != null ? data.getSubLedgerInformationList().size() : 0;
        boolean hasPmt = data.getPaymentInformationList()         != null && !data.getPaymentInformationList().isEmpty();
        boolean hasDoc = data.getSelectDocumentList()             != null && !data.getSelectDocumentList().isEmpty();
        boolean hasFifo= data.getReferenceSitewiseFIFOList()      != null && !data.getReferenceSitewiseFIFOList().isEmpty();

        String vchdet = slCount + " row(s): slcode, glcode, camount (credit), payment_mode"
                + (hasPmt ? ", chqno + chqdt validated for CHEQUE/NEFT lines" : "");

        String fintag = hasDoc  ? "Tag entries matching selected document adjustment amounts (COLLECT — non-blocking)"
                      : hasFifo ? "Tag entries matching FIFO set-off amounts (COLLECT — non-blocking)"
                      :            "Tag entries for FIFO set-off adjustments (COLLECT — non-blocking)";

        ExtentLogger.infoTable(new String[][]{
            { "Expected Outcome", "Details" },
            { "UI — Save",        "Document saved; Document No. displayed on form" },
            { "DB — finvchmain",  "1 record: ownersite, cashbank, voucherno, docdate, refno, remarks" },
            { "DB — finvchdet",   vchdet },
            { "DB — finpost",     "GL balance postings created and balanced (BLOCKING — test fails if absent)" },
            { "DB — fintag",      fintag },
        });
    }

    // ── Hooks ──────────────────────────────────────────────────────────────────

    @Override
    protected void appendExtraConsoleSections(MergedFinanceArVoucherData data, StringBuilder sb) {
        List<ReferenceSitewiseFIFOsetoff> fifoList = data.getReferenceSitewiseFIFOList();
        if (fifoList != null && !fifoList.isEmpty()) {
            sb.append("------------------------------------------------------------\n");
            sb.append("  Reference Site FIFO:\n");
            for (ReferenceSitewiseFIFOsetoff r : fifoList) {
                sb.append(String.format(
                        "    lineNo=%s | refSite=%s | site=%s | adjAmt=%s%n",
                        nullSafe(r.getLineNo()),
                        nullSafe(r.getReferenceSiteName()),
                        nullSafe(r.getSiteName()),
                        r.getAdjustmentAmount() != null ? r.getAdjustmentAmount().toPlainString() : ""));
            }
        }
    }

    @Override
    protected void logMiddleSections(MergedFinanceArVoucherData data) {
        logFifoTable(data);
    }

    // ── AR-specific table ──────────────────────────────────────────────────────

    /** Renders the Reference Site wise FIFO Set-Off table, or does nothing when the list is empty. */
    private void logFifoTable(MergedFinanceArVoucherData data) {
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
}
