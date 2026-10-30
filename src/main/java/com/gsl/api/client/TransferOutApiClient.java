package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.transferout.SearchChallanRequest;
import com.gsl.api.model.transferout.DcSearchResult;
import com.gsl.api.model.transferout.TransferOutCreateRequest;
import com.gsl.api.model.transferout.TransferOutCreateResponse;
import com.gsl.api.model.transferout.TransferOutLineItem;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.Map;

/**
 * WebAPI/PublicAPI client for Transfer Out (Service Invoice against a DC). Mirrors
 * D:\PerformanceAutomationTesting\flows\createTransferOut.js (4 endpoint calls in k6):
 * DC search + save implemented here; GetDCDetails/CalculateItemCharges follow the same pattern.
 */
public class TransferOutApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public TransferOutApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** POST /SI/SearchChallan — finds a DC eligible to transfer out. */
    public DcSearchResult searchChallan(String dcCode) {
        Response response = postRaw(WebApiEndpoints.TransferOut.SEARCH_CHALLAN, SearchChallanRequest.builder().dcCode(dcCode).build());
        return response.jsonPath().getObject("result[0]", DcSearchResult.class);
    }

    /** POST SI/Save — the final save call. */
    public TransferOutCreateResponse createTransferOut(TransferOutCreateRequest request) {
        return post(WebApiEndpoints.TransferOut.SAVE, request, TransferOutCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createTransferOut(TransferOutCreateRequest)} —
     * for a deliberately incomplete/invalid {@code request}, returns the raw {@link Response}
     * instead of throwing.
     */
    public Response createTransferOutExpectFailure(TransferOutCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.TransferOut.SAVE), request);
    }

}
