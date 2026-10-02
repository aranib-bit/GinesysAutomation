package com.gsl.flow.Finance.Operations.Common;

import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagRequest;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagResponse;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.model.finance.FinTag;
import com.gsl.reports.ReportLogger;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orchestrates the Document Adjustment "untag" precondition that Revert/Delete/Edit share across
 * every voucher domain that carries a {@code fintag} row (currently AR and AP Voucher — a Posted
 * document that is tagged against a counter-document cannot be Reverted/Deleted/Unposted until
 * that tag is removed). Lives in {@code com.gsl.flow.Finance.Operations.Common} alongside
 * {@link PostSaveDialogHandler} — this is business logic specific to the voucher revert/delete/
 * edit precondition, not a generic DB/API mechanic (per this repo's utility-layering convention:
 * it changes when the "is this document tagged, and does that block the operation" business rule
 * changes, not when the underlying HTTP/DB mechanism changes), so it does not belong in a generic
 * {@code com.gsl.utils.*} package.
 *
 * <p><b>Detection</b>: {@code fintag.entcode1}/{@code entcode2} are both denormalized to
 * {@code finpost.entcode} (= {@code finvchmain.vchcode} — see {@code FinTag}'s field Javadocs), so
 * a document under test can appear as EITHER side of a live tag row — detection therefore checks
 * both {@link FinTagDao#findByEntCode1(long)} and {@link FinTagDao#findByEntCode2(long)} rather
 * than assuming a document is always the "voucher side".
 *
 * <p><b>Untag</b>: confirmed via a live HAR capture that
 * {@code POST Finance/api/documentAdjustments} with an empty {@code AdjustedDocuments} list clears
 * EVERY {@code fintag} row on the base document in a single call — a multi-invoice-tagged document
 * needs exactly one call, not one call per tag row (confirmed via a second HAR capture). This
 * method therefore never loops per-tag-row; it issues one call whenever any tag is detected.
 *
 * <p><b>Session handling (revised)</b>: self-acquires its OWN, separate WEBAPI session via
 * {@link ApiSessionFactory#withWebApiSession} — the same self-acquire-a-fresh-pool-credential
 * pattern every {@code BackfillStrategy} already uses — rather than reaching for a thread-local
 * via {@code ApiSessionManager.getSession()}. Two confirmed problems with the thread-local
 * approach for THIS calling context (plain {@code WebTest} UI classes, not
 * {@code MultiContextTest}): (1) nothing ever populates it for these classes in the first place
 * (only {@code ContextOrchestrator}/{@code APITest}/{@code MultiContextTest}/backfill strategies
 * establish one), and (2) reusing the UI's own logged-in credential to build a fresh WEBAPI
 * session would trigger {@code WebApiSessionClient.login()}'s code-012 ("already logged in
 * elsewhere") handler, which kills that OTHER session — here, the UI browser's own — to force the
 * login through. {@code empCode} is resolved from the self-acquired session's OWN credential
 * (not the caller's UI user), and the session is always logged out afterward so nothing leaks.
 */
public final class DocumentAdjustmentFlow {

    private DocumentAdjustmentFlow() {}

    /**
     * Checks {@link FinTagDao} both directions for {@code vchcode}; if tagged, untags it via one
     * {@code POST Finance/api/documentAdjustments} call ({@code AdjustedDocuments=[]}) using the
     * document's OWN {@code schemeDocno}/{@code slcode}/{@code actualAmount} (never the
     * counter-document's — see {@link DocumentAdjustmentUntagRequest}'s class Javadoc). Returns
     * whether untagging actually happened, for the caller's own step/report logging.
     *
     * @param vchcode         the document under test's own {@code finvchmain.vchcode}
     * @param schemeDocno     the document under test's own {@code scheme_docno}
     * @param slcode          the document under test's own {@code finvchdet.slcode} (first line's,
     *                        when more than one line exists — see call sites' own Javadoc for the
     *                        "first line found, log a warning on ambiguity" convention)
     * @param actualAmount    the document under test's own actual amount ({@code finvchmain.camount}
     *                        or {@code damount}, whichever is non-zero for this voucher type)
     * @param connectedOUCode the organizational unit code the untag is performed under — derived
     *                        from the document's own owner site, not from any user
     * @return {@code true} if a tag was found and the untag call was issued; {@code false} if the
     *         document was never tagged in the first place (no API call made)
     */
    public static boolean untagIfNeeded(long vchcode, String schemeDocno, long slcode,
            BigDecimal actualAmount, long connectedOUCode) {

        FinTagDao finTagDao = DatabaseManager.getJdbi().onDemand(FinTagDao.class);
        List<FinTag> tagsAsEntCode1 = finTagDao.findByEntCode1(vchcode);
        List<FinTag> tagsAsEntCode2 = finTagDao.findByEntCode2(vchcode);
        boolean tagged = !tagsAsEntCode1.isEmpty() || !tagsAsEntCode2.isEmpty();

        if (!tagged) {
            ReportLogger.business("[DocumentAdjustmentFlow] Document '" + schemeDocno + "' (vchcode=" + vchcode
                    + ") has no fintag rows on either side — untag not required.");
            return false;
        }

        int tagRowCount = tagsAsEntCode1.size() + tagsAsEntCode2.size();
        ReportLogger.business("[DocumentAdjustmentFlow] Document '" + schemeDocno + "' (vchcode=" + vchcode
                + ") has " + tagRowCount + " fintag row(s) (" + tagsAsEntCode1.size() + " as entcode1, "
                + tagsAsEntCode2.size() + " as entcode2) — untagging via one Document Adjustment call.");

        DocumentAdjustmentUntagResponse response = ApiSessionFactory.withWebApiSession(session -> {
            long empCode = DatabaseManager.getJdbi().onDemand(HrEmpDao.class)
                    .findEcodeByAbbre(session.getCredential().username())
                    .orElseThrow(() -> new IllegalStateException(
                            "[DocumentAdjustmentFlow] Could not resolve ecode for self-acquired API user '"
                            + session.getCredential().username() + "'"));

            DocumentAdjustmentUntagRequest request = DocumentAdjustmentUntagRequest.builder()
                    .connectedUserCode(empCode)
                    .connectedOUCode(connectedOUCode)
                    .baseDocumentType(DocumentAdjustmentApiClient.BASE_DOCUMENT_TYPE_VOUCHER)
                    .baseDocumentCode(vchcode)
                    .baseDocumentNumber(schemeDocno)
                    .baseDocumentActualAmount(actualAmount)
                    .baseDocumentSlCode(slcode)
                    .build();

            return session.getDocumentAdjustmentApiClient().untagAllAdjustments(request);
        });

        ReportLogger.business("[DocumentAdjustmentFlow] Untag call completed for '" + schemeDocno
                + "' — response message: '" + response.getMessage() + "'");
        return true;
    }
}
