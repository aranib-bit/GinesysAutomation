package com.gsl.api.webapi.factory;

import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.model.serviceinvoiceadhoc.SimpleServiceCharge;
import com.gsl.api.model.serviceinvoiceadhoc.TagSite;
import com.gsl.api.webapi.model.request.serviceinvoice.CalculateChargesQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.CalculateChargesRequest;
import com.gsl.api.webapi.model.request.serviceinvoice.ChargeCalcLine;
import com.gsl.api.webapi.model.request.serviceinvoice.LedgerTaggedQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.OtherDetailsQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.OwnerSiteDetailQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.SchemeDocListQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.ServiceLookupRequest;
import com.gsl.api.webapi.model.request.serviceinvoice.TdsCalcLine;
import com.gsl.api.webapi.model.request.serviceinvoice.TdsCalculationRequest;
import com.gsl.api.webapi.model.request.serviceinvoice.ValidateReferenceNoRequest;
import com.gsl.api.webapi.model.request.serviceinvoice.VendorLookupQuery;
import com.gsl.api.webapi.model.request.serviceinvoice.VendorLookupRequest;

import java.time.LocalDate;
import java.util.List;

import static com.gsl.api.webapi.constants.ServiceInvoiceDefaults.*;

/** Builds the Service Invoice Adhoc request bodies and query strings; no HTTP, no shared state. */
public final class ServiceInvoiceRequestFactory {

    private ServiceInvoiceRequestFactory() {}

    /** The API's date-time text for a date ({@code yyyy-MM-ddT00:00:00}). */
    public static String stamp(LocalDate date) {
        return date + "T00:00:00";
    }

    public static OwnerSiteDetailQuery ownerSiteDetail(int ownerSiteCode) {
        return OwnerSiteDetailQuery.builder().ownerSiteCode(ownerSiteCode).build();
    }

    /**
     * Services of an owner site. The real site-context body is sent because an empty body lists all services for some sites and none for
     * others; {@code availableSites} carries just the owner site, the UI's per-tenant list of every site is not needed for the lookup.
     */
    public static ServiceLookupRequest serviceLookup(int ownerSiteCode, int ouCode, LocalDate date) {
        return ServiceLookupRequest.builder()
                .srvDate(stamp(date)).tdsTypeCode(null).tdsSection(null)
                .queryFieldName(SERVICE_QUERY_FIELDS).availableSites(List.of(ownerSiteCode))
                .connectedSite(String.valueOf(ownerSiteCode)).workDate(stamp(date)).connectedSiteOuCode(ouCode).connectedSiteType(SITE_TYPE)
                .page(1).start(0).limit(SERVICES_PAGE_SIZE).sort(SERVICE_SORT)
                .build();
    }

    public static SchemeDocListQuery schemeDocList(int siteCode, LocalDate date) {
        return SchemeDocListQuery.builder()
                .siteCode(siteCode).menuShortCode(MENU_SHORT_CODE).pagingNotRequired(true).isExcludeMannual(false).gstApplicability("")
                .workDate(stamp(date)).page(1).start(0).limit(SCHEME_DOCS_PAGE_SIZE)
                .build();
    }

    public static VendorLookupRequest vendorLookup(int ownerSiteCode, int ouCode, LocalDate date) {
        return VendorLookupRequest.builder()
                .query(null).hideTotalCount(false).queryFieldName(VENDOR_QUERY_FIELDS)
                .connectedSite(String.valueOf(ownerSiteCode)).workDate(stamp(date)).connectedSiteOuCode(ouCode).connectedSiteType(SITE_TYPE)
                .pagingNotRequired(false).searchText(null).page(1).start(0).limit(VENDORS_PAGE_SIZE).sort(VENDOR_SORT)
                .build();
    }

    public static VendorLookupQuery vendorLookupQuery(int ownerSiteCode, int ouCode) {
        return VendorLookupQuery.builder().ownerSiteCode(ownerSiteCode).ouCode(ouCode).build();
    }

    public static ValidateReferenceNoRequest validateReferenceNo(int ownerSiteCode, int vendorCode, String invoiceDate, String referenceNo) {
        return ValidateReferenceNoRequest.builder()
                .ownerSiteCode(ownerSiteCode).vendorCode(vendorCode).invoiceDate(invoiceDate).referenceNo(referenceNo).invoiceCode(NEW_INVOICE_CODE)
                .build();
    }

    public static OtherDetailsQuery otherDetails(int ownerSiteCode, int ownerOuCode, int vendorCode, String transactionDate) {
        return OtherDetailsQuery.builder()
                .cacheBuster(System.currentTimeMillis()).ownerSiteCode(ownerSiteCode).ownerOuCode(ownerOuCode).vendorCode(vendorCode)
                .transactionDate(transactionDate).serviceType(OTHER_DETAILS_SERVICE_TYPE)
                .build();
    }

    public static LedgerTaggedQuery ledgerTagged(int glCode, int ownerSiteCode) {
        return LedgerTaggedQuery.builder().cacheBuster(System.currentTimeMillis()).glCode(glCode).ownerSiteCode(ownerSiteCode).build();
    }

    public static CalculateChargesRequest calculateCharges(ServiceLookupResult service, int quantity, int rate) {
        ChargeCalcLine line = ChargeCalcLine.builder()
                .code(0).serviceCode(service.getServiceCode()).serviceAmount(quantity * rate).recordIndex(0).serviceCharges(List.of())
                .glCode(service.getGlCode()).slCode(null).quantity(0).gstItcAppl(service.getGstItcAppl())
                .build();
        return CalculateChargesRequest.builder()
                .services(List.of(line)).applicableItems(List.of()).chargeData(List.of()).skipChargeCode(List.of())
                .build();
    }

    public static CalculateChargesQuery calculateChargesQuery(int docSchemeCode, int vendorCode, int siteCode, String tranDate,
                                                              int tradeGroupCode, int termCode, double exchangeRate) {
        return CalculateChargesQuery.builder()
                .docSchemeCode(docSchemeCode).vendorCode(vendorCode).siteCode(siteCode).tranDate(tranDate)
                .tradeGroupCode(tradeGroupCode).termCode(termCode).formCode(FORM_CODE).isExciseApplicable(false).exchangeRate(exchangeRate)
                .build();
    }

    public static TdsCalculationRequest tdsCalculation(ServiceLookupResult service, int quantity, int rate, List<SimpleServiceCharge> serviceCharges,
                                                       List<TagSite> tagSites, int tdsSourceCode, int vendorCode, String srvDate, int admOuCode) {
        TdsCalcLine line = TdsCalcLine.builder()
                .setIndex(0).index(0).rowIndex(0).code(0).status(NEW_LINE_STATUS).srvAmt(quantity * rate).quantity(null)
                .glCode(service.getGlCode()).gstItcAppl(service.getGstItcAppl()).hsnSacCode(service.getHsnSacCode()).rate(null)
                .serviceCode(service.getServiceCode()).slCode(null).taxCode(service.getTaxCode()).tdsSection("")
                .tagSites(tagSites).serviceName(service.getServiceName()).serviceCharges(serviceCharges)
                .build();
        return TdsCalculationRequest.builder()
                .services(List.of(line)).tdsSourceCode(tdsSourceCode).vendorCode(vendorCode).srvDate(srvDate).admOuCode(admOuCode)
                .tdsAppAmt(0).srvCode(0)
                .build();
    }
}
