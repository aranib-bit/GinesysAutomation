package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

/** One approval task as an approver's inbox shows it: shared by every approver of the level, identified by {@code taskCode}. */
@Value
@Builder
public class ApprovalTask {
    int taskCode;
    String entityCode;
    /** The document's own code (voucher code for AP). */
    String documentCode;
    /** The document number shown to users. */
    String documentNumber;
    int levelNo;
    int totalLevels;
    boolean finalLevel;
    /** Approvals the level already has (so a resumed workflow only adds what is missing). */
    int approvedCount;
    /** Approvals the level needs, as the application counts them. */
    int quorumRequired;
    /** Archive rows only: the whole request's state, e.g. {@code APPROVED}. */
    String instanceStatus;

    /** Whether this task is for the request's document (matched by code, or by number when the code is unknown). */
    public boolean isFor(ApprovalRequest request) {
        if (request.getDocumentCode() != null && documentCode != null) {
            return request.getDocumentCode().equals(documentCode);
        }
        return request.getDocumentNumber() != null && request.getDocumentNumber().equals(documentNumber);
    }

    public boolean isApprovedInstance() {
        return "APPROVED".equalsIgnoreCase(instanceStatus);
    }
}
