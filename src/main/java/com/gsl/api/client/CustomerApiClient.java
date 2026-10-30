package com.gsl.api.client;

import com.gsl.api.webapi.model.response.common.BooleanResultResponse;
import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.PartyRequestMapper;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.config.ConfigFactory;
import com.gsl.utils.Faker.party.PartyDataFactory;
import com.gsl.utils.Faker.party.PartyProfile;
import com.gsl.api.webapi.factory.PartyRequestFactory;
import com.gsl.api.webapi.model.request.party.CustomerCreateRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WebAPI Sales &amp; Distribution Customer master, captured via HAR (CustomerLookup / CustomerCreate): the customer list, the lookups a new
 * customer's codes come from, and {@code Customer/Create}. Used to make sure a Customer sub-ledger of a given class exists before an AP
 * Voucher Customer Advance is raised against it ({@code CustomerBackfill}). Existing customers are only read, never edited.
 */
public class CustomerApiClient extends ApiClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;

    public CustomerApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** One row of {@code Customer/GetCustomers}. {@code code} is the sub-ledger code ({@code slcode}); the row carries the class NAME only. */
    public record Customer(long code, String slId, String name, String className, boolean extinct) {}

    /** What to create: a customer of {@code clsCode} named {@code name} with the unique id {@code slId}. */
    public record CustomerSpec(String name, String slId, long clsCode) {}

    /**
     * The environment codes a new customer needs, all read from the lookups the UI uses. {@code pricelistCode}/{@code priceType} may be
     * null/'M' when the environment has no sale price list.
     */
    public record EnvCodes(long glCode, long tradeGroupCode, long salesTermCode, long purchaseTermCode, Long pricelistCode,
                           String priceType, long taxRegionCode, long gstCategoryCode, Long registeredGstCategoryCode) {}

    // ─── read ────────────────────────────────────────────────────────────────

    /** POST {@code Customer/GetCustomers}, paged until exhausted (typed through {@code PartyMasterWebApiClient}). */
    public List<Customer> fetchCustomers(int connectedSite, int connectedOuCode, String workDate) {
        List<Customer> all = new ArrayList<>();
        for (com.gsl.api.webapi.model.response.party.PartyListItem it :
                new com.gsl.api.webapi.client.PartyMasterWebApiClient(spec).list(com.gsl.api.common.PartyKind.Family.CUSTOMER, connectedSite, connectedOuCode, workDate)) {
            all.add(new Customer(it.getCode(), it.getSlId(), it.getName(), it.getClassName(), it.isExtinct()));
        }
        return all;
    }

    /** The lookup rows ({@code result.items}) of a {@code Shared/*} POST lookup; empty when the environment has none. */
    private List<Map<String, Object>> lookup(String path, Map<String, Object> params, Map<String, Object> body) {
        Response response = postRaw(path, body, params);
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        return items == null ? List.of() : items;
    }

    private static boolean active(Map<String, Object> row, String extinctKey) {
        return !"Y".equalsIgnoreCase(String.valueOf(row.get(extinctKey)));
    }

    private static long code(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    /** Active customer classes ({@code classType=C}). */
    public List<PartyClassRef> fetchCustomerClasses(int site, int ou, String workDate) {
        List<PartyClassRef> out = new ArrayList<>();
        for (Map<String, Object> it : lookup("/Shared/GetSubledgerClassLookup", Map.of("classType", "C"),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true))) {
            if (active(it, "ext")) out.add(new PartyClassRef(code(it, "code"), String.valueOf(it.get("name")), 'C'));
        }
        return out;
    }

    /**
     * Resolves the codes a new customer needs, or empty when the environment lacks one of the required masters (receivable ledger, trade
     * group with a sales and a purchase term, tax region, GST category) - the caller then skips with that reason instead of guessing.
     */
    public Optional<EnvCodes> fetchEnvCodes(int site, int ou, String workDate) {
        List<Map<String, Object>> ledgers = lookup("/Shared/GetLedger_ARAP_Lookup", Map.of(),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true));
        // A receivable: Asset-type AR/AP ledger with sub-ledgers (Sundry Debtors); the name is the tie-breaker, never the only test.
        Map<String, Object> ledger = ledgers.stream()
                .filter(l -> active(l, "ext") && "A".equalsIgnoreCase(String.valueOf(l.get("type"))) && "Y".equalsIgnoreCase(String.valueOf(l.get("slApp"))))
                .min(java.util.Comparator.comparing((Map<String, Object> l) -> !String.valueOf(l.get("name")).toLowerCase().contains("debtor")))
                .orElse(null);
        if (ledger == null) return Optional.empty();

        Map<String, Object> tradeGroup = null;
        Map<String, Object> salesTerm = null;
        Map<String, Object> purchaseTerm = null;
        for (Map<String, Object> tg : lookup("/Shared/GetTradeGroupLookup", Map.of(),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name", "description"), true))) {
            if (!active(tg, "ext")) continue;
            Map<String, Object> params = Map.of("tradeGroupCode", code(tg, "code"));
            Map<String, Object> st = first(lookup("/Shared/GetSalesTermLookup", params,
                    MasterDataSupport.lookupBody(site, ou, workDate, List.of("saleTermName"), true)), "extinct");
            Map<String, Object> pt = first(lookup("/Shared/GetPurchaseTermLookup", params,
                    MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true)), "extinct");
            if (st != null && pt != null) {
                tradeGroup = tg;
                salesTerm = st;
                purchaseTerm = pt;
                break;
            }
        }
        if (tradeGroup == null) return Optional.empty();

        Map<String, Object> taxRegion = first(lookup("/Shared/GetTaxRegionLookup", Map.of(),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true)), "ext");
        List<Map<String, Object>> categories = lookup("/AdmGSTCategory/GetList", Map.of(),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), false));
        // Unregistered needs no GSTIN, which is what a throw-away customer has.
        Map<String, Object> category = categories.stream()
                .filter(c -> String.valueOf(c.get("name")).toLowerCase().contains("unregistered")).findFirst()
                .orElse(null);
        if (taxRegion == null || category == null) return Optional.empty();

        Long registered = categories.stream()
                .filter(c -> String.valueOf(c.get("name")).toLowerCase().contains("registered") && !String.valueOf(c.get("name")).toLowerCase().contains("unregistered"))
                .map(c -> code(c, "code")).findFirst().orElse(null);
        Map<String, Object> priceList = first(lookup("/Shared/GetSalePricelistLookup", Map.of(),
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true)), "ext");
        return Optional.of(new EnvCodes(code(ledger, "glCode"), code(tradeGroup, "code"), code(salesTerm, "code"), code(purchaseTerm, "code"),
                priceList == null ? null : code(priceList, "code"),
                priceList == null ? "M" : String.valueOf(priceList.get("pricE_TYPE")),
                code(taxRegion, "code"), code(category, "code"), registered));
    }

    /** The first row whose {@code extinctKey} flag is not 'Y', or null. */
    private static Map<String, Object> first(List<Map<String, Object>> rows, String extinctKey) {
        return rows.stream().filter(r -> active(r, extinctKey)).findFirst().orElse(null);
    }

    // ─── create (HAR: CustomerCreate) ────────────────────────────────────────

    /**
     * Builds the {@code Customer/Create} request: the POJO defaults (the captured HAR values), the identity and class of {@code spec}, the
     * environment codes, and the contact/address/tax/bank data of the generated {@code profile}. Public so a unit test can compare it with the
     * HAR body.
     */
    public static CustomerCreateRequest buildCreateRequest(CustomerSpec spec, EnvCodes env, List<com.gsl.api.model.udf.UdfValueEntry> udf,
                                                           PartyProfile profile, List<com.gsl.api.common.CityRef> cities) {
        PartyRequestFactory.Identity identity = PartyRequestFactory.Identity.fromProfile(profile, spec.name(), spec.slId(), spec.clsCode(), env.glCode());
        CustomerCreateRequest request = PartyRequestFactory.customer(identity, env.tradeGroupCode(), env.salesTermCode(), env.purchaseTermCode(),
                env.pricelistCode(), env.priceType(), env.taxRegionCode(), env.gstCategoryCode(), udf);
        PartyRequestMapper.apply(request, profile, env.registeredGstCategoryCode(), cities);
        return request;
    }

    /** POST {@code Customer/Create}. The response carries no code, so callers find the new customer by {@code slId} afterwards. */
    public void createCustomer(CustomerSpec spec, EnvCodes env, int site, int ou, String workDate) {
        Response response = postRaw(WebApiEndpoints.Party.CUSTOMER_CREATE,
                buildCreateRequest(spec, env, UdfValueBuilder.fetchAndBuild(this, "FINSL"),
                        PartyDataFactory.generator().next(ConfigFactory.getPartyDataConfig().completeness()),
                        new com.gsl.api.webapi.client.PartyMasterWebApiClient(this.spec).cities(site, ou, workDate)));
        if (!bodyAs(response, BooleanResultResponse.class).isTrue()) {
            throw new ApiException("Customer/Create refused customer '" + spec.name() + "': " + response.asString(), response.statusCode());
        }
    }
}
