package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

/** One approver slot of a hierarchy level: a named user, or a role whose users may approve. */
@Value
@Builder
public class ApprovalApprover {

    /** What the slot names. */
    public enum Type { USER, ROLE }

    Type type;
    Integer userCode;
    Integer roleId;
    String name;
    boolean extinct;
    /** Users currently holding the role (role slots only; null for users). */
    Integer activeUserCount;

    public boolean isRole() {
        return type == Type.ROLE;
    }
}
