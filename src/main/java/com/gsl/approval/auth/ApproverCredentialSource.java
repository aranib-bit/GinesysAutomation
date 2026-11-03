package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;
import com.gsl.testdata.UserCredential;

import java.util.Optional;

/** Where an approver's login credentials come from. The password never leaves the returned {@link UserCredential}. */
public interface ApproverCredentialSource {

    Optional<UserCredential> credentialFor(ApproverIdentity approver);
}
