package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
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

    /**
     * @param deferredSoft shared SoftAssertions from the orchestrator;
     *                     assertAll() is called there after all levels complete
     */
    public FinVchMain validate(ApVoucherDbContext context, SoftAssertions deferredSoft) {
        String schemeDocno = context.getVoucherNo();
        MergedFinanceApVoucherData data = context.getTestData();
        DocumentInformation doc = data.getDocumentInformation();

        FinVchMain h = fetchAndBlock(schemeDocno);
        ActualResultsHolder.get().setHeader(h);
        ReportLogger.trace("[finvchmain] fetched: scheme_docno=" + schemeDocno
                + " vchcode=" + h.getVchcode()
                + " camount=" + h.getCamount()
                + " nartext=" + h.getNartext()
                + " vchdt=" + h.getVchdt());

        String fieldSummary = validateCommonHeaderFields(h, doc, context.getSaveStartTime(), deferredSoft);

        // AP-specific: credit side carries the voucher total (also deferred)
        deferredSoft.assertThat(h.getCamount())
                .as("[Level 1] finvchmain.camount is null")
                .isNotNull();

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finvchmain", buildMainTable(h));
        ReportLogger.infoHtml(fieldSummary);
        ReportLogger.db("Level 1 — field checks deferred; remaining levels will run regardless");
        return h;
    }
}
