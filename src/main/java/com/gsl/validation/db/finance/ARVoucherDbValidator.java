package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import org.testng.Assert;

import java.util.List;

/**
 * Orchestrator — AR Voucher DB Validation.
 * Finance module.
 *
 * Execution order:
 *   Level 1  FinVchMainValidator     BLOCKING  (Finance only)
 *   Level 2  FinVchDetValidator      BLOCKING  (Finance only)
 *   Level 3  FinPostValidator        BLOCKING  (Shared — scoped to vchno)
 *   Level 4a FinTagValidator         COLLECT   (Shared — scoped to postcode1)
 *   Level 4b AdjAmtCascadeValidator  DISABLED  (before/after adjamt delta — re-enable when test data covers invoice finpost rows)
 *
 * Extension points:
 *   Auto-TDS journal → add Level 5 FinJrnValidator (finjrnmain / finjrndet)
 *   Procurement PI   → add ProcurementDbValidator in validation/db/procurement/
 */
public class ARVoucherDbValidator {

    private final FinVchMainValidator finVchMainValidator;
    private final FinVchDetValidator  finVchDetValidator;
    private final FinPostValidator    finPostValidator;
    private final FinTagValidator     finTagValidator;

    public ARVoucherDbValidator() {
        this.finVchMainValidator = new FinVchMainValidator();
        this.finVchDetValidator  = new FinVchDetValidator();
        this.finPostValidator    = new FinPostValidator();
        this.finTagValidator     = new FinTagValidator();
    }

    public void validate(VoucherDbContext context) {
        Assert.assertNotNull(context, "VoucherDbContext must not be null");
        Assert.assertNotNull(context.getVoucherNo(),
                "voucherNo is null — voucher creation may have failed");
        Assert.assertFalse(context.getVoucherNo().isBlank(), "voucherNo is blank");

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AR Voucher DB Validation START — [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

        ExtentLogger.info("── Level 1 : finvchmain ──");
        FinVchMain header = finVchMainValidator.validate(context);

        ExtentLogger.info("── Level 2 : finvchdet ──");
        finVchDetValidator.validate(context, header);

        ExtentLogger.info("── Level 3 : finpost (GL balance) ──");
        List<FinPost> postEntries = finPostValidator.validate(context, header);

        ExtentLogger.info("── Level 4a: fintag (adjustment tagging) ──");
        finTagValidator.validate(context, header, postEntries);

        // Level 4b (AdjAmtCascadeValidator) — temporarily disabled.
        // Re-enable when test data includes invoice finpost rows with populated adjamt.

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AR Voucher DB Validation COMPLETE — ALL PASSED [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

        renderBusinessSummary(context);
    }

    private static void renderBusinessSummary(VoucherDbContext ctx) {
        if (!ExtentManager.hasTest()) return;

        com.gsl.testdata.dto.MergedFinanceArVoucherData data = ctx.getTestData();
        boolean hasCheque = data != null
                && data.getPaymentInformationList() != null
                && data.getPaymentInformationList().stream().anyMatch(p ->
                        p.getPaymentMode() != null
                        && p.getPaymentMode().toString().toUpperCase().contains("CHEQUE"));

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family:monospace;font-size:13px;line-height:2;padding:10px 12px;")
            .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;'>");
        html.append("<b style='font-size:14px;'>BUSINESS VALIDATION</b><br/>");
        html.append("&#10003;&nbsp; Voucher created &mdash; Document No: <b>")
            .append(escHtml(ctx.getVoucherNo())).append("</b><br/>");
        html.append("&#10003;&nbsp; Amount matched (finvchmain.damount verified)<br/>");
        html.append("&#10003;&nbsp; Line amounts validated (finvchdet.camount per SL row)<br/>");
        html.append("&#10003;&nbsp; GL balanced (finpost entries present and balanced)<br/>");
        html.append("&#10003;&nbsp; Adjustment tagged (fintag entries linked to voucher)<br/>");
        if (hasCheque) {
            html.append("&#10003;&nbsp; Cheque number persisted (chqno + chqdt in finvchdet)<br/>");
        }
        html.append("</div>");

        ExtentLogger.infoHtmlOnChildNode("&#9989; Business Validation Summary", html.toString());
    }

    private static String escHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
