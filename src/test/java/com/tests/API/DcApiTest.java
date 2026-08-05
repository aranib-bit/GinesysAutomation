package com.tests.API;

import com.gsl.api.client.DcApiClient;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.fixture.dc.DcApiFixtureFactory;
import com.gsl.api.fixture.dc.DcApiScenarioType;
import com.gsl.api.fixture.dc.DcFixtureContext;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link DcApiClient}. Driven entirely by
 * {@link DcApiScenarioType}/{@link DcApiFixtureFactory}.
 *
 * <p>Requires {@code fixture.dc.itemCode} in {@code api.properties} to be a real, valid item
 * code for {@code api.tenant.active}.
 */
public class DcApiTest extends APITest {

    @Test
    public void dcPositive() {
        runScenarios("dcPositive");
    }

    @Test
    public void dcNegative() {
        runScenarios("dcNegative");
    }

    private void runScenarios(String methodName) {
        DcApiClient client = session().getDcApiClient();
        DcFixtureContext ctx = DcFixtureContext.fromConfig();
        List<DcApiScenarioType> scenarios = DcApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (DcApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
