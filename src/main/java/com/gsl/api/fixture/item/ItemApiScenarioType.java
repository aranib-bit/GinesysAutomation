package com.gsl.api.fixture.item;

import com.gsl.api.client.ItemApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.common.PricingData;
import com.gsl.api.model.item.ItemCreateRequest;
import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.api.model.item.ItemLookupData;
import com.gsl.api.model.item.ItemValidationResult;
import com.gsl.api.validator.ItemApiValidator;
import com.gsl.enums.ItemManagementMode;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * One constant per endpoint-variant pair for {@link ItemApiClient}. {@code fetchUdfConfigList}/
 * {@code fetchDefaultHsnCode}/{@code fetchDefaultUnitName} take no input, so they only get a
 * POSITIVE constant — there is no invalid input to vary for a zero-argument lookup.
 */
public enum ItemApiScenarioType {

    FETCH_UDF_CONFIG_LIST_POSITIVE("GetUDFConfigList", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<Map<String, Object>> result = client.fetchUdfConfigList();
                return result != null
                        ? ScenarioOutcome.pass("fetchUdfConfigList returned " + result.size() + " field(s)")
                        : ScenarioOutcome.fail("fetchUdfConfigList returned null");
            }),

    FETCH_DEFAULT_HSN_CODE_POSITIVE("GetGSTHSNLookup", ScenarioKind.POSITIVE,
            (client, ctx) -> ScenarioOutcome.pass("fetchDefaultHsnCode returned " + client.fetchDefaultHsnCode())),

    FETCH_GROUP_DEFAULTS_POSITIVE("GetGroupDefaultValues", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                ItemLookupData defaults = client.fetchGroupDefaults(ctx.getInventoryGroupCode());
                return defaults != null
                        ? ScenarioOutcome.pass("fetchGroupDefaults returned " + defaults)
                        : ScenarioOutcome.fail("fetchGroupDefaults returned null for a known-valid inventory group");
            }),

    FETCH_GROUP_DEFAULTS_NEGATIVE_INVALID_GROUP("GetGroupDefaultValues", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    ItemLookupData defaults = client.fetchGroupDefaults(-1);
                    boolean unresolved = defaults == null || defaults.getInventoryCategoryCode() == 0;
                    return unresolved
                            ? ScenarioOutcome.pass("fetchGroupDefaults(-1) resolved no category, as expected")
                            : ScenarioOutcome.fail("fetchGroupDefaults(-1) unexpectedly resolved a category: " + defaults);
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchGroupDefaults(-1) rejected as expected: " + e.getMessage());
                }
            }),

    FETCH_DEFAULT_UNIT_NAME_POSITIVE("GetUnitOfMeasurement", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String unit = client.fetchDefaultUnitName();
                return (unit != null && !unit.isBlank())
                        ? ScenarioOutcome.pass("fetchDefaultUnitName returned '" + unit + "'")
                        : ScenarioOutcome.fail("fetchDefaultUnitName returned blank/null");
            }),

    CREATE_ITEM_POSITIVE("ItemMaster/Create", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String uniqueName = "SCN" + Long.toString(System.nanoTime(), 36);
                PricingData pricing = PricingData.builder().costrate(500).wsp(600).mrp(700).listedMrp(800).build();
                try {
                    ItemCreateResponse response = client.createItemWithDefaults(
                            uniqueName, ItemManagementMode.INDIVIDUAL, pricing, ctx.getInventoryGroupCode());
                    ItemValidationResult result = ItemApiValidator.validate(response);
                    return result.isValid()
                            ? ScenarioOutcome.pass("createItemWithDefaults succeeded — itemCode=" + response.getItemCode())
                            : ScenarioOutcome.fail(result.getReason());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createItemWithDefaults threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_ITEM_NEGATIVE_MISSING_MANDATORY_FIELDS("ItemMaster/Create", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                // Deliberately missing cname1/pricing/inventoryGroup/hsnSacMain — the fields
                // createItemWithDefaults always populates from live lookups.
                ItemCreateRequest incomplete = ItemCreateRequest.builder().build();
                Response response = client.createItemExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("ItemMaster/Create rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("ItemMaster/Create unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<ItemApiClient, ItemFixtureContext, ScenarioOutcome> action;

    ItemApiScenarioType(String endpoint, ScenarioKind kind, BiFunction<ItemApiClient, ItemFixtureContext, ScenarioOutcome> action) {
        this.endpoint = endpoint;
        this.kind = kind;
        this.action = action;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public ScenarioKind getKind() {
        return kind;
    }

    public ScenarioOutcome execute(ItemApiClient client, ItemFixtureContext ctx) {
        return action.apply(client, ctx);
    }
}
