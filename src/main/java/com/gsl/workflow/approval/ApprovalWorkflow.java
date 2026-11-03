package com.gsl.workflow.approval;

import com.gsl.approval.model.ApprovalEntity;
import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.ApprovalWorkflowResult;
import com.gsl.approval.reporting.ApprovalReporter;
import com.gsl.approval.service.ApprovalEligibilityService;
import com.gsl.reports.ReportLogger;

/**
 * The one entry point flows call after a document was saved successfully. Reads whether approval is enabled for the entity and returns
 * {@code SKIPPED} when it is explicitly disabled; otherwise hands the document to the {@link ApprovalOrchestrator} (rule, hierarchy,
 * approvals, posted check). Built without an orchestrator it only decides eligibility and returns {@code ELIGIBLE}. A configuration/API
 * failure is thrown, not returned as a status.
 */
public class ApprovalWorkflow {

    private final ApprovalEligibilityService eligibility;
    private final ApprovalOrchestrator orchestrator;
    private final ApprovalReporter reporter;

    /** Eligibility only (Phase 1 behaviour). */
    public ApprovalWorkflow(ApprovalEligibilityService eligibility) {
        this(eligibility, null, ApprovalReporter.NONE);
    }

    public ApprovalWorkflow(ApprovalEligibilityService eligibility, ApprovalOrchestrator orchestrator, ApprovalReporter reporter) {
        this.eligibility = eligibility;
        this.orchestrator = orchestrator;
        this.reporter = reporter;
    }

    public ApprovalWorkflowResult execute(ApprovalRequest request) {
        // The service validates the request (null, blank entity) before anything below dereferences it.
        ApprovalEntity entity = reporter.call("Eligibility", () -> eligibility.getEntityConfiguration(request));
        ReportLogger.business("[Approval] Workflow for entity={} document={}", request.getEntityCode(), request.getDocumentNumber());
        if (!Boolean.TRUE.equals(entity.getApprovalEnabled())) {
            reporter.info("Approval is disabled for " + request.getEntityCode() + " - workflow skipped");
            return ApprovalWorkflowResult.skipped(request, "Approval is disabled for " + request.getEntityCode());
        }
        reporter.info("Approval is enabled for " + request.getEntityCode() + " (enabled by " + entity.getEnabledByName() + " on " + entity.getEnabledOn() + ")");
        if (orchestrator == null) {
            return ApprovalWorkflowResult.eligible(request, "Approval is enabled; the approval itself is not wired in this workflow");
        }
        return orchestrator.run(request, entity);
    }
}
