package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ExtentLogger;
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
    }
}
