package com.gsl.approval.auth;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.approval.model.ApproverIdentity;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;

/**
 * WEBAPI login through the existing {@code ApiSessionFactory}: the application's own login, and - only when the login is refused with code 012
 * ("already logged in elsewhere") - the existing KillUser-and-retry-once inside {@code WebApiSessionClient}. Nobody is ever logged out up
 * front. The session is checked to be the intended approver before it is used.
 */
public class ApiLoginStrategy implements ApproverLoginStrategy {

    @Override
    public ApproverSession login(ApproverIdentity approver, UserCredential credential) {
        ApiSessionBundle bundle;
        try {
            bundle = ApiSessionFactory.createForWebApi(credential);
        } catch (RuntimeException e) {
            throw new ApprovalAuthenticationException("Login as approver '" + approver.getUsername() + "' failed: " + e.getMessage(), e);
        }
        if (!String.valueOf(approver.getUserCode()).equals(bundle.getUserId())) {
            bundle.logout();
            throw new ApprovalAuthenticationException("Logged in as user " + bundle.getUserId() + " but approver '"
                    + approver.getUsername() + "' is user " + approver.getUserCode() + " - check the credentials in UsersList.xlsx");
        }
        ReportLogger.session("[Approval] Logged in as approver '{}' (user {}).", approver.getUsername(), approver.getUserCode());
        return new ApproverSession(approver, bundle.getBaseSpec(), ApproverSession.Source.NEW_LOGIN, bundle::logout);
    }
}
