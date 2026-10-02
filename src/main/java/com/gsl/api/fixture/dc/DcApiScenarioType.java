package com.gsl.api.fixture.dc;

import com.gsl.api.client.DcApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.dc.DcCreateRequest;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.validator.DcApiValidator;
import io.restassured.response.Response;

import java.util.function.BiFunction;

/**
 * One constant per endpoint-variant pair for {@link DcApiClient}. {@code fetchPacketBarcode}
 * takes no input (server-issued, no collision risk per its own javadoc) so it only gets a
 * POSITIVE constant.
 */
public enum DcApiScenarioType {

    FETCH_PACKET_BARCODE_POSITIVE("GetPacketBarcode", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String barcode = client.fetchPacketBarcode();
                return (barcode != null && !barcode.isBlank())
                        ? ScenarioOutcome.pass("fetchPacketBarcode returned '" + barcode + "'")
                        : ScenarioOutcome.fail("fetchPacketBarcode returned blank/null");
            }),

    CREATE_DC_POSITIVE("DC/Save", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                try {
                    DcCreateResponse response = client.createDc(ctx.getItemCode(), ctx.getQuantity(), ctx.getRate());
                    DcApiValidator.validate(response);
                    return ScenarioOutcome.pass("createDc succeeded — dcCode=" + response.getDcCode());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createDc threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_DC_NEGATIVE_MISSING_MANDATORY_FIELDS("DC/Save", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                DcCreateRequest incomplete = DcCreateRequest.builder().build();
                Response response = client.createDcExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("DC/Save rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("DC/Save unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<DcApiClient, DcFixtureContext, ScenarioOutcome> action;

    DcApiScenarioType(String endpoint, ScenarioKind kind, BiFunction<DcApiClient, DcFixtureContext, ScenarioOutcome> action) {
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
    public ScenarioOutcome execute(DcApiClient client, DcFixtureContext ctx) {
        return action.apply(client, ctx);
    }
}
