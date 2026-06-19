package com.gsl.validation.db.finance;

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

    public FinVchMain validate(VoucherDbContext context) {
        String schemeDocno = context.getVoucherNo();
        MergedFinanceArVoucherData data = context.getTestData();
        DocumentInformation doc = data.getDocumentInformation();

        FinVchMain h = fetchAndBlock(schemeDocno);
        ActualResultsHolder.get().setHeader(h);

        validateCommonHeaderFields(h, doc, context.getSaveStartTime());

        // AR-specific: debit side carries the voucher total
        SoftAssertions.assertSoftly(soft ->
            soft.assertThat(h.getDamount())
                    .as("[Level 1] finvchmain.damount is null")
                    .isNotNull()
        );

        ReportLogger.info("<b>finvchmain row:</b>");
        ReportLogger.infoTable(buildMainTable(h));
        ReportLogger.info("Level 1 — PASSED: scheme_docno [" + schemeDocno
                + "] vchcode [" + h.getVchcode() + "]");
        return h;
    }
}
