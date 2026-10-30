package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.common.VoucherActionResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherDocSchemeRef;
import com.gsl.api.model.generalvoucher.GeneralVoucherListItem;
import com.gsl.api.model.generalvoucher.GeneralVoucherListRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherOwnerSite;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Server-free unit tests for {@link GeneralVoucherApiClient}: a JDK {@link HttpServer} on a random port plays the application, so
 * every verb/path/query/body and every response shape (all taken from the captured HARs) is checked without a network, a browser
 * or the environment. Run with {@code -Dtest=GeneralVoucherApiClientUnitTest}.
 */
public class GeneralVoucherApiClientUnitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One request the fake server saw. */
    private record Seen(String method, String path, String query, String body) {}

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    /** "METHOD path" -> canned JSON response body. */
    private final Map<String, String> canned = new ConcurrentHashMap<>();
    private GeneralVoucherApiClient client;

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(), body));
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            String rawQuery = exchange.getRequestURI().getRawQuery();
            String siteKey = null;
            if (rawQuery != null) {
                for (String part : rawQuery.split("&")) {
                    if (part.startsWith("connectedSite=")) siteKey = key + "#site" + part.substring("connectedSite=".length());
                }
            }
            String response = (siteKey != null && canned.containsKey(siteKey)) ? canned.get(siteKey)
                    : canned.getOrDefault(key, "{\"success\":true,\"result\":null}");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new GeneralVoucherApiClient(RestAssured.given().baseUri(base).contentType("application/json")) {
            @Override
            protected String financeBase() {
                return base + "/Finance/api";
            }
        };
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    @BeforeMethod
    public void reset() {
        seen.clear();
        canned.clear();
    }

    private Seen last() {
        assertFalse(seen.isEmpty(), "no request reached the fake server");
        return seen.get(seen.size() - 1);
    }

    private static JsonNode json(String text) throws IOException {
        return JSON.readTree(text);
    }

    // ───────────── create / edit ─────────────

    @Test
    public void createPostsTheHarShapedBodyToVouchersGeneralAndReadsDocNumberAndVchcode() throws IOException {
        canned.put("POST /Finance/api/vouchers/general",
                "{\"status\":0,\"message\":\"GV created\",\"result\":{\"docScheme\":\"VG/00410/26-27\",\"code\":1183324},\"dataVersion\":11462411,\"success\":true}");
        GeneralVoucherCreateRequest request = GeneralVoucherCreateRequest.builder()
                .empCode(513150).connectedOUCode(1)
                .ownerSite(GeneralVoucherOwnerSite.builder().code(1).name("Hena Embroidery").siteType("OS-OO-CM").shortName("HEMB").ouName("Hena Embroidery").ouCode(1).build())
                .docScheme(GeneralVoucherDocSchemeRef.builder().code(137).build())
                .cashBankLedger(CashBankLedgerLookupResult.builder().glCode(4).glName("HDFC Bank").abbreviation("hdfc").type("A").sourceType("B").build())
                .voucherDate("2026-10-15T00:00:00")
                .expenseIncomeDetail(List.of(GeneralVoucherDetailLine.builder()
                        .glCode(1746).glName("(sp)Contracter Charges").siteApplicable("Y").groupName("Job Charges").slCode(36L).slName("WB-INPUT IGST")
                        .expense(1)
                        .costCenterAllocations(List.of(CostCenterAllocation.builder().code(98).name("HAPPY SPROUTS-KP Road").siteType("MS-OO-CM").shortName("RVM2").amount(1).ouCode(1).build()))
                        .build()))
                .requestId("1792068744079")
                .build();

        GeneralVoucherCreateResponse response = client.createGeneralVoucher(request);

        assertEquals(response.getDocScheme(), "VG/00410/26-27");
        assertEquals(response.vchcode(), 1183324L);
        Seen call = last();
        assertEquals(call.method(), "POST");
        assertEquals(call.path(), "/Finance/api/vouchers/general");
        JsonNode body = json(call.body());
        assertEquals(body.get("code").asLong(), 0L);
        assertEquals(body.get("ownerSite").get("Name").asText(), "Hena Embroidery");
        assertEquals(body.get("ownerSite").get("OUCode").asInt(), 1);
        assertEquals(body.get("docScheme").get("code").asInt(), 137);
        assertEquals(body.get("cashBankLedger").get("sourceType").asText(), "B");
        assertEquals(body.get("documentStatus").asText(), "N");
        assertFalse(body.get("IsMarkedForSaveAndRelease").asBoolean());
        assertTrue(body.has("isAutoReleaseApplied") && body.has("isReadOnlyInstance"), "PascalCase/is* flag names must match the HAR");
        assertFalse(body.has("dataVersion"), "header dataVersion is only sent on update");
        JsonNode line = body.get("expenseIncomeDetail").get(0);
        assertEquals(line.get("glCode").asLong(), 1746L);
        assertEquals(line.get("isReversal").asText(), "N");
        assertEquals(line.get("expense").asDouble(), 1.0);
        assertEquals(line.get("income").asDouble(), 0.0);
        assertEquals(line.get("costCenterAllocations").get(0).get("code").asLong(), 98L);
        assertEquals(body.get("attachment").get("foldername").asText(), "0");
        assertTrue(body.get("attachment").has("Files"));
    }

    @Test
    public void updateUsesPutAndCarriesDataVersions() throws IOException {
        canned.put("PUT /Finance/api/vouchers/general",
                "{\"status\":0,\"message\":\"General Voucher updated successfully.\",\"result\":{\"docScheme\":\"VG/00410/26-27\",\"code\":1183324},\"success\":true}");
        GeneralVoucherCreateRequest request = GeneralVoucherCreateRequest.builder()
                .code(1183324).docNumber("VG/00410/26-27").documentStatus("U").dataVersion(11462433L)
                .expenseIncomeDetail(List.of(GeneralVoucherDetailLine.builder().code(186802).dataVersion(11462427).glCode(1746).expense(1).build()))
                .build();

        GeneralVoucherCreateResponse response = client.updateGeneralVoucher(request);

        assertEquals(response.vchcode(), 1183324L);
        assertEquals(last().method(), "PUT");
        JsonNode body = json(last().body());
        assertEquals(body.get("dataVersion").asLong(), 11462433L);
        assertEquals(body.get("documentStatus").asText(), "U");
        assertEquals(body.get("expenseIncomeDetail").get(0).get("code").asLong(), 186802L);
        assertEquals(body.get("expenseIncomeDetail").get(0).get("dataVersion").asLong(), 11462427L);
    }

    // ───────────── revert / release / delete ─────────────

    @Test
    public void revertAndReleaseSendCodeArrayAndParseTheItemTuple() throws IOException {
        String ok = "{\"status\":0,\"message\":\"\",\"result\":[{\"item1\":1183324,\"item2\":\"VG/00410/26-27\",\"item3\":\"success\",\"item4\":11462431}],\"success\":true}";
        canned.put("PUT /Finance/api/vouchers/general/revert", ok);
        canned.put("PUT /Finance/api/vouchers/general/release", ok);

        VoucherActionResponse revert = client.revertGeneralVoucher(List.of(1183324L), 513150, 1);
        assertTrue(revert.allSucceeded());
        assertEquals(last().path(), "/Finance/api/vouchers/general/revert");
        JsonNode body = json(last().body());
        assertEquals(body.get("code").get(0).asLong(), 1183324L);
        assertEquals(body.get("empCode").asLong(), 513150L);
        assertEquals(body.get("connectedOUCode").asInt(), 1);

        VoucherActionResponse release = client.releaseGeneralVoucher(List.of(1183324L), 513150, 1);
        assertTrue(release.allSucceeded());
        assertEquals(last().path(), "/Finance/api/vouchers/general/release");
    }

    @Test
    public void businessFailureInItem3IsReportedEvenThoughHttpIs200() {
        canned.put("PUT /Finance/api/vouchers/general/revert",
                "{\"status\":0,\"message\":\"\",\"result\":[{\"item1\":1183337,\"item2\":\"AP/00080019/26-27\",\"item3\":\"BRS is done for this transaction. Cannot be reverted.\",\"item4\":11462477}],\"success\":true}");
        VoucherActionResponse response = client.revertGeneralVoucher(List.of(1183337L), 513150, 1);
        assertFalse(response.allSucceeded());
        assertEquals(response.failureMessages(), List.of("BRS is done for this transaction. Cannot be reverted."));

        ApiException e = expectThrows(ApiException.class, () -> client.revertGeneralVoucherOrThrow(1183337L, 513150, 1));
        assertTrue(e.getMessage().contains("BRS is done"), e.getMessage());
    }

    @Test
    public void deleteSendsVoucherCodesAndAcceptsCapitalSuccess() throws IOException {
        canned.put("DELETE /Finance/api/vouchers/general",
                "{\"status\":0,\"message\":\"\",\"result\":[{\"item1\":1183324,\"item2\":\"VG/00410/26-27\",\"item3\":\"Success\"}],\"success\":true}");
        VoucherActionResponse response = client.deleteGeneralVouchers(List.of(1183324L), 513150, 1);
        assertTrue(response.allSucceeded(), "delete reports a capital-S Success and has no item4");
        assertEquals(last().method(), "DELETE");
        JsonNode body = json(last().body());
        assertEquals(body.get("voucherCodes").get(0).asLong(), 1183324L);
        assertFalse(body.has("code"), "delete uses voucherCodes, not code");
        client.deleteGeneralVoucherOrThrow(1183324L, 513150, 1);
    }

    // ───────────── read ─────────────

    @Test
    public void listFiltersByVoucherNumberAndMapsTheRow() throws IOException {
        canned.put("POST /Finance/api/vouchers/generalvouchers",
                "{\"status\":0,\"result\":{\"totalCount\":1,\"totalItem\":1,\"items\":[{\"code\":1183324,\"voucherNumber\":\"VG/00410/26-27\",\"paymentMode\":\"Cheque\","
                        + "\"instrumentNumber\":\"0046\",\"status\":\"U\",\"statusDisplay\":\"Unposted\",\"createdBy\":\"Arani Barman\",\"entrySource\":\"WEB\",\"paymentAmtCR\":6.000}],\"success\":true},\"success\":true}");

        GeneralVoucherListItem row = client.findByVoucherNumber("VG/00410/26-27", 1, 513150, 1, "2026-10-15T00:00:00");

        assertNotNull(row);
        assertEquals(row.getCode(), 1183324L);
        assertTrue(row.isUnposted());
        assertFalse(row.isPosted());
        assertEquals(row.getPaymentMode(), "Cheque");
        JsonNode body = json(last().body());
        assertEquals(body.get("ConnectedSiteOUCode").asInt(), 1, "this one key is PascalCase in the HAR");
        assertEquals(body.get("connectedUser").asLong(), 513150L);
        assertEquals(body.get("sort").asText(), "voucherDate DESC,voucherNumber DESC");
        assertTrue(body.get("criteriaLine").isNull());
        JsonNode filter = json(body.get("filter").asText());
        assertEquals(filter.get(0).get("property").asText(), "voucherNumber");
        assertEquals(filter.get(0).get("operator").asText(), "eq");
        assertEquals(filter.get(0).get("value").asText(), "VG/00410/26-27");
    }

    @Test
    public void listReturnsNullWhenTheVoucherIsGone() {
        canned.put("POST /Finance/api/vouchers/generalvouchers", "{\"status\":0,\"result\":{\"totalCount\":0,\"totalItem\":0,\"items\":[],\"success\":true},\"success\":true}");
        assertNull(client.findByVoucherNumber("VG/00411/26-27", 1, 513150, 1, "2026-10-15T00:00:00"));
    }

    @Test
    public void filterEscapesQuotesInTheVoucherNumber() throws IOException {
        JsonNode filter = json(GeneralVoucherListRequest.voucherNumberEquals("A\"B\\C"));
        assertEquals(filter.get(0).get("value").asText(), "A\"B\\C");
    }

    @Test
    public void rootViewMapsHeaderLinesAndDataVersions() {
        canned.put("GET /Finance/api/vouchers/generalvouchers/rootview",
                "{\"status\":0,\"result\":{\"code\":1183324,\"docNumber\":\"VG/00410/26-27\",\"documentStatus\":\"P\",\"dataVersion\":11462432,\"isReadOnlyInstance\":true,\"readOnlyReason\":\"BRS\","
                        + "\"ownerSite\":{\"code\":1,\"name\":\"Hena Embroidery\",\"siteType\":\"OS-OO-CM\",\"ouCode\":1},"
                        + "\"cashBankLedger\":{\"glCode\":4,\"glName\":\"HDFC Bank\",\"sourceType\":\"B\"},"
                        + "\"expenseIncomeDetail\":[{\"code\":186802,\"glCode\":1746,\"siteApplicable\":\"Y\",\"expense\":1.0,\"income\":0.0,\"dataVersion\":11462427,"
                        + "\"costCenterAllocations\":[{\"code\":98,\"name\":\"HAPPY SPROUTS-KP Road\",\"amount\":1.0,\"ouCode\":1}]}],\"unknownFieldIgnored\":1},\"success\":true}");

        GeneralVoucherRootView view = client.fetchRootView(1183324L, 1, 513150);

        assertEquals(last().path(), "/Finance/api/vouchers/generalvouchers/rootview");
        assertTrue(last().query().contains("code=1183324") && last().query().contains("connectedOUCode=1") && last().query().contains("connectedUserCode=513150"), last().query());
        assertTrue(view.isPosted());
        assertEquals(view.getDataVersion(), 11462432L);
        assertTrue(view.isReadOnlyInstance());
        assertEquals(view.getReadOnlyReason(), "BRS");
        assertEquals(view.getCashBankLedger().getSourceType(), "B");
        assertEquals(view.getExpenseIncomeDetail().get(0).getDataVersion(), 11462427L);
        assertEquals(view.getExpenseIncomeDetail().get(0).getCostCenterAllocations().get(0).getName(), "HAPPY SPROUTS-KP Road");
    }

    // ───────────── lookups ─────────────

    @Test
    public void glLookupExposesTypeSourceAndApplicabilityFlags() {
        canned.put("GET /Finance/api/ledgers",
                "{\"status\":0,\"result\":{\"totalCount\":2,\"items\":["
                        + "{\"glCode\":1746,\"glName\":\"(sp)Contracter Charges\",\"groupName\":\"Job Charges\",\"siteApplicable\":\"Y\",\"subLedgerApplicable\":\"Y\",\"type\":\"E\",\"sourceType\":\"G\",\"glSiteCount\":0,\"slCount\":3},"
                        + "{\"glCode\":1117,\"glName\":\"AFFIFA TRUST\",\"siteApplicable\":\"N\",\"subLedgerApplicable\":\"N\",\"type\":\"A\",\"sourceType\":\"G\"}],\"success\":true},\"success\":true}");

        List<GlLedgerLookupResult> gls = client.fetchLedgers(1, 513150, 1, "2026-10-15T00:00:00");

        assertEquals(gls.size(), 2);
        assertTrue(gls.get(0).isSiteApplicable() && gls.get(0).isSubLedgerApplicable());
        assertFalse(gls.get(1).isSiteApplicable() || gls.get(1).isSubLedgerApplicable());
        assertEquals(gls.get(0).getType(), "E");
        assertEquals(gls.get(0).getSlCount(), 3);
        assertTrue(last().query().contains("module=generalvoucher"), last().query());
    }

    @Test
    public void subLedgersAreScopedByParentGlCode() {
        canned.put("GET /Finance/api/subledgers", "{\"status\":0,\"result\":{\"totalCount\":1,\"items\":[{\"slCode\":36,\"slName\":\"WB-INPUT IGST\",\"slid\":\"SL001\",\"classType\":\"O\"}],\"success\":true},\"success\":true}");
        var sls = client.fetchSubLedgers(1746, 1, 513150, 1, "2026-10-15T00:00:00");
        assertEquals(sls.get(0).getSlid(), "SL001");
        assertTrue(last().query().contains("parentGLCode=1746") && last().query().contains("module=generalvoucher"), last().query());
    }

    @Test
    public void costCenterSitesPageThroughAndKeepOnlySameOuUnblockedSites() {
        // page 1 (50 of 120 total): 2 usable, 1 other-OU, 1 blocked; the client must keep paging until it has the wanted number.
        canned.put("GET /Finance/api/sites",
                "{\"status\":0,\"result\":{\"totalCount\":120,\"items\":["
                        + "{\"code\":42,\"name\":\"AFFIFA 1\",\"siteType\":\"OS-OO-CM\",\"shortName\":\"AFF\",\"ouCode\":1,\"blockFin\":\"N\"},"
                        + "{\"code\":1,\"name\":\"Hena Embroidery\",\"siteType\":\"OS-OO-CM\",\"shortName\":\"HEMB\",\"ouCode\":1,\"blockFin\":\"N\"},"
                        + "{\"code\":77,\"name\":\"Other OU site\",\"siteType\":\"OS-OO-CM\",\"ouCode\":2,\"blockFin\":\"N\"},"
                        + "{\"code\":78,\"name\":\"Blocked site\",\"siteType\":\"OS-OO-CM\",\"ouCode\":1,\"blockFin\":\"Y\"}],\"success\":true},\"success\":true}");

        List<CostCenterSiteLookupResult> sites = client.fetchCostCenterSites(1479, null, 1, 1, 513150, "2026-10-15T00:00:00", 2);

        assertEquals(sites.size(), 2);
        assertEquals(sites.get(0).getCode(), 42L);
        assertEquals(sites.get(1).getCode(), 1L);
        assertTrue(last().query().contains("target=costcentertagging") && last().query().contains("glCode=1479"), last().query());
        assertFalse(last().query().contains("slCode="), "slCode only when a sub-ledger is involved");
        CostCenterAllocation allocation = sites.get(0).toAllocation(2.5, 1);
        assertEquals(allocation.getAmount(), 2.5);
        assertEquals(allocation.getOuCode(), 1L);
    }

    @Test
    public void chequesLookupReturnsBookAndLeaf() {
        canned.put("GET /Finance/api/cheques", "{\"status\":0,\"result\":{\"totalCount\":1,\"items\":[{\"chequeNumber\":\"0046\",\"bookNumber\":1,\"chequeDate\":null,\"bookCode\":69619}],\"success\":true},\"success\":true}");
        List<ChequeLookupResult> cheques = client.fetchCheques(4, "2026-10-15T00:00:00");
        assertEquals(cheques.get(0).getChequeNumber(), "0046");
        assertEquals(cheques.get(0).getBookCode(), 69619L);
        assertTrue(last().query().contains("glCode=4") && last().query().contains("module=generalvoucher"), last().query());
    }

    @Test
    public void userContextReadsSitesProfileFlagsAndTheMakerCheckerSetting() {
        canned.put("POST /UserRolePolicy/GetAllSiteForAUser",
                "{\"result\":{\"sites\":[{\"siteCode\":5,\"siteName\":\"Warehouse\",\"siteType\":\"MS-OO-CM\",\"ouCode\":1},{\"siteCode\":1,\"siteName\":\"Hena Embroidery\",\"siteType\":\"OS-OO-CM\",\"shortName\":\"HEMB\",\"ouCode\":1,\"ouName\":\"Hena Embroidery\"}],"
                        + "\"oucode\":1,\"ouName\":\"Hena Embroidery\",\"allowAuthorizeOfSelfCreatedTransaction\":\"Y\",\"profileAccess\":{\"code\":1,\"profName\":\"Default\",\"finCanRevert\":\"Y\",\"finCanRelease\":\"N\"}},\"success\":true}");

        UserSiteContext ctx = client.fetchUserContext();

        assertEquals(last().path(), "/UserRolePolicy/GetAllSiteForAUser");
        assertTrue(last().query().contains("userId=0"));
        assertEquals(ctx.firstOwnerSite().getSiteCode(), 1L);
        assertTrue(ctx.profileFlag("finCanRevert"));
        assertFalse(ctx.profileFlag("finCanRelease"));
        assertFalse(ctx.profileFlag("doesNotExist"));
        assertEquals(ctx.getAllowAuthorizeOfSelfCreatedTransaction(), "Y");
    }

    // ───────────── errors ─────────────

    @Test
    public void successFalseEnvelopeBecomesAnApiException() {
        canned.put("POST /Finance/api/vouchers/general", "{\"status\":1,\"success\":false,\"error\":\"Voucher date is in a closed month\"}");
        ApiException e = expectThrows(ApiException.class, () -> client.createGeneralVoucher(GeneralVoucherCreateRequest.builder().build()));
        assertTrue(e.getMessage().contains("closed month"), e.getMessage());
    }

    @Test
    public void expectFailureVariantReturnsTheRawResponseInsteadOfThrowing() {
        canned.put("POST /Finance/api/vouchers/general", "{\"status\":1,\"success\":false,\"error\":\"nope\"}");
        var raw = client.createGeneralVoucherExpectFailure(GeneralVoucherCreateRequest.builder().build());
        assertEquals(raw.jsonPath().getString("error"), "nope");
    }

    @Test
    public void workDateFormatMatchesTheUiConvention() {
        assertEquals(GeneralVoucherApiClient.workDate(java.time.LocalDate.of(2026, 10, 5)), "2026-10-05T00:00:00");
        // keep the ArrayList import honest for readers extending this class
        assertTrue(new ArrayList<>(List.of(1)).size() == 1);
    }

    // ───────────── owner-site selection ─────────────

    private static com.gsl.api.model.common.UserSiteContext.Site site(long code, long ou) {
        return com.gsl.api.model.common.UserSiteContext.Site.builder().siteCode(code).siteName("S" + code)
                .siteType("OS-OO-CM").ouCode(ou).build();
    }

    @Test
    public void ownerSiteSkipsSitesWithoutALedgerAndIsCached() {
        canned.put("GET /Finance/api/ledgers/cashbankledgers#site42", "{\"success\":true,\"result\":{\"totalCount\":0,\"items\":[]}}");
        canned.put("GET /Finance/api/ledgers/cashbankledgers#site1",
                "{\"success\":true,\"result\":{\"totalCount\":1,\"items\":[{\"glCode\":4,\"glName\":\"HDFC\",\"sourceType\":\"B\"}]}}");
        var ctx = com.gsl.api.model.common.UserSiteContext.builder().sites(List.of(site(42, 1), site(1, 1))).build();
        var choice = new com.gsl.service.GeneralVoucherService(client).findOwnerSiteWithCashBank(ctx, 777001L, "B", "2026-10-16T00:00:00");
        assertEquals(choice.site().getSiteCode(), 1L);
        assertEquals(choice.probed(), List.of("S1(1)=1"));   // ascending order: site 1 first, so site 42 is never probed
        int callsAfterFirst = seen.size();
        var again = new com.gsl.service.GeneralVoucherService(client).findOwnerSiteWithCashBank(ctx, 777001L, "B", "2026-10-16T00:00:00");
        assertEquals(again.site().getSiteCode(), 1L);
        assertEquals(seen.size() - callsAfterFirst, 1, "cached site is re-checked with one call, not re-probed");
    }

    @Test
    public void ownerSiteProbesPastEmptySitesAndReportsWhenNoneQualifies() {
        canned.put("GET /Finance/api/ledgers/cashbankledgers", "{\"success\":true,\"result\":{\"totalCount\":0,\"items\":[]}}");
        var ctx = com.gsl.api.model.common.UserSiteContext.builder().sites(List.of(site(5, 1), site(3, 1))).build();
        org.testng.SkipException e = expectThrows(org.testng.SkipException.class,
                () -> new com.gsl.service.GeneralVoucherService(client).findOwnerSiteWithCashBank(ctx, 777002L, "C", "2026-10-16T00:00:00"));
        assertTrue(e.getMessage().contains("S3(3)=0") && e.getMessage().contains("S5(5)=0"), e.getMessage());
    }
}
