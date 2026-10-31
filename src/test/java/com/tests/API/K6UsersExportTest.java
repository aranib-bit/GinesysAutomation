package com.tests.API;

import com.gsl.api.bulk.K6UsersExport;
import com.gsl.api.core.DataSetupException;
import com.gsl.config.ConfigFactory;
import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.LoginType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserCredentialLoader;
import com.gsl.testdata.UserType;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * What k6 receives from UsersList.xlsx: only PERF users, with their login type and the SSO URL; refused (with the fix named) when there is no PERF
 * user, a password is missing, or an SSO user has no SSO URL. Run with {@code -Dk6.users.write=true -Denv=<env>} to write the real users file of
 * the environment for a standalone k6 run (the path is printed).
 */
public class K6UsersExportTest {

    private static UserCredential user(String name, String pw, LoginType login, UserType... types) {
        return new UserCredential(name, pw, Set.of(types), login, ExecutionSet.SET1, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onlyPerfUsersAreExportedWithTheirLoginType() {
        Map<String, Object> file = K6UsersExport.render(List.of(
                user("webapi1", "a", LoginType.ERP, UserType.UI, UserType.WEBAPI),
                user("webui1", "b", LoginType.ERP, UserType.UI),
                user("perftest1", "perftest1", LoginType.ERP, UserType.PERF),
                user("perftest2", "perftest2", LoginType.SSO, UserType.PERF)), "clone-stage-ideal", " https://sso.example/login ");
        assertEquals(file.get("tenant"), "clone-stage-ideal");
        assertEquals(file.get("ssoLoginUrl"), "https://sso.example/login");
        List<Map<String, Object>> users = (List<Map<String, Object>>) file.get("users");
        assertEquals(users.size(), 2, "UI and WEBAPI accounts never go to a load test");
        assertEquals(users.get(0).get("username"), "perftest1");
        assertEquals(users.get(1).get("loginType"), "SSO");
    }

    @Test
    public void noPerfUserIsRefusedWithTheFixNamed() {
        DataSetupException e = expectThrows(DataSetupException.class,
                () -> K6UsersExport.render(List.of(user("webui1", "b", LoginType.ERP, UserType.UI)), "t", ""));
        assertTrue(e.getMessage().contains("usertype=[PERF]"), e.getMessage());
    }

    @Test
    public void aPerfUserWithoutAPasswordIsRefused() {
        DataSetupException e = expectThrows(DataSetupException.class,
                () -> K6UsersExport.render(List.of(user("perftest9", " ", LoginType.ERP, UserType.PERF)), "t", ""));
        assertTrue(e.getMessage().contains("perftest9"), e.getMessage());
    }

    @Test
    public void anSsoUserWithoutAnSsoUrlFailsFastInsteadOfFallingBackToErp() {
        List<UserCredential> sso = List.of(user("perftest3", "p", LoginType.SSO, UserType.PERF));
        DataSetupException e = expectThrows(DataSetupException.class, () -> K6UsersExport.render(sso, "t", " "));
        assertTrue(e.getMessage().contains("webapi.sso.loginUrl"), e.getMessage());
        assertFalse(K6UsersExport.render(sso, "t", "https://sso.example/login").isEmpty());
    }

    @Test
    public void theActiveEnvironmentsWorkbookCanBeExported() throws Exception {
        List<UserCredential> users = UserCredentialLoader.loadAll();
        if (users.stream().noneMatch(u -> u.has(UserType.PERF))) return; // an environment without perf accounts yet
        String tenant = com.gsl.api.bulk.K6Scripts.tenant();
        if (Boolean.getBoolean("k6.users.write")) {
            Path written = K6UsersExport.exportFor(tenant);
            System.out.println("[K6UsersExport] wrote " + written);
            assertTrue(Files.isRegularFile(written));
        } else {
            assertFalse(K6UsersExport.render(users, tenant, ConfigFactory.getApiConfig().ssoLoginUrl().isBlank() ? "https://unused" : ConfigFactory.getApiConfig().ssoLoginUrl()).isEmpty());
        }
    }
}