package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;
import com.gsl.testdata.UserCredential;

/** How an approver without a live session is logged in. API login now; a UI login arrives with the UI approval strategy. */
public interface ApproverLoginStrategy {

    /** Logs {@code approver} in with {@code credential}; the returned session is a {@code NEW_LOGIN}. */
    ApproverSession login(ApproverIdentity approver, UserCredential credential);
}
