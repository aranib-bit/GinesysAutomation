package com.tests.API;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.approval.auth.ApprovalAuthenticationException;
import com.gsl.approval.auth.ApproverCredentialSource;
import com.gsl.approval.auth.ApproverLoginStrategy;
import com.gsl.approval.auth.ApproverSession;
import com.gsl.approval.auth.ApproverSessionService;
import com.gsl.approval.auth.LiveSessionSource;
import com.gsl.approval.evaluator.ApprovalConditionEvaluator;
import com.gsl.approval.evaluator.ApprovalRuleResolver;
import com.gsl.approval.executor.ApiApprovalStrategy;
import com.gsl.approval.executor.ApprovalActionException;
import com.gsl.approval.executor.ApprovalExecutionStrategy;
import com.gsl.approval.facts.ApVoucherApprovalFacts;
import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.model.ApprovalHierarchy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApprovalPath;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalRuleSet;
import com.gsl.approval.model.ApprovalStatus;
import com.gsl.approval.model.ApprovalTask;
import com.gsl.approval.model.ApprovalWorkflowResult;
import com.gsl.approval.model.ApproverIdentity;
import com.gsl.approval.model.DirectoryPage;
import com.gsl.approval.model.DirectoryUser;
import com.gsl.approval.model.PostedState;
import com.gsl.approval.reporting.ApprovalReporter;
import com.gsl.approval.service.ApprovalAssignmentService;
import com.gsl.approval.service.ApprovalEligibilityService;
import com.gsl.approval.service.ApprovalHierarchyService;
import com.gsl.approval.service.ApprovalIncompleteException;
import com.gsl.approval.service.ApprovalLevelService;
import com.gsl.approval.service.ApprovalPoller;
import com.gsl.approval.service.ApprovalRuleGateway;
import com.gsl.approval.service.ApprovalRuleService;
import com.gsl.approval.service.UserDirectory;
import com.gsl.approval.validation.ApprovalPostedValidator;
import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserType;
import com.gsl.workflow.approval.ApprovalOrchestrator;
import com.gsl.workflow.approval.ApprovalWorkflow;
import com.tests.API.support.StubApiServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 5 of the approval workflow: approving through the levels. A simulated inbox plays the application (a shared task per level, quorum,
 * next level appearing); the users are the ones in the HARs (bt 51009, Aritra Lahiri 155061, Manas Kundu 155039).
 */
public class ApprovalWorkflowExecutionUnitTest {

    private static final int BT = 51009;
    private static final int ARITRA = 155061;
    private static final int MANAS = 155039;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    // ---- simulated application ---------------------------------------------------------------------------------------------------------------

    /** The inbox: one document, levels worked in order, each level's quorum counted like the application does. */
    private static final class Sim implements ApprovalExecutionStrategy {
        final List<ApprovalLevel> levels;
        final Map<Integer, Set<Integer>> approvedAtLevel = new HashMap<>();
        final List<String> log = new ArrayList<>();
        int currentLevel = 1;
        boolean done;
        /** Fault injection: approve calls (by user code) that fail with this kind before taking effect. */
        final Map<Integer, ApprovalActionException.Kind> failFirstApproveWith = new HashMap<>();
        /** Fault injection for AMBIGUOUS: the call was applied although the caller saw no answer. */
        boolean ambiguousButApplied;
        final Set<Integer> sawStale = new HashSet<>();
        /** The application's approval path is readable (like GetApprovalPath); off = the executor has no such view. */
        boolean pathEnabled;
        /** The path never reflects an approval (the application is slow to register it). */
        boolean pathLags;
        String pathRule = "Below 10L";
        String pathHierarchy = "H8";

        Sim(List<ApprovalLevel> levels) {
            this.levels = levels;
        }

        private boolean eligible(ApproverSession s, ApprovalLevel level) {
            return level.getApprovers().stream().anyMatch(a -> a.isRole() ? true : a.getUserCode() == s.identity().getUserCode());
        }

