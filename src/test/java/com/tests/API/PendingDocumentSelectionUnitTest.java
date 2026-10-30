package com.tests.API;

import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.utils.dataprovider.source.finance.ApiPendingDocumentSelector;
import com.gsl.utils.dataprovider.source.finance.PendingDocumentRecheck;
import com.tests.API.support.StubApiServer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Pending-document reservation under parallel scenarios: a FIFO scenario must hold the whole sub-ledger, a plain Select-Document scenario only what it
 * uses, and the live re-check must notice a reserved document that changed or vanished before the UI step.
 */
public class PendingDocumentSelectionUnitTest {

    private static final String DATE = "2026-10-29T00:00:00";
    private static final String PENDING_PATH = "/Finance/api/DocumentAdjustments/apvoucher";

    private StubApiServer server;
    private DocumentAdjustmentApiClient client;
    private final SubLedgerLookupResult sl = SubLedgerLookupResult.builder().slCode(193).slName("Vendor A").slid("V1").classType("S").classCode(2).glCode(29).build();

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        client = new DocumentAdjustmentApiClient(server.spec()) {
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
        DocumentReservationRegistry.clearAll();
        PendingDocumentRecheck.clearCurrentThread();
    }

    private static String doc(String no, int pending) {
        return "{\"document_No\":\"" + no + "\",\"document_Date\":\"2026-09-01T00:00:00\",\"entype\":\"PIM\",\"debit_Credit\":\"C\",\"pending\":" + pending + "}";
    }

    private void pending(String... docs) {
        server.respond("GET", PENDING_PATH, 200, "{\"success\":true,\"result\":{\"totalRecords\":" + docs.length + ",\"items\":[" + String.join(",", docs) + "]}}");
    }

    private void select(int docsNeeded, boolean exclusive) {
        ApiPendingDocumentSelector.select(client, ApiPendingDocumentSelector.Domain.AP, List.of(sl), 1, DATE, 1, docsNeeded, false, null, exclusive);
    }

    @Test
    public void largeAmountsKeepTheirCentsInsteadOfBeingReadAsFloats() {
        // The API sent pending 333578.990; read as a float it became 333579.0 and the test typed a pending the popup does not have.
        pending("{\"document_No\":\"PI/31\",\"document_Date\":\"2025-10-21T00:00:00\",\"entype\":\"PIM\",\"debit_Credit\":\"C\","
                + "\"amount\":667157.990,\"adjamt\":333579.000,\"pending\":333578.990,\"netOfTDSAmt\":667157.990}");
        var result = ApiPendingDocumentSelector.select(client, ApiPendingDocumentSelector.Domain.AP, List.of(sl), 1, DATE, 1, 1, false, null, false);
        var docs = result.getDocsBySlKey().values().iterator().next();
        assertEquals(docs.get(0).getPending().compareTo(new java.math.BigDecimal("333578.99")), 0, docs.get(0).getPending().toPlainString());
    }

    @Test
    public void tdsIsRoundedUpToAWholeRupeeSoThePopupOffersALittleLess() {
        // pending 1000.50, net of TDS 990.495 -> TDS 10.005 -> 11 -> the popup offers 989.50
        pending("{\"document_No\":\"PI/T1\",\"document_Date\":\"2025-10-21T00:00:00\",\"entype\":\"PIM\",\"debit_Credit\":\"C\","
                + "\"amount\":1000.50,\"adjamt\":0,\"pending\":1000.50,\"netOfTDSAmt\":990.495}");
        var result = ApiPendingDocumentSelector.select(client, ApiPendingDocumentSelector.Domain.AP, List.of(sl), 1, DATE, 1, 1, false, null, false);
        var docs = result.getDocsBySlKey().values().iterator().next();
        assertEquals(docs.get(0).getPending().compareTo(new java.math.BigDecimal("989.50")), 0, docs.get(0).getPending().toPlainString());
    }

