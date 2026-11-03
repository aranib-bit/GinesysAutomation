package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gets a session acting as an approver. Order: a live session of that exact user (a UI browser, or an API session another flow holds) is
 * reused - no login, nobody is logged out; only when none exists does it log in with the user's configured credentials. Never uses the
 * document creator's session for another user, and never logs a password or token. Sessions this service logged in are ended by
 * {@link #releaseAll()}; reused ones are left to their owner.
 */
public class ApproverSessionService {

    private final List<LiveSessionSource> liveSources;
    private final ApproverCredentialSource credentials;
    private final ApproverLoginStrategy loginStrategy;
    private final List<ApproverSession> created = new ArrayList<>();

    public ApproverSessionService(List<LiveSessionSource> liveSources, ApproverCredentialSource credentials, ApproverLoginStrategy loginStrategy) {
        this.liveSources = List.copyOf(liveSources);
        this.credentials = credentials;
        this.loginStrategy = loginStrategy;
    }

    /** A session for exactly {@code approver}: a live one if any source has it, otherwise a fresh login. */
    public ApproverSession acquire(ApproverIdentity approver) {
        Optional<ApproverSession> live = findLive(approver);
        if (live.isPresent()) {
            ReportLogger.session("[Approval] Reusing the live {} session of approver '{}' - no login.", live.get().source(), approver.getUsername());
            return live.get();
        }
        return acquireFresh(approver);
    }

    /**
     * Logs {@code approver} in without looking for a live session: for when a reused session turned out to be stale (HTTP 401). A refused
     * login for "already logged in elsewhere" is handled by the existing login (KillUser, retry once), exactly as for any other login.
     */
    public ApproverSession acquireFresh(ApproverIdentity approver) {
        UserCredential credential = credentials.credentialFor(approver).orElseThrow(() -> new ApprovalAuthenticationException(
                "No credentials for approver '" + approver.getUsername() + "' (user " + approver.getUserCode()
                        + "): add the user to this environment's UsersList.xlsx"));
        ApproverSession session = loginStrategy.login(approver, credential);
        synchronized (created) {
            created.add(session);
        }
        return session;
    }

    /**
     * The first candidate that has a live session (cheapest, no login), otherwise the first one that can log in. For a role with many users
     * this picks someone who is already usable instead of logging a new user in.
     */
    public ApproverSession acquireFirstAvailable(List<ApproverIdentity> candidates) {
        for (ApproverIdentity candidate : candidates) {
            Optional<ApproverSession> live = findLive(candidate);
            if (live.isPresent()) {
                return live.get();
            }
        }
        ApprovalAuthenticationException last = null;
        List<String> reasons = new ArrayList<>();
        for (ApproverIdentity candidate : candidates) {
            if (credentials.credentialFor(candidate).isEmpty()) {
                reasons.add("'" + candidate.getUsername() + "' (user " + candidate.getUserCode() + ") has no live session and no row in this environment's UsersList.xlsx");
                continue;
            }
            try {
                return acquire(candidate);
            } catch (ApprovalAuthenticationException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new ApprovalAuthenticationException("No eligible approver can act: " + (reasons.isEmpty() ? "none was offered" : String.join("; ", reasons)));
    }

    /** Ends every session this service logged in. Reused sessions are not touched. */
    public void releaseAll() {
        List<ApproverSession> toClose;
        synchronized (created) {
            toClose = List.copyOf(created);
            created.clear();
        }
        toClose.forEach(ApproverSession::close);
    }

    private Optional<ApproverSession> findLive(ApproverIdentity approver) {
        for (LiveSessionSource source : liveSources) {
            Optional<ApproverSession> found = source.find(approver);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
