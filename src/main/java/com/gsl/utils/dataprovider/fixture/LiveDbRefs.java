package com.gsl.utils.dataprovider.fixture;

import com.gsl.enums.CashBankType;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable carrier of all live reference values fetched from the DB for one test run.
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
 *   <li>{@code slToArApName}        — GL (AR/AP ledger) name per SL, keyed by {@code "slName|slId"};
 *                                     resolved via finsl.glcode → fingl.glname. Used to populate
 *                                     {@code SubLedgerInformation.generalLedger} so Level 2 DB
 *                                     validation can look up the finvchdet row by glcode.</li>
 *   <li>{@code cashBankType}         — {@link CashBankType#CASH} when {@code cashBankName} maps to
 *                                     a Cash GL (fingl.srctype = 'C');
 *                                     {@link CashBankType#BANK} for Bank GLs (srctype = 'B').
 *                                     Drives payment-mode rules and RANDOM mode resolution.</li>
 * </ul>
 */
@Value
public class LiveDbRefs {
    String cashBankName;
    String ownerSiteName;
    String docSchemeName;
    List<SlNameWithId> pendingSlNames;
    Map<String, List<PendingSlDocument>> pendingDocsBySlName;
    Map<String, String> slToArApName;      // key="slName|slId", value=GL name from fingl
    CashBankType cashBankType;             // CASH (srctype='C') or BANK (srctype='B')
}
