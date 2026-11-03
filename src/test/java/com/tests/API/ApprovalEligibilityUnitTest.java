package com.tests.API;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.request.approval.ApprovalEntityConfigsRequest;
import com.gsl.approval.api.ApiApprovalConfigurationGateway;
import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalStatus;
import com.gsl.approval.model.ApprovalWorkflowResult;
import com.gsl.approval.service.ApprovalConfigurationException;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;
import com.gsl.approval.service.ApprovalConfigurationGateway;
import com.gsl.approval.service.ApprovalEligibilityService;
import com.gsl.workflow.approval.ApprovalWorkflow;
import com.tests.API.support.StubApiServer;
import com.tests.API.support.WireAssert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 1 of the approval workflow (HARs: Entities, APApprovalEnable): the entity switch is read live, only an explicit flag means enabled
 * or disabled, and every failure is an {@link ApprovalConfigurationException}, never "disabled". Gateway cases run against a stub server.
 */
public class ApprovalEligibilityUnitTest {

    private static final String CONFIGS_PATH = "/ApprovalEntityConfig/GetEntityConfigs";

    private StubApiServer server;
    private ApprovalWorkflow workflow;

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        workflow = workflowOver(new ApiApprovalConfigurationGateway(new ApprovalApiClient(server.spec())));
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        server.reset();
    }

    private static ApprovalWorkflow workflowOver(ApprovalConfigurationGateway gateway) {
        return new ApprovalWorkflow(new ApprovalEligibilityService(gateway));
    }

    private static ApprovalRequest request(String entityCode) {
        return ApprovalRequest.builder().entityCode(entityCode).documentCode("640989").documentNumber("VP/02718/26-27").build();
    }

    private static ApprovalEntity entity(String code, Boolean enabled) {
        return ApprovalEntity.builder().entityCode(code).displayName(code).approvalEnabled(enabled).build();
    }

    // ---- service + workflow with a fake gateway --------------------------------------------------------------------------------------------

    @Test
    public void enabledEntityIsEligible() {
        ApprovalWorkflowResult r = workflowOver(code -> entity(code, true)).execute(request("AP"));
        assertEquals(r.getStatus(), ApprovalStatus.ELIGIBLE);
        assertEquals(r.getDocumentNumber(), "VP/02718/26-27");
    }

    @Test
    public void disabledEntityIsSkipped() {
        assertEquals(workflowOver(code -> entity(code, false)).execute(request("AP")).getStatus(), ApprovalStatus.SKIPPED);
    }

    @Test
    public void disabledEntityMakesNoFurtherCall() {
        AtomicInteger calls = new AtomicInteger();
        workflowOver(code -> {
            calls.incrementAndGet();
            return entity(code, false);
        }).execute(request("AP"));
        assertEquals(calls.get(), 1, "only the configuration is read when approval is disabled");
    }

    @Test
    public void nullRequestIsRejected() {
        expectThrows(IllegalArgumentException.class, () -> workflowOver(code -> entity(code, true)).execute(null));
    }

    @Test
    public void blankEntityCodeIsRejected() {
        expectThrows(IllegalArgumentException.class, () -> workflowOver(code -> entity(code, true)).execute(request(" ")));
    }

    @Test
    public void missingConfigurationRowIsNotDisabled() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> workflowOver(code -> null).execute(request("AP")));
        assertEquals(e.getKind(), Kind.NOT_FOUND);
    }

    @Test
    public void missingEnabledFlagIsNotDisabled() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> workflowOver(code -> entity(code, null)).execute(request("AP")));
        assertEquals(e.getKind(), Kind.INVALID_RESPONSE);
    }

    @Test
    public void rowForAnotherEntityIsRejected() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> workflowOver(code -> entity("PO", true)).execute(request("AP")));
        assertEquals(e.getKind(), Kind.ENTITY_MISMATCH);
    }

    // ---- gateway + client against the stub server ------------------------------------------------------------------------------------------

    @Test
    public void requestBodyMatchesTheHar() {
        WireAssert.sameJson(ApprovalEntityConfigsRequest.firstPage(25), "approval/entity-configs-request.json");
    }

    @Test
    public void enabledRowFromTheHarIsEligibleAndDisabledRowIsSkipped() {
        server.respond("POST", CONFIGS_PATH, 200, WireAssert.sample("approval/entity-configs-response-enabled.json"));

        assertEquals(workflow.execute(request("AP")).getStatus(), ApprovalStatus.ELIGIBLE);
        assertEquals(workflow.execute(request("PO")).getStatus(), ApprovalStatus.SKIPPED);
        assertEquals(server.last().method(), "POST");
        assertTrue(server.last().body().contains("\"limit\":25"), server.last().body());
    }

    @Test
    public void entityAbsentFromTheListIsNotFoundNotDisabled() {
        server.respond("POST", CONFIGS_PATH, 200, WireAssert.sample("approval/entity-configs-response-enabled.json"));
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> workflow.execute(request("POI")));
        assertEquals(e.getKind(), Kind.NOT_FOUND);
    }

    @Test
    public void httpFailuresKeepTheirKind() {
        assertKindForStatus(401, Kind.AUTHENTICATION);
        assertKindForStatus(403, Kind.AUTHORIZATION);
        assertKindForStatus(404, Kind.NOT_FOUND);
        assertKindForStatus(500, Kind.SERVER);
        assertKindForStatus(503, Kind.SERVER);
    }

    private void assertKindForStatus(int status, Kind expected) {
        server.respond("POST", CONFIGS_PATH, status, "{\"message\":\"x\"}");
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> workflow.execute(request("AP")));
        assertEquals(e.getKind(), expected, "HTTP " + status);
    }

    @Test
    public void successFalseEnvelopeIsAFailureNotDisabled() {
        server.respond("POST", CONFIGS_PATH, 200, "{\"success\":false,\"error\":\"nope\",\"result\":null}");
        expectThrows(ApprovalConfigurationException.class, () -> workflow.execute(request("AP")));
    }

    @Test
    public void bodyWithoutResultListIsInvalid() {
        server.respond("POST", CONFIGS_PATH, 200, "{\"success\":true,\"error\":null}");
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> workflow.execute(request("AP")));
        assertEquals(e.getKind(), Kind.INVALID_RESPONSE);
    }

    @Test
    public void rowWithoutEnabledFlagFromTheServerIsInvalid() {
        server.respond("POST", CONFIGS_PATH, 200,
                "{\"success\":true,\"result\":[{\"entityCode\":\"AP\",\"displayName\":\"AP Voucher\"}]}");
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> workflow.execute(request("AP")));
        assertEquals(e.getKind(), Kind.INVALID_RESPONSE);
    }

    @Test
    public void isWorkflowEnabledReadsTheBooleanResult() {
        server.respond("GET", "/ApprovalAction/IsWorkflowEnabled", 200, "{\"result\":true,\"success\":true,\"error\":null}");
        assertTrue(new ApprovalApiClient(server.spec()).isWorkflowEnabled("AP"));
        assertTrue(server.last().query().contains("entityCode=AP"), server.last().query());
    }
}
