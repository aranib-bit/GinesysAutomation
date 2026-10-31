package com.gsl.api.bulk;

import com.gsl.api.core.DataSetupException;
import com.gsl.config.ConfigFactory;
import com.gsl.testdata.LoginType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserCredentialLoader;
import com.gsl.testdata.UserType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands the performance accounts of this environment's {@code UsersList.xlsx} to k6 (which cannot read .xlsx): the users marked {@code PERF} are
 * written to {@code <k6.home>/data/generated/users-<tenant>.json} (git-ignored) as {@code {tenant, ssoLoginUrl, users:[{username, password,
 * loginType}]}}. {@link K6Runner#configured()} does this automatically before every launch that did not name a {@code USERS_FILE}.
 * Only PERF users are exported: a UI or WEBAPI account is never given to a load test.
 */
public final class K6UsersExport {

    private K6UsersExport() {}

    /** The users file for {@code tenant}, from the active environment's workbook and {@code webapi.sso.loginUrl}. */
    public static Path exportFor(String tenant) {
        return write(UserCredentialLoader.loadAll(), tenant, ConfigFactory.getApiConfig().ssoLoginUrl());
    }

    public static Path write(List<UserCredential> users, String tenant, String ssoLoginUrl) {
        return BulkDataFiles.write("users", tenant, render(users, tenant, ssoLoginUrl));
    }

    /**
     * The file content. Fails with a message naming the fix when there is no PERF user, a PERF user has no password, or an SSO user is exported
     * while no SSO login URL is configured (nothing is silently downgraded to the ERP login).
     */
    public static Map<String, Object> render(List<UserCredential> users, String tenant, String ssoLoginUrl) {
        List<Map<String, Object>> perf = new ArrayList<>();
        List<String> noPassword = new ArrayList<>();
        boolean sso = false;
        for (UserCredential u : users) {
            if (!u.has(UserType.PERF)) continue;
            if (u.password() == null || u.password().isBlank()) noPassword.add(u.username());
            sso |= u.loginType() == LoginType.SSO;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.username());
            row.put("password", u.password());
            row.put("loginType", u.loginType().name());
            perf.add(row);
        }
        if (perf.isEmpty()) {
            throw new DataSetupException("[K6UsersExport] No PERF user in UsersList.xlsx for tenant '" + tenant
                    + "': add rows with usertype=[PERF] (k6 never uses UI or WEBAPI accounts).");
        }
        if (!noPassword.isEmpty()) {
            throw new DataSetupException("[K6UsersExport] " + noPassword.size() + " PERF user(s) have no password (first: " + noPassword.get(0)
                    + "): fill the password cell or set user.default.password / user.<name>.password in the environment's secrets.");
        }
        String url = ssoLoginUrl == null ? "" : ssoLoginUrl.trim();
        if (sso && url.isEmpty()) {
            throw new DataSetupException("[K6UsersExport] A PERF user has logintype=SSO but webapi.sso.loginUrl is blank for this environment.");
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("tenant", tenant);
        file.put("ssoLoginUrl", url);
        file.put("users", perf);
        return file;
    }
}