package com.tests;

import com.gsl.driver.DriverManager;
import com.gsl.driver.MasterDriverFactory;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * Base class for Appium mobile tests.
 * Creates an AppiumDriver via MasterDriverFactory with RunMode.MOBILE.
 *
 * <h3>User acquisition</h3>
 * Acquires a SET-1 UI user from {@link UserPool}.  The user is released in
 * {@code tearDown()} which runs with {@code alwaysRun = true} so it executes
 * even when the test fails.
 *
 * <h3>setUp guard</h3>
 * If driver initialisation throws after the user has been acquired, the user is
 * released immediately so other waiting threads are not starved.
 */
public abstract class MobileTest extends BaseTest {

    private static final ThreadLocal<Boolean> uiUserFromPool =
            ThreadLocal.withInitial(() -> false);

    @BeforeMethod
    public void setUp(Method method) {
        TestContextManager.setTestName(method.getName());
        TestContextManager.setTestId();

        UserCredential uiUser = UserPool.acquireSet1User(UserType.UI, method.getName());
        uiUserFromPool.set(true);

        try {
            DriverManager.setCurrentUser(uiUser);
            TestContextManager.setUiUser(uiUser.username());
            DriverManager.setDriver(MasterDriverFactory.create(RunMode.MOBILE, Browser.CHROME));
        } catch (Exception e) {
            long tid = Thread.currentThread().getId();
            Log.warn("[MobileTest] [T-{}] setUp threw after UI user acquire — returning user to pool. Cause: {}",
                    tid, e.getMessage());
            if (Boolean.TRUE.equals(uiUserFromPool.get())) {
                UserPool.releaseSet1User(uiUser);
                uiUserFromPool.set(false);
            }
            DriverManager.unloadCurrentUser();
            TestContextManager.clear();
            throw e;
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        long tid = Thread.currentThread().getId();

        // Snapshot BEFORE any cleanup — driver.quit() or unloadDriver() may clear state
        // that DriverManager.getCurrentUser() depends on.
        UserCredential ui      = DriverManager.getCurrentUser();
        boolean        fromPool = Boolean.TRUE.equals(uiUserFromPool.get());

        try {
            if (DriverManager.getDriver() != null) {
                DriverManager.getDriver().quit();
                DriverManager.unloadDriver();
            }
        } catch (Exception e) {
            Log.warn("[MobileTest] [T-{}] Driver.quit() threw (driver may already be closed): {}",
                    tid, e.getMessage());
        }

        try {
            if (ui != null && fromPool) {
                UserPool.releaseSet1User(ui);
                Log.info("[MobileTest] [T-{}] UI user '{}' released.", tid, ui.username());
            }
        } catch (Exception e) {
            Log.warn("[MobileTest] [T-{}] User pool release threw: {}", tid, e.getMessage());
        } finally {
            uiUserFromPool.remove();
            DriverManager.unloadCurrentUser();
            TestContextManager.clear();
        }
    }
}
