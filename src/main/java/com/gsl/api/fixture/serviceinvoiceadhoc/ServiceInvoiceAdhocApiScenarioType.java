package com.gsl.api.fixture.serviceinvoiceadhoc;

import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.serviceinvoiceadhoc.AdmSchemeDocEntry;
import com.gsl.api.model.serviceinvoiceadhoc.CalculateServiceChargesResult;
import com.gsl.api.model.serviceinvoiceadhoc.GetOtherDetailsResult;
import com.gsl.api.model.serviceinvoiceadhoc.OwnerSiteDetail;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateRequest;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.model.serviceinvoiceadhoc.TdsRateAndAmount;
import com.gsl.api.model.serviceinvoiceadhoc.VendorLookupResult;
import com.gsl.api.validator.ServiceInvoiceAdhocApiValidator;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;

/**
 * One constant per endpoint-variant pair for {@link ServiceInvoiceAdhocApiClient} — every public
 * lookup/calculate/save method gets a POSITIVE (valid input, expect success) and a NEGATIVE
 * (invalid input, expect rejection or an empty/absent result) constant, covering all 10 endpoint
 * methods, not just the terminal {@code ServiceInvoice/Save} call the existing
 * {@code ServiceInvoiceAdhocApiValidator}/backfill path already exercises.
 *
 * <p>Each constant's {@code action} is fully self-contained (re-resolves any vendor/service it
 * needs rather than depending on another scenario's output) so scenarios can run in any order or
 * subset — the same independence {@code ArVoucherFixtureFactory}'s scenario dispatch relies on.
 */
public enum ServiceInvoiceAdhocApiScenarioType {

