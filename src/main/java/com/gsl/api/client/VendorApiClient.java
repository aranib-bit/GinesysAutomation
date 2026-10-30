package com.gsl.api.client;

import com.gsl.api.webapi.model.response.common.BooleanResultResponse;
import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.PartyRequestMapper;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.config.ConfigFactory;
import com.gsl.utils.Faker.party.PartyDataFactory;
import com.gsl.utils.Faker.party.PartyProfile;
import com.gsl.api.webapi.factory.PartyRequestFactory;
import com.gsl.api.webapi.model.request.party.TdsException;
import com.gsl.api.webapi.model.request.party.VendorMasterCreateRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebAPI Vendor master reads, captured via HAR (VendorTDSApplicable): the vendor list with its TDS-applicable flag, and one vendor's
 * detail with the TDS natures ("exceptions") tagged to it. These replace the {@code finsl.tds_app} and {@code fintds_exception} DB reads
 * the AP Advance-with-TDS scenarios use to pick a vendor — the Finance sub-ledger lookup's own {@code tdsApp} field does not reflect it
 * on every environment.
 */
public class VendorApiClient extends ApiClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;

    public VendorApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** One row of {@code VendorMaster/GetVendors}. {@code code} is the sub-ledger code ({@code slcode}). */
    public record Vendor(long code, String slId, String name, boolean tdsApplicable, boolean extinct) {}

    /** One TDS nature tagged to a vendor; {@code selectionType} is "Services" or "Goods". */
    public record TdsNature(String name, String selectionType) {}

    /** One vendor's TDS setup from {@code VendorMaster/GetVendor}. */
    public record VendorTds(boolean tdsApplicable, List<TdsNature> natures) {}

    /** POST {@code VendorMaster/GetVendors}, paged until exhausted (typed through {@code PartyMasterWebApiClient}). */
    public List<Vendor> fetchVendors(int connectedSite, int connectedOuCode, String workDate) {
        List<Vendor> all = new ArrayList<>();
        for (com.gsl.api.webapi.model.response.party.PartyListItem it :
                new com.gsl.api.webapi.client.PartyMasterWebApiClient(spec).list(com.gsl.api.common.PartyKind.Family.VENDOR_MASTER, connectedSite, connectedOuCode, workDate)) {
            all.add(new Vendor(it.getCode(), it.getSlId(), it.getName(), it.isTdsApplicable(), it.isExtinct()));
        }
        return all;
    }

    /** POST {@code VendorMaster/GetVendor?code=}: the vendor's TDS flag and tagged natures. */
    @SuppressWarnings("unchecked")
    public VendorTds fetchVendorTds(long code) {
        Response response = postRaw(WebApiEndpoints.Party.VENDOR_GET, Map.of(), Map.of("code", code));
        boolean applicable = "Y".equalsIgnoreCase(response.jsonPath().getString("result.tdsapp"));
        List<Map<String, Object>> exceptions = response.jsonPath().getList("result.finTDSExceptions");
        List<TdsNature> natures = new ArrayList<>();
        if (exceptions != null) {
            for (Map<String, Object> ex : exceptions) {
                Map<String, Object> type = (Map<String, Object>) ex.get("tdstype");
                natures.add(new TdsNature(String.valueOf(ex.get("tdsTypeName")),
                        type == null ? null : String.valueOf(type.get("tdsSelectionType"))));
            }
        }
        return new VendorTds(applicable, natures);
    }

    // ─── create (HAR: CreateVendor*) ──────────────────────────────────────────

    /** One TDS nature tagged to a new vendor; the optional fields are the "exception" values (certificate, validity, rate, limit). */
    public record NatureSpec(long tdsTypeCode, String tdsTypeName, long admOuCode, String admOuName,
                             String certificateNo, String dateFrom, String dateTo, Object rate, Object maxLimit) {
        /** A plain nature: tagged with no certificate, validity, rate or limit. */
        public static NatureSpec plain(long code, String name, long ouCode, String ouName) {
            return new NatureSpec(code, name, ouCode, ouName, "", "", "", "", "");
        }
    }

    /** What to create. {@code natures} empty = TDS applicable with no nature tagged (all natures stay selectable). */
    public record VendorSpec(String name, String slId, boolean tdsApplicable, List<NatureSpec> natures) {}

    /** The environment-specific codes a new vendor needs (the Vendor Master rejects a null company code), read from an existing supplier so nothing is hardcoded. */
    public record EnvCodes(Object clscode, Object glcode, Object fintradegrpcode, Object purtermmaincode, Object admcmptaxcode,
                           Object cpcategorycode, Object cmpcode) {}

    /** One row of {@code Shared/GetTDSNameLookup}: a TDS nature. */
    public record TdsNatureRef(long code, String name, String selectionType) {}

    /** One row of {@code Shared/GetOrganizationUnitLookup}. */
    public record OuRef(long code, String name) {}

    /** The environment codes of the first non-extinct supplier in the vendor master (null when the master has none). */
    @SuppressWarnings("unchecked")
    public EnvCodes fetchEnvCodesFromExistingVendor(int connectedSite, int connectedOuCode, String workDate) {
        for (Vendor v : fetchVendors(connectedSite, connectedOuCode, workDate)) {
            if (v.extinct()) continue;
            Response response = postRaw(WebApiEndpoints.Party.VENDOR_GET, Map.of(), Map.of("code", v.code()));
            Map<String, Object> r = response.jsonPath().getMap("result");
            if (r == null || r.get("clscode") == null || r.get("glcode") == null) continue;
            return new EnvCodes(r.get("clscode"), r.get("glcode"), r.get("fintradegrpcode"), r.get("purtermmaincode"),
                    r.get("admcmptaxcode"), r.get("cpcategorycode"), r.get("cmpcode"));
        }
        return null;
    }

    /**
     * The active classes the Vendor Master creates parties in (Supplier, Jobber, Agent, Transporter, Channel, Staff:
     * {@code Shared/GetSubledgerClassLookup?classType=S&J&A&T&E&H}); each carries its class type letter.
     */
    @SuppressWarnings("unchecked")
    public List<PartyClassRef> fetchVendorMasterClasses(int site, int ou, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("classType", List.of("S", "J", "A", "T", "E", "H"));
        Response response = postRaw(WebApiEndpoints.Party.CLASS_LOOKUP,
                MasterDataSupport.lookupBody(site, ou, workDate, List.of("name"), true), params);
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        List<PartyClassRef> out = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> it : items) {
                if ("Y".equalsIgnoreCase(String.valueOf(it.get("ext")))) continue;
                out.add(new PartyClassRef(((Number) it.get("code")).longValue(), String.valueOf(it.get("name")),
                        String.valueOf(it.get("type")).charAt(0)));
            }
        }
        return out;
    }

    /** POST {@code Shared/GetTDSNameLookup}: the configured TDS natures. */
    public List<TdsNatureRef> fetchTdsNatures() {
        Response response = postRaw(WebApiEndpoints.Lookup.TDS_NAME, Map.of(), MasterDataSupport.simpleLookupPaging());
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        List<TdsNatureRef> out = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> it : items) {
                if ("Y".equalsIgnoreCase(String.valueOf(it.get("extinct")))) continue;
                out.add(new TdsNatureRef(((Number) it.get("code")).longValue(), String.valueOf(it.get("name")),
                        String.valueOf(it.get("tdsSelectionType"))));
            }
        }
        return out;
    }

    /** POST {@code Shared/GetOrganizationUnitLookup}. */
    public List<OuRef> fetchOrganizationUnits() {
        Response response = postRaw(WebApiEndpoints.Lookup.ORGANIZATION_UNIT, Map.of(), MasterDataSupport.simpleLookupPaging());
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        List<OuRef> out = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> it : items) {
                out.add(new OuRef(((Number) it.get("code")).longValue(), String.valueOf(it.get("name"))));
            }
        }
        return out;
    }

    /** POST {@code Shared/GetTDSSourceLookup}: the first configured TDS source code, empty when the environment has no TDS source master (qaprod). */
    public java.util.OptionalLong fetchFirstTdsSourceCode() {
        Response response = postRaw(WebApiEndpoints.Lookup.TDS_SOURCE, Map.of(), MasterDataSupport.simpleLookupPaging());
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        if (items == null || items.isEmpty()) return java.util.OptionalLong.empty();
        return java.util.OptionalLong.of(((Number) items.get(0).get("code")).longValue());
    }

    /**
     * Builds the {@code VendorMaster/Create} request: the POJO defaults (the captured HAR values) plus the identity, environment codes and TDS
     * fields of {@code spec}. {@code udf} is the FINSL UDF values (built from the metadata at send time). Public so a unit test can compare it
     * with the HAR bodies.
     */
    public static VendorMasterCreateRequest buildCreateRequest(VendorSpec spec, EnvCodes env, long tdsSourceCode,
                                                               List<com.gsl.api.model.udf.UdfValueEntry> udf, PartyProfile profile, List<com.gsl.api.common.CityRef> cities) {
        List<TdsException> natures = new ArrayList<>();
        for (NatureSpec n : spec.natures()) {
            natures.add(new TdsException(0L, n.tdsTypeCode(), n.tdsTypeName(), n.admOuCode(), n.admOuName(),
                    n.certificateNo(), n.dateFrom(), n.dateTo(), n.rate(), n.maxLimit()));
        }
        PartyRequestFactory.Identity identity = PartyRequestFactory.Identity.fromProfile(
                profile, spec.name(), spec.slId(), ((Number) env.clscode()).longValue(), ((Number) env.glcode()).longValue());
        VendorMasterCreateRequest request = PartyRequestFactory.vendor(identity, ((Number) env.fintradegrpcode()).longValue(),
                ((Number) env.purtermmaincode()).longValue(), ((Number) env.admcmptaxcode()).longValue(), ((Number) env.cpcategorycode()).longValue(),
                spec.tdsApplicable() ? tdsSourceCode : null, spec.tdsApplicable() ? natures : List.of(), udf,
                env.cmpcode() == null ? null : ((Number) env.cmpcode()).longValue());
        // The vendor environment carries one GST category (copied from an existing supplier), so no GSTIN is sent for generated vendors.
        PartyRequestMapper.apply(request, profile, null, cities);
        return request;
    }

    /** POST {@code VendorMaster/Create}. The response carries no code, so callers find the new vendor by {@code slId} afterwards. */
    public void createVendor(VendorSpec spec, EnvCodes env, long tdsSourceCode, int site, int ou, String workDate) {
        Response response = postRaw(WebApiEndpoints.Party.VENDOR_CREATE,
                buildCreateRequest(spec, env, tdsSourceCode, UdfValueBuilder.fetchAndBuild(this, "FINSL"),
                        PartyDataFactory.generator().next(ConfigFactory.getPartyDataConfig().completeness()),
                        new com.gsl.api.webapi.client.PartyMasterWebApiClient(this.spec).cities(site, ou, workDate)));
        if (!bodyAs(response, BooleanResultResponse.class).isTrue()) {
            throw new com.gsl.api.core.ApiException("VendorMaster/Create refused vendor '" + spec.name() + "': " + response.asString(), response.statusCode());
        }
    }

    // ─── extinct / update (HAR: ExtinctVendor) ────────────────────────────────

    /** Keys GetVendor returns that VendorMaster/Update does not take (read-only objects, location lookups, flat UDF columns). */
    private static final java.util.Set<String> UPDATE_DROP = new java.util.HashSet<>(java.util.List.of("agsl", "arapLedger", "bctcountry", "bctstate", "bctstdcode", "consignmentgl", "consignmentsalesgl", "consignmentsalessl", "consignmentsl", "consignmenttrnsfrgl", "consignmenttrnsfrsl", "cpgstinstate", "cpgstinstatename", "finTDSExceptionsHierarchical", "glName", "purchasegl", "purchasereturngl", "purchasereturnsl", "purchasesl", "salesgl", "salesreturngl", "salesreturnsl", "salessl", "sctcountry", "sctstate", "sctstdcode", "transitdiffgl", "transitdiffsl", "transitgl", "transitsl", "trpsl", "vatexpgl", "vatexpsl"));
    /** String fields the UI sends as "" where GetVendor returns null. */
    private static final java.util.Set<String> UPDATE_NULL_TO_EMPTY = new java.util.HashSet<>(java.util.List.of("agslname", "bankaccountno", "bankifccode", "bankmicrcode", "bankname", "bankrtgscode", "bdist", "bemaiL2", "bfX1", "bpH3", "bpH4", "brand", "bwebsite", "chqlabel", "cinno", "consignmentsalesglname", "consignmentsalesslname", "consignmentslname", "consignmenttrnsfrglname", "consignmenttrnsfrslname", "cstno", "exciseno", "gconsignmentglname", "gstinStatus", "gstinTaxPayerType", "lstno", "microno", "product", "purchaseglname", "purchasereturnglname", "purchasereturnslname", "purchaseslname", "rem", "saddr", "salesglname", "salesreturnglname", "salesreturnslname", "saltremail", "scp", "sctname", "sdist", "semail", "servicetaxcategor", "servicetaxno", "sfX1", "sfX2", "slaesslname", "slowner", "spH1", "spH2", "spH3", "spH4", "spin", "ssino", "swebsite", "transitdiffglname", "transitdiffslname", "transitglname", "transitslname", "trpslname", "vatexpglname", "vatexpslname"));

    /**
     * Builds the {@code VendorMaster/Update} body from a {@code GetVendor} result the way the UI does (captured in ExtinctVendor): read-only
     * keys dropped, null strings as "", {@code purchaseglcode} null as 0, the flat UDF columns folded into {@code udfDetails}, the TDS natures
     * reshaped to create-style entries, and {@code ext} set. Public static so it can be unit-tested against the HAR.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildUpdateBody(Map<String, Object> vendor, boolean extinct) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : vendor.entrySet()) {
            String k = e.getKey();
            if (UPDATE_DROP.contains(k) || k.startsWith("udfstring") || k.startsWith("udfnum") || k.startsWith("udfdate")
                    || k.equals("udfDetails") || k.equals("finTDSExceptions")) continue;
            Object v = e.getValue();
            if (v == null && UPDATE_NULL_TO_EMPTY.contains(k)) v = "";
            if (v == null && k.equals("purchaseglcode")) v = 0;
            body.put(k, v);
        }
        body.put("glname", "");
        body.put("ext", extinct ? "Y" : "N");

        List<Map<String, Object>> natures = new ArrayList<>();
        List<Map<String, Object>> existing = (List<Map<String, Object>>) vendor.get("finTDSExceptions");
        if (existing != null) {
            for (Map<String, Object> n : existing) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", 0);
                m.put("tdsTypeCode", n.get("tdsTypeCode"));
                m.put("tdsTypeName", n.get("tdsTypeName"));
                m.put("admOUCode", n.get("admOUCode"));
                m.put("admOUName", n.get("admOUName"));
                m.put("certificateno", n.get("certificateno") == null ? "" : n.get("certificateno"));
                m.put("dtfr", n.get("dtfr") == null ? "" : n.get("dtfr"));
                m.put("dtto", n.get("dtto") == null ? "" : n.get("dtto"));
                m.put("rate", n.get("rate") == null ? "" : n.get("rate"));
                m.put("maxlimit", n.get("maxlimit") == null ? "" : n.get("maxlimit"));
                natures.add(m);
            }
        }
        body.put("finTDSExceptions", natures);
        body.put("udfDetails", foldUdfs(vendor));
        return body;
    }

    /** Folds the flat {@code udfstringNN}/{@code udfnumNN}/{@code udfdateNN} columns into the {@code udfDetails} list the UI sends. */
    private static List<Map<String, Object>> foldUdfs(Map<String, Object> vendor) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 40; i++) out.add(udf(vendor, String.format("udfstring%02d", i), udfStringType(i)));
        for (int i = 1; i <= 10; i++) out.add(udf(vendor, String.format("udfnum%02d", i), "numeric"));
        for (int i = 1; i <= 5; i++) out.add(udf(vendor, String.format("udfdate%02d", i), "date"));
        return out;
    }

    private static String udfStringType(int i) {
        return i <= 10 ? "editablelist" : i <= 20 ? "fixedlist" : i <= 30 ? "string" : "memo";
    }

    private static Map<String, Object> udf(Map<String, Object> vendor, String field, String type) {
        Object v = vendor.get(field);
        if ("date".equals(type) && v instanceof String s && s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            v = s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4);   // the UI sends dd/MM/yyyy
        }
        if (v == null && "string".equals(type)) v = "";
        if (v == null && "memo".equals(type)) v = "";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldName", field);
        m.put("value", v);
        m.put("datatype", type);
        return m;
    }

    /** One vendor's raw {@code GetVendor} record. */
    public Map<String, Object> fetchVendorRecord(long code) {
        Response response = postRaw(WebApiEndpoints.Party.VENDOR_GET, Map.of(), Map.of("code", code));
        Map<String, Object> r = response.jsonPath().getMap("result");
        if (r == null) throw new com.gsl.api.core.ApiException("VendorMaster/GetVendor returned no record for code " + code, response.statusCode());
        return r;
    }

    /** POST {@code VendorMaster/Update}: marks the vendor extinct ({@code ext='Y'}) or active again ({@code 'N'}). */
    public void setExtinct(long code, boolean extinct) {
        Map<String, Object> body = buildUpdateBody(fetchVendorRecord(code), extinct);
        Response response = postRaw(WebApiEndpoints.Party.VENDOR_UPDATE, body);
        if (!bodyAs(response, BooleanResultResponse.class).isTrue()) {
            throw new com.gsl.api.core.ApiException("VendorMaster/Update refused vendor code " + code + ": " + response.asString(), response.statusCode());
        }
    }
}
