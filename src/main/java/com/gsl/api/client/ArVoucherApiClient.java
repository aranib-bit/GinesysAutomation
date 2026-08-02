package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.arvoucher.ArVoucherDetailLine;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.ReferenceSiteLookupResult;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.arvoucher.VoucherDateValidationRequest;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.model.udf.UdfLookupValue;
import com.gsl.api.model.udf.UdfMetadataResponse;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.query.UdfValueQuery;
import com.gsl.reports.ReportLogger;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for AR Voucher creation. Mirrors the real flow captured in
 * {@code CreateARVoucherstage-ideal.ginesys.cloud4.har} — used both as a standalone document
 * type AND as the AP Voucher document-payment backfill mechanism for any {@code clsCode} whose
 * {@code clstype} isn't Supplier (Service Invoice Adhoc's GetVendors lookup is Supplier-only, so
 * it can never satisfy a Customer/Jobber/Others/etc. shortfall — see
 * {@code ArVoucherApBackfillStrategy}). An AR Voucher against any sub-ledger classification
 * lands in {@code v_pending_adj} with {@code debit_credit='C'} once posted — exactly what
 * {@code DbApVoucherDataSource.selectApDocumentsAtomic()} is searching for.
 *
 * <p>Endpoints live under the Finance module's own app path ({@code financeApiBaseUri}), not the
 * WebAPI path the other four flow clients use — confirmed via the HAR that the same authenticated
 * WEBAPI session's {@code ginesysauth} bearer header works unchanged against both, only the base
 * path differs, so every call here passes a full absolute URL to override the shared spec's own
 * baseURI rather than needing a separate login.
 */
public class ArVoucherApiClient extends ApiClient {

    private static final String MENU_SHORT_CODE = "FinanceARVoucher";
    private static final String MODULE = "arvoucher";
    private static final String SITE_TYPE = "OS-OO-CM";
    /** Confirmed via HAR as the scheme actually used to create a real AR Voucher (code=138). */
    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    public ArVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    private static String url(String path) {
        return ConfigFactory.getApiConfig().financeApiBaseUri() + path;
    }

