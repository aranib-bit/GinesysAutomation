package com.gsl.approval.auth;

/** No usable session could be obtained for an approver (no credentials configured, login refused, or the session belongs to someone else). Never carries a password or token. */
public class ApprovalAuthenticationException extends RuntimeException {

    public ApprovalAuthenticationException(String message) {
        super(message);
    }

    public ApprovalAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
