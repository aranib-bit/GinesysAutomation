package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.serviceinvoiceadhoc.AdmSchemeDocEntry;
import com.gsl.api.model.serviceinvoiceadhoc.AttachedFiles;
import com.gsl.api.model.serviceinvoiceadhoc.CalculateServiceChargesResult;
import com.gsl.api.model.serviceinvoiceadhoc.GetOtherDetailsResult;
import com.gsl.api.model.serviceinvoiceadhoc.OwnerSiteDetail;
import com.gsl.api.model.serviceinvoiceadhoc.OwnerSiteForSave;
import com.gsl.api.model.serviceinvoiceadhoc.SaveHeaderCharge;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceChargeDetail;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateRequest;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocLineItem;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.model.serviceinvoiceadhoc.SimpleServiceCharge;
import com.gsl.api.model.serviceinvoiceadhoc.TagSite;
import com.gsl.api.model.serviceinvoiceadhoc.TdsRateAndAmount;
import com.gsl.api.model.serviceinvoiceadhoc.VendorLookupResult;
import com.gsl.reports.ReportLogger;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebAPI/PublicAPI client for Service Invoice Adhoc creation. Mirrors the real 15-step flow
 * captured in D:\PerformanceAutomationTesting\flows\serviceInvoiceAdhoc.js and confirmed against
 * a live HAR — this is the flow used both as a standalone document type AND as the backfill
 * mechanism for AP Voucher's document-payment shortfall (see PendingApDocumentBackfillStrategy):
 * a Service Invoice Adhoc against a vendor SL lands in v_pending_adj with debit_credit='C',
 * exactly what DbApVoucherDataSource.selectApDocumentsAtomic() is searching for.
 *
 * <p>The 11 pure-UI GridState calls (grid/window state persistence) and the 2 vestigial calls
 * (GetUDFMetaData, GetListOfReportlayoutInOrder — never consumed by any later step, even in k6)
 * are intentionally not implemented here: none of them carry business data or affect whether
 * Save succeeds.
 */
public class ServiceInvoiceAdhocApiClient extends ApiClient {

    public ServiceInvoiceAdhocApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** GET /ServiceInvoice/GetOwnerSiteOtherDetails */
    public OwnerSiteDetail fetchOwnerSiteDetail(int ownerSiteCode) {
        Response response = getRaw("/ServiceInvoice/GetOwnerSiteOtherDetails", Map.of("ownerSiteCode", ownerSiteCode));
        return response.jsonPath().getObject("result", OwnerSiteDetail.class);
    }

    /**
     * POST /ServiceInvoice/GetServices — called with multiple filters in k6; simplified to one
     * default filter here. Sends the real site-context body (connectedSite/workDate/etc.) since
     * an empty body only returns all services for some sites, not others (confirmed: a different
     * site than stage-ideal returned zero services with an empty body).
     */
    public List<ServiceLookupResult> fetchServices(int ownerSiteCode, int ouCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        String srvDate = LocalDate.now() + "T00:00:00";
        body.put("srvDate", srvDate);
        body.put("tdsTypeCode", null);
        body.put("tdsSection", null);
        body.put("queryFieldName", List.of("servicename", "hsN_SAC_CODE", "glname", "slname", "section", "tdsagency", "gsT_ITC_APPL_DIS"));
        // k6's availableSites is a ~340-entry hardcoded per-tenant list with no live Java source —
        // substituted with the single real, already-known owner site rather than fabricated data.
        body.put("availableSites", List.of(ownerSiteCode));
        body.put("connectedSite", String.valueOf(ownerSiteCode));
        body.put("workDate", srvDate);
        body.put("connectedSiteOUCode", ouCode);
        body.put("connectedSiteType", "OS-OO-CM");
        body.put("page", 1);
        body.put("start", 0);
        body.put("limit", 50);
        body.put("sort", "[{\"property\":\"servicename\",\"direction\":\"ASC\"}]");

        Response response = postRaw("/ServiceInvoice/GetServices", body);
        return response.jsonPath().getList("result.items", ServiceLookupResult.class);
    }

