package com.gsl.api.webapi.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.chequebook.CreateChequeBookRequest;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import com.gsl.api.webapi.model.response.chequebook.CreateChequeBookResponse;
import com.gsl.api.webapi.model.response.chequebook.DuplicateChequeResponse;
import io.restassured.specification.RequestSpecification;

import java.util.List;

/**
 * Cheque Book HTTP calls only (HAR: CreateChequeBookUsingGenerateLeafwhereduplicateexists). Which range to try, how often and what to do on
 * a conflict is {@code ChequeBookService}'s job.
 */
public class ChequeBookApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} - see {@link ApiClient}'s class Javadoc. */
    public ChequeBookApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** {@code POST /cheques/duplicatecheques}: the numbers of the requested range that are already used (empty = the range is free). */
    public List<Integer> fetchDuplicateCheques(DuplicateChequeRequest request) {
        return post(WebApiEndpoints.Cheque.DUPLICATES, request, DuplicateChequeResponse.class).usedNumbers();
    }

    /** {@code POST /cheques/create}: creates the book and returns the book number the server reports. */
    public String createChequeBook(CreateChequeBookRequest request) {
        CreateChequeBookResponse response = post(WebApiEndpoints.Cheque.CREATE, request, CreateChequeBookResponse.class);
        String bookNumber = response.bookNumber();
        if (bookNumber == null) {
            throw new ApiException("cheques/create for glCode=" + request.getGlcode() + " returned no book number: " + response.getMessage(), 0);
        }
        return bookNumber;
    }
}
