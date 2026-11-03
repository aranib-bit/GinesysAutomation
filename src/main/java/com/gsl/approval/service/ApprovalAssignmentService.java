package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApproverIdentity;
import com.gsl.approval.model.DirectoryUser;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a hierarchy level's approver slots into the users who can act: a named user becomes that user (login name read from the directory),
 * a role becomes every active user holding it. Extinct approvers and extinct users are left out. A level that ends up with fewer users than
 * its quorum needs is a reported configuration error.
 */
public class ApprovalAssignmentService {

    private final UserDirectory directory;

    public ApprovalAssignmentService(UserDirectory directory) {
        this.directory = directory;
    }

    /** The users who can approve {@code level}, without duplicates, in slot order. */
    public List<ApproverIdentity> resolveApprovers(ApprovalLevel level) {
        Map<Integer, ApproverIdentity> byCode = new LinkedHashMap<>();
        for (ApprovalApprover slot : level.getApprovers()) {
            if (slot.isExtinct()) {
                continue;
            }
            if (slot.isRole()) {
                for (DirectoryUser user : directory.activeUsersOfRole(slot.getRoleId())) {
                    byCode.putIfAbsent(user.getUserCode(), user.toIdentity());
                }
            } else {
                DirectoryUser user = directory.byUserCode(slot.getUserCode()).orElseThrow(() -> new ApprovalConfigurationException(
                        Kind.NOT_FOUND, "Approver user code " + slot.getUserCode() + " ('" + slot.getName() + "') is not in the user directory"));
                if (!user.isExtinct()) {
                    byCode.putIfAbsent(user.getUserCode(), user.toIdentity());
                }
            }
        }
        List<ApproverIdentity> approvers = List.copyOf(byCode.values());
        if (approvers.size() < level.requiredApprovals()) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION, "Level " + level.getLevelNo() + " needs "
                    + level.requiredApprovals() + " approval(s) but only " + approvers.size() + " active approver(s) can act");
        }
        return approvers;
    }
}
