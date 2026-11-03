package com.gsl.approval.service;

/** An approval level could not be completed (nobody left who can approve, or the task never appeared for the approvers). The document stays pending. */
public class ApprovalIncompleteException extends RuntimeException {

    public ApprovalIncompleteException(String message) {
        super(message);
    }
}
