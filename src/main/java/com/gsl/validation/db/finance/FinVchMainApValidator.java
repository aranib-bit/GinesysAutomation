package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import org.assertj.core.api.SoftAssertions;

/**
 * Level 1 — finvchmain Header Validation for AP Voucher.
 *
 * Blocking steps and common field assertions are inherited from FinVchMainBaseValidator.
 * This class adds only the AP-specific check: camount must not be null
 * (AP Voucher carries the total on the credit side of finvchmain).
 */
public class FinVchMainApValidator extends FinVchMainBaseValidator {

    public FinVchMain validate(ApVoucherDbContext context) {
        String schemeDocno = context.getVoucherNo();
        MergedFinanceApVoucherData data = context.getTestData();
        DocumentInformation doc = data.getDocumentInformation();

        FinVchMain h = fetchAndBlock(schemeDocno);

        validateCommonHeaderFields(h, doc, context.getSaveStartTime());

        // AP-specific: credit side carries the voucher total
        SoftAssertions.assertSoftly(soft ->
            soft.assertThat(h.getCamount())
                    .as("[Level 1] finvchmain.camount is null")
                    .isNotNull()
        );

        ReportLogger.info("<b>finvchmain row:</b>");
        ReportLogger.infoTable(buildMainTable(h));
        ReportLogger.info("Level 1 — PASSED: scheme_docno [" + schemeDocno
                + "] vchcode [" + h.getVchcode() + "]");
        return h;
    }
}
