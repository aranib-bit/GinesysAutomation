package com.gsl.api.client;

import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.factory.DocSchemeRequestFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.core.ApiClient;
import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.udf.UdfFieldMetadata;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-screen calls for managing document numbering schemes — the three flows recorded in the
 * "Numbering Scheme Creation", "…Tagging To Document" and "…Tagging To OU" HARs. All endpoints are
 * relative to the WEBAPI prefix baked into the session spec ({@code /WebAPI/api}), like every other
 * WEBAPI client. Needs a login with the admin screens' permissions.
 *
 * <p>Deliberately thin: it only sends/reads the recorded shapes. What to create/tag, and every safety
 * guard (test-scheme docnames only, allowed environments), lives in
 * {@code com.gsl.api.integration.DocSchemeProvisioner}.
 */
public class DocSchemeAdminApiClient extends ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** String keys the UI turns from {@code null} into {@code ""} when it re-posts a site (HAR-confirmed). */
    private static final List<String> SITE_NULL_TO_EMPTY_KEYS = List.of(
            "bctdist", "cp", "cstno", "emaiL2", "fax", "gstinStatus", "gstinTaxPayerType", "lstno", "opH2",
            "opH3", "psiteinitial", "psitestopsync", "psitetimezone", "reportname", "rpH1", "version", "website");

    /** Keys of the {@code documentScheme} object inside a site's {@code assignDocSchemes} entry. */
    private static final List<String> DOC_SCHEME_REF_KEYS = List.of(
            "docName", "ext", "sampleNumber", "isExcise", "gstAppl", "batchAppl", "code");

    public DocSchemeAdminApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** A scheme as listed by {@code GetListOfDocScheme}: code, name and the menu shortcodes already tagged to it. */
    public record SchemeRef(long code, String docName, List<String> menuShortcodes) {}

    /** The open accounting period the app uses to render sample numbers (e.g. {@code [26-27]}). */
    public record AccountingPeriod(String dateFrom, String dateTo, String yname) {
        /** {@code "[26-27]"} → {@code "26-27"}. */
        public String fyySuffix() {
            return yname == null ? "" : yname.replace("[", "").replace("]", "").trim();
        }
    }

    /** What to create — only the fields that vary; everything else follows the recorded UI request. */
    public record NewScheme(String docName, String prefix, int width, String remarks) {}

    // ── MenuDocScheme ────────────────────────────────────────────────────────

    /** POST MenuDocScheme/GetListOfDocScheme (paging off) — every scheme with its tagged menu shortcodes. */
    public List<SchemeRef> listSchemes(String workDate) {
        JsonNode items = json(postRaw(WebApiEndpoints.DocScheme.LIST, DocSchemeRequestFactory.list(workDate))).path("result").path("items");
        List<SchemeRef> out = new ArrayList<>();
        for (JsonNode item : items) {
            List<String> codes = new ArrayList<>();
            for (JsonNode m : item.path("assignedModules")) {
                String sh = m.path("menuShCode").asText("");
                if (!sh.isBlank()) codes.add(sh);
            }
            out.add(new SchemeRef(item.path("code").asLong(), item.path("docName").asText(), codes));
        }
        return out;
    }

    /** GET MenuDocScheme/AccountingPeriodInfo — first (current) period. */
    public AccountingPeriod fetchAccountingPeriod() {
        JsonNode first = json(getRaw(WebApiEndpoints.DocScheme.ACCOUNTING_PERIOD, Map.of())).path("result").path(0);
        if (first.isMissingNode()) {
            throw new ApiException("MenuDocScheme/AccountingPeriodInfo returned no accounting period", 0);
        }
        return new AccountingPeriod(first.path("dtFrom").asText(), first.path("dtTo").asText(), first.path("yname").asText());
    }

    /**
     * POST MenuDocScheme/Create. The response is only {@code {"result":true}} — it does NOT return the
     * new scheme's code, so the caller re-lists to find it.
     */
    public void createScheme(NewScheme scheme, AccountingPeriod period) {
        postRaw(WebApiEndpoints.DocScheme.CREATE, DocSchemeRequestFactory.create(scheme.docName(), scheme.prefix(), scheme.width(), scheme.remarks(),
                period.fyySuffix(), period.dateFrom(), period.dateTo()));
    }

    /**
     * POST MenuDocScheme/SaveDocMenuMap — one entry per menu shortcode ({@code onlyDelete=false} tags,
     * {@code true} untags). Callers pass the scheme's already-tagged shortcodes as well as the new ones so
     * nothing existing is dropped.
     */
    public void tagToMenus(long docCode, List<String> menuShortcodes) {
        postRaw(WebApiEndpoints.DocScheme.SAVE_MENU_MAP, DocSchemeRequestFactory.tag(docCode, menuShortcodes));
    }

    // ── AdmOrganizationSite ──────────────────────────────────────────────────

    /** POST AdmOrganizationSite/GetUnTaggedDocumentList — schemes NOT yet tagged to {@code siteCode} (idempotency check). */
    public List<Map<String, Object>> listUntaggedSchemes(long siteCode, String workDate) {
        JsonNode items = json(postRaw(WebApiEndpoints.DocScheme.UNTAGGED_FOR_SITE, DocSchemeRequestFactory.untagged(workDate),
                QueryParams.toMap(DocSchemeRequestFactory.siteCode(siteCode)))).path("result").path("items");
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode item : items) {
            out.add(MAPPER.convertValue(item, new TypeReference<LinkedHashMap<String, Object>>() {}));
        }
        return out;
    }

    /** GET AdmOrganizationSite/GetOrganizationSite?code= — the full site entity ({@code result}). */
    public Map<String, Object> fetchSite(long siteCode) {
        JsonNode result = json(getRawQuery(WebApiEndpoints.DocScheme.SITE_GET, DocSchemeRequestFactory.siteEntity(siteCode))).path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new ApiException("GetOrganizationSite returned no site for code=" + siteCode, 0);
        }
        return MAPPER.convertValue(result, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    /** GET udf/GetUDFMetaData for ADMSITE — needed to rebuild the site's {@code udfDetails} exactly as the UI does. */
    public List<UdfFieldMetadata> fetchSiteUdfMetadata() {
        return UdfValueBuilder.fetchUdfMetadata(this, "ADMSITE");
    }

    /** POST AdmOrganizationSite/Update?code= with a body from {@link #buildSiteUpdateBody}. */
    public void updateSite(long siteCode, Map<String, Object> body) {
        postRaw(WebApiEndpoints.DocScheme.SITE_UPDATE, body, QueryParams.toMap(DocSchemeRequestFactory.siteEntity(siteCode)));
    }

    /**
     * Rebuilds what the UI posts when it saves a site after adding one scheme — a pure function
     * (no HTTP), so the rules are unit-testable. Starting from the {@code GetOrganizationSite} entity:
     * <ul>
     *   <li>the 17 string fields the UI turns from {@code null} into {@code ""} are normalised;</li>
     *   <li>{@code bdist}, {@code salformcode}, {@code udfString01-15} are added when absent (UI-only fields);</li>
     *   <li>{@code assignDocSchemes}: every existing entry kept as {@code {code, documentScheme{7 keys}}}
     *       (GET's extra {@code admOU}/{@code dataVersion} dropped) plus the new scheme with {@code code=0};</li>
     *   <li>{@code udfDetails} rebuilt from the {@code udfstringNN/udfnumNN/udfdateNN} values and their metadata datatypes.</li>
     * </ul>
     * Everything else is passed through untouched. {@code newScheme} is one item of
     * {@link #listUntaggedSchemes}.
     */
    public static Map<String, Object> buildSiteUpdateBody(Map<String, Object> site,
                                                          List<UdfFieldMetadata> udfMetadata,
                                                          Map<String, Object> newScheme) {
        Map<String, Object> body = new LinkedHashMap<>(site);

        for (String key : SITE_NULL_TO_EMPTY_KEYS) {
            if (body.containsKey(key) && body.get(key) == null) body.put(key, "");
        }
        body.putIfAbsent("bdist", "");
        body.putIfAbsent("salformcode", null);
        for (int i = 1; i <= 15; i++) {
            body.putIfAbsent(String.format("udfString%02d", i), "");
        }

        List<Map<String, Object>> assigned = new ArrayList<>();
        Object existing = site.get("assignDocSchemes");
        if (existing instanceof List<?> list) {
            for (Object o : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) o;
                assigned.add(assignEntry(entry.get("code"), asMap(entry.get("documentScheme"))));
            }
        }
        assigned.add(assignEntry(0, newScheme));
        body.put("assignDocSchemes", assigned);

        List<Map<String, Object>> udfDetails = new ArrayList<>();
        for (UdfFieldMetadata meta : udfMetadata) {
            String field = meta.getColumnName().toLowerCase();
            Object value = site.get(field);
            if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) value = d.longValue();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fieldName", field);
            detail.put("value", value);
            detail.put("datatype", meta.getDatatype() == null ? null : meta.getDatatype().toLowerCase());
            udfDetails.add(detail);
        }
        body.put("udfDetails", udfDetails);
        return body;
    }

    private static Map<String, Object> assignEntry(Object code, Map<String, Object> docScheme) {
        Map<String, Object> ref = new LinkedHashMap<>();
        for (String key : DOC_SCHEME_REF_KEYS) {
            ref.put(key, docScheme.get(key));
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", code);
        entry.put("documentScheme", ref);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o == null ? Map.of() : (Map<String, Object>) o;
    }

    /** {@code yyyy-MM-ddT00:00:00} work date the admin screens send. */
    public static String today() {
        return LocalDate.now() + "T00:00:00";
    }

    private static JsonNode json(Response response) {
        try {
            return MAPPER.readTree(response.getBody().asString());
        } catch (IOException e) {
            throw new ApiException("Unparseable JSON response: " + e.getMessage(), response.getStatusCode(), e);
        }
    }
}
