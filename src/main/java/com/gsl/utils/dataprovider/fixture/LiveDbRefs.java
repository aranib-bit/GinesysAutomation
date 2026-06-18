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
     * Resolved reference site name per SL for all AP Voucher scenarios.
     * Keyed by {@code "slName|slId"} (build via
     * {@link com.gsl.database.query.PendingAdjQuery#compositeKey}).
     *
     * <p>Populated in {@code DbApVoucherDataSource} step 5d via
     * {@link com.gsl.database.query.ReferenceSiteQuery#resolveForSl} for all scenarios.
     * For {@code DOC_REFERENCE_SITEWISE_FIFO} the value is overwritten by the more precise
     * {@code v_pending_adj}-based resolution that accounts for actual pending balances.
     */
    @Builder.Default
    Map<String, String> slToRefSiteName = Collections.emptyMap();

    /**
     * {@code admcls.cmpcode} per SL — 1 = system-generated classification, 0 = user-defined.
     * Keyed by {@code "slName|slId"}.
     *
     * <p>Drives scenario generation: system SLs ({@code cmpcode=1}) are needed for NEG-06/NEG-07;
     * user-defined SLs ({@code cmpcode=0}) are needed for DOC-02/DOC-04/DOC-06.
     */
    @Builder.Default
    Map<String, Integer> slToCmpCode = Collections.emptyMap();

    /**
     * {@code admcls.clstype} per SL — e.g. "C" (Customer), "S" (Supplier), "D", "O", etc.
     * Keyed by {@code "slName|slId"}.
     *
     * <p>Used by {@link com.gsl.database.query.ReferenceSiteQuery} to select the correct
     * resolution branch: Customer-type SLs check admsite count; all others use the default.
     */
    @Builder.Default
    Map<String, String> slToClstype = Collections.emptyMap();
}
