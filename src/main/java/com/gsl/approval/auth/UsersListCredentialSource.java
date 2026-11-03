package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserCredentialLoader;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Approver credentials from the environment's {@code UsersList.xlsx}, matched by the user's LOGIN name (case-insensitive). The employee number is
 * deliberately not tried: it can equal another user's login, which would hand someone else's credentials to the login. Passwords are resolved by
 * {@link UserCredentialLoader} the usual way: {@code Secrets} key {@code user.<name>.password}, then the workbook cell, then the shared default.
 */
public class UsersListCredentialSource implements ApproverCredentialSource {

    private List<UserCredential> users;

    @Override
    public synchronized Optional<UserCredential> credentialFor(ApproverIdentity approver) {
        if (users == null) {
            users = UserCredentialLoader.loadAll();
        }
        String wanted = approver.getUsername().toLowerCase(Locale.ROOT);
        return users.stream().filter(u -> u.username().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }
}
