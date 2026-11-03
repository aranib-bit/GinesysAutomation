package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** What {@code ApprovalWorkflow.execute} returns to the calling flow. Holds no token, password or header. */
@Value
@Builder(toBuilder = true)
public class ApprovalWorkflowResult {
    ApprovalStatus status;
    String entityCode;
    String documentCode;
    String documentNumber;
    String message;
    /** The rule that applied, when one was resolved. */
    String ruleName;
    /** Users who approved, in order (level by level). */
    @Builder.Default
    List<String> approvedBy = List.of();
    /** Set once the final level is done: whether the document posted. */
    PostedState postedState;

    public static ApprovalWorkflowResult skipped(ApprovalRequest request, String reason) {
        return of(ApprovalStatus.SKIPPED, request, reason);
    }

    public static ApprovalWorkflowResult eligible(ApprovalRequest request, String message) {
        return of(ApprovalStatus.ELIGIBLE, request, message);
    }

    public static ApprovalWorkflowResult of(ApprovalStatus status, ApprovalRequest request, String message) {
        return ApprovalWorkflowResult.builder()
                .status(status)
                .entityCode(request.getEntityCode())
                .documentCode(request.getDocumentCode())
                .documentNumber(request.getDocumentNumber())
                .message(message)
                .build();
    }

    /** {@code true} when the document is approved and, where it can be read, posted. */
    public boolean isCompleted() {
        return status == ApprovalStatus.APPROVED && postedState != PostedState.UNPOSTED;
    }
}