        @Override
        public List<ApprovalTask> pending(ApproverSession s, String entityCode) {
            if (done || currentLevel > levels.size()) {
                return List.of();
            }
            ApprovalLevel level = levels.get(currentLevel - 1);
            Set<Integer> already = approvedAtLevel.getOrDefault(currentLevel, Set.of());
            if (!eligible(s, level) || already.contains(s.identity().getUserCode())) {
                return List.of();
            }
            return List.of(ApprovalTask.builder().taskCode(100 + currentLevel).entityCode("AP").documentCode("640989").documentNumber("VP/02718/26-27")
                    .levelNo(currentLevel).totalLevels(levels.size()).finalLevel(currentLevel == levels.size())
                    .approvedCount(already.size()).quorumRequired(level.requiredApprovals()).build());
        }

        @Override
        public List<ApprovalTask> archive(ApproverSession s, String entityCode) {
            return List.of();
        }

        @Override
        public Optional<ApprovalPath> path(ApproverSession s, String entityCode, String documentCode) {
            if (!pathEnabled) {
                return Optional.empty();
            }
            List<ApprovalPath.Level> out = new ArrayList<>();
            for (int i = 1; i <= levels.size(); i++) {
                int approved = pathLags ? 0 : approvedAtLevel.getOrDefault(i, Set.of()).size();
                int required = levels.get(i - 1).requiredApprovals();
                boolean satisfied = !pathLags && approved >= required;
                out.add(ApprovalPath.Level.builder().levelNo(i).quorumRequired(required).approvedCount(approved)
                        .status(satisfied ? "APPROVED" : "PENDING").build());
            }
            return Optional.of(ApprovalPath.builder().status(done ? "APPROVED" : "PENDING").ruleName(pathRule).hierarchyName(pathHierarchy).levels(out).build());
        }

        @Override
        public void approve(ApproverSession s, ApprovalTask task, String remarks) {
            int user = s.identity().getUserCode();
            ApprovalActionException.Kind fault = failFirstApproveWith.remove(user);
            if (fault == ApprovalActionException.Kind.STALE_SESSION && s.reused()) {
                sawStale.add(user);
                throw new ApprovalActionException(fault, "401");
            }
            if (fault == ApprovalActionException.Kind.AMBIGUOUS) {
                if (ambiguousButApplied) {
                    apply(user, task);
                }
                throw new ApprovalActionException(fault, "no answer");
            }
            apply(user, task);
        }

        private void apply(int user, ApprovalTask task) {
            log.add("L" + task.getLevelNo() + ":" + user);
            approvedAtLevel.computeIfAbsent(task.getLevelNo(), k -> new HashSet<>()).add(user);
            if (approvedAtLevel.get(task.getLevelNo()).size() >= levels.get(task.getLevelNo() - 1).requiredApprovals()) {
                if (task.getLevelNo() == levels.size()) {
                    done = true;
                } else {
                    currentLevel = task.getLevelNo() + 1;
                }
            }
        }
    }

    /** Collects the information lines so a test can read what the workflow reported. */
    private static final class Capture implements ApprovalReporter {
        final List<String> infos = new ArrayList<>();

        @Override
        public void step(String name, Runnable action) {
            action.run();
        }

        @Override
        public <T> T call(String name, java.util.function.Supplier<T> action) {
            return action.get();
        }

        @Override
        public void info(String message) {
            infos.add(message);
        }
    }

    private ApprovalReporter reporter = ApprovalReporter.NONE;

    // ---- builders --------------------------------------------------------------------------------------------------------------------------

