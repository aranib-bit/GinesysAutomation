package com.gsl.approval.auth;

import com.gsl.api.core.ApiConfigAccess;
import com.gsl.api.core.SessionTokens;
import com.gsl.api.core.WebApiLoginResult;
import com.gsl.api.core.WebApiTokenAuthStrategy;
import com.gsl.approval.model.ApproverIdentity;

import java.util.Optional;

/** Reuses the WEBAPI tokens of a UI browser that is already logged in as the approver ({@link SessionTokens}). */
public class BrowserLiveSessions implements LiveSessionSource {

    @Override
    public Optional<ApproverSession> find(ApproverIdentity approver) {
        SessionTokens.Captured tokens = SessionTokens.findByUserId(String.valueOf(approver.getUserCode()));
        if (tokens == null) {
            return Optional.empty();
        }
        var auth = WebApiTokenAuthStrategy.authResultFor(ApiConfigAccess.webApiBase(),
                WebApiLoginResult.builder().authorizationToken(tokens.authorizationToken())
                        .ginesysAuthToken(tokens.ginesysAuthToken()).userId(tokens.userId()).sessionId(null).build());
        return Optional.of(new ApproverSession(approver, auth.getSpec(), ApproverSession.Source.BROWSER_TOKENS, null));
    }
}
