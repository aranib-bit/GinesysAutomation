package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.apvoucher.ApVoucherAttachment;
import com.gsl.api.model.apvoucher.ApVoucherCreateRequest;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.apvoucher.ApVoucherDetailLine;
import com.gsl.api.model.apvoucher.ApVoucherRevertRequest;
import com.gsl.api.model.apvoucher.ApVoucherRevertResponse;
import com.gsl.api.model.apvoucher.TdsSectionLookupResult;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.ReferenceSiteLookupResult;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.arvoucher.VoucherDateValidationRequest;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for AP Voucher creation. Mirrors the real flow captured in
 * {@code CreateAPVoucherCustomerAdvanceTDS_stage-ideal.ginesys.cloud4.har} and
 * {@code CreateAPVoucherCustomerAdvanceNoTDS_stage-ideal.ginesys.cloud4.har} — used as the AR
 * Voucher document-payment backfill mechanism (see {@code ApVoucherArBackfillStrategy}): an AP
 * Voucher posted {@code onAccount} against a Customer-classified sub-ledger (confirmed via HAR:
 * {@code generalLedgerGroupName="Sundry Debtors"}) lands in {@code v_pending_adj} with
 * {@code debit_credit='D'} once posted — exactly what
 * {@code DbArVoucherDataSource.loadFreshForScenarioInternal()} is searching for. The exact mirror
 * of {@link ArVoucherApiClient}, which does the same thing in the opposite direction for AP
 * Voucher's own shortfall.
 *
 * <p>Reuses {@code com.gsl.api.model.arvoucher}'s {@code SubLedgerLookupResult},
 * {@code DocNumberSchemeEntry}, {@code CashBankLedgerLookupResult}, {@code ReferenceSiteLookupResult}
 * and {@code VoucherDateValidationRequest} — these are generic Finance-module lookup/request shapes
 * shared by both voucher types (same underlying endpoints, package name is just historical from
 * AR being built first), not AR-specific. Only the shapes that genuinely differ for AP Voucher
 * (the create request/response, its detail line, the TDS section lookup, and the attachment shape)
 * get their own classes under {@code com.gsl.api.model.apvoucher}.
 */
public class ApVoucherApiClient extends ApiClient {

    private static final String MODULE = "apvoucher";
    private static final String SITE_TYPE = "OS-OO-CM";
    /** Confirmed via both HARs as the scheme actually used to create a real AP Voucher (code=138). */
    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";
    /** Doc scheme menu for the auto-created TDS journal — confirmed via HAR (code=171, "TDS Journal"). */
    private static final String TDS_JOURNAL_MENU_SHORT_CODE = "FinanceTDSJournal";
    /** {@code srcCode} query param on the TDS section lookup — confirmed via HAR as 1 ("COMPANY") for every
     *  sub-ledger seen in both captures; the shared {@link SubLedgerLookupResult} model doesn't carry the
     *  underlying {@code sourceCode} field, so this mirrors the literal observed value rather than
     *  threading a new field through just for this one call. */
    private static final int DEFAULT_SRC_CODE = 1;

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ApVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** Prefixes {@code path} with {@code financeApiBaseUri} — see class Javadoc for why this client's endpoints need that override. */
    private static String url(String path) {
        return ConfigFactory.getApiConfig().financeApiBaseUri() + path;
    }