    private static ApprovalApprover user(int code, String name) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.USER).userCode(code).name(name).build();
    }

    private static ApprovalApprover role(int id) {
        return ApprovalApprover.builder().type(ApprovalApprover.Type.ROLE).roleId(id).build();
    }

    private static ApprovalLevel level(int no, ApprovalLevel.Quorum q, Integer count, ApprovalApprover... slots) {
        return ApprovalLevel.builder().levelNo(no).quorum(q).quorumCount(count).approvers(List.of(slots)).build();
    }

    private static List<DirectoryUser> directoryUsers() {
        return List.of(
                new DirectoryUser(BT, "bt", "Barbara Tufani", 1161, "Sales Dispatch", false),
                new DirectoryUser(ARITRA, "aritra", "Aritra Lahiri", 1161, "Sales Dispatch", false),
                new DirectoryUser(MANAS, "mkundu", "Manas Kundu", 1161, "Sales Dispatch", false),
                new DirectoryUser(7, "gone", "Gone", 1161, "Sales Dispatch", true));
    }

    private static final ApprovalPoller ONCE = new ApprovalPoller() {
        @Override
        public <T> Optional<T> until(java.util.function.Supplier<Optional<T>> check) {
            return check.get();
        }
    };

    /** Live-session / login bookkeeping shared by a test. */
    private static final class Users {
        final Set<Integer> live = new HashSet<>();
        final Set<String> withCredentials = new HashSet<>(Set.of("bt", "aritra", "mkundu"));
        final List<String> loggedIn = new ArrayList<>();

        ApproverSessionService service() {
            LiveSessionSource source = a -> live.contains(a.getUserCode())
                    ? Optional.of(new ApproverSession(a, null, ApproverSession.Source.BROWSER_TOKENS, null)) : Optional.empty();
            ApproverCredentialSource creds = a -> withCredentials.contains(a.getUsername())
                    ? Optional.of(new UserCredential(a.getUsername(), "x", UserType.UI, ExecutionSet.SET1, null)) : Optional.empty();
            ApproverLoginStrategy login = (a, c) -> {
                loggedIn.add(a.getUsername());
                return new ApproverSession(a, null, ApproverSession.Source.NEW_LOGIN, null);
            };
            return new ApproverSessionService(List.of(source), creds, login);
        }
    }

    private static ApprovalRule rule(int code, String name, int priority, boolean isDefault, int hierarchyCode, ApprovalCondition... conds) {
        return ApprovalRule.builder().ruleCode(code).name(name).priority(priority).defaultRule(isDefault).hierarchyCode(hierarchyCode)
                .conditions(List.of(conds)).build();
    }

    private static ApprovalCondition amountLt(String v) {
        return ApprovalCondition.builder().basis("H").varCode("TOTAL_AMOUNT").dataType("NUMBER").operator("LT").valueText(v).build();
    }

    private static ApprovalCondition amountGte(String v) {
        return ApprovalCondition.builder().basis("H").varCode("TOTAL_AMOUNT").dataType("NUMBER").operator("GTE").valueText(v).build();
    }

    private static ApprovalRequest request(String amount) {
        return ApprovalRequest.builder().entityCode("AP").documentCode("640989").documentNumber("VP/02718/26-27")
                .amount(new BigDecimal(amount)).facts(ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal(amount)).build()).build();
    }

    /** Wires a full workflow over fakes: rules, hierarchies by code, the simulated inbox, the users and a posted state. */
    private ApprovalWorkflow workflow(List<ApprovalRule> rules, Map<Integer, ApprovalHierarchy> hierarchies, Sim sim, Users users,
                                      PostedState posted, boolean enabled, AtomicInteger orchestratorRuns) {
        ApprovalRuleGateway ruleGateway = new ApprovalRuleGateway() {
            @Override
            public ApprovalRuleSet fetchActiveRuleSet(String entityCode) {
                return new ApprovalRuleSet("AP", 1, "ACTIVE", rules);
            }

            @Override
            public ApprovalRule fetchRuleDetail(int ruleCode) {
                return rules.stream().filter(r -> r.getRuleCode() == ruleCode).findFirst().orElseThrow();
            }
        };
        ApprovalHierarchyService hierarchyService = new ApprovalHierarchyService(hierarchies::get);
        UserDirectory directory = new UserDirectory((page, limit) -> new DirectoryPage(directoryUsers().size(), directoryUsers()), 50);
        ApprovalLevelService levels = new ApprovalLevelService(new ApprovalAssignmentService(directory), users.service(), sim, ONCE, reporter);
        ApprovalOrchestrator real = new ApprovalOrchestrator(new ApprovalRuleService(ruleGateway), new ApprovalRuleResolver(new ApprovalConditionEvaluator()),
                hierarchyService, levels, new ApprovalPostedValidator(r -> posted, ONCE), reporter, () -> TODAY) {
            @Override
            public ApprovalWorkflowResult run(ApprovalRequest request, ApprovalEntity entity) {
                orchestratorRuns.incrementAndGet();
                return super.run(request, entity);
            }
        };
        ApprovalEligibilityService eligibility = new ApprovalEligibilityService(code ->
                ApprovalEntity.builder().entityCode(code).approvalEnabled(enabled).build());
        return new ApprovalWorkflow(eligibility, real, ApprovalReporter.NONE);
    }

    private static ApprovalHierarchy hierarchy(int code, ApprovalLevel... levels) {
        return ApprovalHierarchy.builder().code(code).name("H" + code).active(true).levels(List.of(levels)).build();
    }

    /** The staging AP set: below 10 lakh -> one level (bt or aritra, custom quorum 1); 10 lakh or more -> two levels; default Auto Approve. */
    private List<ApprovalRule> stagingRules() {
        return List.of(rule(29, "Below 10L", 1, false, 8, amountLt("1000000")), rule(30, "10 L or more", 2, false, 11, amountGte("1000000")),
                rule(4, "Default Rule", 3, true, 1));
    }

    private Map<Integer, ApprovalHierarchy> stagingHierarchies() {
        return Map.of(
                1, hierarchy(1),
                8, hierarchy(8, level(1, ApprovalLevel.Quorum.CUSTOM, 1, user(BT, "Barbara Tufani"), user(ARITRA, "Aritra Lahiri"))),
                11, hierarchy(11, level(1, ApprovalLevel.Quorum.CUSTOM, 1, user(BT, "Barbara Tufani"), user(ARITRA, "Aritra Lahiri")),
                        level(2, ApprovalLevel.Quorum.ALL, null, user(MANAS, "Manas Kundu"))));
    }

    private ApprovalWorkflowResult run(String amount, Sim sim, Users users, PostedState posted) {
        return workflow(stagingRules(), stagingHierarchies(), sim, users, posted, true, new AtomicInteger()).execute(request(amount));
    }

    private static Sim simFor(ApprovalHierarchy h) {
        return new Sim(h.getLevels());
    }

    // ---- scenarios -------------------------------------------------------------------------------------------------------------------------

    @Test
    public void singleLevelCustomQuorumNeedsOneApprovalAndPrefersTheLiveSession() {
        Sim sim = simFor(stagingHierarchies().get(8));
        Users users = new Users();
        users.live.add(ARITRA);

        ApprovalWorkflowResult r = run("500", sim, users, PostedState.POSTED);

        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertEquals(r.getRuleName(), "Below 10L");
        assertEquals(r.getApprovedBy(), List.of("aritra"));
        assertEquals(sim.log, List.of("L1:" + ARITRA));
        assertTrue(users.loggedIn.isEmpty(), "the live session was reused - nobody was logged in");
        assertTrue(r.isCompleted());
    }

    @Test
    public void twoLevelsAreWorkedInOrderAndAllLevelsAreRequired() {
        Sim sim = simFor(stagingHierarchies().get(11));
        ApprovalWorkflowResult r = run("1200000", sim, new Users(), PostedState.POSTED);

        assertEquals(r.getRuleName(), "10 L or more");
        assertEquals(sim.log, List.of("L1:" + BT, "L2:" + MANAS), "level 2 is only approached after level 1");
        assertEquals(r.getApprovedBy(), List.of("bt", "mkundu"));
        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
    }

    @Test
    public void quorumAllNeedsEveryApprover() {
        ApprovalHierarchy h = hierarchy(8, level(1, ApprovalLevel.Quorum.ALL, null, user(BT, "b"), user(ARITRA, "a")));
        Sim sim = simFor(h);
        ApprovalWorkflowResult r = workflow(List.of(rule(4, "Default Rule", 1, true, 8)), Map.of(8, h), sim, new Users(), PostedState.POSTED, true, new AtomicInteger())
                .execute(request("5"));
        assertEquals(sim.log.size(), 2);
        assertEquals(r.getApprovedBy().size(), 2);
    }

    @Test
    public void customQuorumOfTwoOverARoleStopsAtTwoApprovals() {
        ApprovalHierarchy h = hierarchy(8, level(1, ApprovalLevel.Quorum.CUSTOM, 2, role(1161)));
        Sim sim = simFor(h);
        ApprovalWorkflowResult r = workflow(List.of(rule(4, "Default Rule", 1, true, 8)), Map.of(8, h), sim, new Users(), PostedState.POSTED, true, new AtomicInteger())
                .execute(request("5"));
        assertEquals(sim.log, List.of("L1:" + BT, "L1:" + ARITRA), "three users hold the role but two approvals are enough");
        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
    }

    @Test
    public void autoApproveNeedsNobodyAndStillReportsThePostedState() {
        Sim sim = new Sim(List.of());
        Users users = new Users();
        // A set whose only rule is the default reaches the Auto Approve hierarchy (no levels).
        ApprovalWorkflowResult auto = workflow(List.of(rule(4, "Default Rule", 1, true, 1)), stagingHierarchies(), sim, users, PostedState.POSTED, true, new AtomicInteger())
                .execute(request("5"));
        assertEquals(auto.getStatus(), ApprovalStatus.APPROVED);
        assertTrue(auto.getApprovedBy().isEmpty());
        assertEquals(auto.getPostedState(), PostedState.POSTED);
        assertTrue(users.loggedIn.isEmpty());
        assertEquals(auto.getRuleName(), "Default Rule");
    }

    @Test
    public void unpostedAfterTheFinalApprovalIsReportedNotHidden() {
        ApprovalWorkflowResult r = run("500", simFor(stagingHierarchies().get(8)), new Users(), PostedState.UNPOSTED);
        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertEquals(r.getPostedState(), PostedState.UNPOSTED);
        assertFalse(r.isCompleted());
    }

    @Test
    public void unknownPostedStateIsStillACompletedApproval() {
        ApprovalWorkflowResult r = run("500", simFor(stagingHierarchies().get(8)), new Users(), PostedState.UNKNOWN);
        assertTrue(r.isCompleted());
    }

    @Test
    public void disabledApprovalSkipsWithoutTouchingTheOrchestrator() {
        AtomicInteger runs = new AtomicInteger();
        Users users = new Users();
        ApprovalWorkflowResult r = workflow(stagingRules(), stagingHierarchies(), simFor(stagingHierarchies().get(8)), users, PostedState.POSTED, false, runs)
                .execute(request("500"));
        assertEquals(r.getStatus(), ApprovalStatus.SKIPPED);
        assertEquals(runs.get(), 0);
        assertTrue(users.loggedIn.isEmpty());
    }

    @Test
    public void noApplicableRuleIsReported() {
        // The default rule is the catch-all, so "no rule" only happens when even it is not effective yet.
        ApprovalRule futureDefault = rule(4, "Default Rule", 2, true, 1).toBuilder().effectiveDate(TODAY.plusDays(1)).build();
        List<ApprovalRule> rules = List.of(rule(29, "Below 10L", 1, false, 8, amountLt("1000000")), futureDefault);
        ApprovalWorkflowResult r = workflow(rules, stagingHierarchies(), simFor(stagingHierarchies().get(8)), new Users(), PostedState.POSTED, true, new AtomicInteger())
                .execute(request("5000000"));
        assertEquals(r.getStatus(), ApprovalStatus.NO_APPLICABLE_RULE);
    }

    @Test
    public void aRuleThatCannotBeEvaluatedEndsAsConfigurationErrorAndNobodyApproves() {
        Sim sim = simFor(stagingHierarchies().get(8));
        Users users = new Users();
        ApprovalRequest noAmount = ApprovalRequest.builder().entityCode("AP").documentCode("640989").documentNumber("VP/02718/26-27").facts(Map.of()).build();
        ApprovalWorkflowResult r = workflow(stagingRules(), stagingHierarchies(), sim, users, PostedState.POSTED, true, new AtomicInteger()).execute(noAmount);
        assertEquals(r.getStatus(), ApprovalStatus.CONFIGURATION_ERROR);
        assertTrue(r.getMessage().contains("TOTAL_AMOUNT"), r.getMessage());
        assertTrue(sim.log.isEmpty());
    }

    // ---- recovery --------------------------------------------------------------------------------------------------------------------------

    @Test
    public void aStaleReusedSessionIsReplacedByALoginAndApprovalIsRetriedOnce() {
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.failFirstApproveWith.put(BT, ApprovalActionException.Kind.STALE_SESSION);
        Users users = new Users();
        users.live.add(BT);

        ApprovalWorkflowResult r = run("500", sim, users, PostedState.POSTED);

        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertTrue(sim.sawStale.contains(BT));
        assertEquals(users.loggedIn, List.of("bt"), "one fresh login replaced the stale session");
        assertEquals(sim.log, List.of("L1:" + BT), "approved once");
    }

    @Test
    public void anApprovalWithNoAnswerThatTookEffectIsNotRepeated() {
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.failFirstApproveWith.put(BT, ApprovalActionException.Kind.AMBIGUOUS);
        sim.ambiguousButApplied = true;

        ApprovalWorkflowResult r = run("500", sim, new Users(), PostedState.POSTED);

        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertEquals(sim.log, List.of("L1:" + BT), "reconciled against the inbox: not approved a second time");
    }

    @Test
    public void anApprovalWithNoAnswerThatDidNotTakeEffectFailsInsteadOfBlindlyRetrying() {
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.failFirstApproveWith.put(BT, ApprovalActionException.Kind.AMBIGUOUS);
        sim.ambiguousButApplied = false;

        ApprovalActionException e = expectThrows(ApprovalActionException.class, () -> run("500", sim, new Users(), PostedState.POSTED));
        assertEquals(e.getKind(), ApprovalActionException.Kind.AMBIGUOUS);
        assertTrue(sim.log.isEmpty());
    }

    @Test
    public void whenNoApproverEverSeesTheTaskTheLevelIsIncomplete() {
        // The simulated inbox is on level 1 for bt/aritra; a hierarchy naming only Manas has nobody who sees it.
        ApprovalHierarchy h = hierarchy(8, level(1, ApprovalLevel.Quorum.ALL, null, user(MANAS, "m")));
        Sim sim = new Sim(List.of(level(1, ApprovalLevel.Quorum.ALL, null, user(BT, "b"))));
        expectThrows(ApprovalIncompleteException.class, () -> workflow(List.of(rule(4, "Default Rule", 1, true, 8)), Map.of(8, h), sim,
                new Users(), PostedState.POSTED, true, new AtomicInteger()).execute(request("5")));
    }

    @Test
    public void approverWithoutAnySessionOrCredentialsFailsClearly() {
        Users users = new Users();
        users.withCredentials.clear();
        ApprovalAuthenticationException e = expectThrows(ApprovalAuthenticationException.class,
                () -> run("500", simFor(stagingHierarchies().get(8)), users, PostedState.POSTED));
        assertTrue(e.getMessage().contains("eligible approver") || e.getMessage().contains("credentials"), e.getMessage());
    }

    // ---- the WEBAPI strategy ---------------------------------------------------------------------------------------------------------------

    private StubApiServer server;
    private ApiApprovalStrategy api;
    private final ApproverSession bt = new ApproverSession(new ApproverIdentity(BT, "bt", "Barbara"), null, ApproverSession.Source.NEW_LOGIN, null);

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
        api = new ApiApprovalStrategy(20, s -> new ApprovalApiClient(server.spec()));
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        server.reset();
    }

    private static final String INBOX = "{\"result\":{\"totalCount\":2,\"totalItem\":0,\"items\":["
            + "{\"taskCode\":227,\"instanceCode\":138,\"entityCode\":\"AP\",\"refCode\":\"640989\",\"schemeDocNo\":\"VP/02718/26-27\","
            + "\"docAmount\":300.000,\"levelNo\":1,\"totalLevels\":1,\"isFinalLevel\":true,\"approvedCount\":0,\"quorumReq\":1,"
            + "\"waitingOnDisplay\":\"Aritra Lahiri\",\"actionTaken\":null,\"instanceStatus\":null},"
            + "{\"taskCode\":225,\"instanceCode\":137,\"entityCode\":\"AP\",\"refCode\":\"640983\",\"schemeDocNo\":\"VP/02717/26-27\","
            + "\"levelNo\":2,\"totalLevels\":3,\"isFinalLevel\":false,\"approvedCount\":1,\"quorumReq\":2,\"instanceStatus\":\"INPROGRESS\"}]},"
            + "\"success\":true,\"error\":null}";

    @Test
    public void pendingTasksAreMappedFromTheInboxRows() {
        server.respond("POST", "/ApprovalInbox/GetPending", 200, INBOX);

        List<ApprovalTask> tasks = api.pending(bt, "AP");

        assertEquals(tasks.size(), 2);
        ApprovalTask first = tasks.get(0);
        assertEquals(first.getTaskCode(), 227);
        assertEquals(first.getDocumentCode(), "640989");
        assertEquals(first.getDocumentNumber(), "VP/02718/26-27");
        assertTrue(first.isFinalLevel());
        assertEquals(tasks.get(1).getApprovedCount(), 1);
        assertEquals(tasks.get(1).getQuorumRequired(), 2);
        assertTrue(server.last().body().contains("\"entityCode\":\"AP\""), server.last().body());
        assertTrue(first.isFor(request("1")));
    }

    @Test
    public void approvePostsTheTaskAndRemarksAndAcceptsTrue() {
        server.respond("POST", "/ApprovalAction/Approve", 200, "{\"result\":true,\"success\":true,\"error\":null}");
        api.approve(bt, ApprovalTask.builder().taskCode(227).entityCode("AP").build(), "");
        assertTrue(server.last().body().contains("\"taskCode\":227"), server.last().body());
        assertTrue(server.last().body().contains("\"remarks\":\"\""), server.last().body());
    }

    @Test
    public void approveFailuresKeepTheirKind() {
        ApprovalTask task = ApprovalTask.builder().taskCode(227).entityCode("AP").build();

        server.respond("POST", "/ApprovalAction/Approve", 200, "{\"result\":false,\"success\":true,\"error\":null}");
        assertEquals(expectThrows(ApprovalActionException.class, () -> api.approve(bt, task, "")).getKind(), ApprovalActionException.Kind.REJECTED);

        server.respond("POST", "/ApprovalAction/Approve", 401, "{\"message\":\"x\"}");
        assertEquals(expectThrows(ApprovalActionException.class, () -> api.approve(bt, task, "")).getKind(), ApprovalActionException.Kind.STALE_SESSION);

        server.respond("POST", "/ApprovalAction/Approve", 500, "{\"message\":\"x\"}");
        assertEquals(expectThrows(ApprovalActionException.class, () -> api.approve(bt, task, "")).getKind(), ApprovalActionException.Kind.AMBIGUOUS);

        server.respond("POST", "/ApprovalAction/Approve", 400, "{\"message\":\"x\"}");
        assertEquals(expectThrows(ApprovalActionException.class, () -> api.approve(bt, task, "")).getKind(), ApprovalActionException.Kind.REJECTED);
    }

    @Test
    public void archiveRowsCarryTheInstanceStatus() {
        server.respond("POST", "/ApprovalInbox/GetArchive", 200,
                "{\"result\":{\"totalCount\":1,\"items\":[{\"taskCode\":227,\"refCode\":\"640989\",\"schemeDocNo\":\"VP/02718/26-27\","
                        + "\"levelNo\":1,\"actionTaken\":\"APPROVED\",\"instanceStatus\":\"APPROVED\"}]},\"success\":true}");
        assertTrue(api.archive(bt, "AP").get(0).isApprovedInstance());
    }

    // ---- the application's approval path (GetApprovalPath) ---------------------------------------------------------------------------------

    @Test
    public void withTheApplicationsPathTheLevelIsApprovedAndTheSameRuleIsConfirmed() {
        Capture capture = new Capture();
        reporter = capture;
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.pathEnabled = true;

        ApprovalWorkflowResult r = run("500", sim, new Users(), PostedState.POSTED);

        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertEquals(sim.log, List.of("L1:" + BT));
        assertTrue(capture.infos.stream().anyMatch(i -> i.startsWith("The application applied the same rule")), capture.infos.toString());
        reporter = ApprovalReporter.NONE;
    }

    @Test
    public void aDifferentRuleOnTheApplicationsSideIsReportedAsAMismatch() {
        Capture capture = new Capture();
        reporter = capture;
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.pathEnabled = true;
        sim.pathRule = "Some other rule";

        run("500", sim, new Users(), PostedState.POSTED);

        assertTrue(capture.infos.stream().anyMatch(i -> i.startsWith("MISMATCH with the application")), capture.infos.toString());
        reporter = ApprovalReporter.NONE;
    }

    @Test
    public void aLevelThePathAlreadyShowsSatisfiedNeedsNoApproval() {
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.pathEnabled = true;
        sim.approvedAtLevel.put(1, new HashSet<>(Set.of(ARITRA)));
        sim.done = true;

        ApprovalWorkflowResult r = run("500", sim, new Users(), PostedState.POSTED);

        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
        assertTrue(sim.log.isEmpty(), "nobody approved: the application already had the approval");
        assertTrue(r.getApprovedBy().isEmpty());
    }

    @Test
    public void withThePathACustomQuorumOfTwoStopsWhenTheApplicationSaysSatisfied() {
        ApprovalHierarchy h = hierarchy(8, level(1, ApprovalLevel.Quorum.CUSTOM, 2, role(1161)));
        Sim sim = simFor(h);
        sim.pathEnabled = true;

        ApprovalWorkflowResult r = workflow(List.of(rule(4, "Default Rule", 1, true, 8)), Map.of(8, h), sim, new Users(), PostedState.POSTED, true, new AtomicInteger())
                .execute(request("5"));

        assertEquals(sim.log, List.of("L1:" + BT, "L1:" + ARITRA));
        assertEquals(r.getStatus(), ApprovalStatus.APPROVED);
    }

    @Test
    public void anApprovalTheApplicationNeverRegistersFailsInsteadOfMovingOn() {
        Sim sim = simFor(stagingHierarchies().get(8));
        sim.pathEnabled = true;
        sim.pathLags = true;
        ApprovalIncompleteException e = expectThrows(ApprovalIncompleteException.class, () -> run("500", sim, new Users(), PostedState.POSTED));
        assertTrue(e.getMessage().contains("did not register"), e.getMessage());
        assertEquals(sim.log.size(), 1, "only one approval was attempted - no second approver was asked");
    }

    @Test
    public void thePathIsMappedFromTheApplicationsResponse() {
        server.respond("GET", "/ApprovalAction/GetApprovalPath", 200, com.tests.API.support.WireAssert.sample("approval/approval-path-response.json"));

        ApprovalPath path = api.path(bt, "AP", "641432").orElseThrow();

        assertEquals(path.getRuleName(), "Below 10L");
        assertEquals(path.getHierarchyName(), "AP Voucher < 10L");
        assertEquals(path.level(1).orElseThrow().getQuorumRequired(), 1);
        assertFalse(path.level(1).orElseThrow().isSatisfied());
        assertFalse(path.isApproved());
        assertTrue(server.last().query().contains("refCode=641432"), server.last().query());
    }

    @Test
    public void aSatisfiedLevelIsRecognisedByStatusOrByApprovalCount() {
        assertTrue(ApprovalPath.Level.builder().levelNo(1).quorumRequired(2).approvedCount(0).status("APPROVED").build().isSatisfied());
        assertTrue(ApprovalPath.Level.builder().levelNo(1).quorumRequired(2).approvedCount(2).status("PENDING").build().isSatisfied());
        assertFalse(ApprovalPath.Level.builder().levelNo(1).quorumRequired(2).approvedCount(1).status("PENDING").build().isSatisfied());
    }

    @Test
    public void anUnreadablePathIsNotAFailure() {
        server.respond("GET", "/ApprovalAction/GetApprovalPath", 404, "{\"message\":\"x\"}");
        assertTrue(api.path(bt, "AP", "1").isEmpty());
        server.respond("GET", "/ApprovalAction/GetApprovalPath", 200, "{\"result\":null,\"success\":true}");
        assertTrue(api.path(bt, "AP", "1").isEmpty());
    }

    @Test
    public void theStatusEndpointIsReadable() {
        server.respond("GET", "/ApprovalAction/GetStatus", 200,
                "{\"result\":{\"status\":\"PENDING\",\"label\":\"Pending for Approval\",\"instanceCode\":null,\"currentLevelNo\":null},\"success\":true,\"error\":null}");
        assertEquals(new ApprovalApiClient(server.spec()).getStatus("AP", "641432").getStatus(), "PENDING");
    }
}
