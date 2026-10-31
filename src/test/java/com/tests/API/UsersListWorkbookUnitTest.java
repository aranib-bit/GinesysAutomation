package com.tests.API;

import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserCredentialLoader;
import com.gsl.testdata.UserType;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The real UsersList.xlsx of the active environment, read by the real loader: the naming convention and the type rules hold for every row. */
public class UsersListWorkbookUnitTest {

    private static final List<UserCredential> USERS = UserCredentialLoader.loadAll();

    @Test
    public void theWorkbookLoadsAndUsernamesAreUnique() {
        assertTrue(USERS.size() > 0, "no users loaded from the environment's UsersList.xlsx");
        Set<String> names = new HashSet<>();
        for (UserCredential u : USERS) assertTrue(names.add(u.username()), "duplicate username " + u.username());
    }

    @Test
    public void webapiAccountsAreSharedUiAndWebapiLogins() {
        for (UserCredential u : USERS) {
            if (u.username().matches("webapi\\d+")) {
                assertEquals(u.types(), Set.of(UserType.UI, UserType.WEBAPI), u.username());
            }
        }
    }

    @Test
    public void perftestAccountsArePerfOnlyAndNeverShared() {
        for (UserCredential u : USERS) {
            if (u.username().matches("perftest\\d+")) {
                assertEquals(u.types(), Set.of(UserType.PERF), u.username());
                assertTrue(u.password() != null && !u.password().isBlank(), "perf user without a password: " + u.username());
            }
            if (u.has(UserType.PERF)) {
                assertEquals(u.types().size(), 1, "a PERF user must be alone: " + u.username());
            }
        }
    }

    @Test
    public void thereIsAlwaysAUiPoolForTheBrowserTests() {
        assertTrue(USERS.stream().anyMatch(u -> u.has(UserType.UI)), "no UI user configured");
    }
}