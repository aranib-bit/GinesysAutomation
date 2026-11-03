package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * The application's own view of one document's approval: the rule and hierarchy it applied and, per level, how many approvals it needs and has.
 * The authoritative source for "is this level done", and a check that the framework resolved the same rule the application did.
 */
@Value
@Builder
public class ApprovalPath {
    String status;
    String ruleName;
    String hierarchyName;
    List<Level> levels;

    @Value
    @Builder
    public static class Level {
        int levelNo;
        int quorumRequired;
        int approvedCount;
        String status;

        /** Done when the application marks it approved, or it has the approvals it asked for. */
        public boolean isSatisfied() {
            return "APPROVED".equalsIgnoreCase(status) || (quorumRequired > 0 && approvedCount >= quorumRequired);
        }
    }

    public Optional<Level> level(int levelNo) {
        return levels.stream().filter(l -> l.getLevelNo() == levelNo).findFirst();
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }
}