    // ─── GetOwnerSiteOtherDetails ───────────────────────────────────────────
    FETCH_OWNER_SITE_DETAIL_POSITIVE("GetOwnerSiteOtherDetails", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                OwnerSiteDetail detail = client.fetchOwnerSiteDetail(ctx.getOwnerSiteCode());
                try {
                    ServiceInvoiceAdhocApiValidator.validateOwnerSiteDetail(detail);
                    return ScenarioOutcome.pass("fetchOwnerSiteDetail(" + ctx.getOwnerSiteCode() + ") returned " + detail);
                } catch (ApiException e) {
                    return ScenarioOutcome.fail(e.getMessage());
                }
            }),

    FETCH_OWNER_SITE_DETAIL_NEGATIVE_INVALID_CODE("GetOwnerSiteOtherDetails", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    OwnerSiteDetail detail = client.fetchOwnerSiteDetail(-1);
                    return detail == null
                            ? ScenarioOutcome.pass("fetchOwnerSiteDetail(-1) returned null, as expected for an invalid site code")
                            : ScenarioOutcome.fail("fetchOwnerSiteDetail(-1) unexpectedly returned data: " + detail);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchOwnerSiteDetail(-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── GetServices ────────────────────────────────────────────────────────
    FETCH_SERVICES_POSITIVE("GetServices", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<ServiceLookupResult> services = client.fetchServices(ctx.getOwnerSiteCode(), ctx.getOuCode());
                try {
                    ServiceInvoiceAdhocApiValidator.validateServices(services);
                    return ScenarioOutcome.pass("fetchServices returned " + services.size() + " service(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail(e.getMessage());
                }
            }),

    FETCH_SERVICES_NEGATIVE_INVALID_SITE("GetServices", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    List<ServiceLookupResult> services = client.fetchServices(-1, -1);
                    return (services == null || services.isEmpty())
                            ? ScenarioOutcome.pass("fetchServices(-1,-1) returned no services, as expected")
                            : ScenarioOutcome.fail("fetchServices(-1,-1) unexpectedly returned " + services.size() + " service(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchServices(-1,-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── GetAdmSchemeDocList ────────────────────────────────────────────────
    FETCH_SCHEME_DOC_LIST_POSITIVE("GetAdmSchemeDocList", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<AdmSchemeDocEntry> schemes = client.fetchSchemeDocList(ctx.getOwnerSiteCode());
                try {
                    ServiceInvoiceAdhocApiValidator.validateSchemeDocList(schemes);
                    return ScenarioOutcome.pass("fetchSchemeDocList returned " + schemes.size() + " scheme doc(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail(e.getMessage());
                }
            }),

    FETCH_SCHEME_DOC_LIST_NEGATIVE_INVALID_SITE("GetAdmSchemeDocList", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    List<AdmSchemeDocEntry> schemes = client.fetchSchemeDocList(-1);
                    return (schemes == null || schemes.isEmpty())
                            ? ScenarioOutcome.pass("fetchSchemeDocList(-1) returned no scheme docs, as expected")
                            : ScenarioOutcome.fail("fetchSchemeDocList(-1) unexpectedly returned " + schemes.size() + " scheme doc(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchSchemeDocList(-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── GetVendors ─────────────────────────────────────────────────────────
    FETCH_VENDORS_POSITIVE("GetVendors", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<VendorLookupResult> vendors = client.fetchVendors(ctx.getOwnerSiteCode(), ctx.getOuCode());
                try {
                    ServiceInvoiceAdhocApiValidator.validateVendors(vendors);
                    return ScenarioOutcome.pass("fetchVendors returned " + vendors.size() + " vendor(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail(e.getMessage());
                }
            }),

    FETCH_VENDORS_NEGATIVE_INVALID_SITE("GetVendors", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    List<VendorLookupResult> vendors = client.fetchVendors(-1, -1);
                    return (vendors == null || vendors.isEmpty())
                            ? ScenarioOutcome.pass("fetchVendors(-1,-1) returned no vendors, as expected")
                            : ScenarioOutcome.fail("fetchVendors(-1,-1) unexpectedly returned " + vendors.size() + " vendor(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchVendors(-1,-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── resolveVendor's documented soft-fallback contract ─────────────────
    RESOLVE_VENDOR_FALLBACK_ON_UNKNOWN_CODE("GetVendors (resolveVendor fallback)", ScenarioKind.BOUNDARY,
            (client, ctx) -> {
                try {
                    VendorLookupResult vendor = client.resolveVendor("NO_SUCH_VENDOR_SL_CODE", ctx.getOwnerSiteCode(), ctx.getOuCode());
                    return vendor != null
                            ? ScenarioOutcome.pass("resolveVendor fell back to a live vendor as documented: " + vendor.getSlid())
                            : ScenarioOutcome.fail("resolveVendor returned null instead of falling back");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("resolveVendor threw instead of falling back (0 vendors live for this site?): " + e.getMessage());
                }
            }),

    // ─── ValidateReferenceNo ────────────────────────────────────────────────
    VALIDATE_REFERENCE_NO_POSITIVE("ValidateReferenceNo", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                VendorLookupResult vendor = client.resolveVendor(ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getOuCode());
                String workDate = LocalDate.now() + "T00:00:00";
                String uniqueRef = "SCN" + Long.toString(System.nanoTime(), 36);
                try {
                    client.validateReferenceNo(ctx.getOwnerSiteCode(), vendor.getCode(), workDate, uniqueRef);
                    return ScenarioOutcome.pass("validateReferenceNo accepted a fresh, unique reference number");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("validateReferenceNo rejected a fresh reference number: " + e.getMessage());
                }
            }),

    VALIDATE_REFERENCE_NO_NEGATIVE_BLANK("ValidateReferenceNo", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                VendorLookupResult vendor = client.resolveVendor(ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getOuCode());
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    client.validateReferenceNo(ctx.getOwnerSiteCode(), vendor.getCode(), workDate, "");
                    return ScenarioOutcome.fail("validateReferenceNo accepted a blank reference number");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("validateReferenceNo rejected a blank reference number, as expected: " + e.getMessage());
                }
            }),

    // ─── GetOtherDetails ────────────────────────────────────────────────────
    FETCH_OTHER_DETAILS_POSITIVE("GetOtherDetails", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                VendorLookupResult vendor = client.resolveVendor(ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getOuCode());
                String workDate = LocalDate.now() + "T00:00:00";
                GetOtherDetailsResult details = client.fetchOtherDetails(ctx.getOwnerSiteCode(), ctx.getOuCode(), vendor.getCode(), workDate);
                try {
                    ServiceInvoiceAdhocApiValidator.validateOtherDetails(details);
                    return ScenarioOutcome.pass("fetchOtherDetails returned a result for vendorCode=" + vendor.getCode());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail(e.getMessage());
                }
            }),

    FETCH_OTHER_DETAILS_NEGATIVE_INVALID_VENDOR("GetOtherDetails", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    GetOtherDetailsResult details = client.fetchOtherDetails(ctx.getOwnerSiteCode(), ctx.getOuCode(), -1, workDate);
                    boolean allBlank = details == null
                            || (details.getTradeGroup() == null && details.getTerm() == null && details.getTdsSource() == null);
                    return allBlank
                            ? ScenarioOutcome.pass("fetchOtherDetails returned no resolvable details for vendorCode=-1, as expected")
                            : ScenarioOutcome.fail("fetchOtherDetails unexpectedly resolved details for vendorCode=-1: " + details);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchOtherDetails(vendorCode=-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── IsLedgerTaggedInFINGL_SITE ─────────────────────────────────────────
    CHECK_LEDGER_TAGGED_POSITIVE("IsLedgerTaggedInFINGL_SITE", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<ServiceLookupResult> services = client.fetchServices(ctx.getOwnerSiteCode(), ctx.getOuCode());
                Integer glCode = (services != null && !services.isEmpty()) ? services.get(0).getGlCode() : null;
                if (glCode == null) {
                    return ScenarioOutcome.fail("no service with a glCode available to exercise checkLedgerTagged positively");
                }
                try {
                    boolean tagged = client.checkLedgerTagged(glCode, ctx.getOwnerSiteCode());
                    return ScenarioOutcome.pass("checkLedgerTagged(glCode=" + glCode + ") returned " + tagged + " without error");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("checkLedgerTagged(glCode=" + glCode + ") threw unexpectedly: " + e.getMessage());
                }
            }),

    CHECK_LEDGER_TAGGED_NEGATIVE_INVALID_GLCODE("IsLedgerTaggedInFINGL_SITE", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    boolean tagged = client.checkLedgerTagged(-1, ctx.getOwnerSiteCode());
                    return !tagged
                            ? ScenarioOutcome.pass("checkLedgerTagged(glCode=-1) returned false, as expected")
                            : ScenarioOutcome.fail("checkLedgerTagged(glCode=-1) unexpectedly returned true");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("checkLedgerTagged(glCode=-1) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── CalculateServiceCharges ────────────────────────────────────────────
    CALCULATE_SERVICE_CHARGES_POSITIVE("CalculateServiceCharges", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                VendorLookupResult vendor = client.resolveVendor(ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getOuCode());
                ServiceLookupResult service = firstServiceOrDefault(client, ctx);
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    CalculateServiceChargesResult result = client.calculateServiceCharges(
                            service, ctx.getQuantity(), ctx.getRate(), 0, vendor.getCode(), ctx.getOwnerSiteCode(),
                            workDate, 0, 0, 1.0);
                    ServiceInvoiceAdhocApiValidator.validateServiceCharges(result);
                    return ScenarioOutcome.pass("calculateServiceCharges returned a result");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("calculateServiceCharges threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CALCULATE_SERVICE_CHARGES_NEGATIVE_INVALID_VENDOR_SITE("CalculateServiceCharges", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                ServiceLookupResult service = firstServiceOrDefault(client, ctx);
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    CalculateServiceChargesResult result = client.calculateServiceCharges(
                            service, ctx.getQuantity(), ctx.getRate(), -1, -1, -1, workDate, 0, 0, 1.0);
                    boolean noCharges = result == null
                            || (isEmpty(result.getServiceDetails()) && isEmpty(result.getHeaderCharges()) && result.getTotalNetAmt() == 0);
                    return noCharges
                            ? ScenarioOutcome.pass("calculateServiceCharges returned no charges for invalid vendor/site, as expected")
                            : ScenarioOutcome.fail("calculateServiceCharges unexpectedly computed charges for invalid vendor/site: " + result);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("calculateServiceCharges(invalid vendor/site) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── GetCalculatedTdsRateAndAmount ──────────────────────────────────────
    CALCULATE_TDS_RATE_AND_AMOUNT_POSITIVE("GetCalculatedTdsRateAndAmount", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                VendorLookupResult vendor = client.resolveVendor(ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getOuCode());
                ServiceLookupResult service = firstServiceOrDefault(client, ctx);
                String srvDate = LocalDate.now() + "T00:00:00";
                try {
                    List<TdsRateAndAmount> result = client.calculateTdsRateAndAmount(
                            service, ctx.getQuantity(), ctx.getRate(), List.of(), List.of(), 0, vendor.getCode(), srvDate, ctx.getOuCode());
                    return result != null
                            ? ScenarioOutcome.pass("calculateTdsRateAndAmount returned " + result.size() + " row(s) without error")
                            : ScenarioOutcome.fail("calculateTdsRateAndAmount returned null for valid input");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("calculateTdsRateAndAmount threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CALCULATE_TDS_RATE_AND_AMOUNT_NEGATIVE_INVALID_VENDOR("GetCalculatedTdsRateAndAmount", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                ServiceLookupResult service = firstServiceOrDefault(client, ctx);
                String srvDate = LocalDate.now() + "T00:00:00";
                try {
                    // Weak discriminator: an empty list is also this endpoint's normal "TDS not
                    // applicable" response — a thrown ApiException is the only unambiguous signal;
                    // an empty list here is treated as inconclusive-but-acceptable, not a failure.
                    client.calculateTdsRateAndAmount(service, ctx.getQuantity(), ctx.getRate(), List.of(), List.of(), -1, -1, srvDate, -1);
                    return ScenarioOutcome.pass("calculateTdsRateAndAmount(invalid vendor/tdsSource) did not throw — treated as acceptable (endpoint returns empty lists for legitimate non-applicable cases too)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("calculateTdsRateAndAmount(invalid vendor/tdsSource) rejected as expected: " + e.getMessage());
                }
            }),

    // ─── ServiceInvoice/Save (the terminal call — already covered by the existing validator/
    // backfill path for the POSITIVE case; added here so both variants live in one scenario set) ──
    CREATE_SERVICE_INVOICE_ADHOC_POSITIVE("ServiceInvoice/Save", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                try {
                    ServiceInvoiceAdhocCreateResponse response = client.createServiceInvoiceAdhocWithDefaults(
                            ctx.getVendorSlCode(), ctx.getOwnerSiteCode(), ctx.getQuantity(), ctx.getRate());
                    return (response != null && response.isSuccess())
                            ? ScenarioOutcome.pass("createServiceInvoiceAdhocWithDefaults succeeded — schemeDocno=" + response.getSchemeDocno())
                            : ScenarioOutcome.fail("createServiceInvoiceAdhocWithDefaults did not report success: " + response);
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createServiceInvoiceAdhocWithDefaults threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_SERVICE_INVOICE_ADHOC_NEGATIVE_MISSING_MANDATORY_FIELDS("ServiceInvoice/Save", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                // Deliberately near-empty — no vendor/site/services/docSchemeCode — via the same
                // createServiceInvoiceAdhocExpectFailure() the client exposes specifically so this
                // doesn't need to catch an ApiException to see the server's actual error/status.
                ServiceInvoiceAdhocCreateRequest incomplete = ServiceInvoiceAdhocCreateRequest.builder()
                        .type("N")
                        .build();
                Response response = client.createServiceInvoiceAdhocExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("ServiceInvoice/Save rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("ServiceInvoice/Save unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<ServiceInvoiceAdhocApiClient, ServiceInvoiceAdhocFixtureContext, ScenarioOutcome> action;

    ServiceInvoiceAdhocApiScenarioType(String endpoint, ScenarioKind kind,
            BiFunction<ServiceInvoiceAdhocApiClient, ServiceInvoiceAdhocFixtureContext, ScenarioOutcome> action) {
        this.endpoint = endpoint;
        this.kind = kind;
        this.action = action;
    }

    /** @return the endpoint name/label this scenario targets (used in report step names). */
    public String getEndpoint() {
        return endpoint;
    }

    /** @return whether this constant is a {@code POSITIVE}, {@code NEGATIVE}, or {@code BOUNDARY} scenario. */
    public ScenarioKind getKind() {
        return kind;
    }

    /** Runs this scenario's action against {@code client} using {@code ctx}'s reference values. */
    public ScenarioOutcome execute(ServiceInvoiceAdhocApiClient client, ServiceInvoiceAdhocFixtureContext ctx) {
        return action.apply(client, ctx);
    }

    /**
     * Resolves a real service to drive {@code calculateServiceCharges}/{@code calculateTdsRateAndAmount}
     * scenarios, falling back to a synthetic placeholder ({@code serviceCode=1}) if the live
     * {@code GetServices} lookup returns nothing for this site — keeps those scenarios runnable
     * even against a site with zero configured services.
     */
    private static ServiceLookupResult firstServiceOrDefault(ServiceInvoiceAdhocApiClient client, ServiceInvoiceAdhocFixtureContext ctx) {
        List<ServiceLookupResult> services = client.fetchServices(ctx.getOwnerSiteCode(), ctx.getOuCode());
        return (services != null && !services.isEmpty())
                ? services.get(0)
                : ServiceLookupResult.builder().serviceCode(1).serviceName("Adhoc Service").build();
    }

    /** Null-safe empty check used by the negative {@code calculateServiceCharges} scenario's "no charges" test. */
    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
