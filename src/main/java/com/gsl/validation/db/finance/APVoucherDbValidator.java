package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import org.testng.Assert;

import java.util.Collections;
import java.util.List;

/**
 * Orchestrator — AP Voucher DB Validation.
 *
 * Execution order:
 *   Level 1  FinVchMainApValidator   BLOCKING  (AP-specific: camount check)
 *   Level 2  FinVchDetApValidator    BLOCKING  (AP-specific: damount/Dr comparison)
 *   Level 3  FinPostValidator        BLOCKING  (Shared — scoped to vchno)
 *   Level 4a FinTagApValidator        COLLECT   (AP-specific: uses AP SelectDocument)
 */
public class APVoucherDbValidator {

    private final FinVchMainApValidator  finVchMainApValidator;
    private final FinVchDetApValidator   finVchDetApValidator;
    private final FinPostValidator       finPostValidator;
    private final FinTagApValidator      finTagApValidator;

    public APVoucherDbValidator() {
        this.finVchMainApValidator = new FinVchMainApValidator();
        this.finVchDetApValidator  = new FinVchDetApValidator();
        this.finPostValidator      = new FinPostValidator();
        this.finTagApValidator     = new FinTagApValidator();
    }

    public void validate(ApVoucherDbContext context) {
        Assert.assertNotNull(context, "ApVoucherDbContext must not be null");
        Assert.assertNotNull(context.getVoucherNo(),
                "voucherNo is null — AP voucher creation may have failed");
        Assert.assertFalse(context.getVoucherNo().isBlank(), "voucherNo is blank");

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AP Voucher DB Validation START — [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

        ExtentLogger.info("── Level 1 : finvchmain ──");
        FinVchMain header = finVchMainApValidator.validate(context);

        ExtentLogger.info("── Level 2 : finvchdet ──");
        finVchDetApValidator.validate(context, header);

        // FinPostValidator only uses voucherNo — a minimal shim is safe here.
        VoucherDbContext finPostCtx = VoucherDbContext.builder()
                .voucherNo(context.getVoucherNo())
                .preState(Collections.emptyMap())
                .postState(Collections.emptyMap())
                .build();

        ExtentLogger.info("── Level 3 : finpost (GL balance) ──");
        List<FinPost> postEntries = finPostValidator.validate(finPostCtx, header);

        ExtentLogger.info("── Level 4a: fintag (adjustment tagging) ──");
        finTagApValidator.validate(context, header, postEntries);

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AP Voucher DB Validation COMPLETE — ALL PASSED [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

        renderBusinessSummary(context);
    }

    private static void renderBusinessSummary(ApVoucherDbContext ctx) {
        if (!ExtentManager.hasTest()) return;

        MergedFinanceApVoucherData data = ctx.getTestData();
        boolean hasCheque = data != null
                && data.getPaymentInformationList() != null
                && data.getPaymentInformationList().stream().anyMatch(p ->
                        p.getPaymentMode() != null
                        && p.getPaymentMode().toString().toUpperCase().contains("CHEQUE"));
        boolean hasRefSite = data != null
                && data.getReferenceSitewiseFIFOList() != null
                && !data.getReferenceSitewiseFIFOList().isEmpty();

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family:monospace;font-size:13px;line-height:2;padding:10px 12px;")
            .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;'>");
        html.append("<b style='font-size:14px;'>BUSINESS VALIDATION</b><br/>");
        html.append("&#10003;&nbsp; Voucher created &mdash; Document No: <b>")
            .append(escHtml(ctx.getVoucherNo())).append("</b><br/>");
        html.append("&#10003;&nbsp; Amount matched (finvchmain.camount verified)<br/>");
        html.append("&#10003;&nbsp; Line amounts validated (finvchdet.damount per SL row)<br/>");
        html.append("&#10003;&nbsp; GL balanced (finpost entries present and balanced)<br/>");
        html.append("&#10003;&nbsp; Adjustment tagged (fintag entries linked to voucher)<br/>");
        if (hasCheque) {
            html.append("&#10003;&nbsp; Cheque number persisted (chqno + chqdt in finvchdet)<br/>");
        }
        if (hasRefSite) {
            html.append("&#10003;&nbsp; Reference site mapped (ref_admsite_name in finvchdet)<br/>");
        }
        html.append("</div>");

        ExtentLogger.infoHtmlOnChildNode("&#9989; Business Validation Summary", html.toString());
    }

    private static String escHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
