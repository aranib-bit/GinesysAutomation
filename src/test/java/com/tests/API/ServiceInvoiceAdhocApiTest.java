package com.tests.API;

import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.fixture.serviceinvoiceadhoc.ServiceInvoiceAdhocApiFixtureFactory;
import com.gsl.api.fixture.serviceinvoiceadhoc.ServiceInvoiceAdhocApiScenarioType;
import com.gsl.api.fixture.serviceinvoiceadhoc.ServiceInvoiceAdhocFixtureContext;
import com.gsl.reports.StepExecutor;
import com.tests.APITest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

/**
 * Endpoint-level coverage for every public method on {@link ServiceInvoiceAdhocApiClient} — not
 * just the terminal {@code ServiceInvoice/Save} call the existing backfill path
 * ({@code PendingApDocumentBackfillStrategy}) already exercises. Driven entirely by
 * {@link ServiceInvoiceAdhocApiScenarioType}/{@link ServiceInvoiceAdhocApiFixtureFactory} so
 * adding a new endpoint variant means adding one enum constant, not a new test method.
 *
 * <p>Requires {@code fixture.serviceinvoiceadhoc.ownerSiteCode}/{@code .vendorSlCode} in
 * {@code api.properties} to be real, valid ids for {@code api.tenant.active} — see that file's
 * "Fixture-driven API endpoint testing" section.
 */
public class ServiceInvoiceAdhocApiTest extends APITest {

    @Test
    public void serviceInvoiceAdhocPositive() {
        runScenarios("serviceInvoiceAdhocPositive");
    }

    @Test
    public void serviceInvoiceAdhocNegative() {
        runScenarios("serviceInvoiceAdhocNegative");
    }

    private void runScenarios(String methodName) {
        ServiceInvoiceAdhocApiClient client = session().getServiceInvoiceAdhocApiClient();
        ServiceInvoiceAdhocFixtureContext ctx = ServiceInvoiceAdhocFixtureContext.fromConfig();
        List<ServiceInvoiceAdhocApiScenarioType> scenarios = ServiceInvoiceAdhocApiFixtureFactory.scenariosFor(methodName);

        SoftAssert softAssert = new SoftAssert();
        for (ServiceInvoiceAdhocApiScenarioType scenario : scenarios) {
            ScenarioOutcome outcome = StepExecutor.call(scenario.name() + " (" + scenario.getEndpoint() + ")",
                    () -> scenario.execute(client, ctx));
            softAssert.assertTrue(outcome.isPassed(), scenario.name() + " — " + outcome.getDetail());
        }
        softAssert.assertAll();
    }
}
