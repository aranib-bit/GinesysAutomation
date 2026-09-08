package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
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

    public TransferOutApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** POST /SI/SearchChallan — finds a DC eligible to transfer out. */
    public DcSearchResult searchChallan(String dcCode) {
        Response response = postRaw("/SI/SearchChallan", Map.of("dcCode", dcCode));
        return response.jsonPath().getObject("result[0]", DcSearchResult.class);
    }

    /** POST SI/Save — the final save call. */
    public TransferOutCreateResponse createTransferOut(TransferOutCreateRequest request) {
        return post("/SI/Save", request, TransferOutCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createTransferOut(TransferOutCreateRequest)} —
     * for a deliberately incomplete/invalid {@code request}, returns the raw {@link Response}
     * instead of throwing.
     */
    public Response createTransferOutExpectFailure(TransferOutCreateRequest request) {
        return postRawExpectFailure("/SI/Save", request);
    }

    /** Builds and saves a single-line Transfer Out against the given DC, matching k6's flow. */
    public TransferOutCreateResponse createTransferOut(String dcCode, String itemCode, int quantity, int rate) {
        // * NOTE: this overload does not call searchChallan() — dcCode is threaded straight into
        // the line item as supplied by the caller. Only the actual Save call below gets its own
        // sub-section; a "search" node here would misrepresent a call that isn't made.
        TransferOutLineItem line = TransferOutLineItem.builder()
                .dcCode(dcCode)
                .itemCode(itemCode)
                .invQty(quantity)
                .rate(rate)
                .build();

        TransferOutCreateRequest request = TransferOutCreateRequest.builder()
                .invoiceDate(LocalDate.now() + "T00:00:00")
                .item(line)
                .build();

        return StepExecutor.call("Create Transfer Out via API", () -> createTransferOut(request));
    }
}
