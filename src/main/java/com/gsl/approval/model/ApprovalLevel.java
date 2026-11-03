package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** One sequential level of a hierarchy: every level must be satisfied, in order, before the document is approved. */
@Value
@Builder
public class ApprovalLevel {

    /** How many approvals satisfy the level. */
    public enum Quorum { ALL, CUSTOM }

    int levelNo;
    String label;
    Quorum quorum;
    /** The approvals a {@link Quorum#CUSTOM} level needs; null for {@link Quorum#ALL}. */
    Integer quorumCount;
    Integer dueDays;
    List<ApprovalApprover> approvers;

    /** Approvals needed to move to the next level: every approver slot for ALL, the custom number for CUSTOM. */
    public int requiredApprovals() {
        return quorum == Quorum.ALL ? approvers.size() : quorumCount;
    }
}
