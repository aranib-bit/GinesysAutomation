package com.gsl.approval.auth;

import com.gsl.api.core.LiveApiSessions;
import com.gsl.approval.model.ApproverIdentity;
import io.restassured.specification.RequestSpecification;

import java.util.Optional;

/** Reuses a WEBAPI session another flow of this JVM already logged in as the approver ({@link LiveApiSessions}). */
public class ApiLiveSessions implements LiveSessionSource {

    @Override
    public Optional<ApproverSession> find(ApproverIdentity approver) {
        RequestSpecification spec = LiveApiSessions.find(String.valueOf(approver.getUserCode()));
        return spec == null ? Optional.empty()
                : Optional.of(new ApproverSession(approver, spec, ApproverSession.Source.LIVE_API_SESSION, null));
    }
}
