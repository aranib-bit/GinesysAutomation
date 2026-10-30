package com.gsl.api.fixture.grc;

import com.gsl.api.client.GrcApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.grc.GrcCreateRequest;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.model.grc.VendorTaxRegion;
import com.gsl.api.validator.GrcApiValidator;
import io.restassured.response.Response;

import java.util.function.BiFunction;

/** One constant per endpoint-variant pair for {@link GrcApiClient}. */
public enum GrcApiScenarioType {

    FETCH_VENDOR_TAX_REGION_POSITIVE("GetGRCVendorTaxRegion", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                VendorTaxRegion region = client.fetchVendorTaxRegion(ctx.getVendorCode());
                return region != null
                        ? ScenarioOutcome.pass("fetchVendorTaxRegion returned " + region)
                        : ScenarioOutcome.fail("fetchVendorTaxRegion returned null for a known-valid vendorCode");
            }),

    FETCH_VENDOR_TAX_REGION_NEGATIVE_INVALID_VENDOR("GetGRCVendorTaxRegion", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    VendorTaxRegion region = client.fetchVendorTaxRegion(-1);
                    return region == null
                            ? ScenarioOutcome.pass("fetchVendorTaxRegion(-1) returned null, as expected")
                            : ScenarioOutcome.fail("fetchVendorTaxRegion(-1) unexpectedly returned " + region);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchVendorTaxRegion(-1) rejected as expected: " + e.getMessage());
                }
            }),

    CREATE_GRC_POSITIVE("GRC/Save", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                try {
                    GrcCreateResponse response = new com.gsl.service.GrcService(client).createGrc(
                            ctx.getItemCode(), ctx.getVendorCode(), ctx.getSiteCode(), ctx.getOuCode(),
                            ctx.getQuantity(), ctx.getRate());
                    GrcApiValidator.validate(response);
                    return ScenarioOutcome.pass("createGrc succeeded — grcCode=" + response.getGrcCode());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createGrc threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_GRC_NEGATIVE_MISSING_MANDATORY_FIELDS("GRC/Save", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                GrcCreateRequest incomplete = GrcCreateRequest.builder().build();
                Response response = client.createGrcExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("GRC/Save rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("GRC/Save unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<GrcApiClient, GrcFixtureContext, ScenarioOutcome> action;

    GrcApiScenarioType(String endpoint, ScenarioKind kind, BiFunction<GrcApiClient, GrcFixtureContext, ScenarioOutcome> action) {
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
    public ScenarioOutcome execute(GrcApiClient client, GrcFixtureContext ctx) {
        return action.apply(client, ctx);
    }
}