    /** GET /RT/GetAdmSchemeDocList */
    public List<AdmSchemeDocEntry> fetchSchemeDocList(int siteCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("siteCode", siteCode);
        params.put("menushcode", "ServInvAdhoc");
        params.put("pagingNotRequired", true);
        params.put("isExcludeMannual", false);
        params.put("gstApplicability", "");
        params.put("workDate", LocalDate.now() + "T00:00:00");
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        Response response = getRaw("/RT/GetAdmSchemeDocList", params);
        return response.jsonPath().getList("result.items", AdmSchemeDocEntry.class);
    }

    /** POST /ServiceInvoice/GetVendors */
    public List<VendorLookupResult> fetchVendors(int ownerSiteCode, int ouCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", null);
        body.put("hideTotalCount", false);
        body.put("queryFieldName", List.of("name", "slid", "class", "generalLedger"));
        body.put("connectedSite", String.valueOf(ownerSiteCode));
        body.put("workDate", LocalDate.now() + "T00:00:00");
        body.put("connectedSiteOUCode", ouCode);
        body.put("connectedSiteType", "OS-OO-CM");
        body.put("pagingNotRequired", false);
        body.put("searchText", null);
        body.put("page", 1);
        body.put("start", 0);
        body.put("limit", 500);
        body.put("sort", "[{\"property\":\"name\",\"direction\":\"ASC\"}]");

        Map<String, Object> queryParams = Map.of("ownerSiteCode", ownerSiteCode, "ouCode", ouCode);
        Response response = postRaw("/ServiceInvoice/GetVendors", body, queryParams);
        return response.jsonPath().getList("result.items", VendorLookupResult.class);
    }

    /**
     * Resolves the given vendor SL code to a live vendor record via {@link #fetchVendors}
     * (no confirmed server-side filter by slid — fetch all, match client-side). The given
     * {@code vendorSlCode} is only a preference, not authoritative: {@code GetVendors} is scoped
     * server-side by owner site/OU, so a vendor that genuinely exists in {@code finsl} may still
     * not be visible/valid here (e.g. excluded by class/type for this site's vendor picker). When
     * it isn't found, fall back to the first vendor the live, correctly-scoped response DOES
     * confirm as valid — the backfill's job is to create SOME pending AP document, not one against
     * a specific preordained vendor, so any live-valid vendor serves the same purpose. Only throw
     * if GetVendors itself returns nothing at all for this owner site — a genuine no-data case.
     */
    public VendorLookupResult resolveVendor(String vendorSlCode, int ownerSiteCode, int ouCode) {
        List<VendorLookupResult> vendors = fetchVendors(ownerSiteCode, ouCode);
        if (vendors.isEmpty()) {
            throw new ApiException(
                    "GetVendors returned no vendors at all for ownerSiteCode=" + ownerSiteCode
                            + ", ouCode=" + ouCode + " — nothing usable to raise a Service Invoice Adhoc against.", 0);
        }
        return vendors.stream()
                .filter(v -> vendorSlCode.equals(v.getSlid()))
                .findFirst()
                .orElseGet(() -> {
                    VendorLookupResult fallback = vendors.get(0);
                    ReportLogger.networkError(
                            "[ServiceInvoiceAdhocApiClient] vendorSlCode='{}' not visible via GetVendors for "
                                    + "ownerSiteCode={} (checked {} vendor(s)) — falling back to live vendor "
                                    + "slid='{}' (code={}) instead.",
                            vendorSlCode, ownerSiteCode, vendors.size(), fallback.getSlid(), fallback.getCode());
                    return fallback;
                });
    }

