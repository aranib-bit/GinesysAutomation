package com.tests.API;

import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import com.gsl.api.webapi.model.request.audit.ApplicableAuditModulesRequest;
import com.gsl.api.webapi.model.request.audit.AuditHistoryQuery;
import com.gsl.api.webapi.model.request.audit.AuditModuleLogRequest;
import com.tests.API.support.WireAssert;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * The Service Invoice Adhoc and Audit request POJOs must send exactly what the hand-built Map bodies sent before the migration (proven live):
 * the golden files in {@code har-samples/serviceinvoice/} are those bodies, nulls included.
 */
public class ServiceInvoiceWireTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 29);
    private static final String STAMP = "2026-10-29T00:00:00";
    private static final ServiceLookupResult SERVICE = ServiceLookupResult.builder()
            .serviceCode(5).serviceName("Consulting").glCode(21).hsnSacCode("9983").gstItcAppl("Y").taxCode(3).build();

    @Test
    public void getServicesBody() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.serviceLookup(7, 3, DATE), "serviceinvoice/get-services.json");
    }

    @Test
    public void getVendorsBodyAndQuery() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.vendorLookup(7, 3, DATE), "serviceinvoice/get-vendors.json");
        WireAssert.sameJson(ServiceInvoiceRequestFactory.vendorLookupQuery(7, 3), "serviceinvoice/vendors-query.json");
    }

    @Test
    public void validateReferenceNoBody() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.validateReferenceNo(7, 11, STAMP, "PABC"), "serviceinvoice/validate-reference-no.json");
    }

    @Test
    public void calculateChargesBodyAndQuery() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.calculateCharges(SERVICE, 10, 100), "serviceinvoice/calculate-charges.json");
        WireAssert.sameJson(ServiceInvoiceRequestFactory.calculateChargesQuery(2, 11, 7, STAMP, 1, 4, 1.0), "serviceinvoice/calculate-charges-query.json");
    }

    @Test
    public void tdsCalculationBody() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.tdsCalculation(SERVICE, 10, 100, List.of(), List.of(), 1, 11, STAMP, 3),
                "serviceinvoice/calculate-tds.json");
    }

    @Test
    public void schemeDocListQuery() {
        WireAssert.sameJson(ServiceInvoiceRequestFactory.schemeDocList(7, DATE), "serviceinvoice/scheme-doc-list-query.json");
    }

    @Test
    public void auditBodiesAndQuery() {
        WireAssert.sameJson(ApplicableAuditModulesRequest.builder().workDate(STAMP).page(1).start(0).limit(500).build(), "serviceinvoice/audit-modules.json");
        WireAssert.sameJson(AuditModuleLogRequest.builder().moduleShortCode("FinVoucher").moduleShortName("Voucher")
                .fromDate("2026-10-01T00:00:00").toDate(STAMP).user(null).build(), "serviceinvoice/audit-module-log.json");
        WireAssert.sameJson(AuditHistoryQuery.builder().code("1183324").tableName("FINVCHMAIN").moduleShortCode("FinVoucher")
                .fromDate("2026-10-01T00:00:00").toDate(STAMP).revisionFilter(false).build(), "serviceinvoice/audit-history-query.json");
    }

    @Test
    public void cacheBustedQueriesCarryTheirKeys() {
        var other = com.gsl.api.core.QueryParams.toMap(ServiceInvoiceRequestFactory.otherDetails(7, 3, 11, STAMP));
        org.testng.Assert.assertEquals(other.keySet(), java.util.Set.of("_dc", "ownerSiteCode", "ownerOuCode", "vendorCode", "transactionDate", "serviceType"));
        var tagged = com.gsl.api.core.QueryParams.toMap(ServiceInvoiceRequestFactory.ledgerTagged(21, 7));
        org.testng.Assert.assertEquals(tagged.keySet(), java.util.Set.of("_dc", "glCode", "ownerSiteCode"));
    }
}
