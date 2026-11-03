package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalHierarchy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;

import java.util.List;

/**
 * Loads a hierarchy and validates it can be worked through: it is active, its levels run 1..N without gaps, every level has approvers and a
 * usable quorum. An unusable hierarchy is a reported configuration error, never silently skipped.
 */
public class ApprovalHierarchyService {

    private final ApprovalHierarchyGateway gateway;

    public ApprovalHierarchyService(ApprovalHierarchyGateway gateway) {
        this.gateway = gateway;
    }

    /** The hierarchy, validated. */
    public ApprovalHierarchy getHierarchy(int hierarchyCode) {
        ApprovalHierarchy hierarchy = gateway.fetchHierarchy(hierarchyCode);
        if (hierarchy == null) {
            throw new ApprovalConfigurationException(Kind.NOT_FOUND, "Approval hierarchy " + hierarchyCode + " was not returned");
        }
        assertActive(hierarchy);
        validateLevels(hierarchy);
        return hierarchy;
    }

    public void assertActive(ApprovalHierarchy hierarchy) {
        if (!hierarchy.isActive()) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                    "Approval hierarchy " + hierarchy.getCode() + " ('" + hierarchy.getName() + "') is not active");
        }
    }

    /** {@code true} when nobody has to approve: the hierarchy has no levels. */
    public boolean isAutoApprove(ApprovalHierarchy hierarchy) {
        return hierarchy.isAutoApprove();
    }

    private static void validateLevels(ApprovalHierarchy hierarchy) {
        List<ApprovalLevel> levels = hierarchy.getLevels();
        for (int i = 0; i < levels.size(); i++) {
            ApprovalLevel level = levels.get(i);
            String where = "hierarchy " + hierarchy.getCode() + " ('" + hierarchy.getName() + "') level " + level.getLevelNo();
            if (level.getLevelNo() != i + 1) {
                throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                        where + " is out of sequence: expected level " + (i + 1));
            }
            if (level.getApprovers().isEmpty()) {
                throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION, where + " has no approvers");
            }
            if (level.getQuorum() == ApprovalLevel.Quorum.CUSTOM) {
                validateCustomQuorum(level, where);
            }
        }
    }

    private static void validateCustomQuorum(ApprovalLevel level, String where) {
        Integer count = level.getQuorumCount();
        if (count == null || count < 1) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION, where + " has a custom quorum without a valid count");
        }
        boolean onlyUsers = level.getApprovers().stream().noneMatch(ApprovalApprover::isRole);
        if (onlyUsers && count > level.getApprovers().size()) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                    where + " needs " + count + " approvals but names only " + level.getApprovers().size() + " users");
        }
    }
}