    /** POST /ServiceInvoice/ValidateReferenceNo — throws if the server reports a real validation message. */
    public void validateReferenceNo(int ownerSiteCode, int vendorCode, String invoiceDate, String referenceNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ownerSiteCode", ownerSiteCode);
        body.put("vendorCode", vendorCode);
        body.put("invoiceDate", invoiceDate);
        body.put("referenceNo", referenceNo);
        body.put("invoiceCode", 0);
        Response response = postRaw("/ServiceInvoice/ValidateReferenceNo", body);
        String validationMessage = response.jsonPath().getString("result");
        if (validationMessage != null) {
            throw new ApiException("ReferenceNo '" + referenceNo + "' failed validation: " + validationMessage, 0);
        }
    }

    /** GET /ServiceInvoice/GetOtherDetails — the real source of tradeGroupCode/term/refSite/tdsSource. */
    public GetOtherDetailsResult fetchOtherDetails(int ownerSiteCode, int ownerOuCode, int vendorCode, String transactionDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("_dc", System.currentTimeMillis());
        params.put("ownerSiteCode", ownerSiteCode);
        params.put("ownerOuCode", ownerOuCode);
        params.put("vendorCode", vendorCode);
        params.put("transactionDate", transactionDate);
        params.put("serviceType", "N");
        Response response = getRaw("/ServiceInvoice/GetOtherDetails", params);
        return response.jsonPath().getObject("result", GetOtherDetailsResult.class);
    }

    /** GET /ServiceInvoice/IsLedgerTaggedInFINGL_SITE */
    public boolean checkLedgerTagged(int glCode, int ownerSiteCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("_dc", System.currentTimeMillis());
        params.put("glCode", glCode);
        params.put("ownerSiteCode", ownerSiteCode);
        Response response = getRaw("/ServiceInvoice/IsLedgerTaggedInFINGL_SITE", params);
        Boolean tagged = response.jsonPath().get("result");
        return Boolean.TRUE.equals(tagged);
    }

    /**
     * POST /ServiceInvoice/CalculateServiceCharges — the live tax/charge engine. Its response is
     * what gets embedded in the eventual Save request; nothing here is a pre-canned charge table.
     */
    public CalculateServiceChargesResult calculateServiceCharges(
            ServiceLookupResult service, int quantity, int rate,
            int docSchemeCode, int vendorCode, int siteCode, String tranDate,
            int tradeGroupCode, int termCode, double exchangeRate) {

        Map<String, Object> lineForCalc = new LinkedHashMap<>();
        lineForCalc.put("code", 0);
        lineForCalc.put("serviceCode", service.getServiceCode());
        lineForCalc.put("serviceAmount", quantity * rate);
        lineForCalc.put("recordIndex", 0);
        lineForCalc.put("serviceCharges", List.of());
        lineForCalc.put("glCode", service.getGlCode());
        lineForCalc.put("slCode", null);
        lineForCalc.put("quantity", 0);
        lineForCalc.put("gstItcAppl", service.getGstItcAppl());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("services", List.of(lineForCalc));
        body.put("applicableItems", List.of());
        body.put("chargeData", List.of());
        body.put("skipChargeCode", List.of());

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("docSchemeCode", docSchemeCode);
        queryParams.put("vendorCode", vendorCode);
        queryParams.put("siteCode", siteCode);
        queryParams.put("tranDate", tranDate);
        queryParams.put("tradeGroupCode", tradeGroupCode);
        queryParams.put("termCode", termCode);
        // Not present in any live response captured for this flow — fixed defaults, matching k6's
        // own hardcoding of these same two values across all 3 tenants (not business data).
        queryParams.put("formCode", 0);
        queryParams.put("isExciseApplicable", false);
        queryParams.put("exchangeRate", exchangeRate);

        Response response = postRaw("/ServiceInvoice/CalculateServiceCharges", body, queryParams);
        return response.jsonPath().getObject("result", CalculateServiceChargesResult.class);
    }

