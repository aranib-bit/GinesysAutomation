package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** An approval hierarchy: ordered levels, or none for the system "Auto Approve" hierarchy. */
@Value
@Builder
public class ApprovalHierarchy {
    int code;
    String name;
    boolean active;
    Integer versionNo;
    /** Ordered by level number, 1..N without gaps (validated by {@code ApprovalHierarchyService}). */
    List<ApprovalLevel> levels;

    /** No levels: documents are approved as soon as they are sent for approval, nobody has to act. */
    public boolean isAutoApprove() {
        return levels.isEmpty();
    }
}
