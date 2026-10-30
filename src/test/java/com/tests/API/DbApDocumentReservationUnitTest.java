package com.tests.API;

import com.gsl.database.DocumentReservationRegistry;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** DB-mode AP document selection: the cut to documentCount happens while reserving (FIN-AP-ADD-DOC-05), and a changed pending amount is caught after reserving (DOC-03). */
public class DbApDocumentReservationUnitTest {

    private static PendingSlDocument doc(String no, String pending) {
        PendingSlDocument d = new PendingSlDocument();
        d.setDocumentNo(no);
        d.setPending(new BigDecimal(pending));
        return d;
    }

    private static Map<String, List<PendingSlDocument>> one(String slKey, PendingSlDocument... docs) {
        Map<String, List<PendingSlDocument>> m = new LinkedHashMap<>();
        m.put(slKey, new ArrayList<>(List.of(docs)));
        return m;
    }

    @AfterMethod(alwaysRun = true)
    public void clean() {
        DocumentReservationRegistry.releaseCurrentThread();
    }

    @Test
    public void whenTheOldestDocumentIsHeldElsewhereTheNextOneIsTaken() throws Exception {
        // another thread holds AR/00048 (the case of DOC-04 holding the only document DOC-05 could see before the backfill added AR/00050)
        Thread other = new Thread(() -> DocumentReservationRegistry.tryReserve("AR/00048"));
        other.start();
        other.join();
        try {
            Map<String, List<PendingSlDocument>> got = DocumentReservationRegistry.filterAndReserveUpTo(
                    one("AESHNA|C002", doc("AR/00048", "500"), doc("AR/00050", "500")), 1);
            assertEquals(got.get("AESHNA|C002").size(), 1);
            assertEquals(got.get("AESHNA|C002").get(0).getDocumentNo(), "AR/00050");
        } finally {
            DocumentReservationRegistry.release("AR/00048");
        }
    }

    @Test
    public void onlyTheNeededNumberOfDocumentsIsHeld() {
        Map<String, List<PendingSlDocument>> got = DocumentReservationRegistry.filterAndReserveUpTo(
                one("S|1", doc("D1", "10"), doc("D2", "10"), doc("D3", "10")), 2);
        assertEquals(got.get("S|1").size(), 2);
        // the third stays free for other scenarios
        assertTrue(DocumentReservationRegistry.tryReserve("D3"), "an unneeded document must not be held");
    }

    @Test
    public void aSubLedgerWithTooFewFreeDocumentsHoldsNothing() throws Exception {
        Thread other = new Thread(() -> DocumentReservationRegistry.tryReserve("X1"));
        other.start();
        other.join();
        try {
            Map<String, List<PendingSlDocument>> got = DocumentReservationRegistry.filterAndReserveUpTo(one("S|2", doc("X1", "5"), doc("X2", "5")), 2);
            assertTrue(got.isEmpty());
            assertTrue(DocumentReservationRegistry.tryReserve("X2"), "a partial hold must be given back");
        } finally {
            DocumentReservationRegistry.release("X1");
        }
    }

    @Test
    public void aChangedPendingAmountDropsTheSubLedgerAndReleasesItsDocuments() {
        // VP/02054/26-27 was read as 644.99; another scenario adjusted 322.49 of it before this thread reserved it
        Map<String, List<PendingSlDocument>> reserved = one("AAR|BT56", doc("VP/02054/26-27", "644.99"));
        Map<String, List<PendingSlDocument>> fresh = one("AAR|BT56", doc("VP/02054/26-27", "322.50"));
        List<String> released = new ArrayList<>();
        Map<String, List<PendingSlDocument>> kept = DbApVoucherDataSource.dropStaleReservations(reserved, fresh, released::addAll);
        assertTrue(kept.isEmpty());
        assertEquals(released, List.of("VP/02054/26-27"));
    }

    @Test
    public void aDocumentNoLongerPendingDropsTheSubLedger() {
        List<String> released = new ArrayList<>();
        Map<String, List<PendingSlDocument>> kept = DbApVoucherDataSource.dropStaleReservations(
                one("S|3", doc("G1", "10")), new LinkedHashMap<>(), released::addAll);
        assertTrue(kept.isEmpty());
        assertEquals(released, List.of("G1"));
    }

    @Test
    public void unchangedDocumentsAreKeptAndNothingIsReleased() {
        List<String> released = new ArrayList<>();
        Map<String, List<PendingSlDocument>> kept = DbApVoucherDataSource.dropStaleReservations(
                one("S|4", doc("H1", "10.00")), one("S|4", doc("H1", "10.000")), released::addAll);
        assertEquals(kept.get("S|4").size(), 1);
        assertFalse(!released.isEmpty(), released.toString());
    }
}
