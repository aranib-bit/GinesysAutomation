package com.tests;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.ApiPlatform;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Base class for REST API tests. No WebDriver is created.
 *
 * <h3>User acquisition</h3>
 * Acquires a SET-1 UI user from {@link UserPool} (WEBAPI login is the same account a browser
 * login uses, drawn from UsersList.xlsx's UI users — not a separate API-type pool; this
 * environment has zero SET1 API users configured) and builds a RestAssured
 * {@link RequestSpecification}. The user is released in {@code tearDown()} which
 * runs with {@code alwaysRun = true} so it executes even when the test fails.
 *
 * <h3>setUp guard</h3>
 * If any step after {@code acquireSet1User()} throws, the acquired user is
 * released immediately so other threads are not starved.
 *
 * <h3>Platform targeting</h3>
 * Override {@link #targetPlatform()} to run against PUBLICAPI (OAuth2 client-credentials, no
 * UserPool) instead of the default WEBAPI (UserPool-backed, via UserType.UI). {@link #session()}
 * exposes the five k6-flow API clients (Item/GRC/DC/TransferOut/ServiceInvoiceAdhoc); it is
 * built lazily on first call, so subclasses that never call it never pay for the extra login
 * round trip — {@code requestSpec} stays exactly the basic-auth spec it always was.
 */
public abstract class APITest extends BaseTest {

    private static final ThreadLocal<Boolean> apiUserFromPool =
            ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<ApiSessionBundle> lazySession = new ThreadLocal<>();

    protected RequestSpecification requestSpec;

    /** Override to target PUBLICAPI. Default keeps every existing subclass's behavior unchanged. */
    protected ApiPlatform targetPlatform() {
        return ApiPlatform.WEBAPI;
    }

    @BeforeMethod
    public void setUp(Method method) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        if (targetPlatform() == ApiPlatform.WEBAPI) {
            // UserPool-backed, basic auth. Draws from UserType.UI (the UsersList.xlsx UI-user
            // pool) — WEBAPI login is the same account a browser login uses, not a separate
            // API-type pool; this environment has zero SET1 API users configured, so acquiring
            // UserType.API here always threw a SkipException before this fix. session() (below)
            // builds the richer ApiSessionBundle lazily, only if a subclass actually asks for it.
            UserCredential apiUser = UserPool.acquireSet1User(UserType.UI, method.getName());
            apiUserFromPool.set(true);

            try {
                DriverManager.setApiUser(apiUser);
                TestContextManager.setApiUser(apiUser.username());
                requestSpec = RestAssured.given()
                        .baseUri(config.url())
                        .auth().basic(apiUser.username(), apiUser.password());
            } catch (Exception e) {
                long tid = Thread.currentThread().getId();
                Log.warn("[APITest] [T-{}] setUp threw after API user acquire — returning user to pool. Cause: {}",
                        tid, e.getMessage());
                if (Boolean.TRUE.equals(apiUserFromPool.get())) {
                    UserPool.releaseSet1User(apiUser);
                    apiUserFromPool.set(false);
                }
                DriverManager.unloadApiUser();
                TestContextManager.clear();
                throw e;
            }
        } else {
            // PUBLICAPI — no UserPool involvement at all; OAuth2 client-credentials from config.
            apiUserFromPool.set(false);
            ApiSessionBundle publicSession = ApiSessionFactory.createForPublicApi();
            lazySession.set(publicSession);
            requestSpec = publicSession.getBaseSpec();
        }
    }

    /**
     * Flow-client access (Item/GRC/DC/TransferOut/ServiceInvoiceAdhoc), same shape regardless of
     * {@link #targetPlatform()}. For WEBAPI this performs the real two-token login on first call
     * — deliberately lazy so subclasses that never call it never pay for it.
     */
    protected ApiSessionBundle session() {
        ApiSessionBundle cached = lazySession.get();
        if (cached != null) {
            return cached;
        }
        ApiSessionBundle built = ApiSessionFactory.createForWebApi(DriverManager.getApiUser());
        lazySession.set(built);
        return built;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        long tid = Thread.currentThread().getId();

        // Snapshot before any cleanup — DriverManager may be unloaded by the time the
        // release call runs if anything in the chain throws.
        UserCredential api      = DriverManager.getApiUser();
        boolean        fromPool = Boolean.TRUE.equals(apiUserFromPool.get());

        try {
            ApiSessionBundle cached = lazySession.get();
            if (cached != null) {
                cached.logout(); // no-op for PUBLICAPI, real Session/Logout for WEBAPI
            }
        } catch (Exception e) {
            Log.warn("[APITest] [T-{}] session logout threw: {}", tid, e.getMessage());
        }

        try {
            if (api != null && fromPool) {
                UserPool.releaseSet1User(api);
                Log.info("[APITest] [T-{}] API user '{}' released.", tid, api.username());
            }
        } catch (Exception e) {
            Log.warn("[APITest] [T-{}] User pool release threw: {}", tid, e.getMessage());
        } finally {
            apiUserFromPool.remove();
            lazySession.remove();
            DriverManager.unloadApiUser();
            TestContextManager.clear();
        }
    }
}