    @Test
    public void aWholeRupeeTdsLeavesTheNetUnchanged() {
        pending("{\"document_No\":\"PI/T2\",\"document_Date\":\"2025-10-21T00:00:00\",\"entype\":\"PIM\",\"debit_Credit\":\"C\","
                + "\"amount\":1000,\"adjamt\":0,\"pending\":1000,\"netOfTDSAmt\":990}");
        var result = ApiPendingDocumentSelector.select(client, ApiPendingDocumentSelector.Domain.AP, List.of(sl), 1, DATE, 1, 1, false, null, false);
        assertEquals(result.getDocsBySlKey().values().iterator().next().get(0).getPending().compareTo(new java.math.BigDecimal("990")), 0);
    }

    @Test
    public void exclusiveClaimHoldsEveryDocumentOfTheSubLedger() {
        pending(doc("D1", 100), doc("D2", 200), doc("D3", 300));
        select(1, true);
        assertEquals(DocumentReservationRegistry.currentReservations(), java.util.Set.of("D1", "D2", "D3"));
    }

    @Test
    public void plainSelectionKeepsOnlyTheDocumentsItNeeds() {
        pending(doc("D1", 100), doc("D2", 200), doc("D3", 300));
        select(1, false);
        assertEquals(DocumentReservationRegistry.currentReservations().size(), 1);
    }

    @Test
    public void aSelectDocumentScenarioCannotTakeADocumentOfAnExclusivelyClaimedSubLedger() {
        pending(doc("D1", 100), doc("D2", 200));
        select(1, true);
        // Same thread holds the claim, so the registry refuses the same documents again, as another thread would see them.
        expectThrows(SkipException.class, () -> select(1, false));
    }

    @Test
    public void anExclusiveClaimIsRefusedWhenAnotherScenarioAlreadyHoldsOneDocument() {
        pending(doc("D1", 100), doc("D2", 200), doc("D3", 300));
        assertTrue(DocumentReservationRegistry.tryReserve("D2"));
        expectThrows(SkipException.class, () -> select(1, true));
        // The refused claim must not leave its other documents held.
        assertEquals(DocumentReservationRegistry.currentReservations(), java.util.Set.of("D2"));
    }

    @Test
    public void twoThreadsNeverBothClaimTheSameSubLedgerExclusively() throws Exception {
        List<PendingSlDocument> docs = List.of(slDoc("D1"), slDoc("D2"), slDoc("D3"));
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        Thread a = new Thread(() -> first.set(DocumentReservationRegistry.reserveAllOrNone("Vendor A|V1", docs)));
        Thread b = new Thread(() -> second.set(DocumentReservationRegistry.reserveAllOrNone("Vendor A|V1", docs)));
        a.start();
        a.join();
        b.start();
        b.join();
        assertTrue(first.get());
        assertFalse(second.get());
    }

    @Test
    public void recheckPassesWhenTheReservedDocumentsAreUnchanged() {
        pending(doc("D1", 100), doc("D2", 200));
        select(1, true);
        PendingDocumentRecheck.verify(client);
    }

    @Test
    public void recheckNamesTheDocumentWhoseAmountChanged() {
        pending(doc("D1", 100), doc("D2", 200));
        select(1, true);
        pending(doc("D1", 100), doc("D2", 150));
        AssertionError error = expectThrows(AssertionError.class, () -> PendingDocumentRecheck.verify(client));
        assertTrue(error.getMessage().contains("D2") && error.getMessage().contains("200") && error.getMessage().contains("150"), error.getMessage());
    }

    @Test
    public void recheckNamesTheDocumentThatIsNoLongerPending() {
        pending(doc("D1", 100), doc("D2", 200));
        select(1, true);
        pending(doc("D1", 100));
        AssertionError error = expectThrows(AssertionError.class, () -> PendingDocumentRecheck.verify(client));
        assertTrue(error.getMessage().contains("D2 is no longer pending"), error.getMessage());
    }

    private static PendingSlDocument slDoc(String no) {
        PendingSlDocument d = new PendingSlDocument();
        d.setDocumentNo(no);
        return d;
    }
}
