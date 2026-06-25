package com.gsl.reports;

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
        int slCount    = data.getSubLedgerInformationList() != null ? data.getSubLedgerInformationList().size() : 0;
        boolean hasPmt = data.getPaymentInformationList()   != null && !data.getPaymentInformationList().isEmpty();
        boolean hasDoc = data.getSelectDocumentList()        != null && !data.getSelectDocumentList().isEmpty();

        String vchdet = slCount + " row(s): slcode, glcode, camount (credit), payment_mode"
                + (hasPmt ? ", chqno + chqdt validated for CHEQUE/NEFT lines" : "");

        String fintag = hasDoc
                ? "Tag entries matching selected document adjustment amounts (COLLECT — non-blocking)"
                : "Tag entries for FIFO set-off adjustments (COLLECT — non-blocking)";

        ExtentLogger.infoTable(new String[][]{
            { "Expected Outcome", "Details" },
            { "UI — Save",        "Document saved; Document No. displayed on form" },
            { "DB — finvchmain",  "1 record: ownersite, cashbank, voucherno, docdate, refno, remarks" },
            { "DB — finvchdet",   vchdet },
            { "DB — finpost",     "GL balance postings created and balanced (BLOCKING — test fails if absent)" },
            { "DB — fintag",      fintag },
        });
    }
}
