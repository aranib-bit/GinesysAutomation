package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import org.assertj.core.api.SoftAssertions;
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

        ActualResultsHolder holder = ActualResultsHolder.get();
        holder.setVoucherNo(context.getVoucherNo());
        holder.setVoucherType("AR");
        holder.setTestData(context.getTestData());

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AR Voucher DB Validation START — [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

        // Level 1 field assertions are deferred so that a nartext/vchdt mismatch does NOT
        // block Levels 2–4. assertAll() is called after Level 4a completes.
        SoftAssertions deferredLevel1 = new SoftAssertions();

        ExtentLogger.info("── Level 1 : finvchmain ──");
        FinVchMain header = finVchMainValidator.validate(context, deferredLevel1);

        ExtentLogger.info("── Level 2 : finvchdet ──");
        finVchDetValidator.validate(context, header);

        ExtentLogger.info("── Level 3 : finpost (GL balance) ──");
        List<FinPost> postEntries = finPostValidator.validate(context, header);

        ExtentLogger.info("── Level 4a: fintag (adjustment tagging) ──");
        finTagValidator.validate(context, header, postEntries);

        // Level 4b (AdjAmtCascadeValidator) — temporarily disabled.
        // Re-enable when test data includes invoice finpost rows with populated adjamt.

        // Now surface any Level 1 field mismatches collected above
        ExtentLogger.info("── Level 1 deferred assertions ──");
        deferredLevel1.assertAll();

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AR Voucher DB Validation COMPLETE — ALL PASSED [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");

    }
}
