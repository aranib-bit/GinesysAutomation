package com.gsl.api.fixture.transferout;

import com.gsl.api.client.TransferOutApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.transferout.DcSearchResult;
import com.gsl.api.model.transferout.TransferOutCreateRequest;
import com.gsl.api.model.transferout.TransferOutCreateResponse;
import com.gsl.api.validator.TransferOutApiValidator;
import io.restassured.response.Response;

import java.util.function.BiFunction;

/** One constant per endpoint-variant pair for {@link TransferOutApiClient}. */
public enum TransferOutApiScenarioType {

    SEARCH_CHALLAN_POSITIVE("SI/SearchChallan", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                DcSearchResult result = client.searchChallan(ctx.getDcCode());
                return result != null
                        ? ScenarioOutcome.pass("searchChallan returned " + result)
                        : ScenarioOutcome.fail("searchChallan returned null for a known-valid dcCode");
            }),

    SEARCH_CHALLAN_NEGATIVE_INVALID_DC("SI/SearchChallan", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    DcSearchResult result = client.searchChallan("NO_SUCH_DC_CODE");
                    return result == null
                            ? ScenarioOutcome.pass("searchChallan(invalid dcCode) returned null, as expected")
                            : ScenarioOutcome.fail("searchChallan(invalid dcCode) unexpectedly returned " + result);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("searchChallan(invalid dcCode) rejected as expected: " + e.getMessage());
                }
            }),

    CREATE_TRANSFER_OUT_POSITIVE("SI/Save", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                try {
                    TransferOutCreateResponse response = new com.gsl.service.TransferOutService(client).createTransferOut(
                            ctx.getDcCode(), ctx.getItemCode(), ctx.getQuantity(), ctx.getRate());
                    TransferOutApiValidator.validate(response);
                    return ScenarioOutcome.pass("createTransferOut succeeded — siCode=" + response.getSiCode());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createTransferOut threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_TRANSFER_OUT_NEGATIVE_MISSING_MANDATORY_FIELDS("SI/Save", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                TransferOutCreateRequest incomplete = TransferOutCreateRequest.builder().build();
                Response response = client.createTransferOutExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("SI/Save rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("SI/Save unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<TransferOutApiClient, TransferOutFixtureContext, ScenarioOutcome> action;

    TransferOutApiScenarioType(String endpoint, ScenarioKind kind,
            BiFunction<TransferOutApiClient, TransferOutFixtureContext, ScenarioOutcome> action) {
        this.endpoint = endpoint;
        this.kind = kind;
        this.action = action;
    }

    /** @return the endpoint name/label this scenario targets (used in report step names). */
    public String getEndpoint() {
        return endpoint;
    }

    /** @return whether this constant is a {@code POSITIVE}, {@code NEGATIVE}, or {@code BOUNDARY} scenario. */
    public ScenarioKind getKind() {
        return kind;
    }

    /** Runs this scenario's action against {@code client} using {@code ctx}'s reference values. */
    public ScenarioOutcome execute(TransferOutApiClient client, TransferOutFixtureContext ctx) {
        return action.apply(client, ctx);
    }
}
