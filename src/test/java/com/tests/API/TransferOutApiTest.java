package com.tests.API;

import com.gsl.api.client.TransferOutApiClient;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.fixture.transferout.TransferOutApiFixtureFactory;
import com.gsl.api.fixture.transferout.TransferOutApiScenarioType;
import com.gsl.api.fixture.transferout.TransferOutFixtureContext;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link TransferOutApiClient}. Driven
 * entirely by {@link TransferOutApiScenarioType}/{@link TransferOutApiFixtureFactory}.
 *
 * <p>Requires {@code fixture.transferout.dcCode}/{@code .itemCode} in {@code api.properties} to
 * be real, valid ids for {@code api.tenant.active}.
 */
public class TransferOutApiTest extends APITest {

    @Test
    public void transferOutPositive() {
        runScenarios("transferOutPositive");
    }

    @Test
    public void transferOutNegative() {
        runScenarios("transferOutNegative");
    }

    private void runScenarios(String methodName) {
        TransferOutApiClient client = session().getTransferOutApiClient();
        TransferOutFixtureContext ctx = TransferOutFixtureContext.fromConfig();
        List<TransferOutApiScenarioType> scenarios = TransferOutApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (TransferOutApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
