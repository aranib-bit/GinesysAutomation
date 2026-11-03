package com.tests.API;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.approval.api.ApiApprovalPolicyGateway;
import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalHierarchy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalRuleSet;
import com.gsl.approval.service.ApprovalConfigurationException;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;
import com.gsl.approval.service.ApprovalHierarchyService;
import com.gsl.approval.service.ApprovalRuleService;
import com.tests.API.support.StubApiServer;
import com.tests.API.support.WireAssert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 2 of the approval workflow (HARs: Approval Rules, RulePrioritySets, Approval_Hierarchy): the active rules and a hierarchy are read,
 * mapped and validated. Mapping runs against a stub server with HAR samples; validation runs against fake gateways.
 */
public class ApprovalPolicyRetrievalUnitTest {

    private StubApiServer server;
    private ApiApprovalPolicyGateway apiGateway;

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        apiGateway = new ApiApprovalPolicyGateway(new ApprovalApiClient(server.spec()));
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        server.reset();
    }

    // ---- mapping from the HAR samples ------------------------------------------------------------------------------------------------------

    @Test
    public void activeRuleSetKeepsPriorityDefaultFlagAndDates() {
        server.respond("GET", "/ApprovalRulePriority/GetActiveVersion", 200, WireAssert.sample("approval/active-rule-set-response.json"));

        ApprovalRuleSet set = apiGateway.fetchActiveRuleSet("AP");

        assertEquals(set.getVersionNo(), 2);
        assertEquals(set.getStatus(), "ACTIVE");
        assertEquals(set.getRules().size(), 4);
        ApprovalRule first = set.getRules().get(0);
        assertEquals(first.getName(), "Below 10L");
        assertEquals(first.getPriority(), Integer.valueOf(1));
        assertEquals(first.getEffectiveDate(), LocalDate.of(2026, 10, 30));
        ApprovalRule last = set.getRules().get(3);
        assertTrue(last.isDefaultRule());
        assertNull(last.getEffectiveDate());
        assertTrue(server.last().query().contains("entityCode=AP"), server.last().query());
    }

    @Test
    public void ruleDetailCarriesHierarchyAndConditions() {
        server.respond("GET", "/ApprovalRule/Get", 200, WireAssert.sample("approval/rule-below-10l-response.json"));

        ApprovalRule rule = apiGateway.fetchRuleDetail(29);

        assertEquals(rule.getHierarchyCode(), Integer.valueOf(8));
        assertFalse(rule.isDefaultRule());
        assertEquals(rule.getConditions().size(), 1);
        assertEquals(rule.getConditions().get(0).getVarCode(), "TOTAL_AMOUNT");
        assertEquals(rule.getConditions().get(0).getOperator(), "LT");
        assertEquals(rule.getConditions().get(0).getValueText(), "1000000");
        assertFalse(rule.getConditions().get(0).isLineBasis());
        assertTrue(server.last().query().contains("id=29"), server.last().query());
    }

    @Test
    public void defaultRuleDetailHasNoConditions() {
        server.respond("GET", "/ApprovalRule/Get", 200, WireAssert.sample("approval/rule-default-response.json"));

        ApprovalRule rule = apiGateway.fetchRuleDetail(4);

        assertTrue(rule.isDefaultRule());
        assertTrue(rule.getConditions().isEmpty());
        assertNull(rule.getEffectiveDate());
        assertEquals(rule.getHierarchyCode(), Integer.valueOf(1));
    }

    @Test
    public void twoLevelHierarchyMapsQuorumAndApprovers() {
        server.respond("GET", "/ApprovalHierarchy/Get", 200, WireAssert.sample("approval/hierarchy-two-level-response.json"));

        ApprovalHierarchy h = new ApprovalHierarchyService(apiGateway::fetchHierarchy).getHierarchy(11);

        assertEquals(h.getLevels().size(), 2);
        ApprovalLevel l1 = h.getLevels().get(0);
        assertEquals(l1.getQuorum(), ApprovalLevel.Quorum.CUSTOM);
        assertEquals(l1.requiredApprovals(), 1);
        assertEquals(l1.getApprovers().size(), 2);
        assertEquals(l1.getApprovers().get(0).getUserCode(), Integer.valueOf(51009));
        ApprovalLevel l2 = h.getLevels().get(1);
        assertEquals(l2.getQuorum(), ApprovalLevel.Quorum.ALL);
        assertEquals(l2.requiredApprovals(), 1);
        assertEquals(l2.getApprovers().get(0).getUserCode(), Integer.valueOf(155039));
    }

    @Test
    public void autoApproveHierarchyHasNoLevels() {
        server.respond("GET", "/ApprovalHierarchy/Get", 200, WireAssert.sample("approval/hierarchy-auto-approve-response.json"));

        ApprovalHierarchyService service = new ApprovalHierarchyService(apiGateway::fetchHierarchy);
        assertTrue(service.isAutoApprove(service.getHierarchy(1)));
    }

    @Test
    public void roleApproverIsMappedAsARole() {
        server.respond("GET", "/ApprovalHierarchy/Get", 200,
                "{\"success\":true,\"result\":{\"code\":2,\"name\":\"AP Approval\",\"isActive\":\"Y\",\"levels\":[{\"levelNo\":1,"
                        + "\"quorumType\":\"A\",\"approvers\":[{\"approverType\":\"R\",\"userCode\":null,\"roleId\":1161,"
                        + "\"name\":\"Sales Dispatch\",\"isExtinct\":false,\"activeUserCount\":4}]}]}}");

        ApprovalApprover approver = apiGateway.fetchHierarchy(2).getLevels().get(0).getApprovers().get(0);

        assertTrue(approver.isRole());
        assertEquals(approver.getRoleId(), Integer.valueOf(1161));
        assertEquals(approver.getActiveUserCount(), Integer.valueOf(4));
    }

    @Test
    public void httpFailureKeepsItsKind() {
        server.respond("GET", "/ApprovalRulePriority/GetActiveVersion", 401, "{\"message\":\"x\"}");
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class, () -> apiGateway.fetchActiveRuleSet("AP"));
        assertEquals(e.getKind(), Kind.AUTHENTICATION);
    }

    // ---- rule service validation ----------------------------------------------------------------------------------------------------------

    private static ApprovalRule summary(int code, String name, Integer priority, boolean isDefault) {
        return ApprovalRule.builder().ruleCode(code).name(name).priority(priority).defaultRule(isDefault).build();
    }

    private static ApprovalRuleService ruleService(ApprovalRuleSet set, Map<Integer, ApprovalRule> details) {
        return new ApprovalRuleService(new com.gsl.approval.service.ApprovalRuleGateway() {
            @Override
            public ApprovalRuleSet fetchActiveRuleSet(String entityCode) {
                return set;
            }

            @Override
            public ApprovalRule fetchRuleDetail(int ruleCode) {
                return details.get(ruleCode);
            }
        });
    }

    private static ApprovalRule detail(int code, int hierarchyCode) {
        return ApprovalRule.builder().ruleCode(code).hierarchyCode(hierarchyCode).build();
    }

    @Test
    public void rulesComeBackInPriorityOrderWithTheirHierarchy() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 2, "ACTIVE", List.of(
                summary(4, "Default Rule", 3, true), summary(29, "Below 10L", 1, false), summary(30, "10 L or more", 2, false)));
        List<ApprovalRule> rules = ruleService(set, Map.of(4, detail(4, 1), 29, detail(29, 8), 30, detail(30, 11)))
                .getActiveRulesInPriorityOrder("AP");

        assertEquals(rules.stream().map(ApprovalRule::getName).toList(), List.of("Below 10L", "10 L or more", "Default Rule"));
        assertEquals(rules.get(0).getHierarchyCode(), Integer.valueOf(8));
        assertEquals(rules.get(2).getHierarchyCode(), Integer.valueOf(1));
    }

    @Test
    public void noPrioritySetStillYieldsTheDefaultRule() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 0, null, List.of(summary(4, "Default Rule", 1, true)));
        assertEquals(ruleService(set, Map.of(4, detail(4, 1))).getActiveRulesInPriorityOrder("AP").size(), 1);
    }

    @Test
    public void duplicatePriorityIsAConfigurationError() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 1, "ACTIVE", List.of(
                summary(5, "A", 1, false), summary(6, "B", 1, false), summary(4, "Default Rule", 2, true)));
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(set, Map.of(4, detail(4, 1), 5, detail(5, 2), 6, detail(6, 2))).getActiveRulesInPriorityOrder("AP"));
        assertEquals(e.getKind(), Kind.INVALID_CONFIGURATION);
    }

    @Test
    public void missingDefaultRuleIsAConfigurationError() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 1, "ACTIVE", List.of(summary(5, "A", 1, false)));
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(set, Map.of(5, detail(5, 2))).getActiveRulesInPriorityOrder("AP"));
        assertEquals(e.getKind(), Kind.INVALID_CONFIGURATION);
    }

    @Test
    public void ruleWithoutPriorityIsAConfigurationError() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 1, "ACTIVE", List.of(summary(5, "A", null, false), summary(4, "Default Rule", 2, true)));
        expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(set, Map.of(4, detail(4, 1), 5, detail(5, 2))).getActiveRulesInPriorityOrder("AP"));
    }

    @Test
    public void emptySetAndInactiveSetAreConfigurationErrors() {
        ApprovalConfigurationException empty = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(new ApprovalRuleSet("AP", 1, "ACTIVE", Collections.emptyList()), Map.of()).getActiveRulesInPriorityOrder("AP"));
        assertEquals(empty.getKind(), Kind.INVALID_CONFIGURATION);

        ApprovalRuleSet superseded = new ApprovalRuleSet("AP", 3, "SUPERSEDED", List.of(summary(4, "Default Rule", 1, true)));
        ApprovalConfigurationException inactive = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(superseded, Map.of(4, detail(4, 1))).getActiveRulesInPriorityOrder("AP"));
        assertEquals(inactive.getKind(), Kind.INVALID_CONFIGURATION);
    }

    @Test
    public void ruleSetOfAnotherEntityIsRejected() {
        ApprovalRuleSet set = new ApprovalRuleSet("PO", 1, "ACTIVE", List.of(summary(4, "Default Rule", 1, true)));
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(set, Map.of(4, detail(4, 1))).getActiveRulesInPriorityOrder("AP"));
        assertEquals(e.getKind(), Kind.ENTITY_MISMATCH);
    }

    @Test
    public void ruleDetailMissingIsNotFound() {
        ApprovalRuleSet set = new ApprovalRuleSet("AP", 1, "ACTIVE", List.of(summary(4, "Default Rule", 1, true)));
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> ruleService(set, Map.of()).getActiveRulesInPriorityOrder("AP"));
        assertEquals(e.getKind(), Kind.NOT_FOUND);
    }

    @Test
    public void effectiveDateBoundary() {
        ApprovalRule rule = ApprovalRule.builder().effectiveDate(LocalDate.of(2026, 11, 3)).build();
        assertFalse(rule.isEffective(LocalDate.of(2026, 11, 2)));
        assertTrue(rule.isEffective(LocalDate.of(2026, 11, 3)));
        assertTrue(ApprovalRule.builder().build().isEffective(LocalDate.of(2000, 1, 1)));
    }

    // ---- hierarchy service validation ------------------------------------------------------------------------------------------------------

    private static ApprovalApprover user(int code) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.USER).userCode(code).build();
    }

    private static ApprovalApprover role(int id) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.ROLE).roleId(id).build();
    }

    private static ApprovalLevel level(int no, ApprovalLevel.Quorum q, Integer count, ApprovalApprover... approvers) {
        return ApprovalLevel.builder().levelNo(no).quorum(q).quorumCount(count).approvers(List.of(approvers)).build();
    }

    private static ApprovalHierarchyService hierarchyOf(boolean active, ApprovalLevel... levels) {
        ApprovalHierarchy h = ApprovalHierarchy.builder().code(9).name("H").active(active).levels(List.of(levels)).build();
        return new ApprovalHierarchyService(code -> h);
    }

    @Test
    public void inactiveHierarchyIsRejected() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> hierarchyOf(false, level(1, ApprovalLevel.Quorum.ALL, null, user(1))).getHierarchy(9));
        assertEquals(e.getKind(), Kind.INVALID_CONFIGURATION);
    }

    @Test
    public void levelGapIsRejected() {
        expectThrows(ApprovalConfigurationException.class, () -> hierarchyOf(true,
                level(1, ApprovalLevel.Quorum.ALL, null, user(1)), level(3, ApprovalLevel.Quorum.ALL, null, user(2))).getHierarchy(9));
    }

    @Test
    public void levelWithoutApproversIsRejected() {
        expectThrows(ApprovalConfigurationException.class,
                () -> hierarchyOf(true, level(1, ApprovalLevel.Quorum.ALL, null)).getHierarchy(9));
    }

    @Test
    public void customQuorumMustBeAchievable() {
        expectThrows(ApprovalConfigurationException.class,
                () -> hierarchyOf(true, level(1, ApprovalLevel.Quorum.CUSTOM, 0, user(1))).getHierarchy(9));
        expectThrows(ApprovalConfigurationException.class,
                () -> hierarchyOf(true, level(1, ApprovalLevel.Quorum.CUSTOM, 3, user(1), user(2))).getHierarchy(9));
    }

    @Test
    public void customQuorumOverARoleIsAccepted() {
        // How many users a role has is only known when the role is expanded, so the count cannot be checked here.
        assertEquals(hierarchyOf(true, level(1, ApprovalLevel.Quorum.CUSTOM, 3, role(1161))).getHierarchy(9).getLevels().size(), 1);
    }

    @Test
    public void missingHierarchyIsNotFound() {
        ApprovalConfigurationException e = expectThrows(ApprovalConfigurationException.class,
                () -> new ApprovalHierarchyService(code -> null).getHierarchy(9));
        assertEquals(e.getKind(), Kind.NOT_FOUND);
    }
}
