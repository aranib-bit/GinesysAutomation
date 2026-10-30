package com.tests.API;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.VoucherActionResponse;
import com.tests.API.support.StubApiServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** The shared Finance voucher client against a stub server: paged lookups, typed rootview, actions and the document-adjustment queries. */
public class VoucherLookupClientUnitTest {

    private static final String WD = "2026-10-29T00:00:00";

    private StubApiServer server;
    private ApVoucherApiClient ap;
    private DocumentAdjustmentApiClient adjustments;

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        ap = new ApVoucherApiClient(server.spec()) {
            @Override
            protected String financeBase() {
                return server.base() + "/Finance/api";
            }
        };
        adjustments = new DocumentAdjustmentApiClient(server.spec()) {
            @Override
            protected String financeBase() {
                return server.base() + "/Finance/api";
            }
        };
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        server.reset();
    }

    @Test
    public void subLedgersArePagedAndSentWithTheModuleParameters() {
        server.respond("GET", "/Finance/api/subledgers", 200,
                "{\"success\":true,\"result\":{\"totalCount\":1,\"items\":[{\"slCode\":193,\"slName\":\"Cust\",\"classType\":\"C\",\"classCode\":1}]}}");

        List<SubLedgerLookupResult> all = ap.fetchSubLedgers(1, 1, 513150, WD);

        assertEquals(all.size(), 1);
        assertEquals(all.get(0).getSlCode(), 193L);
        String query = server.last().query();
        assertTrue(query.contains("module=apvoucher"), query);
        assertTrue(query.contains("connectedSiteType=OS-OO-CM"), query);
        assertTrue(query.contains("queryFieldName=slName") && query.contains("queryFieldName=className"), query);
        assertTrue(query.contains("limit=100"), query);
    }

    @Test
    public void docSchemesUseTheSiteParameterAndTheModulesMenu() {
        server.respond("GET", "/Finance/api/docnumberschemes", 200, "{\"success\":true,\"result\":{\"totalCount\":1,\"items\":[{\"code\":138,\"name\":\"AR/AP Voucher\"}]}}");
        assertEquals(ap.fetchDocNumberSchemes(1, WD).get(0).getCode(), 138L);
        String query = server.last().query();
        assertTrue(query.contains("connectedSite=1") && query.contains("menusysshortcode=FinanceAPVoucher"), query);
    }

    @Test
    public void rootviewIsTypedAndKeepsEveryHeaderField() {
        server.respond("GET", "/Finance/api/vouchers/apvouchers/rootview", 200,
                "{\"success\":true,\"result\":{\"documentStatus\":\"P\",\"reference\":\"REF-1\",\"remarks\":\"r\",\"voucherDate\":\"2026-10-29T00:00:00\"}}");

        assertEquals(ap.fetchDocumentStatus(1183324, 1, 513150), "P");
        Map<String, Object> header = ap.fetchVoucherHeader(1183324, 1, 513150);
        assertEquals(header.get("reference"), "REF-1");
        assertEquals(header.get("documentStatus"), "P");
        assertTrue(server.last().query().contains("code=1183324"), server.last().query());
    }

    @Test
    public void aVoucherThatCannotBeReadHasNoStatusAndNoHeader() {
        server.respond("GET", "/Finance/api/vouchers/apvouchers/rootview", 404, "{\"success\":false}");
        assertNull(ap.fetchDocumentStatus(1, 1, 1));
        assertTrue(ap.fetchVoucherHeader(1, 1, 1).isEmpty());
    }

    @Test
    public void revertAndDeleteUseTheModuleSegment() {
        server.respond("PUT", "/Finance/api/vouchers/ap/revert", 200, "{\"success\":true,\"result\":[{\"item1\":5,\"item2\":\"AP/1\",\"item3\":\"success\",\"item4\":9}]}");
        VoucherActionResponse r = ap.revertApVoucher(5, 513150, 1);
        assertTrue(r.allSucceeded());
        assertEquals(server.last().method(), "PUT");

        server.respond("DELETE", "/Finance/api/vouchers/ap", 200, "{\"success\":true,\"result\":[{\"item1\":5,\"item2\":\"AP/1\",\"item3\":\"Success\"}]}");
        assertTrue(ap.deleteApVoucher(5, 513150, 1).allSucceeded());
        assertTrue(server.last().body().contains("\"voucherCodes\":[5]"), server.last().body());
    }

    @Test
    public void pendingDocumentsAreQueriedByModuleAndPaged() {
        server.respond("GET", "/Finance/api/DocumentAdjustments/arvoucher", 200,
                "{\"success\":true,\"result\":{\"totalRecords\":1,\"items\":[{\"document_No\":\"PI/1\",\"pending\":10.5}]}}");
        var docs = adjustments.fetchPendingDocuments(DocumentAdjustmentApiClient.MODULE_AR, 1, 193, 1, WD);
        assertEquals(docs.size(), 1);
        String query = server.last().query();
        assertTrue(query.contains("docType=VDP") && query.contains("slCode=193") && query.contains("limit=500"), query);
    }
}
