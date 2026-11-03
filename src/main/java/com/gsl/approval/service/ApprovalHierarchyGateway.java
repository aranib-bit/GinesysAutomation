package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalHierarchy;

/** Reads one hierarchy from the application. The only way {@link ApprovalHierarchyService} reaches it, so it can be faked in tests. */
public interface ApprovalHierarchyGateway {

    /** The hierarchy with its levels and approvers; {@code null} when the application returned none. */
    ApprovalHierarchy fetchHierarchy(int hierarchyCode);
}
