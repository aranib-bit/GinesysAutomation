package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.model.request.voucher.CashBankLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.ChequeQuery;
import com.gsl.api.webapi.model.request.voucher.CostCenterSiteQuery;
import com.gsl.api.webapi.model.request.voucher.GlLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.ParentGlSubLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.DocNumberSchemeQuery;
import com.gsl.api.webapi.model.request.voucher.PendingDocumentQuery;
import com.gsl.api.webapi.model.request.voucher.ReferenceSitePendingQuery;
import com.gsl.api.webapi.model.request.voucher.ReferenceSiteQuery;
import com.gsl.api.webapi.model.request.voucher.RootviewQuery;
import com.gsl.api.webapi.model.request.voucher.SubLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.TdsSectionQuery;
import com.tests.API.support.WireAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * The Finance lookup query POJOs must send what the UI sent (HAR query strings in {@code har-samples/voucher/}): every HAR parameter apart from
 * paging and the cache-buster is sent, nothing is invented beyond a short allow-list, and the constant values (module, site type, field list,
 * sort, target, doc type) are identical.
 */
public class VoucherQueryWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PAGING = Set.of("page", "start", "limit", "_dc");
    /** Parameters the framework sends that some HAR captures do not (the server ignores or defaults them). */
    private static final Set<String> ALLOWED_EXTRAS = Set.of("searchText", "pagingNotRequired");
    /** Extra parameters the framework keeps for one lookup because the flows are proven live with them (the HAR capture sends fewer). */
    private static final Map<String, Set<String>> SAMPLE_EXTRAS = Map.of(
            "gv-subledgers", Set.of("connectedSite", "connectedSiteOUCode", "connectedSiteType", "workDate", "queryFieldName", "query"),
            "gv-costcenter-sites", Set.of("connectedSiteOUCode", "module"),
            "gv-ledgers", Set.of());
    private static final Set<String> CONSTANT_VALUES = Set.of("module", "connectedSiteType", "queryFieldName", "target", "sort", "query", "docType", "menusysshortcode");

    private static final String WORK_DATE = "2026-10-29T00:00:00";

    @DataProvider(name = "queries")
    public Object[][] queries() {
        return new Object[][]{
                {"ap-cashbankledgers", CashBankLedgerQuery.forModule("apvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").build()},
                {"ar-cashbankledgers", CashBankLedgerQuery.forModule("arvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").build()},
                {"ap-subledgers", SubLedgerQuery.forModule("apvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").pagingNotRequired(true).build()},
                {"ar-subledgers", SubLedgerQuery.forModule("arvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").pagingNotRequired(true).build()},
                {"ap-docnumberschemes", DocNumberSchemeQuery.builder().connectedSite(1).menuShortCode("FinanceAPVoucher").workDate(WORK_DATE).build()},
                {"ar-docnumberschemes", DocNumberSchemeQuery.builder().connectedSite(1).menuShortCode("FinanceARVoucher").workDate(WORK_DATE).build()},
                {"ap-referencesite", ReferenceSiteQuery.forModule("apvoucher", 1, 1, WORK_DATE, "OS-OO-CM", 193, 1234).build()},
                {"ar-referencesite", ReferenceSiteQuery.forModule("arvoucher", 1, 1, WORK_DATE, "OS-OO-CM", 193, 0).build()},
                {"ap-pending-documents", new PendingDocumentQuery(1, 193, 1, WORK_DATE, "VDP", WORK_DATE)},
                {"ar-pending-documents", new PendingDocumentQuery(1, 193, 1, WORK_DATE, "VDP", WORK_DATE)},
                {"ar-reference-fifo", ReferenceSitePendingQuery.builder().module("arvoucher").ouCode(1).slCode(193).glCode(1).connectedOuCode(1).date(WORK_DATE).workDate(WORK_DATE).build()},
                {"ap-tdssection", TdsSectionQuery.builder().searchText("").queryFieldName(TdsSectionQuery.QUERY_FIELDS).connectedSite(1)
                        .connectedUserCode(513150L).module("apvoucher").workDate(WORK_DATE).connectedOuCode(1).connectedSiteType("OS-OO-CM")
                        .slCode(193).srcCode(1).voucherDate(WORK_DATE).build()},
                {"gv-cashbankledgers", CashBankLedgerQuery.forModule("generalvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").build()},
                {"gv-ledgers", GlLedgerQuery.forModule("generalvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM").build()},
                {"gv-subledgers", ParentGlSubLedgerQuery.forModule("generalvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM", 1746).build()},
                {"gv-costcenter-sites", CostCenterSiteQuery.forModule("generalvoucher", 1, 513150, 1, WORK_DATE, "OS-OO-CM", 1746, 36L).build()},
                {"gv-cheques", new ChequeQuery("generalvoucher", 4, WORK_DATE)},
                {"gv-docnumberschemes", DocNumberSchemeQuery.builder().connectedSite(1).menuShortCode("FinanceGenVoucher").workDate(WORK_DATE).build()},
                {"gv-rootview", new RootviewQuery(1183324, 1, 513150)},
                {"ap-rootview", new RootviewQuery(1183324, 1, 513150)},
                {"ar-rootview", new RootviewQuery(1183324, 1, 513150)},
        };
    }

    @Test(dataProvider = "queries")
    public void queryMatchesTheHar(String sampleName, Object query) throws IOException {
        JsonNode har = JSON.readTree(WireAssert.sample("voucher/" + sampleName + ".json")).get("query");
        Map<String, Object> mine = QueryParams.toMap(query);

        // HAR parameter names are matched case-insensitively (the server is: one capture spells it connectedsite, another connectedSite).
        Set<String> required = new TreeSet<>();
        har.fieldNames().forEachRemaining(k -> {
            if (!PAGING.contains(k)) required.add(k);
        });
        Set<String> mineLower = new HashSet<>();
        mine.keySet().forEach(k -> mineLower.add(k.toLowerCase()));
        Set<String> missing = new TreeSet<>();
        required.forEach(k -> {
            if (!mineLower.contains(k.toLowerCase())) missing.add(k);
        });
        assertTrue(missing.isEmpty(), sampleName + ": HAR parameters not sent: " + missing);

        Set<String> requiredLower = new HashSet<>();
        required.forEach(k -> requiredLower.add(k.toLowerCase()));
        Set<String> extras = new HashSet<>();
        mine.keySet().forEach(k -> {
            if (!requiredLower.contains(k.toLowerCase())) extras.add(k);
        });
        extras.removeAll(PAGING);
        extras.removeAll(ALLOWED_EXTRAS);
        extras.removeAll(SAMPLE_EXTRAS.getOrDefault(sampleName, Set.of()));
        assertTrue(extras.isEmpty(), sampleName + ": parameters not in the HAR: " + extras);

        for (String key : required) {
            if (!CONSTANT_VALUES.contains(key) || !mine.containsKey(key)) continue;
            JsonNode expected = har.get(key);
            Object actual = mine.get(key);
            if (actual instanceof Iterable<?> list) {
                int i = 0;
                for (Object v : list) assertEquals(String.valueOf(v), expected.get(i++).asText(), sampleName + " " + key);
                assertEquals(i, expected.size(), sampleName + " " + key + " size");
            } else {
                assertEquals(String.valueOf(actual), expected.get(0).asText(), sampleName + " " + key);
            }
        }
    }
}
