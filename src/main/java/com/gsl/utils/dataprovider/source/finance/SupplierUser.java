package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The login name data discovery uses. Logins come from {@code UsersList.xlsx} ({@link UserPool}); the old
 * {@code appusername} config key is no longer used. A data supplier runs BEFORE any session is acquired, so there
 * is usually no thread user yet: discovery then uses the first pool UI user that exists in {@code hrdemp}. Per-user
 * data is still resolved after login with the real thread user.
 */
public final class SupplierUser {

    private SupplierUser() {}

    /** Thread user if a session is bound, else the first {@link UserType#UI} pool user with an {@code hrdemp} row. */
    public static String username() {
        UserCredential thread = DriverManager.getCurrentUser();
        if (thread != null) return thread.username();

        HrEmpDao dao = DatabaseManager.getJdbi().onDemand(HrEmpDao.class);
        List<String> tried = new ArrayList<>();
        for (String name : UserPool.allUsernamesOfType(UserType.UI)) {
            tried.add(name);
            if (dao.findEcodeByAbbre(name).isPresent()) {
                Log.info("[DB] Supplier-time discovery uses pool user '{}' (no session bound yet)", name);
                return name;
            }
        }
        throw new IllegalStateException("No UsersList.xlsx UI user has an hrdemp record; tried " + tried
                + ". Add a user that exists in hrdemp (abbre) to UsersList.xlsx.");
    }
}
