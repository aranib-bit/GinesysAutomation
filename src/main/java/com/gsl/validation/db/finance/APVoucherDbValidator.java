package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import org.assertj.core.api.SoftAssertions;
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

        MergedFinanceApVoucherData apData = context.getTestData();
        ActualResultsHolder holder = ActualResultsHolder.get();
        holder.setVoucherNo(context.getVoucherNo());
        holder.setVoucherType("AP");
        holder.setTestData(apData);
        if (apData != null) {
            holder.setOnAccount(apData.isOnAccount());
            holder.setTdsProvided(apData.isTdsProvided());
        }

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AP Voucher DB Validation START — [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");
        ReportLogger.db("AP Voucher DB Validation START — [{}]", context.getVoucherNo());

        // Level 1 field assertions are deferred so that a nartext/vchdt mismatch does NOT
        // block Levels 2–4. assertAll() is called after Level 4a completes.
        SoftAssertions deferredLevel1 = new SoftAssertions();

        ExtentLogger.info("── Level 1 : finvchmain ──");
        ReportLogger.db("── Level 1 : finvchmain ──");
        ReportLogger.trace("[APValidator] Level 1 START — finvchmain header for voucherNo=" + context.getVoucherNo());
        FinVchMain header = finVchMainApValidator.validate(context, deferredLevel1);
        ReportLogger.trace("[APValidator] Level 1 DONE — vchcode=" + header.getVchcode()
                + " camount=" + header.getCamount());

        ExtentLogger.info("── Level 2 : finvchdet ──");
        ReportLogger.db("── Level 2 : finvchdet ──");
        ReportLogger.trace("[APValidator] Level 2 START — finvchdet lines for vchcode=" + header.getVchcode());
        finVchDetApValidator.validate(context, header);
        ReportLogger.trace("[APValidator] Level 2 DONE");

        // FinPostValidator only uses voucherNo — a minimal shim is safe here.
        VoucherDbContext finPostCtx = VoucherDbContext.builder()
                .voucherNo(context.getVoucherNo())
                .preState(Collections.emptyMap())
                .postState(Collections.emptyMap())
                .build();

        ExtentLogger.info("── Level 3 : finpost (GL balance) ──");
        ReportLogger.db("── Level 3 : finpost (GL balance) ──");
        ReportLogger.trace("[APValidator] Level 3 START — finpost GL balance for vchcode=" + header.getVchcode());
        List<FinPost> postEntries = finPostValidator.validate(finPostCtx, header);
        ReportLogger.trace("[APValidator] Level 3 DONE — " + postEntries.size() + " GL entries");

        ExtentLogger.info("── Level 4a: fintag (adjustment tagging) ──");
        ReportLogger.db("── Level 4a: fintag (adjustment tagging) ──");
        ReportLogger.trace("[APValidator] Level 4a START — fintag adjustment tagging");
        finTagApValidator.validate(context, header, postEntries);
        ReportLogger.trace("[APValidator] Level 4a DONE");

        // Now surface any Level 1 field mismatches collected above
        ExtentLogger.info("── Level 1 deferred assertions ──");
        deferredLevel1.assertAll();

        ExtentLogger.info("══════════════════════════════════════════════════");
        ExtentLogger.info("AP Voucher DB Validation COMPLETE — ALL PASSED [" + context.getVoucherNo() + "]");
        ExtentLogger.info("══════════════════════════════════════════════════");
        ReportLogger.db("AP Voucher DB Validation COMPLETE — ALL PASSED [{}]", context.getVoucherNo());
        ReportLogger.trace("[APValidator] ALL LEVELS PASSED for voucherNo=" + context.getVoucherNo());

    }
}
