package com.tests;

import com.gsl.database.DocumentReservationRegistry;
import com.gsl.driver.Driver;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Base class for all Selenium WebDriver tests.
 *
 * <h3>User acquisition</h3>
 * <ol>
 *   <li><b>DataSupplier mode</b> — the test method declares a {@code UserCredential}
 *       parameter.  {@link #setUp} detects it via {@link #extractCredential} and uses it
 *       directly; {@code UserPool} is NOT polled.  (Currently unused — DataSupplier emits
 *       {@code null}, but the hook is kept for future use.)</li>
 *   <li><b>UserPool mode (default)</b> — no credential in test params; setUp calls
 *       {@link UserPool#acquireSet1User} which <em>blocks</em> until a user is free.
 *       This guarantees one user = one active browser session at a time.</li>
 * </ol>
 *
 * <h3>API user</h3>
 * API user acquisition is <b>optional</b>.  If no API users are configured in
 * {@code UsersList.xlsx}, setUp skips API acquire and logs a notice — tests that
 * do not need an API user are unaffected.
 *
 * <h3>Validation</h3>
 * setUp throws a {@code SkipException} when no UI user is available, giving a clear
 * message instead of a NullPointerException at login.
 */
public abstract class WebTest extends BaseTest {

    private static final ThreadLocal<Boolean> uiUserFromPool  = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> apiUserFromPool = ThreadLocal.withInitial(() -> false);

    @BeforeMethod
    public void setUp(Method method, Object[] testParams) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        // ── Resolve UI user ───────────────────────────────────────────────────
        UserCredential uiFromParam = extractCredential(testParams, UserType.UI);
        UserCredential uiUser;

        if (uiFromParam != null) {
            // Credential supplied directly by DataSupplier row — use as-is, skip pool
            uiUser = uiFromParam;
            uiUserFromPool.set(false);
            Log.info("[WebTest] [T-{}] UI user '{}' supplied by DataSupplier.",
                    Thread.currentThread().getId(), uiUser.username());
        } else {
            // Pool mode — blocks until a user is free (one user = one session guarantee)
            uiUser = UserPool.acquireSet1User(UserType.UI);
            uiUserFromPool.set(true);
        }

        // Validation: UI user must always be present
        if (uiUser == null) {
            throw new org.testng.SkipException(
                    "[WebTest] [T-" + Thread.currentThread().getId() + "] " +
                    "No SET1 UI user available. " +
                    "Ensure UsersList.xlsx has rows with userType=UI and executionSet=SET1.");
        }

        // ── Guard: release acquired pool users if any subsequent setup step throws ──────────
        // Without this, an exception after acquire() but before tearDown() can snapshot a null
        // user (DriverManager not yet set) and silently leak the user out of the pool forever.
        try {
            DriverManager.setCurrentUser(uiUser);
            TestContextManager.setUiUser(uiUser.username());

            // ── Resolve API user (optional) ───────────────────────────────────────
            UserCredential apiFromParam = extractCredential(testParams, UserType.API);
            UserCredential apiUser      = null;

            if (apiFromParam != null) {
                apiUser = apiFromParam;
                apiUserFromPool.set(false);
                Log.info("[WebTest] [T-{}] API user '{}' supplied by DataSupplier.",
                        Thread.currentThread().getId(), apiUser.username());
            } else if (UserPool.hasUsersOfType(UserType.API)) {
                // Only acquire from pool when API users are actually configured
                apiUser = UserPool.acquireSet1User(UserType.API);
                apiUserFromPool.set(true);
            } else {
                // No API users configured — this is normal for UI-only tests
                apiUserFromPool.set(false);
                Log.debug("[WebTest] [T-{}] No API users configured — skipping API user acquire.",
                        Thread.currentThread().getId());
            }

            if (apiUser != null) {
                DriverManager.setApiUser(apiUser);
                TestContextManager.setApiUser(apiUser.username());
            }

            // ── Launch browser ────────────────────────────────────────────────────
            Log.info("[WebTest] [T-{}] Starting browser for UI user '{}'.",
                    Thread.currentThread().getId(), uiUser.username());
            Driver.initDriver();

        } catch (Exception e) {
            // Release any pool-acquired users so other waiting threads are not starved
            long tid = Thread.currentThread().getId();
            Log.warn("[WebTest] [T-{}] setUp threw after user acquire — returning users to pool. Cause: {}", tid, e.getMessage());
            if (Boolean.TRUE.equals(uiUserFromPool.get())) {
                UserPool.releaseSet1User(uiUser);
                uiUserFromPool.set(false);
            }
            UserCredential apiSnap = DriverManager.getApiUser();
            if (apiSnap != null && Boolean.TRUE.equals(apiUserFromPool.get())) {
                UserPool.releaseSet1User(apiSnap);
                apiUserFromPool.set(false);
            }
            DriverManager.unloadCurrentUser();
            DriverManager.unloadApiUser();
            TestContextManager.clear();
            throw e;  // re-throw so TestNG records the failure / skip correctly
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        long tid = Thread.currentThread().getId();

        // Snapshot users before cleanup so we can release them even if DriverManager is cleared
        UserCredential ui  = DriverManager.getCurrentUser();
        UserCredential api = DriverManager.getApiUser();
        boolean uiFromPool  = Boolean.TRUE.equals(uiUserFromPool.get());
        boolean apiFromPool = Boolean.TRUE.equals(apiUserFromPool.get());

        try {
            DocumentReservationRegistry.releaseCurrentThread();
        } catch (Exception e) {
            Log.warn("[WebTest] [T-{}] DocumentReservationRegistry.releaseCurrentThread() threw: {}", tid, e.getMessage());
        }

        try {
            Driver.quitDriver();
        } catch (Exception e) {
            Log.warn("[WebTest] [T-{}] Driver.quitDriver() threw (browser may already be closed): {}", tid, e.getMessage());
        }

        // Return users to pool — runs even if Driver.quitDriver() threw
        try {
            if (ui != null) {
                if (uiFromPool) UserPool.releaseSet1User(ui);
                Log.info("[WebTest] [T-{}] UI user '{}' released.", tid, ui.username());
            }
            if (api != null) {
                if (apiFromPool) UserPool.releaseSet1User(api);
                Log.info("[WebTest] [T-{}] API user '{}' released.", tid, api.username());
            }
        } catch (Exception e) {
            Log.warn("[WebTest] [T-{}] User pool release threw: {}", tid, e.getMessage());
        } finally {
            uiUserFromPool.remove();
            apiUserFromPool.remove();
            DriverManager.unloadCurrentUser();
            DriverManager.unloadApiUser();
            TestContextManager.clear();
        }
    }

    /**
     * Scans test parameters for a {@link UserCredential} of the requested type.
     * Returns {@code null} if none found (triggers pool fallback in setUp).
     */
    private static UserCredential extractCredential(Object[] params, UserType type) {
        if (params == null) return null;
        for (Object p : params) {
            if (p instanceof UserCredential uc && uc.userType() == type) return uc;
        }
        return null;
    }
}
