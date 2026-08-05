package com.tests.API;

import com.gsl.api.client.ItemApiClient;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.fixture.item.ItemApiFixtureFactory;
import com.gsl.api.fixture.item.ItemApiScenarioType;
import com.gsl.api.fixture.item.ItemFixtureContext;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link ItemApiClient} — not just the
 * terminal {@code ItemMaster/Create} call existing callers already exercise. Driven entirely by
 * {@link ItemApiScenarioType}/{@link ItemApiFixtureFactory}.
 *
 * <p>Requires {@code fixture.item.inventoryGroupCode} in {@code api.properties} to be a real,
 * valid inventory group code for {@code api.tenant.active}.
 */
public class ItemApiTest extends APITest {

    @Test
    public void itemPositive() {
        runScenarios("itemPositive");
    }

    @Test
    public void itemNegative() {
        runScenarios("itemNegative");
    }

    private void runScenarios(String methodName) {
        ItemApiClient client = session().getItemApiClient();
        ItemFixtureContext ctx = ItemFixtureContext.fromConfig();
        List<ItemApiScenarioType> scenarios = ItemApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (ItemApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