    /** GET Finance/api/docnumberschemes */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("connectedSite", connectedSite);
        params.put("menusysshortcode", MENU_SHORT_CODE);
        params.put("pagingNotRequired", true);
        params.put("workDate", workDate);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        Response response = getRaw(url("/docnumberschemes"), params);
        return response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
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
     * GET Finance/api/subledgers — unlike Service Invoice Adhoc's GetVendors, returns every
     * classification (Customer/Supplier/Jobber/Others/TDS/etc.), not just Supplier.
     * {@code pagingNotRequired=true} so a target of any classCode further down the (potentially
     * 200+ row) list is never missed to a page-1-only fetch.
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
     * Resolves a live sub-ledger matching {@code requiredClsCode} (0 = no filter, first result
     * wins). Unlike {@code ServiceInvoiceAdhocApiClient.resolveVendor}, there is no silent
     * fallback to a differently-classified SL on miss — that exact shortcut was the root cause of
     * a prior AP Voucher backfill bug (a wrongly-classified document that never satisfied the
     * shortfall it was meant to fix). A genuine miss here means this environment has no active
     * sub-ledger of the required classification at all, which callers must see as a hard failure.
     */
    public SubLedgerLookupResult resolveSubLedger(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate, long requiredClsCode) {
        List<SubLedgerLookupResult> subLedgers = fetchSubLedgers(connectedSite, connectedOUCode, connectedUserCode, workDate);
        if (subLedgers.isEmpty()) {
            throw new ApiException(
                    "GetSubLedgers returned no sub-ledgers at all for connectedSite=" + connectedSite
                            + ", connectedOUCode=" + connectedOUCode + " — nothing usable to raise an AR Voucher against.", 0);
        }
        if (requiredClsCode <= 0) {
            return subLedgers.get(0);
        }
        return subLedgers.stream()
                .filter(sl -> sl.getClassCode() == requiredClsCode)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "No sub-ledger with classCode=" + requiredClsCode + " found among " + subLedgers.size()
                                + " live sub-ledger(s) for connectedSite=" + connectedSite
                                + " — cannot backfill an AR Voucher for this classification.", 0));
    }

    /** GET Finance/api/sites?target=referencesite */
    public List<ReferenceSiteLookupResult> fetchReferenceSites(
            int connectedOUCode, long slCode, int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target", "referencesite");
        params.put("module", MODULE);
        params.put("connectedOUCode", connectedOUCode);
        params.put("slCode", slCode);
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

    /** POST Finance/api/vouchers/ar/validations/voucherdate — throws (via base validateResponse) on a real validation failure. */
    public void validateVoucherDate(long empCode, String voucherDate, long connectedOUCode) {
        VoucherDateValidationRequest request = VoucherDateValidationRequest.builder()
                .voucherDate(voucherDate)
                .empCode(empCode)
                .connectedOUCode(connectedOUCode)
                .build();
        postRaw(url("/vouchers/ar/validations/voucherdate"), request);
    }

    /** POST Finance/api/vouchers/ar — the final save call. */
    public ArVoucherCreateResponse createArVoucher(ArVoucherCreateRequest request) {
        return post(url("/vouchers/ar"), request, ArVoucherCreateResponse.class);
    }

    /**
     * GET /udf/GetUDFMetaData — lives under the original WebAPI base (same as
     * {@code ServiceInvoiceAdhocApiClient}'s own relative endpoints), NOT {@code financeApiBaseUri}
     * — confirmed via the HAR path {@code /WebAPI/api/udf/GetUDFMetaData}. Passed as a relative
     * path (not wrapped in {@link #url}) so it resolves against the shared spec's own base URI.
     */
    public List<UdfFieldMetadata> fetchUdfMetadata(String tableName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("_dc", System.currentTimeMillis());
        params.put("tableName", tableName);
        params.put("lookupData", true);
        Response response = getRaw("/udf/GetUDFMetaData", params);
        return response.as(UdfMetadataResponse.class).getMetaData();
    }

    private static final int UNIQUE_UDF_VALUE_MAX_ATTEMPTS = 5;

    /**
     * Builds {@code udfValues} from live metadata — one {@link UdfValueEntry} per field, matching
     * the exact shape confirmed via HAR: {@code ""} for String/Memo, {@code null} otherwise, UNLESS
     * a field is mandatory, in which case a real, datatype-correct value is required instead (and
     * DB-validated against {@code finvchmain} first when the field is also {@code isUnique}).
     */
    private List<UdfValueEntry> buildUdfValues(List<UdfFieldMetadata> metadata) {
        List<UdfValueEntry> entries = new ArrayList<>();
        for (UdfFieldMetadata field : metadata) {
            String fieldName = field.getColumnName().toLowerCase();
            String datatype = field.getDatatype().toLowerCase();
            Object value = field.isMandatory() ? resolveMandatoryUdfValue(field, datatype) : defaultUdfValue(datatype);
            entries.add(UdfValueEntry.builder().fieldName(fieldName).value(value).datatype(datatype).build());
            ReportLogger.dataBackfill(
                    "[ArVoucherApiClient] UDF field '{}' (datatype={}, mandatory={}, unique={}) -> value={}",
                    fieldName, datatype, field.isMandatory(), field.isUnique(), value);
        }
        return entries;
    }

    /** Non-mandatory default — matches the real captured request's own pattern exactly. */
    private static Object defaultUdfValue(String datatypeLower) {
        return ("string".equals(datatypeLower) || "memo".equals(datatypeLower)) ? "" : null;
    }

    /**
     * Generates a real value for a mandatory field, re-generating up to
     * {@value #UNIQUE_UDF_VALUE_MAX_ATTEMPTS} times if the field is also {@code isUnique} and a
     * candidate collides with an existing {@code finvchmain} row — never sends a known-duplicate
     * value and lets the server reject it opaquely.
     */
    private Object resolveMandatoryUdfValue(UdfFieldMetadata field, String datatypeLower) {
        Jdbi jdbi = field.isUnique() ? DatabaseManager.getJdbi() : null;

        for (int attempt = 0; attempt < UNIQUE_UDF_VALUE_MAX_ATTEMPTS; attempt++) {
            Object candidate = generateUdfCandidate(field, datatypeLower, attempt);
            if (jdbi == null || !UdfValueQuery.existsInFinVchMain(jdbi, field.getColumnName(), candidate)) {
                return candidate;
            }
            ReportLogger.dataBackfillWarn(
                    "[ArVoucherApiClient] UDF value collision for '{}' — candidate {} already exists, retrying ({}/{})",
                    field.getColumnName(), candidate, attempt + 1, UNIQUE_UDF_VALUE_MAX_ATTEMPTS);
        }
        throw new ApiException("Could not generate a unique value for mandatory UDF field '"
                + field.getColumnName() + "' after " + UNIQUE_UDF_VALUE_MAX_ATTEMPTS + " attempts.", 0);
    }

    /** Datatype-correct Java-typed candidate — see class-level datatype table in the plan. */
    private Object generateUdfCandidate(UdfFieldMetadata field, String datatypeLower, int attempt) {
        return switch (datatypeLower) {
            case "fixedlist" -> pickFixedListCandidate(field, attempt);
            case "editablelist", "string", "memo" -> "AUTO_" + System.nanoTime() + (attempt > 0 ? "_" + attempt : "");
            case "numeric" -> (System.currentTimeMillis() % 1_000_000) + attempt;
            case "date" -> LocalDate.now().plusDays(attempt) + "T00:00:00";
            default -> throw new ApiException("Unrecognized UDF datatype '" + field.getDatatype()
                    + "' for mandatory field '" + field.getColumnName() + "'.", 0);
        };
    }

    /**
     * Picks a valid FixedList choice — prefers {@code lookupData.data} (populated for the
     * "Comma Separated" sub-mode, and would cover "Select Query" too if it ever comes back
     * populated), falling back to {@code listValues.split(",")}. A mandatory, Select-Query-sourced
     * field with no pre-resolved candidates has no verified, safe way to derive a value — throws
     * rather than attempting untested {@code lookupQuery} placeholder substitution.
     */
    private Object pickFixedListCandidate(UdfFieldMetadata field, int attempt) {
        List<String> candidates = new ArrayList<>();
        if (field.getLookupData() != null && field.getLookupData().getData() != null) {
            for (UdfLookupValue v : field.getLookupData().getData()) {
                if (v.getValue() != null) candidates.add(v.getValue());
            }
        }
        if (candidates.isEmpty() && field.getListValues() != null && !field.getListValues().isBlank()) {
            candidates.addAll(Arrays.asList(field.getListValues().split(",")));
        }
        if (candidates.isEmpty()) {
            if (field.isSqlData() || "S".equalsIgnoreCase(field.getQueryType())) {
                throw new ApiException("Mandatory Select-Query FixedList field '" + field.getColumnName()
                        + "' has no pre-resolved lookup data — lookupQuery execution with "
                        + "<Connected Site>/<WorkDate> substitution isn't implemented; needs a live captured example first.", 0);
            }
            throw new ApiException("Mandatory FixedList field '" + field.getColumnName()
                    + "' has no listValues/lookupData to choose from.", 0);
        }
        if (attempt >= candidates.size()) {
            throw new ApiException("Exhausted all " + candidates.size() + " candidate value(s) for mandatory+unique "
                    + "FixedList field '" + field.getColumnName() + "' — every value already in use.", 0);
        }
        return candidates.get(attempt).trim();
    }

    /**
     * Orchestrates the full flow for a given required classification, threading each step's live
     * response into the next rather than any hardcoded business data — same principle as
     * {@code ServiceInvoiceAdhocApiClient.createServiceInvoiceAdhocWithDefaults}. This is the
     * method {@code ArVoucherApBackfillStrategy} calls to backfill AP Voucher's document-payment
     * shortfall for any non-Supplier clsCode.
     *
     * @param requiredClsCode the admcls.clscode the backfilled sub-ledger must belong to (0 = any)
     */
    public ArVoucherCreateResponse createArVoucherWithDefaults(
            long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate) {

        String workDate = LocalDate.now() + "T00:00:00";

        List<DocNumberSchemeEntry> schemes = fetchDocNumberSchemes(ownerSiteCode, workDate);
        DocNumberSchemeEntry scheme = schemes.stream()
                .filter(s -> PREFERRED_DOC_SCHEME_NAME.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
        long docSchemeCode = scheme != null ? scheme.getCode() : 0;

        List<CashBankLedgerLookupResult> cashBankLedgers = fetchCashBankLedgers(ownerSiteCode, (int) empCode, ouCode, workDate);
        if (cashBankLedgers.isEmpty()) {
            throw new ApiException(
                    "GetCashBankLedgers returned no ledgers for ownerSiteCode=" + ownerSiteCode + " — cannot raise an AR Voucher.", 0);
        }
        CashBankLedgerLookupResult cashBankLedger = cashBankLedgers.get(0);

        SubLedgerLookupResult subLedger = resolveSubLedger(ownerSiteCode, ouCode, (int) empCode, workDate, requiredClsCode);

        List<ReferenceSiteLookupResult> refSites = fetchReferenceSites(ouCode, subLedger.getSlCode(), ownerSiteCode, workDate);
        ReferenceSiteLookupResult refSite = refSites.stream()
                .filter(s -> s.getCode() == ownerSiteCode)
                .findFirst()
                .orElseGet(() -> refSites.isEmpty() ? null : refSites.get(0));
        long referenceSiteCode = refSite != null ? refSite.getCode() : ownerSiteCode;
        String referenceSiteName = refSite != null ? refSite.getName() : null;

        validateVoucherDate(empCode, workDate, ouCode);

        List<UdfFieldMetadata> udfMetadata = fetchUdfMetadata("FINVCHMAIN");
        List<UdfValueEntry> udfValues = buildUdfValues(udfMetadata);

        double amount = quantity * rate;
        ArVoucherDetailLine line = ArVoucherDetailLine.builder()
                .subLedgerCode(subLedger.getSlCode())
                .subLedgerName(subLedger.getSlName())
                .subLedgerId(subLedger.getSlid())
                .subLedgerClassName(subLedger.getClassName())
                .ledgerCode(subLedger.getGlCode())
                .ledgerName(subLedger.getGlName())
                .ledgerGroupName(subLedger.getGeneralLedgerGroupName())
                .ledgerAbbreviation(subLedger.getGeneralLedgerAbbre())
                .referenceSiteName(referenceSiteName)
                .referenceSiteCode(referenceSiteCode)
                .paymentMode("C")
                .amount(amount)
                .build();

        String requestId = String.valueOf(System.nanoTime());

        ArVoucherCreateRequest request = ArVoucherCreateRequest.builder()
                .voucherDate(workDate)
                .docSchemeCode(docSchemeCode)
                .empCode(empCode)
                .connectedOUCode(ouCode)
                .connectedSiteCode(ownerSiteCode)
                .ownerSiteCode(ownerSiteCode)
                .cashBankGLCode(cashBankLedger.getGlCode())
                .arVoucherDetail(List.of(line))
                .requestId(requestId)
                .udfValues(udfValues)
                .build();

        ReportLogger.dataBackfill(
                "[ArVoucherApiClient] Creating AR Voucher — requiredClsCode={}, subLedger='{}' (classCode={}), ownerSiteCode={}, cashBankGLCode={}, amount={}",
                requiredClsCode, subLedger.getSlName(), subLedger.getClassCode(), ownerSiteCode, cashBankLedger.getGlCode(), amount);

        return createArVoucher(request);
    }
}
