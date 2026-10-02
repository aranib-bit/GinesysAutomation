package com.gsl.validation.db.finance;

import com.gsl.enums.VoucherType;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
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

        // Round 6: "at least one of damount/camount must be > 0, both cannot be zero" — mirrors
        // FinPostValidator's identical check on finpost (no cross-column evaluation exists in the
        // DiffEngine/snapshot-diff layer, so this business rule is asserted here instead).
        boolean hasDebit = h.getDamount() != null && h.getDamount().compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = h.getCamount() != null && h.getCamount().compareTo(BigDecimal.ZERO) > 0;
        deferredSoft.assertThat(hasDebit || hasCredit)
                .as("[Level 1] finvchmain has zero on both damount and camount")
                .isTrue();

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("finvchmain", buildMainTable(h));
        // Item J (Round 9), fixed: this GV-specific fragment (this check's exact shape is GV-only,
        // per the base class's own Javadoc, so it doesn't belong in the shared summary) used to be
        // its OWN separate ReportLogger.infoHtml(...) call — rendering as a visibly separate Info
        // box with a gap below the Level 1 summary. Merged into the SAME box (one Info entry, no
        // gap) by inserting before fieldSummary's single closing </div> instead. Safe because
        // buildLevel1Summary emits exactly one top-level <div>...</div> with no nested divs.
        String damountLine = "&#10003;&nbsp; damount/camount: at least one side populated (<b>Dr "
                + (h.getDamount() == null ? "—" : h.getDamount())
                + "</b> / <b>Cr " + (h.getCamount() == null ? "—" : h.getCamount()) + "</b>)<br/>";
        ReportLogger.infoHtml(fieldSummary.replace("</div>", damountLine + "</div>"));
        ReportLogger.db("Level 1 — field checks deferred; remaining levels will run regardless");
        return h;
    }
}
