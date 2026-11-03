package com.gsl.workflow.approval;

import com.gsl.approval.evaluator.ApprovalEvaluationException;
import com.gsl.approval.evaluator.ApprovalRuleResolver;
import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.model.ApprovalHierarchy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalStatus;
import com.gsl.approval.model.ApprovalWorkflowResult;
import com.gsl.approval.model.PostedState;
import com.gsl.approval.model.RuleDecision;
import com.gsl.approval.model.RuleEvaluation;
import com.gsl.approval.reporting.ApprovalReporter;
import com.gsl.approval.service.ApprovalHierarchyService;
import com.gsl.approval.service.ApprovalLevelService;
import com.gsl.approval.service.ApprovalRuleService;
import com.gsl.approval.validation.ApprovalPostedValidator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs an approval-enabled document through the workflow: resolve the applicable rule from the live rules and the document's values, load
 * its hierarchy, work every level in order, then report whether the document posted. Each stage is one report step. A rule that cannot be
 * evaluated ends the run as {@code CONFIGURATION_ERROR} naming the cause; the document is left pending, never guessed at.
 */
public class ApprovalOrchestrator {

    private final ApprovalRuleService rules;
    private final ApprovalRuleResolver resolver;
    private final ApprovalHierarchyService hierarchies;
    private final ApprovalLevelService levels;
    private final ApprovalPostedValidator posted;
    private final ApprovalReporter reporter;
    private final Supplier<LocalDate> workDate;

    public ApprovalOrchestrator(ApprovalRuleService rules, ApprovalRuleResolver resolver, ApprovalHierarchyService hierarchies,
                                ApprovalLevelService levels, ApprovalPostedValidator posted, ApprovalReporter reporter, Supplier<LocalDate> workDate) {
        this.rules = rules;
        this.resolver = resolver;
        this.hierarchies = hierarchies;
        this.levels = levels;
        this.posted = posted;
        this.reporter = reporter;
        this.workDate = workDate;
    }

    public ApprovalWorkflowResult run(ApprovalRequest request, ApprovalEntity entity) {
        RuleDecision decision;
        try {
            decision = reporter.call("Rule resolution", () -> resolveRule(request));
        } catch (ApprovalEvaluationException e) {
            return ApprovalWorkflowResult.of(ApprovalStatus.CONFIGURATION_ERROR, request, e.getKind() + ": " + e.getMessage());
        }
        if (decision.matched().isEmpty()) {
            return ApprovalWorkflowResult.of(ApprovalStatus.NO_APPLICABLE_RULE, request, "No active approval rule applies to " + request.getDocumentNumber());
        }
        ApprovalRule rule = decision.matched().get();

        ApprovalHierarchy hierarchy = reporter.call("Hierarchy", () -> {
            ApprovalHierarchy h = hierarchies.getHierarchy(rule.getHierarchyCode());
            reporter.info("Hierarchy '" + h.getName() + "': " + (h.isAutoApprove() ? "Auto Approve (no levels)" : h.getLevels().size() + " level(s)"));
            return h;
        });

        List<String> approvedBy = new ArrayList<>();
        for (ApprovalLevel level : hierarchy.getLevels()) {
            String stepName = "Level " + level.getLevelNo() + " of " + hierarchy.getLevels().size()
                    + (level.getLabel() == null ? "" : " - " + level.getLabel());
            approvedBy.addAll(reporter.call(stepName, () -> levels.processLevel(request, level, rule.getName(), hierarchy.getName())));
        }

        PostedState state = reporter.call("Final status", () -> {
            PostedState s = posted.awaitPosted(request);
            reporter.info("Document " + request.getDocumentNumber() + " is " + s);
            return s;
        });
        String message = hierarchy.isAutoApprove() ? "Approved automatically (Auto Approve)" : "Approved at " + hierarchy.getLevels().size() + " level(s)";
        return ApprovalWorkflowResult.of(ApprovalStatus.APPROVED, request, message).toBuilder()
                .ruleName(rule.getName())
                .approvedBy(List.copyOf(approvedBy))
                .postedState(state)
                .build();
    }

    private RuleDecision resolveRule(ApprovalRequest request) {
        List<ApprovalRule> active = rules.getActiveRulesInPriorityOrder(request.getEntityCode());
        RuleDecision decision = resolver.resolve(request.entityType(), active, request.getFacts(), workDate.get());
        for (RuleEvaluation e : decision.getEvaluations()) {
            StringBuilder line = new StringBuilder("Rule ").append(e.getRule().getPriority()).append(" '").append(e.getRule().getName())
                    .append("' -> ").append(e.getOutcome());
            e.getConditions().forEach(c -> line.append(" | ").append(c.getVarCode()).append(' ').append(c.getOperator()).append(' ')
                    .append(c.getExpected()).append(" (document: ").append(c.getActual()).append(") = ").append(c.isMatched()));
            reporter.info(line.toString());
        }
        return decision;
    }
}