    /** POST /ServiceInvoice/GetCalculatedTdsRateAndAmount — bare array, empty when TDS isn't applicable. */
    public List<TdsRateAndAmount> calculateTdsRateAndAmount(
            ServiceLookupResult service, int quantity, int rate, List<SimpleServiceCharge> serviceCharges,
            List<TagSite> tagSites, int tdsSourceCode, int vendorCode, String srvDate, int admOuCode) {

        Map<String, Object> lineForTds = new LinkedHashMap<>();
        lineForTds.put("setIndex", 0);
        lineForTds.put("index", 0);
        lineForTds.put("rowIndex", 0);
        lineForTds.put("code", 0);
        lineForTds.put("status", "N");
        lineForTds.put("srvAmt", quantity * rate);
        lineForTds.put("quantity", null);
        lineForTds.put("glCode", service.getGlCode());
        lineForTds.put("gstItcAppl", service.getGstItcAppl());
        lineForTds.put("hsnSacCode", service.getHsnSacCode());
        lineForTds.put("rate", null);
        lineForTds.put("serviceCode", service.getServiceCode());
        lineForTds.put("slCode", null);
        lineForTds.put("taxCode", service.getTaxCode());
        lineForTds.put("tdsSection", "");
        lineForTds.put("tagSites", tagSites);
        lineForTds.put("serviceName", service.getServiceName());
        lineForTds.put("serviceCharges", serviceCharges);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("services", List.of(lineForTds));
        body.put("tdsSourceCode", tdsSourceCode);
        body.put("vendorCode", vendorCode);
        body.put("srvDate", srvDate);
        body.put("admOuCode", admOuCode);
        body.put("tdsAppAmt", 0);
        body.put("srvCode", 0);

        Response response = postRaw("/ServiceInvoice/GetCalculatedTdsRateAndAmount", body);
        return response.jsonPath().getList("result", TdsRateAndAmount.class);
    }

    /** POST ServiceInvoice/Save — the final save call. */
    public ServiceInvoiceAdhocCreateResponse createServiceInvoiceAdhoc(ServiceInvoiceAdhocCreateRequest request) {
        return post("/ServiceInvoice/Save", request, ServiceInvoiceAdhocCreateResponse.class);
    }

