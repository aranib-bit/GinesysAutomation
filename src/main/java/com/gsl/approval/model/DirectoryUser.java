package com.gsl.approval.model;

import lombok.Value;

/**
 * One row of the application's user directory: who the user is and which role they hold. {@code username} is the LOGIN name (the directory's
 * {@code abbre}, unique across users); the employee number ({@code eno}) is kept only for display because it is not a safe login - it differs from the
 * login for many users (user 51009: number 7, login bt) and can be an e-mail address. No password is ever read from the directory.
 */
@Value
public class DirectoryUser {
    int userCode;
    /** The login name. */
    String username;
    String name;
    int roleId;
    String roleName;
    boolean extinct;
    /** The employee number (display only); may be null. */
    String employeeNo;

    public DirectoryUser(int userCode, String username, String name, int roleId, String roleName, boolean extinct) {
        this(userCode, username, name, roleId, roleName, extinct, null);
    }

    public DirectoryUser(int userCode, String username, String name, int roleId, String roleName, boolean extinct, String employeeNo) {
        this.userCode = userCode;
        this.username = username;
        this.name = name;
        this.roleId = roleId;
        this.roleName = roleName;
        this.extinct = extinct;
        this.employeeNo = employeeNo;
    }

    public ApproverIdentity toIdentity() {
        return new ApproverIdentity(userCode, username, name, employeeNo);
    }
}
