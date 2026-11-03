package com.gsl.approval.model;

import lombok.Value;

/**
 * A user who may approve: the application's user code and LOGIN name (shown in reports, never a password or token). The employee number is kept
 * for display only; it is not used to look up credentials.
 */
@Value
public class ApproverIdentity {
    int userCode;
    String username;
    String displayName;
    String employeeNo;

    public ApproverIdentity(int userCode, String username, String displayName) {
        this(userCode, username, displayName, null);
    }

    public ApproverIdentity(int userCode, String username, String displayName, String employeeNo) {
        this.userCode = userCode;
        this.username = username;
        this.displayName = displayName;
        this.employeeNo = employeeNo;
    }
}
