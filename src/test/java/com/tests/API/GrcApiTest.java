package com.tests.API;

import com.gsl.api.client.GrcApiClient;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.fixture.grc.GrcApiFixtureFactory;
import com.gsl.api.fixture.grc.GrcApiScenarioType;
import com.gsl.api.fixture.grc.GrcFixtureContext;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link GrcApiClient}. Driven entirely by
 * {@link GrcApiScenarioType}/{@link GrcApiFixtureFactory}.
 *
 * <p>Requires {@code fixture.grc.vendorCode}/{@code .siteCode}/{@code .ouCode}/{@code .itemCode}
 * in {@code api.properties} to be real, valid ids for {@code api.tenant.active}.
 */
public class GrcApiTest extends APITest {

    @Test
    public void grcPositive() {
        runScenarios("grcPositive");
    }

    @Test
    public void grcNegative() {
        runScenarios("grcNegative");
    }

    private void runScenarios(String methodName) {
        GrcApiClient client = session().getGrcApiClient();
        GrcFixtureContext ctx = GrcFixtureContext.fromConfig();
        List<GrcApiScenarioType> scenarios = GrcApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (GrcApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
