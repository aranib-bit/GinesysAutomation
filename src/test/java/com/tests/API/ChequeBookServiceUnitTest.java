package com.tests.API;

import com.gsl.api.core.ApiException;
import com.gsl.api.service.ChequeBookService;
import com.gsl.api.webapi.client.ChequeBookApiClient;
import com.gsl.api.webapi.model.request.chequebook.CreateChequeBookRequest;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The range-search workflow, tested with a fake client (no HTTP). */
public class ChequeBookServiceUnitTest {

    /** Fake client: ranges starting at a number in {@code occupiedStarts} report a used number; creates are recorded. */
    private static final class FakeClient extends ChequeBookApiClient {
        final Set<Integer> occupiedStarts;
        final List<DuplicateChequeRequest> lookups = new ArrayList<>();
        final List<CreateChequeBookRequest> creates = new ArrayList<>();
        RuntimeException createFailure;
        int failCreatesBeforeSuccess;

        FakeClient(Set<Integer> occupiedStarts) {
            super(null);
            this.occupiedStarts = occupiedStarts;
        }

        @Override
        public List<Integer> fetchDuplicateCheques(DuplicateChequeRequest request) {
            lookups.add(request);
            return occupiedStarts.contains(request.getChequeStartNumber()) ? List.of(request.getChequeStartNumber()) : List.of();
        }

        @Override
        public String createChequeBook(CreateChequeBookRequest request) {
            creates.add(request);
            if (createFailure != null && failCreatesBeforeSuccess-- > 0) throw createFailure;
            return "B" + creates.size();
        }
    }

    private static ChequeBookService service(FakeClient c, int leaves, int start, int attempts) {
        return new ChequeBookService(c, new ChequeBookService.Settings(leaves, 5, start, attempts));
    }

    @Test
    public void freeFirstRangeCreatesOnceAndReturnsItsStart() {
        FakeClient c = new FakeClient(Set.of());
        assertEquals(service(c, 10, 100, 3).createFreeChequeBook(4, 1, 2587, "d"), 100);
        assertEquals(c.creates.size(), 1);
        assertEquals(c.creates.get(0).getChequeDetails().size(), 10);
        assertEquals(c.creates.get(0).getChequeDetails().get(0).getChequeNumber(), "00100");
        assertEquals(c.lookups.get(0).getChequeEndNumber(), 109);
    }

    @Test
    public void occupiedRangesAreSkippedInLeafCountSteps() {
        FakeClient c = new FakeClient(Set.of(100, 110));
        assertEquals(service(c, 10, 100, 5).createFreeChequeBook(4, 1, 2587, "d"), 120);
        assertEquals(c.lookups.size(), 3);
        assertEquals(c.creates.size(), 1);
    }

    @Test
    public void exhaustingTheAttemptsThrowsWithoutCreating() {
        FakeClient c = new FakeClient(Set.of(100, 110, 120));
        ApiException e = expectThrows(ApiException.class, () -> service(c, 10, 100, 3).createFreeChequeBook(4, 1, 2587, "d"));
        assertTrue(e.getMessage().contains("No free cheque range"), e.getMessage());
        assertTrue(c.creates.isEmpty());
    }

    @Test
    public void integerOverflowFailsExplicitly() {
        FakeClient c = new FakeClient(Set.of());
        expectThrows(ArithmeticException.class, () -> service(c, 10, Integer.MAX_VALUE - 5, 2).createFreeChequeBook(4, 1, 2587, "d"));
    }

    @Test
    public void aDuplicateConflictDuringCreateMovesToTheNextRange() {
        FakeClient c = new FakeClient(Set.of());
        c.createFailure = new ApiException("Call to /cheques/create failed with HTTP 400: Duplicate cheque numbers", 400);
        c.failCreatesBeforeSuccess = 1;
        assertEquals(service(c, 10, 100, 3).createFreeChequeBook(4, 1, 2587, "d"), 110);
        assertEquals(c.creates.size(), 2);
    }

    @Test
    public void aNonDuplicateFailurePropagatesWithoutRetry() {
        FakeClient c = new FakeClient(Set.of());
        c.createFailure = new ApiException("Call to /cheques/create failed with HTTP 500: boom", 500);
        c.failCreatesBeforeSuccess = 1;
        expectThrows(ApiException.class, () -> service(c, 10, 100, 3).createFreeChequeBook(4, 1, 2587, "d"));
        assertEquals(c.creates.size(), 1);
    }
}
