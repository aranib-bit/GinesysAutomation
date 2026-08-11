package com.gsl.validation.db.finance;

import com.gsl.enums.VoucherType;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import org.assertj.core.api.SoftAssertions;

import java.time.LocalDateTime;

/**
 * Level 1 — finvchmain Header Validation for General Voucher.
 *
 * <p>Blocking steps and common field assertions (vchdt, refno, nartext, entry_source,
 * creation_dt) are inherited from {@link FinVchMainBaseValidator} — GV's
 * {@code DocumentInformation} already implements {@code VoucherDocumentHeader}, so no
 * GV-specific field-mapping code is needed here. This class adds only the GV-specific
 * check: {@code damount} must not be null (GV posts like AR — the debit side carries the
 * voucher total; see {@code GeneralVoucherDbValidator}'s original Javadoc).
 *
 * <p>Mirrors {@link FinVchMainValidator} (AR)'s shape exactly, differing only in the
 * {@code DocumentInformation} package and {@link VoucherType#GENERAL}.
 */
public class FinVchMainGvValidator extends FinVchMainBaseValidator {

    /**
     * @param saveStartTime nullable — when null, {@code validateCommonHeaderFields}'s
     *                       creation_dt tolerance check is skipped (documented on that method)
     * @param deferredSoft  shared SoftAssertions from the orchestrator;
     *                      assertAll() is called there after all levels complete
     */
    public FinVchMain validate(String schemeDocno, DocumentInformation doc,
                                LocalDateTime saveStartTime, SoftAssertions deferredSoft) {

        FinVchMain h = fetchAndBlock(schemeDocno);
        ActualResultsHolder.get().setHeader(h);

        String fieldSummary = validateCommonHeaderFields(h, doc, saveStartTime, VoucherType.GENERAL, deferredSoft);

        // GV-specific: debit side carries the voucher total (also deferred)
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
