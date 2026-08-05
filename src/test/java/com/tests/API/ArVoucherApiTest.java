package com.tests.API;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.fixture.arvoucher.ArVoucherApiFixtureFactory;
import com.gsl.api.fixture.arvoucher.ArVoucherApiScenarioType;
import com.gsl.api.fixture.arvoucher.ArVoucherFixtureContext;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link ArVoucherApiClient} — including its
 * documented hard-fail-on-classification-miss contract for {@code resolveSubLedger}, the most
 * direct design contrast with {@code ServiceInvoiceAdhocApiClient.resolveVendor}'s soft fallback.
 * Driven entirely by {@link ArVoucherApiScenarioType}/{@link ArVoucherApiFixtureFactory}.
 *
 * <p>Requires {@code fixture.arvoucher.ownerSiteCode}/{@code .ouCode}/{@code .empCode} in
 * {@code api.properties} to be real, valid ids for {@code api.tenant.active}.
 */
public class ArVoucherApiTest extends APITest {

    @Test
    public void arVoucherPositive() {
        runScenarios("arVoucherPositive");
    }

    @Test
    public void arVoucherNegative() {
        runScenarios("arVoucherNegative");
    }

    private void runScenarios(String methodName) {
        ArVoucherApiClient client = session().getArVoucherApiClient();
        ArVoucherFixtureContext ctx = ArVoucherFixtureContext.fromConfig();
        List<ArVoucherApiScenarioType> scenarios = ArVoucherApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (ArVoucherApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
