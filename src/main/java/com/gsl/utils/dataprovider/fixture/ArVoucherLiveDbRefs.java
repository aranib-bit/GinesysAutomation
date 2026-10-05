package com.gsl.utils.dataprovider.fixture;

import com.gsl.enums.CashBankType;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable carrier of all live reference values fetched from the DB for one AR Voucher test run
 * — the AR analogue of {@link ApVoucherLiveDbRefs}, trimmed to only the fields AR actually uses.
 *
 * <p>Extracted out of the former shared {@code LiveDbRefs} (now {@link ApVoucherLiveDbRefs}),
 * which carried 13 fields. <b>Verified field-by-field against actual usage</b> (not just the
 * old class's Javadoc, which undercounted this) — {@code DbArVoucherDataSource} populates, and
 * {@code ArVoucherTemplateLoader} reads, these 8 fields; the remaining 5
 * ({@code slToCmpCode}/{@code slToClstype}/{@code selectDocPaymentMode}, plus TDS
 * behaviour/resolution which AR passes as {@code null}/omitted respectively — true no-ops) are
 * confirmed AP-only (zero references anywhere in AR code). Mirrors {@link LiveGvDbRefs}, which
 * was already kept separate for the same reason (no overlap with AP's TDS/pending-document/
 * classification fields).
 *
 * <ul>
 *   <li>{@code cashBankName}        — active Cash or Bank GL scoped to the user's sites (fingl)</li>
 *   <li>{@code ownerSiteName}       — first OS-OO-CM site the user is assigned to (admsite)</li>
 *   <li>{@code docSchemeName}       — AR Voucher doc scheme verified for the user's site</li>
 *   <li>{@code pendingSlNames}      — (slName, slId) pairs with open AR documents (v_pending_adj)</li>
 *   <li>{@code pendingDocsBySlName} — open documents per SL, keyed by {@code "slName|slId"}
 *                                     (SELECT_DOCUMENT scenario only; FIFO templates receive an
 *                                     empty map). Build keys via
 *                                     {@link com.gsl.database.query.PendingAdjQuery#compositeKey}.</li>
 *   <li>{@code slToArApName}        — GL (AR ledger) name per SL, keyed by {@code "slName|slId"};
 *                                     resolved via finsl.glcode → fingl.glname. Used to populate
 *                                     {@code SubLedgerInformation.generalLedger} so Level 2 DB
 *                                     validation can look up the finvchdet row by glcode.</li>
 *   <li>{@code slToRefSiteName}     — resolved reference site name per SL, keyed by
 *                                     {@code "slName|slId"} — read by
 *                                     {@link com.gsl.utils.dataprovider.fixture.finance.ArVoucherTemplateLoader}
 *                                     (confirmed genuinely used by AR, unlike the other 5
 *                                     dropped fields).</li>
 *   <li>{@code cashBankType}         — {@link CashBankType#CASH} when {@code cashBankName} maps to
 *                                     a Cash GL (fingl.srctype = 'C');
 *                                     {@link CashBankType#BANK} for Bank GLs (srctype = 'B').
 *                                     Drives payment-mode rules and RANDOM mode resolution.</li>
 * </ul>
 */
@Value
@Builder
public class ArVoucherLiveDbRefs {
    String cashBankName;
    String ownerSiteName;
    String docSchemeName;
    /** Why {@link #docSchemeName} was chosen (mode, candidates, remaining numbers) — appended to the
     *  data-gathering log. May be null when the refs are built without a resolver run. */
    List<String> docSchemeNarrative;
    List<SlNameWithId> pendingSlNames;
    Map<String, List<PendingSlDocument>> pendingDocsBySlName;
    Map<String, String> slToArApName;              // key="slName|slId", value=GL name from fingl
    Map<String, String> slToRefSiteName;            // key="slName|slId", value=resolved reference site name
    CashBankType cashBankType;                     // CASH (srctype='C') or BANK (srctype='B')
}
