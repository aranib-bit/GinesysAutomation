package com.gsl.service;

import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
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
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static com.gsl.api.webapi.constants.ServiceInvoiceDefaults.DOCUMENT_TYPE;
import static com.gsl.api.webapi.constants.ServiceInvoiceDefaults.REFERENCE_NO_MAX_LENGTH;

/**
 * Service Invoice Adhoc workflows: vendor resolution and the full create flow that threads each step's live response into the next. It is also
 * the backfill mechanism for AP Voucher's document-payment shortfall (see PendingApDocumentBackfillStrategy): a Service Invoice Adhoc against a
 * vendor SL lands in v_pending_adj with debit_credit='C', exactly what the AP document selection searches for. The client only makes the HTTP calls.
 */
public class ServiceInvoiceAdhocService {

    private final ServiceInvoiceAdhocApiClient client;

    public ServiceInvoiceAdhocService(ServiceInvoiceAdhocApiClient client) {
        this.client = client;
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
        List<VendorLookupResult> vendors = client.fetchVendors(ownerSiteCode, ouCode);
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
                            "[ServiceInvoiceAdhocService] vendorSlCode='{}' not visible via GetVendors for "
                                    + "ownerSiteCode={} (checked {} vendor(s)) — falling back to live vendor "
                                    + "slid='{}' (code={}) instead.",
                            vendorSlCode, ownerSiteCode, vendors.size(), fallback.getSlid(), fallback.getCode());
                    return fallback;
                });
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
        String workDate = ServiceInvoiceRequestFactory.stamp(LocalDate.now());

        OwnerSiteDetail ownerSite = StepExecutor.call("Get Owner Site Details for Service Invoice Adhoc",
                () -> client.fetchOwnerSiteDetail(ownerSiteCode));

        List<ServiceLookupResult> services = StepExecutor.call("Get Services for Service Invoice Adhoc",
                () -> client.fetchServices(ownerSiteCode, ouCode));
        ServiceLookupResult service = services.isEmpty()
                ? ServiceLookupResult.builder().serviceCode(1).serviceName("Adhoc Service").build()
                : services.get(0);

        List<AdmSchemeDocEntry> schemeDocs = StepExecutor.call("Get Doc Scheme Nos for Service Invoice Adhoc",
                () -> client.fetchSchemeDocList(ownerSiteCode));
        AdmSchemeDocEntry selectedScheme = schemeDocs.isEmpty() ? null : schemeDocs.get(0);
        int docSchemeCode = selectedScheme != null ? selectedScheme.getDocSchemeCode() : 0;
        String gstAppl = selectedScheme != null ? selectedScheme.getGstAppl() : "N";

        VendorLookupResult vendor = StepExecutor.call("Get Vendor Details for Service Invoice Adhoc",
                () -> resolveVendor(vendorSlCode, ownerSiteCode, ouCode));

        GetOtherDetailsResult otherDetails = StepExecutor.call("Get Other Details for Service Invoice Adhoc",
                () -> client.fetchOtherDetails(ownerSiteCode, ouCode, vendor.getCode(), workDate));
        int tradeGroupCode = otherDetails.getTradeGroup() != null ? otherDetails.getTradeGroup().getCode() : 0;
        int termCode = otherDetails.getTerm() != null ? otherDetails.getTerm().getCode() : 0;
        int tdsSourceCode = otherDetails.getTdsSource() != null ? otherDetails.getTdsSource().getCode() : 0;

        String referenceNo = generateReferenceNo();
        StepExecutor.call("Validate Reference No for Service Invoice Adhoc", () -> {
            client.validateReferenceNo(ownerSiteCode, vendor.getCode(), workDate, referenceNo);
            return null;
        });

        if (service.getGlCode() != null) {
            boolean tagged = StepExecutor.call("Check Ledger Tagged for Service Invoice Adhoc",
                    () -> client.checkLedgerTagged(service.getGlCode(), ownerSiteCode));
            if (!tagged) {
                ReportLogger.networkError(
                        "[ServiceInvoiceAdhocService] glCode={} is not tagged in FINGL_SITE for ownerSiteCode={} "
                                + "— proceeding to Save anyway; the server's own validation is the final word.",
                        service.getGlCode(), ownerSiteCode);
            }
        }

        CalculateServiceChargesResult charges = StepExecutor.call("Calculate Service Charges for Service Invoice Adhoc",
                () -> client.calculateServiceCharges(
                        service, quantity, rate, docSchemeCode, vendor.getCode(), ownerSiteCode, workDate,
                        tradeGroupCode, termCode, otherDetails.getExchangeRate()));

        List<ServiceChargeDetail> lineChargeDetails = !charges.getServiceDetails().isEmpty()
                ? charges.getServiceDetails().get(0).getServiceCharges()
                : List.of();
        List<SimpleServiceCharge> lineCharges = lineChargeDetails.stream()
                .map(ServiceInvoiceAdhocService::toSimpleCharge)
                .collect(Collectors.toList());
        List<SaveHeaderCharge> headerCharges = charges.getHeaderCharges().stream()
                .map(ServiceInvoiceAdhocService::toSaveHeaderCharge)
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

        List<TdsRateAndAmount> tdsResults = StepExecutor.call("Calculate TDS Rate And Amount for Service Invoice Adhoc",
                () -> client.calculateTdsRateAndAmount(
                        service, quantity, rate, lineCharges, tagSites, tdsSourceCode, vendor.getCode(), workDate, ouCode));
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
                .type(DOCUMENT_TYPE)
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

        return StepExecutor.call("Create Service Invoice Adhoc via API", () -> client.createServiceInvoiceAdhoc(request));
    }

    /** Maps a calculated line charge into the line-level shape {@code Save} expects. */
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

    /** Maps a calculated header charge into the header-level shape {@code Save} expects (distinct field casing from {@link #toSimpleCharge}). */
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

    /** Generates a short (≤16 char), effectively-unique {@code documentNo} candidate — base-36 timestamp + random suffix, not server-validated for collisions. */
    private static String generateReferenceNo() {
        String candidate = "P" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1_000_000), 36);
        return candidate.length() > REFERENCE_NO_MAX_LENGTH ? candidate.substring(0, REFERENCE_NO_MAX_LENGTH) : candidate;
    }
}
