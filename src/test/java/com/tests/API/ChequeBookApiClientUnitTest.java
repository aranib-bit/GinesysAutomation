package com.tests.API;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.client.ChequeBookApiClient;
import com.gsl.api.webapi.factory.ChequeBookRequestFactory;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import com.tests.API.support.StubApiServer;
import com.tests.API.support.WireAssert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** {@link ChequeBookApiClient} against a stub server: endpoint, verb, request body, and the response/error handling. */
public class ChequeBookApiClientUnitTest {

    private StubApiServer server;
    private ChequeBookApiClient client;

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        client = new ChequeBookApiClient(server.spec()) {
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
    public void duplicateLookupPostsTheHarBodyAndReturnsUsedNumbers() {
        server.respond("POST", "/Finance/api/cheques/duplicatecheques", 200, WireAssert.sample("chequebook/duplicate-response-used.json"));

        List<Integer> used = client.fetchDuplicateCheques(DuplicateChequeRequest.of(4, 300, 325, 5));

        assertEquals(used.size(), 26);
        assertEquals(server.last().method(), "POST");
        assertEquals(server.last().path(), "/Finance/api/cheques/duplicatecheques");
        assertTrue(server.last().body().contains("\"chequeStartNumber\":300"), server.last().body());
        assertTrue(server.last().body().contains("\"glCode\":4"), server.last().body());
    }

    @Test
    public void freeRangeYieldsAnEmptyList() {
        server.respond("POST", "/Finance/api/cheques/duplicatecheques", 200, WireAssert.sample("chequebook/duplicate-response-free.json"));
        assertTrue(client.fetchDuplicateCheques(DuplicateChequeRequest.of(4, 700, 800, 5)).isEmpty());
    }

    @Test
    public void createAcceptsHttp201AndReturnsTheBookNumber() {
        server.respond("POST", "/Finance/api/cheques/create", 201, WireAssert.sample("chequebook/create-response.json"));

        String book = client.createChequeBook(ChequeBookRequestFactory.create(1, 2587, 4, "2026-10-27T00:00:00", 700, 3, 5));

        assertEquals(book, "2");
        assertTrue(server.last().body().contains("\"CHQNO\":\"00700\""), server.last().body());
        assertTrue(server.last().body().contains("\"CHQDT\":null"), server.last().body());
    }

    @Test
    public void createWithSuccessFalseThrows() {
        server.respond("POST", "/Finance/api/cheques/create", 200, "{\"success\":false,\"error\":\"nope\",\"result\":null}");
        expectThrows(ApiException.class, () -> client.createChequeBook(ChequeBookRequestFactory.create(1, 2587, 4, "d", 1, 1, 5)));
    }

    @Test
    public void createWithoutABookNumberThrows() {
        server.respond("POST", "/Finance/api/cheques/create", 201, "{\"success\":true,\"message\":\"ok\",\"result\":{\"item1\":[]}}");
        ApiException e = expectThrows(ApiException.class, () -> client.createChequeBook(ChequeBookRequestFactory.create(1, 2587, 4, "d", 1, 1, 5)));
        assertTrue(e.getMessage().contains("no book number"), e.getMessage());
    }

    @Test
    public void serverErrorsSurfaceAsApiException() {
        server.respond("POST", "/Finance/api/cheques/duplicatecheques", 500, "{\"message\":\"boom\"}");
        ApiException e = expectThrows(ApiException.class, () -> client.fetchDuplicateCheques(DuplicateChequeRequest.of(4, 1, 2, 5)));
        assertEquals(e.getStatusCode(), 500);
    }
}
