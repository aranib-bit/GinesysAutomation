package com.gsl.validation.db.finance;

import com.gsl.enums.VoucherType;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import org.assertj.core.api.SoftAssertions;

/**
 * Level 1 — finvchmain Header Validation for AR Voucher.
 *
 * Blocking steps and common field assertions (vchdt, refno, nartext,
 * entry_source, creation_dt) are inherited from FinVchMainBaseValidator.
 * This class adds only the AR-specific check: damount must not be null.
 */
public class FinVchMainValidator extends FinVchMainBaseValidator {

    /**
     * @param deferredSoft shared SoftAssertions from the orchestrator;
     *                     assertAll() is called there after all levels complete
     */
    public FinVchMain validate(VoucherDbContext context, SoftAssertions deferredSoft) {
        String schemeDocno = context.getVoucherNo();
        MergedFinanceArVoucherData data = context.getTestData();
        DocumentInformation doc = data.getDocumentInformation();

        FinVchMain h = fetchAndBlock(schemeDocno);
        ActualResultsHolder.get().setHeader(h);

        String fieldSummary = validateCommonHeaderFields(h, doc, context.getSaveStartTime(), VoucherType.AR, deferredSoft);

        // AR-specific: debit side carries the voucher total (also deferred)
        deferredSoft.assertThat(h.getDamount())
                .as("[Level 1] finvchmain.damount is null")
                .isNotNull();

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finvchmain", buildMainTable(h));
        ReportLogger.infoHtml(fieldSummary);
        ReportLogger.db("Level 1 — field checks deferred; remaining levels will run regardless");
        return h;
    }
}
