package com.gsl.utils.dataprovider.fixture;

import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.model.finance.TdsResolution;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
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
@Builder
public class LiveDbRefs {
    String cashBankName;
    String ownerSiteName;
    String docSchemeName;
    List<SlNameWithId> pendingSlNames;
    Map<String, List<PendingSlDocument>> pendingDocsBySlName;
    Map<String, String> slToArApName;              // key="slName|slId", value=GL name from fingl
    CashBankType cashBankType;                     // CASH (srctype='C') or BANK (srctype='B')

    /**
     * Which TDS Applicable Amount behaviour the template loader should generate for
     * {@code ADVANCE_WITH_TDS} scenarios. Sourced from
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions#getTdsBehaviour()}.
     * {@code null} → {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS} in
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherTemplateLoader}.
     */
    TdsApplicableAmountBehaviour tdsBehaviour;

    /**
     * Resolved TDS name and type per vendor SL for {@code ADVANCE_WITH_TDS} scenarios.
     * Keyed by {@code "slName|slId"} (build via
     * {@link com.gsl.database.query.PendingAdjQuery#compositeKey}).
     *
     * <p>Populated in {@code DbApVoucherDataSource} step 5c via
     * {@link com.gsl.database.query.TdsNameQuery#resolve} for all TDS-applicable
     * advance-payment scenarios.
     *
     * <p><b>Default: empty map</b> — AR Voucher and non-TDS AP scenarios never set this field;
     * the {@code @Builder.Default} ensures {@code DbArVoucherDataSource} requires no changes
     * when new AP-only fields are added here.
     *
     * <p>Consumed by
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherTemplateLoader#buildTdsRows}
     * to populate {@link com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable#getTdsName()}.
     * {@code tdsName} is <b>never null</b> — {@code requireNonNull} is enforced in the template loader.
     */
    @Builder.Default
    Map<String, TdsResolution> slToTdsResolution = Collections.emptyMap();

    /**
     * Best available reference site name per SL for {@code DOC_REFERENCE_SITEWISE_FIFO} scenarios.
     * Keyed by {@code "slName|slId"} (build via
     * {@link com.gsl.database.query.PendingAdjQuery#compositeKey}).
     *
     * <p>Resolved in {@code DbApVoucherDataSource} step 5d by querying
     * {@code v_pending_adj GROUP BY ref_admsite_name} and reserving via
     * {@link com.gsl.database.ReferenceSiteAmountRegistry}.
     * Empty map for all non-FIFO-sitewise scenarios.
     */
    @Builder.Default
    Map<String, String> slToRefSiteName = Collections.emptyMap();
}