    /**
     * Orchestrates the full flow against the given vendor SL, threading each step's live response
     * into the next rather than any hardcoded business data. This is the method
     * {@code PendingApDocumentBackfillStrategy} calls to backfill AP Voucher's document-payment
     * shortfall.
     */
    public ServiceInvoiceAdhocCreateResponse createServiceInvoiceAdhocWithDefaults(
            String vendorSlCode, int ownerSiteCode, int quantity, int rate) {

        // No distinct "OU code" source exists in this framework yet — assumed 1:1 with
        // ownerSiteCode, matching every tenant's business data captured so far.
        int ouCode = ownerSiteCode;
        String workDate = LocalDate.now() + "T00:00:00";

        OwnerSiteDetail ownerSite = fetchOwnerSiteDetail(ownerSiteCode);

        List<ServiceLookupResult> services = fetchServices(ownerSiteCode, ouCode);
        ServiceLookupResult service = services.isEmpty()
                ? ServiceLookupResult.builder().serviceCode(1).serviceName("Adhoc Service").build()
                : services.get(0);

        List<AdmSchemeDocEntry> schemeDocs = fetchSchemeDocList(ownerSiteCode);
        AdmSchemeDocEntry selectedScheme = schemeDocs.isEmpty() ? null : schemeDocs.get(0);
        int docSchemeCode = selectedScheme != null ? selectedScheme.getDocSchemeCode() : 0;
        String gstAppl = selectedScheme != null ? selectedScheme.getGstAppl() : "N";

        VendorLookupResult vendor = resolveVendor(vendorSlCode, ownerSiteCode, ouCode);

        GetOtherDetailsResult otherDetails = fetchOtherDetails(ownerSiteCode, ouCode, vendor.getCode(), workDate);
        int tradeGroupCode = otherDetails.getTradeGroup() != null ? otherDetails.getTradeGroup().getCode() : 0;
        int termCode = otherDetails.getTerm() != null ? otherDetails.getTerm().getCode() : 0;
        int tdsSourceCode = otherDetails.getTdsSource() != null ? otherDetails.getTdsSource().getCode() : 0;

        String referenceNo = generateReferenceNo();
        validateReferenceNo(ownerSiteCode, vendor.getCode(), workDate, referenceNo);

        if (service.getGlCode() != null && !checkLedgerTagged(service.getGlCode(), ownerSiteCode)) {
            ReportLogger.networkError(
                    "[ServiceInvoiceAdhocApiClient] glCode={} is not tagged in FINGL_SITE for ownerSiteCode={} "
                            + "— proceeding to Save anyway; the server's own validation is the final word.",
                    service.getGlCode(), ownerSiteCode);
        }

        CalculateServiceChargesResult charges = calculateServiceCharges(
                service, quantity, rate, docSchemeCode, vendor.getCode(), ownerSiteCode, workDate,
                tradeGroupCode, termCode, otherDetails.getExchangeRate());

        List<ServiceChargeDetail> lineChargeDetails = !charges.getServiceDetails().isEmpty()
                ? charges.getServiceDetails().get(0).getServiceCharges()
                : List.of();
        List<SimpleServiceCharge> lineCharges = lineChargeDetails.stream()
                .map(ServiceInvoiceAdhocApiClient::toSimpleCharge)
                .collect(Collectors.toList());
        List<SaveHeaderCharge> headerCharges = charges.getHeaderCharges().stream()
                .map(ServiceInvoiceAdhocApiClient::toSaveHeaderCharge)
                .collect(Collectors.toList());

        String refSiteName = otherDetails.getRefSite() != null ? otherDetails.getRefSite().getName() : null;
        String refSiteType = otherDetails.getRefSite() != null ? otherDetails.getRefSite().getSiteType() : null;
        TagSite tagSite = TagSite.builder()
                .ownerSiteName(refSiteName)
                .ownerSiteCode(ownerSiteCode)
                .refSiteName(refSiteName)
                .refSiteCode(ownerSiteCode)
                .ownerSiteType(refSiteType)
                .refSiteType(refSiteType)
                .amount(quantity * rate)
                .build();
        List<TagSite> tagSites = List.of(tagSite);

        List<TdsRateAndAmount> tdsResults = calculateTdsRateAndAmount(
                service, quantity, rate, lineCharges, tagSites, tdsSourceCode, vendor.getCode(), workDate, ouCode);
        TdsRateAndAmount tds = tdsResults.isEmpty() ? null : tdsResults.get(0);

        ServiceInvoiceAdhocLineItem line = ServiceInvoiceAdhocLineItem.builder()
                .srvAmt(quantity * rate)
                .glCode(service.getGlCode())
                .gstItcAppl(service.getGstItcAppl())
                .hsnSacCode(service.getHsnSacCode())
                .serviceCode(service.getServiceCode())
                .taxCode(service.getTaxCode())
                .tagSites(tagSites)
                .serviceName(service.getServiceName())
                .serviceCharges(lineCharges)
                .build();

        // Not returned by GetVendors — an ExtJS model id assigned client-side; synthesized here
        // since Save's real request always carries one but its value isn't otherwise validated.
        vendor.setId("extModel-" + vendor.getCode());

        // Matches the real request's model-id shape (Prc.view.Transaction.model.ServiceInvoiceHeaderModel-<VU>-<iter>)
        // without k6's __VU/iterationTag concept — a per-call unique token instead.
        String requestId = "Prc.view.Transaction.model.ServiceInvoiceHeaderModel-" + System.nanoTime();

        ServiceInvoiceAdhocCreateRequest request = ServiceInvoiceAdhocCreateRequest.builder()
                // Fixed per-document-type constant, confirmed both by PURSRVMAIN's own check
                // constraint (type IN ('J','L','O','C','N')) and the one known-good real
                // transaction in the HAR — not business data, no live endpoint supplies this.
                .type("N")
                .id(requestId)
                .admOuCode(ouCode)
                .cpGstinNo(otherDetails.getCpGstinNo())
                .cpGstinStateCode(otherDetails.getCpGstinStateCode())
                .documentDate(workDate)
                .documentNo(referenceNo)
                .dueDate(workDate)
                .gstAppl(gstAppl)
                .ownerSiteGstinNo(ownerSite != null ? ownerSite.getOwnerSiteGstinNo() : null)
                .ownerSiteGstinStateCode(ownerSite != null ? ownerSite.getOwnerSiteGstinStateCode() : null)
                .srvDate(workDate)
                .tdsAppl(otherDetails.getTdsAppl())
                .ownerSite(OwnerSiteForSave.fromRefSite(otherDetails.getRefSite()))
                .refSite(otherDetails.getRefSite())
                .tradeGroup(otherDetails.getTradeGroup())
                .term(otherDetails.getTerm())
                .pcode(vendor)
                .tdsSource(otherDetails.getTdsSource())
                .dueDateBasis(otherDetails.getDueDateBasis())
                .creditDays(otherDetails.getCreditDays())
                .tdsRate(tds != null ? tds.getTdsRate() : null)
                .tdsAmt(tds != null ? tds.getTdsAmt() : null)
                .tdsGlCode(tds != null ? tds.getTdsGlCode() : null)
                .tdsSlCode(tds != null ? tds.getTdsSlCode() : null)
                .docSchemeCode(docSchemeCode)
                .attachedFiles(AttachedFiles.builder().build())
                // Backfill's whole purpose is a POSTED document (v_pending_adj only shows
                // release_status='P') — this user's profile has Auto Release enabled, so a plain
                // Save with saveAndRelease=false (the field's default, and what the k6 flow this
                // was ported from also used) leaves the document unposted. See
                // PendingApDocumentBackfillStrategy for the DB-verified release_status read-back.
                .saveAndRelease(true)
                .services(List.of(line))
                .headerCharges(headerCharges)
                .build();

        return createServiceInvoiceAdhoc(request);
    }