    /**
     * GET Finance/api/docnumberschemes for the AP Voucher scheme itself. Note: confirmed via HAR
     * as using a lowercase {@code connectedsite} query key here (unlike every other lookup in this
     * client, which use {@code connectedSite}) — ASP.NET model binding is case-insensitive so this
     * is almost certainly just how the SPA happened to build this one call, but kept literal to
     * match the confirmed-working capture rather than "fixing" the casing.
     */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("connectedsite", connectedSite);
        params.put("menusysshortcode", "FinanceAPVoucher");
        params.put("page", 1);
        params.put("limit", 25);
        params.put("workDate", workDate);
        params.put("start", 0);
        Response response = getRaw(url("/docnumberschemes"), params);
        return response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
    }

    /** GET Finance/api/docnumberschemes for the auto-created TDS journal scheme — TDS-only, confirmed via HAR. */
    public long fetchTdsJournalDocSchemeCode(int connectedSite) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("connectedSite", connectedSite);
        params.put("menusysshortcode", TDS_JOURNAL_MENU_SHORT_CODE);
        params.put("page", 1);
        params.put("limit", 10);
        Response response = getRaw(url("/docnumberschemes"), params);
        List<DocNumberSchemeEntry> schemes = response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
        if (schemes.isEmpty()) {
            throw new ApiException(
                    "GetDocNumberSchemes(menusysshortcode=" + TDS_JOURNAL_MENU_SHORT_CODE
                            + ") returned no scheme — cannot deduct TDS without an auto-created journal scheme.", 0);
        }
        return schemes.get(0).getCode();
    }

    /** GET Finance/api/ledgers/cashbankledgers */
    public List<CashBankLedgerLookupResult> fetchCashBankLedgers(
            int connectedSite, int connectedUserCode, int connectedOUCode, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");
        params.put("searchText", "");
        params.put("queryFieldName", List.of("glName", "abbreviation"));
        params.put("connectedSite", connectedSite);
        params.put("connectedUserCode", connectedUserCode);
        params.put("module", MODULE);
        params.put("workDate", workDate);
        params.put("connectedOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("pagingNotRequired", false);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        params.put("sort", "glName ASC");
        Response response = getRaw(url("/ledgers/cashbankledgers"), params);
        return response.jsonPath().getList("result.items", CashBankLedgerLookupResult.class);
    }

    /**
     * GET Finance/api/subledgers — same endpoint AR's client uses, returns every classification.
     * {@code pagingNotRequired=true} so a target of any classCode further down the list is never
     * missed — same reasoning as {@link ArVoucherApiClient#fetchSubLedgers}, even though the
     * captured HAR's own UI flow used a plain page-1 fetch followed by a live-search call instead
     * (that's a search-box UX detail, not something a programmatic resolver should reproduce).
     */
    public List<SubLedgerLookupResult> fetchSubLedgers(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchText", "");
        params.put("module", MODULE);
        params.put("connectedOUCode", connectedOUCode);
        params.put("connectedUserCode", connectedUserCode);
        params.put("queryFieldName", List.of("slName", "className"));
        params.put("connectedSite", connectedSite);
        params.put("workDate", workDate);
        params.put("connectedSiteOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("pagingNotRequired", true);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        Response response = getRaw(url("/subledgers"), params);
        return response.jsonPath().getList("result.items", SubLedgerLookupResult.class);
    }

    /**
     * Resolves a live, Customer-classified ({@code classType='C'}) sub-ledger, optionally further
     * narrowed to {@code requiredClsCode} (0 = any Customer classification, first result wins).
     *
     * <p>Unlike {@link ArVoucherApiClient#resolveSubLedger} — which may legitimately resolve
     * against any classification, since AR Voucher itself has no classification restriction —
     * {@code /Finance/api/subledgers} under {@code module=apvoucher} returns sub-ledgers of
     * <b>every</b> classification (Customer/Supplier/Jobber/Agent/TDS-agency/Others/etc.), but
     * this client's on-account "Customer Advance" flow (confirmed via both the TDS and No-TDS
     * HAR captures — {@code generalLedgerGroupName="Sundry Debtors"} on the created line) only
     * produces the {@code debit_credit='D'} shape AR Voucher needs against a Customer-classified
     * SL. The Customer filter is therefore applied unconditionally, before any
     * {@code requiredClsCode} narrowing — never falls back to a differently-classified SL on miss,
     * same hard-fail principle as {@code ArVoucherApiClient.resolveSubLedger}.
     *
     * <p>Deliberately public and standalone (not folded into {@link #createApVoucherWithDefaults})
     * so {@code ApVoucherArBackfillStrategy} can resolve the sub-ledger first, run its own DB-only
     * TDS-applicability check ({@code finsl.tds_app}) against the resolved SL, then pass both the
     * SL and that decision into {@link #createApVoucherWithDefaults} — keeping DB access out of
     * this class entirely (no other {@code api.client.*} class touches JDBI).
     */
    public SubLedgerLookupResult resolveSubLedger(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate, long requiredClsCode) {
        List<SubLedgerLookupResult> subLedgers = fetchSubLedgers(connectedSite, connectedOUCode, connectedUserCode, workDate);
        if (subLedgers.isEmpty()) {
            throw new ApiException(
                    "GetSubLedgers returned no sub-ledgers at all for connectedSite=" + connectedSite
                            + ", connectedOUCode=" + connectedOUCode + " — nothing usable to raise an AP Voucher against.", 0);
        }
        List<SubLedgerLookupResult> customerSubLedgers = subLedgers.stream()
                .filter(sl -> "C".equalsIgnoreCase(sl.getClassType()))
                .collect(java.util.stream.Collectors.toList());
        if (customerSubLedgers.isEmpty()) {
            throw new ApiException(
                    "GetSubLedgers returned " + subLedgers.size() + " sub-ledger(s) for connectedSite=" + connectedSite
                            + " but none classified as Customer (classType='C') — cannot raise an AP Voucher Customer Advance.", 0);
        }
        if (requiredClsCode <= 0) {
            return customerSubLedgers.get(0);
        }
        return customerSubLedgers.stream()
                .filter(sl -> sl.getClassCode() == requiredClsCode)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "No Customer-classified sub-ledger with classCode=" + requiredClsCode + " found among "
                                + customerSubLedgers.size() + " Customer sub-ledger(s) for connectedSite=" + connectedSite
                                + " — cannot backfill an AP Voucher for this classification.", 0));
    }

    /** GET Finance/api/sites?target=referencesite */
    public List<ReferenceSiteLookupResult> fetchReferenceSites(
            int connectedOUCode, long slCode, long glCode, int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target", "referencesite");
        params.put("module", MODULE);
        params.put("connectedOUCode", connectedOUCode);
        params.put("slCode", slCode);
        params.put("glCode", glCode);
        params.put("queryFieldName", List.of("name", "siteType", "ouName"));
        params.put("connectedSite", connectedSite);
        params.put("workDate", workDate);
        params.put("connectedSiteOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 500);
        Response response = getRaw(url("/sites"), params);
        return response.jsonPath().getList("result.items", ReferenceSiteLookupResult.class);
    }

    /**
     * GET Finance/api/tdssection/tdsname — TDS-only. Lists the configured TDS deduction types
     * (name/section/rate/agency) available for the given sub-ledger; does NOT itself say whether
     * TDS applies (that's {@code finsl.tds_app} in the DB) — only call this once applicability is
     * already known to be true.
     */
    public List<TdsSectionLookupResult> fetchTdsSection(
            int connectedSite, int connectedUserCode, int connectedOUCode, String workDate, long slCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");
        params.put("searchText", "");
        params.put("queryFieldName", List.of("tdsName", "section", "tdsSourceName", "tdsSourceTypeDisplay"));
        params.put("connectedSite", connectedSite);
        params.put("connectedUserCode", connectedUserCode);
        params.put("module", MODULE);
        params.put("workDate", workDate);
        params.put("connectedOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("pagingNotRequired", false);
        params.put("slCode", slCode);
        params.put("srcCode", DEFAULT_SRC_CODE);
        params.put("voucherDate", workDate);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        params.put("sort", "tdsName ASC");
        Response response = getRaw(url("/tdssection/tdsname"), params);
        return response.jsonPath().getList("result.items", TdsSectionLookupResult.class);
    }

    /** POST Finance/api/vouchers/ap/validations/voucherdate — throws (via base validateResponse) on a real validation failure. */
    public void validateVoucherDate(long empCode, String voucherDate, long connectedOUCode) {
        VoucherDateValidationRequest request = VoucherDateValidationRequest.builder()
                .voucherDate(voucherDate)
                .empCode(empCode)
                .connectedOUCode(connectedOUCode)
                .build();
        postRaw(url("/vouchers/ap/validations/voucherdate"), request);
    }

    /** POST Finance/api/vouchers/ap — the final save call. */
    public ApVoucherCreateResponse createApVoucher(ApVoucherCreateRequest request) {
        return post(url("/vouchers/ap"), request, ApVoucherCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createApVoucher(ApVoucherCreateRequest)} — for a
     * deliberately incomplete/invalid {@code request}, returns the raw {@link Response} instead
     * of throwing.
     */
    public Response createApVoucherExpectFailure(ApVoucherCreateRequest request) {
        return postRawExpectFailure(url("/vouchers/ap"), request);
    }

    /**
     * PUT Finance/api/vouchers/ap/revert — un-posts a currently-Posted AP Voucher via API rather
     * than the UI Actions→Revert path. Confirmed via a live HAR capture (request:
     * {@code {"code":[<vchcode>],"empCode":<empCode>,"connectedOUCode":<ouCode>}}; response:
     * {@code {"status":0,...,"result":[{"item1":<vchcode>,"item2":"<scheme_docno>","item3":
     * "success","item4":<postcode>}],"success":true}}) — a real implementation, not a stub, unlike
     * {@code GeneralVoucherApiClient.revertGeneralVoucher} (which is still an honest
     * {@link UnsupportedOperationException} stub pending exactly this kind of capture). Used by
     * {@code EditApVoucher}'s "Unpost if Posted" step (API-driven per the user's explicit
     * instruction — a Posted+tagged AP Voucher cannot be unposted until untagged first, so
     * {@code DocumentAdjustmentFlow.untagIfNeeded} must run immediately before this call).
     *
     * @param vchcode         the {@code finvchmain.vchcode} of the Posted AP Voucher to revert
     * @param empCode         the logged-in employee code performing the revert
     * @param connectedOUCode the organizational unit code the revert is performed under
     */
    public ApVoucherRevertResponse revertApVoucher(long vchcode, long empCode, long connectedOUCode) {
        ApVoucherRevertRequest request = ApVoucherRevertRequest.builder()
                .code(List.of(vchcode))
                .empCode(empCode)
                .connectedOUCode(connectedOUCode)
                .build();
        return put(url("/vouchers/ap/revert"), request, ApVoucherRevertResponse.class);
    }

    /** GET /udf/GetUDFMetaData — lives under the original WebAPI base, same as {@link ArVoucherApiClient#fetchUdfMetadata}. */
    public List<UdfFieldMetadata> fetchUdfMetadata(String tableName) {
        return UdfValueBuilder.fetchUdfMetadata(this, tableName);
    }

    /** Fetches metadata then builds {@code udfValues} in one call — see {@link UdfValueBuilder#fetchAndBuild}. */
    public List<UdfValueEntry> fetchAndBuildUdfValues(String tableName) {
        return UdfValueBuilder.fetchAndBuild(this, tableName);
    }

    /**
     * Orchestrates the full flow against an already-resolved {@code subLedger} and a
     * caller-supplied {@code tdsApplicable} decision — see {@link #resolveSubLedger} javadoc for
     * why resolution and the TDS decision happen outside this method rather than inside it (as
     * {@link ArVoucherApiClient#createArVoucherWithDefaults} does for its own, DB-free case).
     * This is the method {@code ApVoucherArBackfillStrategy} calls to backfill AR Voucher's
     * document-payment shortfall.
     */
    public ApVoucherCreateResponse createApVoucherWithDefaults(
            SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            boolean tdsApplicable) {
        return createApVoucherWithDefaults(subLedger, ownerSiteCode, ouCode, empCode, quantity, rate,
                tdsApplicable, null);
    }

    /**
     * Same as above, but uses the doc scheme named {@code docSchemeName} (resolved by the caller from
     * the DB so an exhausted scheme is never picked). {@code null} keeps the legacy pick: the
     * preferred "AR/AP Voucher" scheme, else the first one the API offers.
     */
    public ApVoucherCreateResponse createApVoucherWithDefaults(
            SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            boolean tdsApplicable, String docSchemeName) {

        String workDate = LocalDate.now() + "T00:00:00";

        List<DocNumberSchemeEntry> schemes = StepExecutor.call("Get Doc Scheme Nos for AP Voucher",
                () -> fetchDocNumberSchemes(ownerSiteCode, workDate));
        DocNumberSchemeEntry scheme = DocSchemeSelector.select(
                schemes, docSchemeName, PREFERRED_DOC_SCHEME_NAME, "AP Voucher");
        long docSchemeCode = scheme != null ? scheme.getCode() : 0;

        List<CashBankLedgerLookupResult> cashBankLedgers = StepExecutor.call("Get Cash/Bank Details for AP Voucher",
                () -> fetchCashBankLedgers(ownerSiteCode, (int) empCode, ouCode, workDate));
        if (cashBankLedgers.isEmpty()) {
            throw new ApiException(
                    "GetCashBankLedgers returned no ledgers for ownerSiteCode=" + ownerSiteCode + " — cannot raise an AP Voucher.", 0);
        }
        CashBankLedgerLookupResult cashBankLedger = cashBankLedgers.get(0);

        List<ReferenceSiteLookupResult> refSites = StepExecutor.call("Get Reference Sites for AP Voucher",
                () -> fetchReferenceSites(ouCode, subLedger.getSlCode(), subLedger.getGlCode(), ownerSiteCode, workDate));
        ReferenceSiteLookupResult refSite = refSites.stream()
                .filter(s -> s.getCode() == ownerSiteCode)
                .findFirst()
                .orElseGet(() -> refSites.isEmpty() ? null : refSites.get(0));
        long referenceSiteCode = refSite != null ? refSite.getCode() : ownerSiteCode;

        StepExecutor.call("Validate Voucher Date for AP Voucher", () -> {
            validateVoucherDate(empCode, workDate, ouCode);
            return null;
        });

        List<UdfValueEntry> udfValues = StepExecutor.call("Get UDF Metadata for AP Voucher",
                () -> fetchAndBuildUdfValues("FINVCHMAIN"));

        double grossAmount = quantity * rate;
        Long autoCreatedJournalDocSchemeCode = null;

        ApVoucherDetailLine.ApVoucherDetailLineBuilder lineBuilder = ApVoucherDetailLine.builder()
                .subLedgerCode(subLedger.getSlCode())
                .subLedgerName(subLedger.getSlName())
                .ledgerCode(subLedger.getGlCode())
                .generalLedgerGroupName(subLedger.getGeneralLedgerGroupName())
                .generalLedgerAbbre(subLedger.getGeneralLedgerAbbre() != null ? subLedger.getGeneralLedgerAbbre() : "")
                .referenceSiteCode(referenceSiteCode);

        if (tdsApplicable) {
            List<TdsSectionLookupResult> tdsSections = StepExecutor.call("Get TDS Section for AP Voucher",
                    () -> fetchTdsSection(ownerSiteCode, (int) empCode, ouCode, workDate, subLedger.getSlCode()));
            if (tdsSections.isEmpty()) {
                throw new ApiException(
                        "GetTdsSection returned no TDS types for slCode=" + subLedger.getSlCode()
                                + " — tds_app='Y' in DB but no TDS section configured to deduct against.", 0);
            }
            // Deterministic pick, same "vary by slcode" convention as TdsNameQuery.resolve — any
            // one candidate is a valid choice for backfill purposes.
            TdsSectionLookupResult tds = tdsSections.get((int) (subLedger.getSlCode() % tdsSections.size()));

            double tdsAmtRaw = grossAmount * tds.getTdsRate() / 100.0;
            double tdsRoundOffAmt = Math.round(tdsAmtRaw);
            double netPayable = grossAmount - tdsRoundOffAmt;

            autoCreatedJournalDocSchemeCode = StepExecutor.call("Get TDS Journal Doc Scheme for AP Voucher",
                    () -> fetchTdsJournalDocSchemeCode(ownerSiteCode));

            lineBuilder
                    .deductTDS(true)
                    .tdsRate(tds.getTdsRate())
                    .tdsTypeCode(tds.getCode())
                    .tdsAppAmt(grossAmount)
                    .tdsAmt(tdsAmtRaw)
                    .tdsGrossAmt(grossAmount)
                    .tdsRoundOffAmt(tdsRoundOffAmt)
                    .netPayable(netPayable)
                    .amount(netPayable);

            ReportLogger.dataBackfill(
                    "[ApVoucherApiClient] TDS applicable — subLedger='{}', tdsName='{}', tdsTypeCode={}, tdsRate={}, gross={}, roundOff={}, netPayable={}",
                    subLedger.getSlName(), tds.getTdsName(), tds.getCode(), tds.getTdsRate(), grossAmount, tdsRoundOffAmt, netPayable);
        } else {
            lineBuilder.deductTDS(false).amount(grossAmount);
        }

        ApVoucherDetailLine line = lineBuilder.build();

        String requestId = String.valueOf(System.nanoTime());

        ApVoucherCreateRequest request = ApVoucherCreateRequest.builder()
                .voucherDate(workDate)
                .docSchemeCode(docSchemeCode)
                .empCode(empCode)
                .connectedOUCode(ouCode)
                .connectedSiteCode(ownerSiteCode)
                .ownerSiteCode(ownerSiteCode)
                .cashBankGLCode(cashBankLedger.getGlCode())
                .autoCreatedJournalDocSchemeCode(autoCreatedJournalDocSchemeCode)
                .apVoucherDetail(List.of(line))
                .requestId(requestId)
                .attachment(ApVoucherAttachment.builder().build())
                .udfValues(udfValues)
                .build();

        ReportLogger.dataBackfill(
                "[ApVoucherApiClient] Creating AP Voucher — subLedger='{}' (classCode={}), ownerSiteCode={}, cashBankGLCode={}, tdsApplicable={}, amount={}",
                subLedger.getSlName(), subLedger.getClassCode(), ownerSiteCode, cashBankLedger.getGlCode(), tdsApplicable, line.getAmount());

        return StepExecutor.call("Create AP Voucher via API", () -> createApVoucher(request));
    }
}
