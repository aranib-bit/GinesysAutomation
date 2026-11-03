package com.gsl.approval.executor;

import com.gsl.approval.auth.ApproverSession;
import com.gsl.approval.model.ApprovalPath;
import com.gsl.approval.model.ApprovalTask;

import java.util.List;
import java.util.Optional;

/**
 * How approval actions are performed as an approver: through the WEBAPI now ({@link ApiApprovalStrategy}), through the Approval Inbox screen
 * later. The services above only use this interface, so the same workflow, rule logic and report serve both. Which strategy runs is
 * configuration; a failure never switches to the other one on its own.
 */
public interface ApprovalExecutionStrategy {

    /** Every task waiting for {@code session}'s approver, for {@code entityCode}, all pages. */
    List<ApprovalTask> pending(ApproverSession session, String entityCode);

    /** Every task {@code session}'s approver has finished, for {@code entityCode}, all pages. */
    List<ApprovalTask> archive(ApproverSession session, String entityCode);

    /**
     * The application's approval path of one document - rule, hierarchy and each level's quorum and approvals so far - or empty when it cannot
     * be read (no approval request exists yet, or the strategy has no such view). Advisory: callers fall back to counting their own approvals.
     */
    default Optional<ApprovalPath> path(ApproverSession session, String entityCode, String documentCode) {
        return Optional.empty();
    }

    /**
     * Approves {@code task} as the session's approver.
     *
     * @throws ApprovalActionException stale session, ambiguous outcome, or refused
     */
    void approve(ApproverSession session, ApprovalTask task, String remarks);
}
