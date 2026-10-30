package com.tests.API;

import com.gsl.api.webapi.factory.ChequeBookRequestFactory;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import com.gsl.api.webapi.model.response.chequebook.CreateChequeBookResponse;
import com.gsl.api.webapi.model.response.chequebook.DuplicateChequeResponse;
import com.tests.API.support.WireAssert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The Cheque Book POJOs must match the captured HAR JSON (names, nulls, zero-padding, 101 leaves). */
public class ChequeBookWireTest {

    @Test(description = "duplicatecheques request serializes like the HAR")
    public void duplicateRequestMatchesHar() {
        WireAssert.sameJson(DuplicateChequeRequest.of(4, 300, 325, 5), "chequebook/duplicate-request.json");
    }

    @Test(description = "cheques/create request built by the factory equals the HAR body (101 leaves 00700..00800)")
    public void createRequestMatchesHar() {
        WireAssert.sameJson(ChequeBookRequestFactory.create(1, 2587, 4, "2026-10-27T00:00:00", 700, 101, 5), "chequebook/create-request.json");
    }

    @Test(description = "duplicate response: used numbers vs a free range")
    public void duplicateResponsesDeserialize() {
        DuplicateChequeResponse used = WireAssert.read("chequebook/duplicate-response-used.json", DuplicateChequeResponse.class);
        assertTrue(used.isSuccess());
        assertEquals(used.usedNumbers().size(), 26);
        assertEquals(used.usedNumbers().get(0), Integer.valueOf(300));
        assertTrue(WireAssert.read("chequebook/duplicate-response-free.json", DuplicateChequeResponse.class).usedNumbers().isEmpty());
    }

    @Test(description = "create response exposes result.item2 as the book number")
    public void createResponseDeserializes() {
        CreateChequeBookResponse r = WireAssert.read("chequebook/create-response.json", CreateChequeBookResponse.class);
        assertTrue(r.isSuccess());
        assertEquals(r.bookNumber(), "2");
    }
}