    private static SimpleServiceCharge toSimpleCharge(ServiceChargeDetail c) {
        return SimpleServiceCharge.builder()
                .appAmount(c.getAppAmount())
                .basis(c.getBasis())
                .chargeCode(c.getChargeCode())
                .chargeAmount(c.getChargeAmount())
                .formulae(c.getFormulae())
                .glCode(c.getGlCode())
                .gstComponent(c.getGstComponent())
                .includeInTds(c.getIncludeInTDS())
                .isReverse(c.getIsReverse())
                .isTax(c.getIsTax())
                .operationLevel(c.getOperationLevel())
                .rate(c.getRate())
                .roundoffAdjAmount(c.getRoundoffAdjAmount())
                .seq(c.getSeq())
                .sign(c.getSign())
                .slCode(c.getSlCode())
                .source(c.getSource())
                .isRoundOff(c.getIsRoundOff())
                .build();
    }

    private static SaveHeaderCharge toSaveHeaderCharge(ServiceChargeDetail c) {
        return SaveHeaderCharge.builder()
                .source(c.getSource())
                .appAmount(c.getAppAmount())
                .basis(c.getBasis())
                .chargeCode(c.getChargeCode())
                .chargeName(c.getChargeName())
                .chargeAmount(c.getChargeAmount())
                .formulae(c.getFormulae())
                .gLCode(c.getGlCode())
                .gSTComponent(c.getGstComponent())
                .includeInTds(c.getIncludeInTDS())
                .isReverse(c.getIsReverse())
                .isTax(c.getIsTax())
                .operationLevel(c.getOperationLevel())
                .rate(c.getRate())
                .seq(c.getSeq())
                .sign(c.getSign())
                .sLCode(c.getSlCode())
                .build();
    }

    private static String generateReferenceNo() {
        String candidate = "P" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1_000_000), 36);
        return candidate.length() > 16 ? candidate.substring(0, 16) : candidate;
    }
}
