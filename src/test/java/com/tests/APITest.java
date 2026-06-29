package com.tests;

import com.gsl.driver.DriverManager;
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
 * Acquires a SET-1 API user from {@link UserPool} and builds a RestAssured
 * {@link RequestSpecification}.  The user is released in {@code tearDown()} which
 * runs with {@code alwaysRun = true} so it executes even when the test fails.
 *
 * <h3>setUp guard</h3>
 * If any step after {@code acquireSet1User()} throws, the acquired user is
 * released immediately so other threads are not starved.
 */
public abstract class APITest extends BaseTest {

    private static final ThreadLocal<Boolean> apiUserFromPool =
            ThreadLocal.withInitial(() -> false);

    protected RequestSpecification requestSpec;

    @BeforeMethod
    public void setUp(Method method) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        UserCredential apiUser = UserPool.acquireSet1User(UserType.API, method.getName());
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
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        long tid = Thread.currentThread().getId();

        // Snapshot before any cleanup — DriverManager may be unloaded by the time the
        // release call runs if anything in the chain throws.
        UserCredential api      = DriverManager.getApiUser();
        boolean        fromPool = Boolean.TRUE.equals(apiUserFromPool.get());

        try {
            if (api != null && fromPool) {
                UserPool.releaseSet1User(api);
                Log.info("[APITest] [T-{}] API user '{}' released.", tid, api.username());
            }
        } catch (Exception e) {
            Log.warn("[APITest] [T-{}] User pool release threw: {}", tid, e.getMessage());
        } finally {
            apiUserFromPool.remove();
            DriverManager.unloadApiUser();
            TestContextManager.clear();
        }
    }
}
