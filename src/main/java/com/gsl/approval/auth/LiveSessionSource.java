package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;

import java.util.Optional;

/** A place that may already hold a logged-in session of an approver (a UI browser, an API session of another flow). */
public interface LiveSessionSource {

    /** A session acting as exactly {@code approver}, or empty when this source holds none. Never logs anyone in. */
    Optional<ApproverSession> find(ApproverIdentity approver);
}
