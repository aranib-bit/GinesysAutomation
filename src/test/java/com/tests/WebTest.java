package com.tests;

import com.gsl.browser.CustomMetricsObserverInjector;
import com.gsl.browser.injector.FrontendDiagnosticsInjectorFactory;
import com.gsl.config.ConfigFactory;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.ReservedDocumentKeyAware;
import com.gsl.driver.Driver;
import com.gsl.driver.DriverManager;
import com.gsl.driver.ExecutionContext;
import com.gsl.driver.ExecutionManager;
import com.gsl.driver.FailureClassifier;
import com.gsl.driver.FailureClassifier.FailureCategory;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.testng.ITestResult;
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
    /** True while the current row holds a SessionPool session — routes tearDown to giveBack(). */
    private static final ThreadLocal<Boolean> sessionBorrowed = ThreadLocal.withInitial(() -> false);

    @BeforeMethod
    public void setUp(Method method, Object[] testParams) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        String qualifiedLabel = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // ── SessionPool mode (default): borrow a pooled logged-in session for exactly this row.
        // The pool binds EVERYTHING session-scoped to this thread (driver, ui/api user, network
        // capture, ExecutionContext with nav cache, MDC [user=...]) before returning, so the
        // row's Step-1 SessionManager.ensureLoggedIn() gets a cached HomePage on reuse and
        // mid-row DB loads resolve login → hrdemp.abbre → ecode for the user this row ACTUALLY
        // runs as. tearDown's finally gives the session back after every row — rows from all
        // @Test methods interleave across all pool sessions (work-stealing), no thread ever
        // holds a user between rows.
        UserCredential uiFromParam = extractCredential(testParams, UserType.UI);
        if (uiFromParam == null) {
            com.gsl.driver.SessionPool.borrow(qualifiedLabel);
            sessionBorrowed.set(true);
            ExecutionManager.logState("setUp:borrowed");
            return;
        }

        // ── Legacy branch: DataSupplier supplied the credential directly — per-method driver
        // lifecycle, no pool involvement. (Currently unused — kept for future use.)
        UserCredential uiUser = uiFromParam;
        uiUserFromPool.set(false);
        Log.info("[WebTest] [T-{}] UI user '{}' supplied by DataSupplier.",
                Thread.currentThread().getId(), uiUser.username());

        // ── Guard: release acquired pool users if any subsequent setup step throws ──────────
        // Without this, an exception after acquire() but before tearDown() can snapshot a null
        // user (DriverManager not yet set) and silently leak the user out of the pool forever.
        try {
            DriverManager.setCurrentUser(uiUser);
            TestContextManager.setUiUser(uiUser.username());

            // ── Resolve API user (optional) ───────────────────────────────────────
            UserCredential apiFromParam = extractCredential(testParams, UserType.WEBAPI);
            UserCredential apiUser      = null;

            if (apiFromParam != null) {
                apiUser = apiFromParam;
                apiUserFromPool.set(false);
                Log.info("[WebTest] [T-{}] API user '{}' supplied by DataSupplier.",
                        Thread.currentThread().getId(), apiUser.username());
            } else if (UserPool.hasUsersOfType(UserType.WEBAPI)) {
                // Only acquire from pool when API users are actually configured
                apiUser = UserPool.acquireSet1User(UserType.WEBAPI, qualifiedLabel);
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

            // ── Inject PerformanceObserver listeners for Custom Metrics ───────────
            if (ConfigFactory.getDiagnosticsConfig().customMetricsEnabled()) {
                CustomMetricsObserverInjector.inject(DriverManager.getDriver());
            }

            // ── Inject framework-specific frontend performance instrumentation ────
            if (ConfigFactory.getDiagnosticsConfig().frontendDiagnosticsEnabled()) {
                FrontendDiagnosticsInjectorFactory.get().inject(DriverManager.getDriver());
            }

            ExecutionManager.logState("setUp:freshAcquire");
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

    /**
     * @param result the just-finished <b>test method's</b> result, injected by TestNG.
     *               <p>It must be a parameter, never {@code Reporter.getCurrentTestResult()}: inside an
     *               {@code @AfterMethod}, TestNG has already swapped the thread-local to the
     *               <i>configuration</i> method's own {@code ITestResult}, whose status is
     *               {@code STARTED} — so a {@code getStatus() == FAILURE} check on it is dead code and
     *               the recovery below silently never fires. Confirmed in run 20260807_095328: a
     *               General Voucher test timed out with its modal still open, {@code classify()} was
     *               never called, the dirty session was returned to the pool, and the next three tests
     *               all failed clicking the side menu through the leftover form.
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        long tid = Thread.currentThread().getId();
        ExecutionManager.logState("tearDown:start");

        try {
            // Plan §15a: a data source that claims ALL of a @Test method's rows upfront in one
            // load() pass (DbGeneralVoucherEditDataSource, DbGeneralVoucherRevertDataSource) puts
            // every row's reservation key into this thread's THREAD_RESERVATIONS set before any
            // row runs — releaseCurrentThread() would release ALL of them the moment the FIRST
            // row's tearDown fires, prematurely reopening still-pending sibling rows to a parallel
            // thread (confirmed live: DbGeneralVoucherEditDataSource's own javadoc already
            // documents an "unexplained no-op Save on a claimed voucher" from this exact race).
            // So: if this row's own parameter DTO tells us exactly which document it used
            // (implements ReservedDocumentKeyAware), release ONLY that one key. Every other
            // domain/data source's DTO doesn't implement the interface, so falls straight through
            // to the original releaseCurrentThread() behavior below — unchanged.
            String releasedKey = null;
            Object[] params = result != null ? result.getParameters() : null;
            if (params != null) {
                for (Object p : params) {
                    if (p instanceof ReservedDocumentKeyAware keyed && keyed.getReservedDocumentKey() != null) {
                        releasedKey = keyed.getReservedDocumentKey();
                        break;
                    }
                }
            }
            if (releasedKey != null) {
                DocumentReservationRegistry.release(releasedKey);
            } else {
                DocumentReservationRegistry.releaseCurrentThread();
            }
        } catch (Exception e) {
            Log.warn("[WebTest] [T-{}] DocumentReservationRegistry release threw: {}", tid, e.getMessage());
        }

        // ── SessionPool mode: give the borrowed session back after EVERY row. On a genuine
        // session/browser/navigation failure (never a plain assertion/DB-validation failure —
        // see FailureClassifier), RecoveryManager Level-4 closes the session first and notifies
        // the pool, so the giveBack in the finally just unbinds and drops it; the freed capacity
        // slot lets any waiting thread create a fresh replacement.
        if (Boolean.TRUE.equals(sessionBorrowed.get())) {
            try {
                ExecutionContext ctx = ExecutionContext.current();
                ReportLogger.session("[T-{}] tearDown: returning session to pool — user='{}', module='{}', subModule='{}'.",
                        tid, ctx.getUser() != null ? ctx.getUser().username() : "unknown",
                        ctx.getCurrentModule(), ctx.getCurrentSubModule());
                if (result != null && result.getStatus() == ITestResult.FAILURE) {
                    FailureCategory category = FailureClassifier.classify(result.getThrowable());
                    if (category == FailureCategory.NAVIGATION_FAILURE
                            || category == FailureCategory.SESSION_FAILURE
                            || category == FailureCategory.BROWSER_FAILURE) {
                        com.gsl.driver.RecoveryManager.forceFreshSessionNextTime(category);
                    }
                    // TEST_FAILURE / ENVIRONMENT_FAILURE: no session action — return it for reuse.
                }
            } finally {
                com.gsl.driver.SessionPool.giveBack();
                sessionBorrowed.remove();
                TestContextManager.clear();
            }
            return;
        }

        // ── Legacy per-method mode (test never called SessionManager) — unchanged. ──────────
        // Snapshot users before cleanup so we can release them even if DriverManager is cleared
        UserCredential ui  = DriverManager.getCurrentUser();
        UserCredential api = DriverManager.getApiUser();
        boolean uiFromPool  = Boolean.TRUE.equals(uiUserFromPool.get());
        boolean apiFromPool = Boolean.TRUE.equals(apiUserFromPool.get());

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
            if (p instanceof UserCredential uc && uc.has(type)) return uc;
        }
        return null;
    }
}
